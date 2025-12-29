package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.ProjectSummary;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.repository.ProjectRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    @NonNull
    public Project getOrCreateProject(String name) {
        return Objects.requireNonNull(projectRepository.findByName(name)
                .orElseGet(() -> projectRepository.save(new Project(name))));
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> listProjects() {
        return projectRepository.findAllWithExperimentCount().stream()
                .map(row -> {
                    Project project = (Project) row[0];
                    long count = (Long) row[1];
                    return new ProjectSummary(
                            project.getId(),
                            project.getName(),
                            count,
                            project.getCreatedAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    @NonNull
    public Project getProject(String name) {
        return Objects.requireNonNull(projectRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + name)));
    }
}
