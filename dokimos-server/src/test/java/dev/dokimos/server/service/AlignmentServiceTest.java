package dev.dokimos.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.dokimos.server.dto.v1.AlignmentView;
import dev.dokimos.server.entity.Annotation;
import dev.dokimos.server.entity.AnnotationVerdict;
import dev.dokimos.server.entity.EvalResult;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.ItemResult;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.repository.AnnotationRepository;
import dev.dokimos.server.repository.ExperimentRepository;
import dev.dokimos.server.repository.ExperimentRunRepository;
import dev.dokimos.server.repository.ItemResultRepository;
import dev.dokimos.server.repository.ProjectRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Service-level coverage for {@link AlignmentService} backed by the in-memory H2 DataJpaTest stack.
 * Verifies the agreement math, the UNSURE and unannotated exclusions, the sparse-evaluator matrix,
 * and the missing-run guard.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AlignmentServiceTest.TestConfig.class)
class AlignmentServiceTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        AlignmentService alignmentService(
                ExperimentRunRepository runRepository,
                ItemResultRepository itemResultRepository,
                AnnotationRepository annotationRepository) {
            return new AlignmentService(runRepository, itemResultRepository, annotationRepository);
        }
    }

    @Autowired
    private AlignmentService alignmentService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ExperimentRepository experimentRepository;

    @Autowired
    private ExperimentRunRepository runRepository;

    @Autowired
    private ItemResultRepository itemResultRepository;

    @Autowired
    private AnnotationRepository annotationRepository;

    @Test
    void allItemsAgree_rateIsOne() {
        ExperimentRun run = newRun();
        annotate(itemWithEval(run, "accuracy", true), AnnotationVerdict.CORRECT);
        annotate(itemWithEval(run, "accuracy", false), AnnotationVerdict.INCORRECT);

        AlignmentView view = alignmentService.getAlignment(run.getId());

        assertThat(view.annotatedItems()).isEqualTo(2);
        AlignmentView.EvaluatorAlignment accuracy = only(view);
        assertThat(accuracy.evaluatorName()).isEqualTo("accuracy");
        assertThat(accuracy.comparableCount()).isEqualTo(2);
        assertThat(accuracy.agreedCount()).isEqualTo(2);
        assertThat(accuracy.excludedUnsure()).isZero();
        assertThat(accuracy.alignmentRate()).isEqualTo(1.0);
    }

    @Test
    void allItemsDisagree_rateIsZero() {
        ExperimentRun run = newRun();
        annotate(itemWithEval(run, "accuracy", true), AnnotationVerdict.INCORRECT);
        annotate(itemWithEval(run, "accuracy", false), AnnotationVerdict.CORRECT);

        AlignmentView.EvaluatorAlignment accuracy = only(alignmentService.getAlignment(run.getId()));

        assertThat(accuracy.comparableCount()).isEqualTo(2);
        assertThat(accuracy.agreedCount()).isZero();
        assertThat(accuracy.alignmentRate()).isEqualTo(0.0);
    }

    @Test
    void unsureItemsExcludedFromRateButCounted() {
        ExperimentRun run = newRun();
        annotate(itemWithEval(run, "accuracy", true), AnnotationVerdict.CORRECT);
        annotate(itemWithEval(run, "accuracy", false), AnnotationVerdict.UNSURE);

        AlignmentView view = alignmentService.getAlignment(run.getId());
        AlignmentView.EvaluatorAlignment accuracy = only(view);

        assertThat(view.annotatedItems()).isEqualTo(2);
        assertThat(accuracy.comparableCount()).isEqualTo(1);
        assertThat(accuracy.agreedCount()).isEqualTo(1);
        assertThat(accuracy.excludedUnsure()).isEqualTo(1);
        assertThat(accuracy.alignmentRate()).isEqualTo(1.0);
    }

    @Test
    void unannotatedItemsSkippedEntirely() {
        ExperimentRun run = newRun();
        annotate(itemWithEval(run, "accuracy", true), AnnotationVerdict.CORRECT);
        itemWithEval(run, "accuracy", false);

        AlignmentView view = alignmentService.getAlignment(run.getId());
        AlignmentView.EvaluatorAlignment accuracy = only(view);

        assertThat(view.annotatedItems()).isEqualTo(1);
        assertThat(accuracy.comparableCount()).isEqualTo(1);
        assertThat(accuracy.agreedCount()).isEqualTo(1);
        assertThat(accuracy.alignmentRate()).isEqualTo(1.0);
    }

    @Test
    void multipleEvaluatorsComputedIndependently() {
        ExperimentRun run = newRun();
        ItemResult first = newItem(run);
        addEval(first, "accuracy", true);
        addEval(first, "relevance", false);
        itemResultRepository.save(first);
        annotate(first, AnnotationVerdict.CORRECT);

        ItemResult second = newItem(run);
        addEval(second, "accuracy", false);
        addEval(second, "relevance", false);
        itemResultRepository.save(second);
        annotate(second, AnnotationVerdict.CORRECT);

        AlignmentView view = alignmentService.getAlignment(run.getId());

        AlignmentView.EvaluatorAlignment accuracy = byName(view, "accuracy");
        AlignmentView.EvaluatorAlignment relevance = byName(view, "relevance");
        assertThat(accuracy.comparableCount()).isEqualTo(2);
        assertThat(accuracy.agreedCount()).isEqualTo(1);
        assertThat(accuracy.alignmentRate()).isEqualTo(0.5);
        assertThat(relevance.comparableCount()).isEqualTo(2);
        assertThat(relevance.agreedCount()).isZero();
        assertThat(relevance.alignmentRate()).isEqualTo(0.0);
    }

    @Test
    void sparseEvaluatorUsesOnlyItemsItRanOn() {
        ExperimentRun run = newRun();
        annotate(itemWithEval(run, "accuracy", true), AnnotationVerdict.CORRECT);
        ItemResult second = newItem(run);
        addEval(second, "accuracy", false);
        addEval(second, "toxicity", true);
        itemResultRepository.save(second);
        annotate(second, AnnotationVerdict.CORRECT);

        AlignmentView view = alignmentService.getAlignment(run.getId());

        assertThat(byName(view, "accuracy").comparableCount()).isEqualTo(2);
        AlignmentView.EvaluatorAlignment toxicity = byName(view, "toxicity");
        assertThat(toxicity.comparableCount()).isEqualTo(1);
        assertThat(toxicity.agreedCount()).isEqualTo(1);
    }

    @Test
    void evaluatorWithOnlyUnsureItems_rateIsNull() {
        ExperimentRun run = newRun();
        annotate(itemWithEval(run, "accuracy", true), AnnotationVerdict.UNSURE);

        AlignmentView.EvaluatorAlignment accuracy = only(alignmentService.getAlignment(run.getId()));

        assertThat(accuracy.comparableCount()).isZero();
        assertThat(accuracy.excludedUnsure()).isEqualTo(1);
        assertThat(accuracy.alignmentRate()).isNull();
    }

    @Test
    void runWithNoAnnotations_hasNoEvaluators() {
        ExperimentRun run = newRun();
        itemWithEval(run, "accuracy", true);
        itemWithEval(run, "accuracy", false);

        AlignmentView view = alignmentService.getAlignment(run.getId());

        assertThat(view.annotatedItems()).isZero();
        assertThat(view.evaluators()).isEmpty();
    }

    @Test
    void emptyRun_hasNoEvaluators() {
        ExperimentRun run = newRun();

        AlignmentView view = alignmentService.getAlignment(run.getId());

        assertThat(view.annotatedItems()).isZero();
        assertThat(view.evaluators()).isEmpty();
    }

    @Test
    void missingRunRaisesNotFound() {
        assertThatThrownBy(() -> alignmentService.getAlignment(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Run not found");
    }

    private AlignmentView.EvaluatorAlignment only(AlignmentView view) {
        assertThat(view.evaluators()).hasSize(1);
        return view.evaluators().get(0);
    }

    private AlignmentView.EvaluatorAlignment byName(AlignmentView view, String name) {
        return view.evaluators().stream()
                .filter(e -> e.evaluatorName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private ItemResult itemWithEval(ExperimentRun run, String evaluatorName, boolean success) {
        ItemResult item = newItem(run);
        addEval(item, evaluatorName, success);
        return itemResultRepository.save(item);
    }

    private ItemResult newItem(ExperimentRun run) {
        return new ItemResult(run, Map.of("q", "question"), Map.of("a", "expected"), Map.of("a", "actual"), null);
    }

    private void addEval(ItemResult item, String evaluatorName, boolean success) {
        item.addEvalResult(new EvalResult(evaluatorName, success ? 1.0 : 0.0, 0.5, success, null));
    }

    private void annotate(ItemResult item, AnnotationVerdict verdict) {
        annotationRepository.save(new Annotation(item, verdict));
    }

    private ExperimentRun newRun() {
        Project project = projectRepository.save(new Project("p-" + UUID.randomUUID()));
        Experiment experiment = experimentRepository.save(new Experiment(project, "e-" + UUID.randomUUID()));
        return runRepository.save(new ExperimentRun(experiment, null));
    }
}
