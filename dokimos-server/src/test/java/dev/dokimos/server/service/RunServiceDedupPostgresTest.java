package dev.dokimos.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.dokimos.server.dto.v1.AddItemsRequest;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.repository.ExperimentRepository;
import dev.dokimos.server.repository.ExperimentRunRepository;
import dev.dokimos.server.repository.IngestedBatchRepository;
import dev.dokimos.server.repository.ItemResultRepository;
import dev.dokimos.server.repository.ProjectRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Proves {@link RunService#addItems} dedup behavior against a real PostgreSQL instance (Testcontainers)
 * with the production Flyway schema, rather than against mocks. The proof is deterministic and
 * sequential: calling {@code addItems} twice with the same idempotency key inserts the items exactly
 * once and records a single {@code ingested_batches} row, while a different key inserts again. A
 * sequential proof is preferred over a thread race because it is reliable and still exercises the real
 * unique constraint and the real {@code existsByRunIdAndIdempotencyKey} read against committed data.
 *
 * <p>Self-skips when Docker is unavailable so it stays safe in the normal build. Not tagged as an
 * integration test: when Docker is present it runs as part of {@code mvn test}, matching
 * {@code FlywayMigrationTest}. The mock-based {@code RunServiceTest} cases remain the fast unit
 * coverage; this test backs them with real persistence.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RunServiceDedupPostgresTest.TestConfig.class)
class RunServiceDedupPostgresTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        DatasetService datasetService(
                dev.dokimos.server.repository.DatasetRepository datasetRepository,
                dev.dokimos.server.repository.DatasetVersionRepository versionRepository,
                dev.dokimos.server.repository.DatasetItemRepository itemRepository,
                ItemResultRepository itemResultRepository) {
            return new DatasetService(datasetRepository, versionRepository, itemRepository, itemResultRepository);
        }

        @org.springframework.context.annotation.Bean
        RunService runService(
                ExperimentRunRepository runRepository,
                ItemResultRepository itemResultRepository,
                IngestedBatchRepository ingestedBatchRepository,
                DatasetService datasetService,
                dev.dokimos.server.repository.DatasetItemRepository datasetItemRepository,
                dev.dokimos.server.repository.AnnotationRepository annotationRepository) {
            return new RunService(
                    runRepository,
                    itemResultRepository,
                    ingestedBatchRepository,
                    datasetService,
                    datasetItemRepository,
                    annotationRepository);
        }
    }

    private static final boolean DOCKER_AVAILABLE =
            DockerClientFactory.instance().isDockerAvailable();

    // Started eagerly in a static initializer (not @BeforeAll) so the container is running before the
    // Spring context is built: @DynamicPropertySource is evaluated during context creation, which the
    // test framework performs before @BeforeAll. Only created when Docker is available so the class
    // self-skips cleanly otherwise.
    private static final PostgreSQLContainer<?> postgres;

    static {
        if (DOCKER_AVAILABLE) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine");
            postgres.start();
            awaitConnectable(postgres);
        } else {
            postgres = null;
        }
    }

    /**
     * Polls the mapped JDBC port until it accepts a connection. The container readiness probe runs
     * inside the Docker network, but the host side of the port forward (for example under Colima) can
     * lag by a moment, so we wait until the host can actually open a JDBC connection before the Spring
     * context tries to reach it.
     */
    private static void awaitConnectable(PostgreSQLContainer<?> container) {
        org.postgresql.ds.PGSimpleDataSource ds = new org.postgresql.ds.PGSimpleDataSource();
        ds.setUrl(container.getJdbcUrl());
        ds.setUser(container.getUsername());
        ds.setPassword(container.getPassword());
        RuntimeException last = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            try (java.sql.Connection ignored = ds.getConnection()) {
                return;
            } catch (Exception e) {
                last = new IllegalStateException("Postgres not yet connectable", e);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for Postgres", ie);
                }
            }
        }
        throw new IllegalStateException("Postgres did not become connectable in time", last);
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (!DOCKER_AVAILABLE) {
            // Context is never loaded because the @Test self-skips via assumeTrue, but the registry
            // callback still runs during setup, so register harmless placeholders.
            return;
        }
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        // Flyway builds the production schema (V1 to V3), including the ingested_batches table and its
        // composite primary key, so the dedup path runs against the same schema as production.
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private RunService runService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ExperimentRepository experimentRepository;

    @Autowired
    private ExperimentRunRepository runRepository;

    @Autowired
    private ItemResultRepository itemResultRepository;

    @Autowired
    private IngestedBatchRepository ingestedBatchRepository;

    // NOT_SUPPORTED suspends the transaction @DataJpaTest wraps around the test method so each
    // addItems call runs in (and commits) its own transaction. This exercises the real cross
    // transaction visibility the dedup depends on: the second same-key call reads the committed
    // ingested_batches row, rather than a row merely flushed into a shared, never-committed context.
    @Test
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void sameKeyInsertsOnceThenDifferentKeyInsertsAgain() {
        assumeTrue(DOCKER_AVAILABLE, "Docker is not available, skipping real-Postgres dedup verification");

        UUID runId = persistRunningRun();

        AddItemsRequest request = singleItemRequest();

        // Two calls with the SAME key: the items and dedup row are written once, the retry no-ops.
        runService.addItems(runId, request, "key-1");
        runService.addItems(runId, request, "key-1");

        assertThat(itemResultRepository.count()).isEqualTo(1);
        assertThat(ingestedBatchRepository.existsByRunIdAndIdempotencyKey(runId, "key-1"))
                .isTrue();
        assertThat(countIngestedBatchesForRun(runId)).isEqualTo(1);

        // A different key for the same run inserts a second item and a second dedup row.
        runService.addItems(runId, request, "key-2");

        assertThat(itemResultRepository.count()).isEqualTo(2);
        assertThat(ingestedBatchRepository.existsByRunIdAndIdempotencyKey(runId, "key-2"))
                .isTrue();
        assertThat(countIngestedBatchesForRun(runId)).isEqualTo(2);
    }

    private long countIngestedBatchesForRun(UUID runId) {
        return ingestedBatchRepository.findAll().stream()
                .filter(b -> b.getRunId().equals(runId))
                .count();
    }

    private UUID persistRunningRun() {
        Project project = projectRepository.save(new Project("dedup-project-" + UUID.randomUUID()));
        Experiment experiment = experimentRepository.save(new Experiment(project, "dedup-experiment"));
        ExperimentRun run = runRepository.save(new ExperimentRun(experiment, Map.of()));
        return run.getId();
    }

    private AddItemsRequest singleItemRequest() {
        return new AddItemsRequest(List.of(new AddItemsRequest.ItemData(
                Map.of("input", "q"),
                Map.of("output", "a"),
                Map.of("output", "a"),
                List.of(new AddItemsRequest.EvalData("exact-match", 1.0, 0.9, true, "ok", Map.of())),
                true)));
    }
}
