package dev.dokimos.server.controller.v1;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.dokimos.server.controller.GlobalExceptionHandler;
import dev.dokimos.server.dto.v1.CreateRunRequest;
import dev.dokimos.server.dto.v1.ExperimentSummary;
import dev.dokimos.server.dto.v1.ProjectSummary;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.service.ExperimentService;
import dev.dokimos.server.service.ProjectService;
import dev.dokimos.server.service.RunService;
import dev.dokimos.server.tenant.TenantScope;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class ProjectControllerTest extends AbstractControllerTest {

    @Mock
    private ProjectService projectService;

    @Mock
    private ExperimentService experimentService;

    @Mock
    private RunService runService;

    @BeforeEach
    void setUp() {
        ProjectController controller = new ProjectController(projectService, experimentService, runService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listProjects_shouldReturnProjects() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectSummary summary = new ProjectSummary(projectId, "my-project", 5, Instant.now());
        when(projectService.listProjects(any(TenantScope.class))).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("my-project"))
                .andExpect(jsonPath("$[0].experimentCount").value(5));
    }

    @Test
    void listProjects_shouldReturnEmptyList() throws Exception {
        when(projectService.listProjects(any(TenantScope.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listExperiments_shouldReturnExperiments() throws Exception {
        Project project = new Project("my-project");
        UUID experimentId = UUID.randomUUID();
        ExperimentSummary summary = new ExperimentSummary(experimentId, "my-experiment", Instant.now(), null);

        when(projectService.getProject(eq("my-project"), any(TenantScope.class)))
                .thenReturn(project);
        when(experimentService.listExperiments(eq(project), any(TenantScope.class)))
                .thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/projects/my-project/experiments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("my-experiment"));
    }

    @Test
    void listExperiments_shouldReturn404WhenProjectNotFound() throws Exception {
        when(projectService.getProject(eq("unknown"), any(TenantScope.class)))
                .thenThrow(new IllegalArgumentException("Project not found: unknown"));

        mockMvc.perform(get("/api/v1/projects/unknown/experiments"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Project not found: unknown"));
    }

    @Test
    void createRun_shouldCreateAndReturnRunId() throws Exception {
        Project project = new Project("my-project");
        Experiment experiment = new Experiment(project, "my-experiment");
        UUID runId = UUID.randomUUID();
        ExperimentRun run = new ExperimentRun(experiment, Map.of());
        setField(run, "id", runId);

        when(projectService.getOrCreateProject(eq("my-project"), any(TenantScope.class)))
                .thenReturn(project);
        when(experimentService.getOrCreateExperiment(eq(project), eq("my-experiment"), any(TenantScope.class)))
                .thenReturn(experiment);
        when(runService.createRun(eq(experiment), any(CreateRunRequest.class), any(TenantScope.class)))
                .thenReturn(run);

        CreateRunRequest request =
                new CreateRunRequest("my-experiment", Map.of("key", "value"), "nightly", "abc123", "main", "ci");

        mockMvc.perform(post("/api/v1/projects/my-project/runs")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runId").value(runId.toString()));
    }

    @Test
    void createRun_shouldReturn400WhenDatasetNameWithoutVersion() throws Exception {
        CreateRunRequest request = new CreateRunRequest("my-experiment", null, null, null, null, null, "qa", null);

        mockMvc.perform(post("/api/v1/projects/my-project/runs")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("must be set together")));
    }

    @Test
    void createRun_shouldReturn400WhenDatasetVersionWithoutName() throws Exception {
        CreateRunRequest request = new CreateRunRequest("my-experiment", null, null, null, null, null, null, 1);

        mockMvc.perform(post("/api/v1/projects/my-project/runs")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("must be set together")));
    }

    @Test
    void createRun_shouldReturn400WhenDatasetVersionIsZero() throws Exception {
        CreateRunRequest request = new CreateRunRequest("my-experiment", null, null, null, null, null, "qa", 0);

        mockMvc.perform(post("/api/v1/projects/my-project/runs")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteProject_shouldReturn204OnSuccess() throws Exception {
        doNothing().when(projectService).deleteProject(eq("my-project"), any(TenantScope.class));

        mockMvc.perform(delete("/api/v1/projects/{projectName}", "my-project")).andExpect(status().isNoContent());

        verify(projectService).deleteProject(eq("my-project"), any(TenantScope.class));
    }

    @Test
    void deleteProject_shouldReturn404WhenNotFound() throws Exception {
        doThrow(new IllegalArgumentException("Project not found: unknown"))
                .when(projectService)
                .deleteProject(eq("unknown"), any(TenantScope.class));

        mockMvc.perform(delete("/api/v1/projects/{projectName}", "unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Project not found: unknown"));
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
