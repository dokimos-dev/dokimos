package dev.dokimos.server.controller.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProjectControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        when(projectService.listProjects()).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("my-project"))
                .andExpect(jsonPath("$[0].experimentCount").value(5));
    }

    @Test
    void listProjects_shouldReturnEmptyList() throws Exception {
        when(projectService.listProjects()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listExperiments_shouldReturnExperiments() throws Exception {
        Project project = new Project("my-project");
        UUID experimentId = UUID.randomUUID();
        ExperimentSummary summary = new ExperimentSummary(
                experimentId, "my-experiment", Instant.now(), null);

        when(projectService.getProject("my-project")).thenReturn(project);
        when(experimentService.listExperiments(project)).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/projects/my-project/experiments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("my-experiment"));
    }

    @Test
    void listExperiments_shouldReturn404WhenProjectNotFound() throws Exception {
        when(projectService.getProject("unknown"))
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

        when(projectService.getOrCreateProject("my-project")).thenReturn(project);
        when(experimentService.getOrCreateExperiment(project, "my-experiment")).thenReturn(experiment);
        when(runService.createRun(eq(experiment), any())).thenReturn(run);

        CreateRunRequest request = new CreateRunRequest("my-experiment", Map.of("key", "value"));

        mockMvc.perform(post("/api/v1/projects/my-project/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runId").value(runId.toString()));
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
