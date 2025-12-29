package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.ProjectSummary;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(projectRepository);
    }

    @Test
    void getOrCreateProject_shouldReturnExistingProject() {
        Project existing = createProject("my-project");
        when(projectRepository.findByName("my-project")).thenReturn(Optional.of(existing));

        Project result = projectService.getOrCreateProject("my-project");

        assertThat(result).isEqualTo(existing);
        verify(projectRepository, never()).save(any());
    }

    @Test
    void getOrCreateProject_shouldCreateNewProject() {
        when(projectRepository.findByName("new-project")).thenReturn(Optional.empty());
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project result = projectService.getOrCreateProject("new-project");

        assertThat(result.getName()).isEqualTo("new-project");
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void getProject_shouldReturnProject() {
        Project project = createProject("my-project");
        when(projectRepository.findByName("my-project")).thenReturn(Optional.of(project));

        Project result = projectService.getProject("my-project");

        assertThat(result).isEqualTo(project);
    }

    @Test
    void getProject_shouldThrowWhenNotFound() {
        when(projectRepository.findByName("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProject("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project not found: unknown");
    }

    @Test
    void listProjects_shouldReturnProjectSummaries() {
        Project project1 = createProject("project-1");
        Project project2 = createProject("project-2");

        List<Object[]> rows = List.of(
                new Object[] { project1, 5L },
                new Object[] { project2, 3L });
        when(projectRepository.findAllWithExperimentCount()).thenReturn(rows);

        List<ProjectSummary> result = projectService.listProjects();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("project-1");
        assertThat(result.get(0).experimentCount()).isEqualTo(5);
        assertThat(result.get(1).name()).isEqualTo("project-2");
        assertThat(result.get(1).experimentCount()).isEqualTo(3);
    }

    @Test
    void listProjects_shouldReturnEmptyList() {
        when(projectRepository.findAllWithExperimentCount()).thenReturn(List.of());

        List<ProjectSummary> result = projectService.listProjects();

        assertThat(result).isEmpty();
    }

    private Project createProject(String name) {
        Project project = new Project(name);
        setField(project, "id", UUID.randomUUID());
        setField(project, "createdAt", Instant.now());
        return project;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
