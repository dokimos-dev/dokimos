package dev.dokimos.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.dokimos.server.dto.v1.AnnotationRequest;
import dev.dokimos.server.dto.v1.AnnotationView;
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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Service-level coverage for {@link AnnotationService} backed by the in-memory H2 DataJpaTest stack.
 * Verifies the upsert (create then update of a single row), get, delete, and the run-membership guard.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AnnotationServiceTest.TestConfig.class)
class AnnotationServiceTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        AnnotationService annotationService(
                AnnotationRepository annotationRepository, ItemResultRepository itemResultRepository) {
            return new AnnotationService(annotationRepository, itemResultRepository);
        }
    }

    @Autowired
    private org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager entityManager;

    @Autowired
    private AnnotationService annotationService;

    @Autowired
    private AnnotationRepository annotationRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ExperimentRepository experimentRepository;

    @Autowired
    private ExperimentRunRepository runRepository;

    @Autowired
    private ItemResultRepository itemResultRepository;

    @Test
    void upsert_createsThenUpdatesSameRow() {
        Fixture fixture = newRunWithItem();

        AnnotationView created = annotationService.upsert(
                fixture.runId,
                fixture.itemResultId,
                new AnnotationRequest(AnnotationVerdict.INCORRECT, Map.of("a", "fixed"), "first note"),
                "alice");

        assertThat(created.verdict()).isEqualTo(AnnotationVerdict.INCORRECT);
        assertThat(created.overriddenExpectedOutput()).containsEntry("a", "fixed");
        assertThat(created.note()).isEqualTo("first note");
        assertThat(created.createdBy()).isEqualTo("alice");
        assertThat(annotationRepository.count()).isEqualTo(1);

        AnnotationView updated = annotationService.upsert(
                fixture.runId,
                fixture.itemResultId,
                new AnnotationRequest(AnnotationVerdict.CORRECT, null, "second note"),
                "bob");

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.verdict()).isEqualTo(AnnotationVerdict.CORRECT);
        assertThat(updated.overriddenExpectedOutput()).isNull();
        assertThat(updated.note()).isEqualTo("second note");
        // createdBy is recorded only at creation time, not overwritten on update.
        assertThat(updated.createdBy()).isEqualTo("alice");
        assertThat(annotationRepository.count()).isEqualTo(1);
    }

    @Test
    void get_returnsExistingAnnotation() {
        Fixture fixture = newRunWithItem();
        annotationService.upsert(
                fixture.runId, fixture.itemResultId, new AnnotationRequest(AnnotationVerdict.UNSURE, null, null), null);

        AnnotationView view = annotationService.get(fixture.runId, fixture.itemResultId);

        assertThat(view.verdict()).isEqualTo(AnnotationVerdict.UNSURE);
    }

    @Test
    void get_onUnannotatedItemRaisesNotFound() {
        Fixture fixture = newRunWithItem();

        assertThatThrownBy(() -> annotationService.get(fixture.runId, fixture.itemResultId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Annotation not found");
    }

    @Test
    void delete_removesAnnotation() {
        Fixture fixture = newRunWithItem();
        annotationService.upsert(
                fixture.runId,
                fixture.itemResultId,
                new AnnotationRequest(AnnotationVerdict.CORRECT, null, null),
                null);

        annotationService.delete(fixture.runId, fixture.itemResultId);
        entityManager.flush();

        assertThat(annotationRepository.findByItemResultId(fixture.itemResultId))
                .isEmpty();
    }

    @Test
    void upsert_onItemNotBelongingToRunRaisesNotFound() {
        Fixture fixture = newRunWithItem();
        UUID otherRunId = newRun().getId();

        assertThatThrownBy(() -> annotationService.upsert(
                        otherRunId,
                        fixture.itemResultId,
                        new AnnotationRequest(AnnotationVerdict.CORRECT, null, null),
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to run");
    }

    @Test
    void upsert_onMissingItemResultRaisesNotFound() {
        ExperimentRun run = newRun();

        assertThatThrownBy(() -> annotationService.upsert(
                        run.getId(),
                        UUID.randomUUID(),
                        new AnnotationRequest(AnnotationVerdict.CORRECT, null, null),
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Item result not found");
    }

    private record Fixture(UUID runId, UUID itemResultId) {}

    private Fixture newRunWithItem() {
        ExperimentRun run = newRun();
        ItemResult item = itemResultRepository.save(
                new ItemResult(run, Map.of("q", "question"), Map.of("a", "expected"), Map.of("a", "actual"), null));
        return new Fixture(run.getId(), item.getId());
    }

    private ExperimentRun newRun() {
        Project project = projectRepository.save(new Project("p-" + UUID.randomUUID()));
        Experiment experiment = experimentRepository.save(new Experiment(project, "e-" + UUID.randomUUID()));
        return runRepository.save(new ExperimentRun(experiment, null));
    }
}
