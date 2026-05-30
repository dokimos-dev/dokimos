package dev.dokimos.server.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.repository.ExperimentRepository;
import dev.dokimos.server.repository.ExperimentRunRepository;
import dev.dokimos.server.repository.ProjectRepository;
import dev.dokimos.server.service.ExperimentService;
import dev.dokimos.server.service.ProjectService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves the tenant isolation boundary at the repository and service layers. Each scoped read takes a
 * {@link TenantScope}; this verifies that own-plus-shared visibility, foreign-tenant invisibility,
 * shared-only ({@code scoped(null)}) visibility, unrestricted visibility, the project-count aggregate,
 * per-tenant naming, the worker (unrestricted) path, and the cross-tenant by-id 404 all behave per the
 * plan. Backed by the in-memory H2 DataJpaTest stack the rest of the suite uses; the scope predicate is
 * plain JPQL/Criteria, so the behavior matches Postgres (which the migration and Postgres-backed tests
 * cover separately).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(TenantScopingTest.TestConfig.class)
class TenantScopingTest {

    private static final TenantScope TENANT_A = TenantScope.scoped("tenant-a");
    private static final TenantScope TENANT_B = TenantScope.scoped("tenant-b");
    private static final TenantScope ANONYMOUS = TenantScope.scoped(null);
    private static final TenantScope WORKER = TenantScope.unrestricted();

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        ProjectService projectService(ProjectRepository projectRepository) {
            return new ProjectService(projectRepository);
        }

        @org.springframework.context.annotation.Bean
        ExperimentService experimentService(
                ExperimentRepository experimentRepository, ExperimentRunRepository runRepository) {
            return new ExperimentService(experimentRepository, runRepository);
        }
    }

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ExperimentRepository experimentRepository;

    @Autowired
    private ExperimentRunRepository runRepository;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ExperimentService experimentService;

    @Test
    void scopedRead_seesOwnAndSharedButNotForeign() {
        UUID own = stampProject("p-own", "tenant-a").getId();
        UUID shared = stampProject("p-shared", null).getId();
        UUID foreign = stampProject("p-foreign", "tenant-b").getId();

        assertThat(projectRepository.findById(own, TENANT_A)).isPresent();
        assertThat(projectRepository.findById(shared, TENANT_A)).isPresent();
        assertThat(projectRepository.findById(foreign, TENANT_A)).isEmpty();
    }

    @Test
    void scopedNull_seesSharedOnly() {
        UUID shared = stampProject("p-shared", null).getId();
        UUID owned = stampProject("p-owned", "tenant-a").getId();

        assertThat(projectRepository.findById(shared, ANONYMOUS)).isPresent();
        assertThat(projectRepository.findById(owned, ANONYMOUS)).isEmpty();
    }

    @Test
    void unrestricted_seesEveryTenant() {
        UUID a = stampProject("p-a", "tenant-a").getId();
        UUID b = stampProject("p-b", "tenant-b").getId();
        UUID shared = stampProject("p-s", null).getId();

        assertThat(projectRepository.findById(a, WORKER)).isPresent();
        assertThat(projectRepository.findById(b, WORKER)).isPresent();
        assertThat(projectRepository.findById(shared, WORKER)).isPresent();
    }

    @Test
    void projectCountAggregate_isScoped() {
        stampProject("a1", "tenant-a");
        stampProject("a2", "tenant-a");
        stampProject("s1", null);
        stampProject("b1", "tenant-b");

        // tenant A sees its own two plus the one shared row, never tenant B's.
        assertThat(projectService.listProjects(TENANT_A)).hasSize(3);
        // anonymous sees the shared row only.
        assertThat(projectService.listProjects(ANONYMOUS)).hasSize(1);
        // a worker sees all four.
        assertThat(projectService.listProjects(WORKER)).hasSize(4);
    }

    @Test
    void twoTenants_canEachOwnDefaultProject() {
        Project a = projectService.getOrCreateProject("default", TENANT_A);
        Project b = projectService.getOrCreateProject("default", TENANT_B);

        assertThat(a.getId()).isNotEqualTo(b.getId());
        assertThat(a.getTenantId()).isEqualTo("tenant-a");
        assertThat(b.getTenantId()).isEqualTo("tenant-b");
        // Each resolves only its own row on a second lookup.
        assertThat(projectService.getOrCreateProject("default", TENANT_A).getId())
                .isEqualTo(a.getId());
        assertThat(projectService.getOrCreateProject("default", TENANT_B).getId())
                .isEqualTo(b.getId());
    }

    @Test
    void crossTenantById_returnsNotFoundForExperimentAndRun() {
        Project projectB = stampProject("p-b", "tenant-b");
        Experiment experimentB = experimentRepository.save(stamp(new Experiment(projectB, "e"), "tenant-b"));
        ExperimentRun runB = runRepository.save(stamp(new ExperimentRun(experimentB, null), "tenant-b"));

        // Tenant A cannot see tenant B's experiment or run by id; both surface as a 404 (IllegalArgument).
        assertThatThrownBy(() -> experimentService.getExperiment(experimentB.getId(), TENANT_A))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        assertThat(runRepository.findById(runB.getId(), TENANT_A)).isEmpty();
        // The owning tenant and a worker still see them.
        assertThat(experimentService.getExperiment(experimentB.getId(), TENANT_B))
                .isNotNull();
        assertThat(runRepository.findById(runB.getId(), WORKER)).isPresent();
    }

    @Test
    void worker_unrestrictedSeesAllTenantsRuns() {
        Project pa = stampProject("pa", "tenant-a");
        Project pb = stampProject("pb", "tenant-b");
        Experiment ea = experimentRepository.save(stamp(new Experiment(pa, "ea"), "tenant-a"));
        Experiment eb = experimentRepository.save(stamp(new Experiment(pb, "eb"), "tenant-b"));
        runRepository.save(stamp(new ExperimentRun(ea, null), "tenant-a"));
        runRepository.save(stamp(new ExperimentRun(eb, null), "tenant-b"));

        List<ExperimentRun> aRuns = runRepository.findByExperiment(ea, WORKER);
        List<ExperimentRun> bRuns = runRepository.findByExperiment(eb, WORKER);
        assertThat(aRuns).hasSize(1);
        assertThat(bRuns).hasSize(1);
    }

    private Project stampProject(String name, String tenantId) {
        Project project = new Project(name);
        project.setTenantId(tenantId);
        return projectRepository.save(project);
    }

    private static Experiment stamp(Experiment experiment, String tenantId) {
        experiment.setTenantId(tenantId);
        return experiment;
    }

    private static ExperimentRun stamp(ExperimentRun run, String tenantId) {
        run.setTenantId(tenantId);
        return run;
    }
}
