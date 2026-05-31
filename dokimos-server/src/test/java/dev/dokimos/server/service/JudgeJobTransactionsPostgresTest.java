package dev.dokimos.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

import dev.dokimos.server.entity.EvalJob;
import dev.dokimos.server.entity.EvalResult;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.ItemResult;
import dev.dokimos.server.entity.LlmConnection;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.repository.EvalJobRepository;
import dev.dokimos.server.repository.EvalResultRepository;
import dev.dokimos.server.repository.ExperimentRepository;
import dev.dokimos.server.repository.ExperimentRunRepository;
import dev.dokimos.server.repository.ItemResultRepository;
import dev.dokimos.server.repository.LlmConnectionRepository;
import dev.dokimos.server.repository.ProjectRepository;
import dev.dokimos.server.service.JudgeJobTransactions.ScoredResult;
import dev.dokimos.server.tenant.TenantScope;
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
 * Proves the judge worker stamps each eval result with its parent item's tenant, so a worker-produced
 * result stays visible only under the same scope as the item it belongs to. Runs on PostgreSQL
 * (Testcontainers) with the Flyway schema because {@code persistPage} resolves the parent tenant through
 * {@code getReferenceById}, which needs a live session. Self-skips when Docker is unavailable.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JudgeJobTransactionsPostgresTest.TestConfig.class)
class JudgeJobTransactionsPostgresTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        JudgeJobTransactions judgeJobTransactions(
                EvalJobRepository jobRepository,
                EvalResultRepository evalResultRepository,
                ItemResultRepository itemResultRepository) {
            return new JudgeJobTransactions(
                    jobRepository, evalResultRepository, itemResultRepository, mock(RunService.class));
        }
    }

    private static final boolean DOCKER_AVAILABLE =
            DockerClientFactory.instance().isDockerAvailable();

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
            return;
        }
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private JudgeJobTransactions transactions;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ExperimentRepository experimentRepository;

    @Autowired
    private ExperimentRunRepository runRepository;

    @Autowired
    private ItemResultRepository itemResultRepository;

    @Autowired
    private LlmConnectionRepository connectionRepository;

    @Autowired
    private EvalJobRepository jobRepository;

    @Autowired
    private EvalResultRepository evalResultRepository;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    // NOT_SUPPORTED suspends the @DataJpaTest transaction so the setup commits before persistPage runs:
    // persistPage uses REQUIRES_NEW and cannot see rows only flushed into a never-committed context. The
    // assertion read is wrapped in its own committed transaction for the same reason.
    @Test
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void persistPage_shouldStampEvalResultWithParentItemTenant() {
        assumeTrue(DOCKER_AVAILABLE, "Docker not available");

        org.springframework.transaction.support.TransactionTemplate tx =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);

        UUID jobId = tx.execute(status -> {
            Project project = projectRepository.save(new Project("judge-project-" + UUID.randomUUID()));
            Experiment experiment = experimentRepository.save(new Experiment(project, "judge-experiment"));
            ExperimentRun run = runRepository.save(new ExperimentRun(experiment, Map.of()));
            LlmConnection connectionEntity =
                    new LlmConnection("judge-conn-" + UUID.randomUUID(), "https://example.invalid", "gpt");
            connectionEntity.setCredentialRef("OPENAI_KEY");
            LlmConnection connection = connectionRepository.save(connectionEntity);
            EvalJob jobEntity = new EvalJob(run, connection, "correctness", "criteria");
            jobEntity.setEvaluationParams("{}");
            return jobRepository.save(jobEntity).getId();
        });

        UUID itemId = tx.execute(status -> {
            ExperimentRun run = runRepository.findAll(TenantScope.unrestricted()).stream()
                    .findFirst()
                    .orElseThrow();
            ItemResult item = new ItemResult(run, Map.of("q", "x"), Map.of("a", "y"), Map.of("a", "y"), Map.of());
            item.setTenantId("tenant-a");
            return itemResultRepository.save(item).getId();
        });

        EvalResult eval = new EvalResult("correctness", 1.0, 0.9, true, "ok");
        transactions.persistPage(jobId, List.of(new ScoredResult(itemId, eval)), itemId);

        EvalResult persisted =
                tx.execute(status -> evalResultRepository.findById(eval.getId()).orElseThrow());
        assertThat(persisted.getTenantId()).isEqualTo("tenant-a");
    }
}
