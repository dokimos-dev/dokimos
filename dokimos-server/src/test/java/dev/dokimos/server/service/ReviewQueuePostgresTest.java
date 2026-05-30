package dev.dokimos.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.dokimos.server.dto.v1.ReviewQueueItem;
import dev.dokimos.server.entity.Annotation;
import dev.dokimos.server.entity.AnnotationVerdict;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.ItemResult;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.repository.AnnotationRepository;
import dev.dokimos.server.repository.ExperimentRepository;
import dev.dokimos.server.repository.ExperimentRunRepository;
import dev.dokimos.server.repository.ItemResultRepository;
import dev.dokimos.server.repository.ProjectRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Proves the review-queue query against a real PostgreSQL instance (Testcontainers) with the
 * production Flyway schema rather than against mocks. The query is the part worth backing with real
 * persistence: the null-guarded project/experiment/run filters and the {@code NOT EXISTS} resolved-by-
 * annotation predicate behave differently under Hibernate than under any mock. Self-skips when Docker
 * is unavailable so it stays safe in the normal build.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ReviewQueuePostgresTest.TestConfig.class)
class ReviewQueuePostgresTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        ReviewQueueService reviewQueueService(
                ItemResultRepository itemResultRepository, AnnotationRepository annotationRepository) {
            return new ReviewQueueService(itemResultRepository, annotationRepository);
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
    private ReviewQueueService reviewQueueService;

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
    void list_shouldReturnOnlyUnresolvedItems() {
        assumeTrue(DOCKER_AVAILABLE, "Docker not available");
        ExperimentRun run = persistRun("alpha", "exp-a");
        ItemResult unannotated = persistItem(run);
        ItemResult unsure = persistItem(run);
        ItemResult correct = persistItem(run);
        ItemResult incorrect = persistItem(run);
        annotate(unsure, AnnotationVerdict.UNSURE);
        annotate(correct, AnnotationVerdict.CORRECT);
        annotate(incorrect, AnnotationVerdict.INCORRECT);

        Page<ReviewQueueItem> page = reviewQueueService.list(null, null, null, PageRequest.of(0, 50));

        assertThat(page.getContent())
                .extracting(ReviewQueueItem::itemId)
                .containsExactlyInAnyOrder(unannotated.getId(), unsure.getId());
    }

    @Test
    void list_shouldSurfaceExistingVerdictForUnsureItems() {
        assumeTrue(DOCKER_AVAILABLE, "Docker not available");
        ExperimentRun run = persistRun("beta", "exp-b");
        ItemResult unannotated = persistItem(run);
        ItemResult unsure = persistItem(run);
        annotate(unsure, AnnotationVerdict.UNSURE);

        Page<ReviewQueueItem> page = reviewQueueService.list(null, null, null, PageRequest.of(0, 50));

        assertThat(itemFor(page.getContent(), unsure).currentVerdict()).isEqualTo(AnnotationVerdict.UNSURE);
        assertThat(itemFor(page.getContent(), unannotated).currentVerdict()).isNull();
    }

    @Test
    void list_shouldScopeByRunAndProject() {
        assumeTrue(DOCKER_AVAILABLE, "Docker not available");
        ExperimentRun runOne = persistRun("gamma", "exp-c");
        ExperimentRun runTwo = persistRun("delta", "exp-d");
        ItemResult itemOne = persistItem(runOne);
        ItemResult itemTwo = persistItem(runTwo);

        assertThat(reviewQueueService
                        .list(null, null, runOne.getId(), PageRequest.of(0, 50))
                        .getContent())
                .extracting(ReviewQueueItem::itemId)
                .containsExactly(itemOne.getId());

        assertThat(reviewQueueService
                        .list("delta", null, null, PageRequest.of(0, 50))
                        .getContent())
                .extracting(ReviewQueueItem::itemId)
                .containsExactly(itemTwo.getId());

        assertThat(reviewQueueService
                        .list("no-such-project", null, null, PageRequest.of(0, 50))
                        .getContent())
                .isEmpty();
    }

    private static ReviewQueueItem itemFor(List<ReviewQueueItem> items, ItemResult target) {
        return items.stream()
                .filter(i -> i.itemId().equals(target.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("item not in queue: " + target.getId()));
    }

    private ExperimentRun persistRun(String projectName, String experimentName) {
        Project project = projectRepository.save(new Project(projectName));
        Experiment experiment = experimentRepository.save(new Experiment(project, experimentName));
        return runRepository.save(new ExperimentRun(experiment, Map.of()));
    }

    private ItemResult persistItem(ExperimentRun run) {
        return itemResultRepository.save(
                new ItemResult(run, Map.of("q", "x"), Map.of("a", "y"), Map.of("a", "y"), Map.of()));
    }

    private void annotate(ItemResult item, AnnotationVerdict verdict) {
        annotationRepository.save(new Annotation(item, verdict));
    }
}
