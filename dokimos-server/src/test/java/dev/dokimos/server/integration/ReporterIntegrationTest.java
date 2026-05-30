package dev.dokimos.server.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dokimos.core.CallMetrics;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.Example;
import dev.dokimos.core.ItemResult;
import dev.dokimos.core.RunHandle;
import dev.dokimos.core.RunStatus;
import dev.dokimos.server.client.DokimosServerReporter;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.repository.EvalResultRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReporterIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private EvalResultRepository evalResultRepository;

    @Autowired
    private EntityManager entityManager;

    private DokimosServerReporter reporter;

    @AfterEach
    void tearDown() {
        if (reporter != null) {
            reporter.close();
        }
    }

    @Test
    void shouldStoreRunAndItemsSuccessfully() {
        String projectName = "test-project-" + UUID.randomUUID();
        String experimentName = "test-experiment";
        Map<String, Object> metadata = Map.of(
                "model", "gpt-5",
                "temperature", 0.7,
                "version", "1.0.0");

        reporter = DokimosServerReporter.builder()
                .serverUrl("http://localhost:" + port)
                .projectName(projectName)
                .build();

        RunHandle handle = reporter.startRun(experimentName, metadata);

        // The Run should be created
        assertThat(handle).isNotNull();
        assertThat(handle.runId()).isNotNull();
        assertThat(handle.runId()).doesNotStartWith("local-");

        UUID runId = UUID.fromString(handle.runId());

        // Report several items with some data
        ItemResult item1 = new ItemResult(
                Example.of("What is the capital of France?", "Paris"),
                Map.of("output", "Paris"),
                List.of(
                        EvalResult.success("exact-match", 1.0, "Output matches expected"),
                        EvalResult.of("semantic-similarity", 0.95, 0.8, "High semantic similarity")),
                new CallMetrics(100, 50, 0.002, 430L));

        ItemResult item2 = createItemResult(
                "What is 2 + 2?", "4", "4", List.of(EvalResult.success("exact-match", 1.0, "Output matches expected")));

        ItemResult item3 = createItemResult(
                "Translate 'hello' to Spanish",
                "hola",
                "Hola",
                List.of(
                        EvalResult.failure("exact-match", 0.0, "Case mismatch"),
                        EvalResult.of("semantic-similarity", 0.98, 0.8, "Very high semantic similarity")));

        reporter.reportItem(handle, item1);
        reporter.reportItem(handle, item2);
        reporter.reportItem(handle, item3);

        // Flush to ensure all items are correctly sent
        reporter.flush();

        // Complete the run with SUCCESS
        reporter.completeRun(handle, RunStatus.SUCCESS);

        // Make sure the data was stored correctly using JPQL with joins to avoid lazy
        // loading
        ExperimentRun storedRun = entityManager
                .createQuery(
                        "SELECT r FROM ExperimentRun r " + "JOIN FETCH r.experiment e "
                                + "JOIN FETCH e.project "
                                + "WHERE r.id = :runId",
                        ExperimentRun.class)
                .setParameter("runId", runId)
                .getSingleResult();

        assertThat(storedRun.getStatus()).isEqualTo(dev.dokimos.server.entity.RunStatus.SUCCESS);
        assertThat(storedRun.getCompletedAt()).isNotNull();
        assertThat(storedRun.getExperiment().getName()).isEqualTo(experimentName);
        assertThat(storedRun.getExperiment().getProject().getName()).isEqualTo(projectName);
        assertThat(storedRun.getConfig()).containsEntry("model", "gpt-5");
        assertThat(storedRun.getConfig()).containsEntry("version", "1.0.0");

        // Verify items were stored along with their eval results
        List<dev.dokimos.server.entity.ItemResult> storedItems = entityManager
                .createQuery(
                        "SELECT DISTINCT i FROM ItemResult i " + "LEFT JOIN FETCH i.evalResults "
                                + "WHERE i.run.id = :runId "
                                + "ORDER BY i.createdAt ASC",
                        dev.dokimos.server.entity.ItemResult.class)
                .setParameter("runId", runId)
                .getResultList();

        assertThat(storedItems).hasSize(3);

        // Verify the first item
        var firstItem = storedItems.get(0);
        assertThat(firstItem.getInput()).isEqualTo(Map.of("input", "What is the capital of France?"));
        assertThat(firstItem.getExpectedOutput()).isEqualTo(Map.of("output", "Paris"));
        assertThat(firstItem.getActualOutput()).isEqualTo(Map.of("output", "Paris"));
        assertThat(firstItem.getEvalResults()).hasSize(2);

        // The call metrics carried on the first item must round-trip into the stored row.
        assertThat(firstItem.getTokensIn()).isEqualTo(100);
        assertThat(firstItem.getTokensOut()).isEqualTo(50);
        assertThat(firstItem.getCostUsd()).isCloseTo(0.002, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(firstItem.getLatencyMs()).isEqualTo(430L);

        // The run-level aggregates are materialized at completion from the single item that carried
        // metrics; the other two items contribute null and are ignored by SUM/AVG.
        assertThat(storedRun.getTotalTokensIn()).isEqualTo(100L);
        assertThat(storedRun.getTotalTokensOut()).isEqualTo(50L);
        assertThat(storedRun.getTotalCostUsd()).isCloseTo(0.002, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(storedRun.getAvgLatencyMs()).isEqualTo(430.0);

        // Verify eval results for first item
        var evalResults = firstItem.getEvalResults();
        assertThat(evalResults)
                .extracting(dev.dokimos.server.entity.EvalResult::getEvaluatorName)
                .containsExactlyInAnyOrder("exact-match", "semantic-similarity");

        var exactMatchEval = evalResults.stream()
                .filter(e -> "exact-match".equals(e.getEvaluatorName()))
                .findFirst()
                .orElseThrow();
        assertThat(exactMatchEval.getScore()).isEqualTo(1.0);
        assertThat(exactMatchEval.isSuccess()).isTrue();

        var semanticEval = evalResults.stream()
                .filter(e -> "semantic-similarity".equals(e.getEvaluatorName()))
                .findFirst()
                .orElseThrow();
        assertThat(semanticEval.getScore()).isEqualTo(0.95);
        assertThat(semanticEval.getThreshold()).isEqualTo(0.8);
        assertThat(semanticEval.isSuccess()).isTrue();

        // Verify third item (with failure)
        var thirdItem = storedItems.get(2);
        assertThat(thirdItem.getInput()).isEqualTo(Map.of("input", "Translate 'hello' to Spanish"));
        var thirdItemEvals = thirdItem.getEvalResults();
        var failedEval = thirdItemEvals.stream()
                .filter(e -> "exact-match".equals(e.getEvaluatorName()))
                .findFirst()
                .orElseThrow();
        assertThat(failedEval.isSuccess()).isFalse();
        assertThat(failedEval.getScore()).isEqualTo(0.0);
        assertThat(failedEval.getReason()).isEqualTo("Case mismatch");

        // Verify total counts
        long totalEvalResults = evalResultRepository.count();
        assertThat(totalEvalResults).isEqualTo(5); // 2 + 1 + 2 evals
    }

    @Test
    void shouldStoreItemsEvenWhenRunFailsPartially() {
        String projectName = "test-project-" + UUID.randomUUID();
        String experimentName = "partial-failure-experiment";
        Map<String, Object> metadata = Map.of("test-type", "failure-handling");

        reporter = DokimosServerReporter.builder()
                .serverUrl("http://localhost:" + port)
                .projectName(projectName)
                .build();

        RunHandle handle = reporter.startRun(experimentName, metadata);
        assertThat(handle.runId()).doesNotStartWith("local-");

        UUID runId = UUID.fromString(handle.runId());

        // Report some items before failing
        ItemResult item1 = createItemResult(
                "Question 1", "Answer 1", "Answer 1", List.of(EvalResult.success("check", 1.0, "Passed")));

        ItemResult item2 = createItemResult(
                "Question 2", "Answer 2", "Wrong Answer", List.of(EvalResult.failure("check", 0.0, "Mismatch")));

        reporter.reportItem(handle, item1);
        reporter.reportItem(handle, item2);

        reporter.flush();

        // Simulate an error / FAILED status
        reporter.completeRun(handle, RunStatus.FAILED);

        // The run should be marked as failed
        ExperimentRun storedRun = entityManager
                .createQuery("SELECT r FROM ExperimentRun r WHERE r.id = :runId", ExperimentRun.class)
                .setParameter("runId", runId)
                .getSingleResult();

        assertThat(storedRun.getStatus()).isEqualTo(dev.dokimos.server.entity.RunStatus.FAILED);
        assertThat(storedRun.getCompletedAt()).isNotNull();

        // Verify that the items that were sent before the error are still persisted
        List<dev.dokimos.server.entity.ItemResult> storedItems = entityManager
                .createQuery(
                        "SELECT DISTINCT i FROM ItemResult i " + "LEFT JOIN FETCH i.evalResults "
                                + "WHERE i.run.id = :runId "
                                + "ORDER BY i.createdAt ASC",
                        dev.dokimos.server.entity.ItemResult.class)
                .setParameter("runId", runId)
                .getResultList();

        assertThat(storedItems).hasSize(2);

        // Verify first item is stored correctly
        var firstItem = storedItems.get(0);
        assertThat(firstItem.getInput()).isEqualTo(Map.of("input", "Question 1"));
        assertThat(firstItem.getActualOutput()).isEqualTo(Map.of("output", "Answer 1"));
        assertThat(firstItem.getEvalResults()).hasSize(1);
        assertThat(firstItem.getEvalResults().get(0).isSuccess()).isTrue();

        // Verify second item is stored correctly
        var secondItem = storedItems.get(1);
        assertThat(secondItem.getInput()).isEqualTo(Map.of("input", "Question 2"));
        assertThat(secondItem.getActualOutput()).isEqualTo(Map.of("output", "Wrong Answer"));
        assertThat(secondItem.getEvalResults()).hasSize(1);
        assertThat(secondItem.getEvalResults().get(0).isSuccess()).isFalse();
    }

    private ItemResult createItemResult(
            String input, String expectedOutput, String actualOutput, List<EvalResult> evalResults) {
        Example example = Example.of(input, expectedOutput);
        return new ItemResult(example, Map.of("output", actualOutput), evalResults);
    }
}
