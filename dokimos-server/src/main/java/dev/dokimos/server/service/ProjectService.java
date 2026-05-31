package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.ProjectSummary;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.repository.ProjectRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.util.List;
import java.util.Objects;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    /**
     * Resolves the project by name within the scope, creating it stamped with the scope's tenant when it
     * does not exist. The lookup is scoped so two tenants can each own a project of the same name; a new
     * row is stamped from the scope so it lands in the caller's tenant.
     *
     * @param name the project name
     * @param scope the tenant scope of the caller
     * @return the existing or newly created project
     */
    @Transactional
    @NonNull
    public Project getOrCreateProject(String name, TenantScope scope) {
        return Objects.requireNonNull(projectRepository.findByName(name, scope).orElseGet(() -> {
            Project project = new Project(name);
            project.setTenantId(scope.stampTenantId());
            return projectRepository.save(project);
        }));
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> listProjects(TenantScope scope) {
        return projectRepository.findAllWithExperimentCount(scope).stream()
                .map(row -> {
                    Project project = (Project) row[0];
                    long count = (Long) row[1];
                    return new ProjectSummary(project.getId(), project.getName(), count, project.getCreatedAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    @NonNull
    public Project getProject(String name, TenantScope scope) {
        return Objects.requireNonNull(projectRepository
                .findByName(name, scope)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + name)));
    }

    /** Deletes a project visible under the scope; FKs cascade to its experiments, runs, items, and evals. */
    @Transactional
    public void deleteProject(String name, TenantScope scope) {
        Project project = getProject(name, scope);
        projectRepository.delete(project);
    }
}
