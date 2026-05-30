package dev.dokimos.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.dokimos.server.dto.v1.CreateRunRequest;
import dev.dokimos.server.dto.v1.CreateVersionRequest;
import dev.dokimos.server.dto.v1.DatasetDetails;
import dev.dokimos.server.dto.v1.DatasetSummary;
import dev.dokimos.server.dto.v1.DatasetVersionDetails;
import dev.dokimos.server.dto.v1.PromoteRequest;
import dev.dokimos.server.entity.Dataset;
import dev.dokimos.server.entity.DatasetItem;
import dev.dokimos.server.entity.DatasetVersion;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.ItemResult;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.repository.DatasetItemRepository;
import dev.dokimos.server.repository.DatasetRepository;
import dev.dokimos.server.repository.DatasetVersionRepository;
import dev.dokimos.server.repository.ExperimentRepository;
import dev.dokimos.server.repository.ExperimentRunRepository;
import dev.dokimos.server.repository.IngestedBatchRepository;
import dev.dokimos.server.repository.ItemResultRepository;
import dev.dokimos.server.repository.ProjectRepository;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Service-level coverage for {@link DatasetService} backed by the in-memory H2 DataJpaTest stack.
 * Verifies the create/list/version lifecycle plus the dataset-version link that flows from {@link
 * CreateRunRequest} onto {@link ExperimentRun}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({DatasetServiceTest.TestConfig.class})
class DatasetServiceTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        DatasetService datasetService(
                DatasetRepository datasetRepository,
                DatasetVersionRepository versionRepository,
                DatasetItemRepository itemRepository,
                ItemResultRepository itemResultRepository) {
            return new DatasetService(datasetRepository, versionRepository, itemRepository, itemResultRepository);
        }

        @org.springframework.context.annotation.Bean
        ComparisonSupport comparisonSupport(ItemResultRepository itemResultRepository) {
            return new ComparisonSupport(itemResultRepository);
        }

        @org.springframework.context.annotation.Bean
        RegressionAlertService regressionAlertService(
                ExperimentRunRepository runRepository,
                ComparisonSupport comparisonSupport,
                org.springframework.context.ApplicationEventPublisher eventPublisher) {
            return new RegressionAlertService(runRepository, comparisonSupport, eventPublisher);
        }

        @org.springframework.context.annotation.Bean
        RunService runService(
                ExperimentRunRepository runRepository,
                ItemResultRepository itemResultRepository,
                IngestedBatchRepository ingestedBatchRepository,
                DatasetService datasetService,
                DatasetItemRepository datasetItemRepository,
                dev.dokimos.server.repository.AnnotationRepository annotationRepository,
                RegressionAlertService regressionAlertService) {
            return new RunService(
                    runRepository,
                    itemResultRepository,
                    ingestedBatchRepository,
                    datasetService,
                    datasetItemRepository,
                    annotationRepository,
                    regressionAlertService);
        }
    }

    @Autowired
    private org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager entityManager;

    @Autowired
    private DatasetService datasetService;

    @Autowired
    private RunService runService;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private DatasetVersionRepository versionRepository;

    @Autowired
    private DatasetItemRepository itemRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ExperimentRepository experimentRepository;

    @Autowired
    private ExperimentRunRepository runRepository;

    @Autowired
    private ItemResultRepository itemResultRepository;

    @Autowired
    private jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @Test
    void createDataset_persistsAndReturns() {
        Dataset created = datasetService.createDataset("qa", "small QA set");

        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("qa");
        assertThat(created.getDescription()).isEqualTo("small QA set");
        assertThat(datasetRepository.existsByName("qa")).isTrue();
    }

    @Test
    void createDataset_duplicateNameRaisesConflict() {
        datasetService.createDataset("qa", null);

        assertThatThrownBy(() -> datasetService.createDataset("qa", "another"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Dataset already exists");
    }

    @Test
    void createVersion_incrementsAndPersistsItems() {
        datasetService.createDataset("qa", null);

        DatasetVersion v1 = datasetService.createVersion("qa", "first", twoItems(), "alice");
        DatasetVersion v2 = datasetService.createVersion("qa", "second", twoItems(), "bob");

        assertThat(v1.getVersion()).isEqualTo(1);
        assertThat(v1.getItemCount()).isEqualTo(2);
        assertThat(v1.getCreatedBy()).isEqualTo("alice");
        assertThat(v2.getVersion()).isEqualTo(2);
        assertThat(v2.getCreatedBy()).isEqualTo("bob");

        var page = itemRepository.findByDatasetVersionOrderByOrdinalAsc(v1, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(2);
        List<DatasetItem> items = page.getContent();
        assertThat(items.get(0).getOrdinal()).isZero();
        assertThat(items.get(0).getInputs()).containsEntry("q", "first question");
        assertThat(items.get(1).getOrdinal()).isEqualTo(1);
        assertThat(items.get(1).getInputs()).containsEntry("q", "second question");
    }

    @Test
    void createVersion_emptyItemsRejected() {
        datasetService.createDataset("qa", null);

        assertThatThrownBy(() -> datasetService.createVersion("qa", null, List.of(), "alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one item");
    }

    @Test
    void createVersion_unknownDatasetRaisesNotFound() {
        assertThatThrownBy(() -> datasetService.createVersion("missing", null, twoItems(), "alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dataset not found");
    }

    @Test
    void listDatasets_surfacesLatestVersionAndItemCount() {
        datasetService.createDataset("qa", "qa set");
        datasetService.createVersion("qa", null, twoItems(), "alice");
        datasetService.createVersion("qa", null, twoItems(), "alice");
        datasetService.createDataset("empty", null);

        List<DatasetSummary> all = datasetService.listDatasets();

        DatasetSummary qa =
                all.stream().filter(d -> d.name().equals("qa")).findFirst().orElseThrow();
        assertThat(qa.latestVersion()).isEqualTo(2);
        assertThat(qa.latestItemCount()).isEqualTo(2);

        DatasetSummary empty =
                all.stream().filter(d -> d.name().equals("empty")).findFirst().orElseThrow();
        assertThat(empty.latestVersion()).isNull();
        assertThat(empty.latestItemCount()).isNull();
    }

    @Test
    void listDatasets_constantQueryCountRegardlessOfDatasetCount() {
        // Five datasets, each with three versions. The N+1 path would issue 1 + 5 = 6 selects;
        // the collapsed path issues 2 (one for datasets, one for latest-version-per-dataset).
        for (int i = 0; i < 5; i++) {
            String name = "ds-" + i;
            datasetService.createDataset(name, null);
            datasetService.createVersion(name, null, twoItems(), "alice");
            datasetService.createVersion(name, null, twoItems(), "alice");
            datasetService.createVersion(name, null, twoItems(), "alice");
        }

        // Flush pending writes from the create loop before resetting stats so the auto-flush
        // triggered by the subsequent read does not pollute the read's query count.
        entityManager.flush();
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        List<DatasetSummary> all = datasetService.listDatasets();

        assertThat(all).hasSize(5);
        // Every dataset reports its latest (third) version with two items each.
        assertThat(all).allSatisfy(s -> {
            assertThat(s.latestVersion()).isEqualTo(3);
            assertThat(s.latestItemCount()).isEqualTo(2);
        });
        // The implementation must not fan out per dataset: two queries total.
        assertThat(stats.getPrepareStatementCount())
                .as("listDatasets must collapse to O(1) queries; one per dataset would be 6")
                .isLessThanOrEqualTo(2L);
    }

    @Test
    void getDatasetDetails_listsVersionsNewestFirst() {
        datasetService.createDataset("qa", null);
        datasetService.createVersion("qa", "v1", twoItems(), "alice");
        datasetService.createVersion("qa", "v2", twoItems(), "bob");

        DatasetDetails details = datasetService.getDatasetDetails("qa");

        assertThat(details.versions()).hasSize(2);
        assertThat(details.versions().get(0).version()).isEqualTo(2);
        assertThat(details.versions().get(1).version()).isEqualTo(1);
    }

    @Test
    void deleteDataset_removesDatasetShell() {
        // H2 under JPA test slice cannot exercise the ON DELETE CASCADE on the FK because the
        // Hibernate-generated schema omits the cascade clause (no @OnDelete on the entity). The
        // real cascade and the SET NULL on experiment_runs.dataset_version_id are verified end
        // to end against PostgreSQL in FlywayMigrationTest.v5BuildsDatasetTables.
        datasetService.createDataset("qa", null);

        datasetService.deleteDataset("qa");
        entityManager.flush();

        assertThat(datasetRepository.existsByName("qa")).isFalse();
    }

    @Test
    void deleteDataset_unknownRaisesNotFound() {
        assertThatThrownBy(() -> datasetService.deleteDataset("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dataset not found");
    }

    @Test
    void getLatestVersion_noVersionsRaisesNotFound() {
        datasetService.createDataset("qa", null);

        assertThatThrownBy(() -> datasetService.getLatestVersion("qa"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no versions");
    }

    @Test
    void createRun_withDatasetLink_setsDatasetVersionOnRun() {
        datasetService.createDataset("qa", null);
        DatasetVersion version = datasetService.createVersion("qa", null, twoItems(), "alice");

        Project project = projectRepository.save(new Project("p"));
        Experiment experiment = experimentRepository.save(new Experiment(project, "e"));

        CreateRunRequest request = new CreateRunRequest("e", null, null, null, null, null, "qa", 1);

        ExperimentRun created = runService.createRun(experiment, request);
        ExperimentRun reloaded = runRepository.findById(created.getId()).orElseThrow();

        assertThat(reloaded.getDatasetVersion()).isNotNull();
        assertThat(reloaded.getDatasetVersion().getId()).isEqualTo(version.getId());
        assertThat(reloaded.getDatasetVersion().getVersion()).isEqualTo(1);
    }

    @Test
    void createRun_datasetNameWithoutVersion_raises() {
        Project project = projectRepository.save(new Project("p"));
        Experiment experiment = experimentRepository.save(new Experiment(project, "e"));

        CreateRunRequest request = new CreateRunRequest("e", null, null, null, null, null, "qa", null);

        assertThatThrownBy(() -> runService.createRun(experiment, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be set together");
    }

    @Test
    void createRun_datasetVersionWithoutName_raises() {
        Project project = projectRepository.save(new Project("p"));
        Experiment experiment = experimentRepository.save(new Experiment(project, "e"));

        CreateRunRequest request = new CreateRunRequest("e", null, null, null, null, null, null, 1);

        assertThatThrownBy(() -> runService.createRun(experiment, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be set together");
    }

    @Test
    void createRun_withoutDataset_leavesRunUnlinked() {
        Project project = projectRepository.save(new Project("p"));
        Experiment experiment = experimentRepository.save(new Experiment(project, "e"));

        CreateRunRequest request = new CreateRunRequest("e", null, null, null, null, null);
        ExperimentRun created = runService.createRun(experiment, request);

        ExperimentRun reloaded = runRepository.findById(created.getId()).orElseThrow();
        assertThat(reloaded.getDatasetVersion()).isNull();
    }

    @Test
    void promote_createsNewVersionWithOverrideAndFallback() {
        datasetService.createDataset("qa", null);

        ItemResult overridden = newItemResult(Map.of("q", "one"), Map.of("a", "orig one"), Map.of("tag", "t1"));
        ItemResult fallback = newItemResult(Map.of("q", "two"), Map.of("a", "orig two"), null);

        PromoteRequest request = new PromoteRequest(
                "qa",
                "promoted from review",
                List.of(
                        new PromoteRequest.PromoteItem(overridden.getId(), Map.of("a", "corrected one")),
                        new PromoteRequest.PromoteItem(fallback.getId(), null)));

        DatasetVersionDetails details = datasetService.promote(request, "alice");

        assertThat(details.datasetName()).isEqualTo("qa");
        assertThat(details.version()).isEqualTo(1);
        assertThat(details.itemCount()).isEqualTo(2);
        assertThat(details.createdBy()).isEqualTo("alice");

        DatasetVersion version = datasetService.getVersion("qa", 1);
        List<DatasetItem> items = itemRepository
                .findByDatasetVersionOrderByOrdinalAsc(version, PageRequest.of(0, 10))
                .getContent();

        // Order preserved: ordinal 0 is the overridden item, ordinal 1 is the fallback.
        assertThat(items.get(0).getInputs()).containsEntry("q", "one");
        assertThat(items.get(0).getExpectedOutputs()).containsEntry("a", "corrected one");
        assertThat(items.get(0).getMetadata()).containsEntry("tag", "t1");
        assertThat(items.get(1).getInputs()).containsEntry("q", "two");
        // No override supplied, so the item result's expected output is carried over.
        assertThat(items.get(1).getExpectedOutputs()).containsEntry("a", "orig two");
    }

    @Test
    void promote_missingItemResultRaisesNotFound() {
        datasetService.createDataset("qa", null);

        PromoteRequest request = new PromoteRequest(
                "qa", null, List.of(new PromoteRequest.PromoteItem(java.util.UUID.randomUUID(), null)));

        assertThatThrownBy(() -> datasetService.promote(request, "alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Item result not found");
    }

    private ItemResult newItemResult(
            Map<String, Object> input, Map<String, Object> expectedOutput, Map<String, Object> metadata) {
        Project project = projectRepository.save(new Project("p-" + java.util.UUID.randomUUID()));
        Experiment experiment = experimentRepository.save(new Experiment(project, "e-" + java.util.UUID.randomUUID()));
        ExperimentRun run = runRepository.save(new ExperimentRun(experiment, null));
        return itemResultRepository.save(new ItemResult(run, input, expectedOutput, Map.of("a", "actual"), metadata));
    }

    private List<CreateVersionRequest.ItemPayload> twoItems() {
        return List.of(
                new CreateVersionRequest.ItemPayload(Map.of("q", "first question"), Map.of("a", "first answer"), null),
                new CreateVersionRequest.ItemPayload(
                        Map.of("q", "second question"), Map.of("a", "second answer"), Map.of("tag", "smoke")));
    }
}
