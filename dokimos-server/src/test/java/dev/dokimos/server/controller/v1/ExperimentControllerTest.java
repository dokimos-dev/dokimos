package dev.dokimos.server.controller.v1;

import dev.dokimos.server.controller.GlobalExceptionHandler;
import dev.dokimos.server.dto.v1.RunSummary;
import dev.dokimos.server.dto.v1.TrendData;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.entity.RunStatus;
import dev.dokimos.server.service.ExperimentService;
import dev.dokimos.server.service.RunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ExperimentControllerTest {

        private MockMvc mockMvc;

        @Mock
        private ExperimentService experimentService;

        @Mock
        private RunService runService;

        @BeforeEach
        void setUp() {
                ExperimentController controller = new ExperimentController(experimentService, runService);
                mockMvc = MockMvcBuilders.standaloneSetup(controller)
                                .setControllerAdvice(new GlobalExceptionHandler())
                                .build();
        }

        @Test
        void listRuns_shouldReturnRuns() throws Exception {
                UUID experimentId = UUID.randomUUID();
                UUID runId = UUID.randomUUID();
                Project project = new Project("my-project");
                Experiment experiment = new Experiment(project, "my-experiment");

                RunSummary summary = new RunSummary(
                                runId, RunStatus.SUCCESS, Map.of(), 10, 8, 0.8, Instant.now(), Instant.now());

                when(experimentService.getExperiment(experimentId)).thenReturn(experiment);
                when(runService.listRuns(experiment)).thenReturn(List.of(summary));

                mockMvc.perform(get("/api/v1/experiments/{experimentId}/runs", experimentId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].status").value("SUCCESS"))
                                .andExpect(jsonPath("$[0].passRate").value(0.8));
        }

        @Test
        void listRuns_shouldReturn404WhenExperimentNotFound() throws Exception {
                UUID experimentId = UUID.randomUUID();
                when(experimentService.getExperiment(experimentId))
                                .thenThrow(new IllegalArgumentException("Experiment not found: " + experimentId));

                mockMvc.perform(get("/api/v1/experiments/{experimentId}/runs", experimentId))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.message").value("Experiment not found: " + experimentId));
        }

        @Test
        void getTrends_shouldReturnTrendData() throws Exception {
                UUID experimentId = UUID.randomUUID();
                UUID runId = UUID.randomUUID();
                TrendData.RunPoint point = new TrendData.RunPoint(
                                runId, Instant.now(), 0.85, 20, 17);
                TrendData trendData = new TrendData("my-experiment", "my-project", List.of(point));

                when(experimentService.getTrends(experimentId, 20)).thenReturn(trendData);

                mockMvc.perform(get("/api/v1/experiments/{experimentId}/trends", experimentId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.experimentName").value("my-experiment"))
                                .andExpect(jsonPath("$.runs[0].passRate").value(0.85));
        }

        @Test
        void getTrends_shouldUseCustomLimit() throws Exception {
                UUID experimentId = UUID.randomUUID();
                TrendData trendData = new TrendData("my-experiment", "my-project", List.of());

                when(experimentService.getTrends(experimentId, 50)).thenReturn(trendData);

                mockMvc.perform(get("/api/v1/experiments/{experimentId}/trends", experimentId)
                                .param("limit", "50"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.experimentName").value("my-experiment"));
        }

        @Test
        void getTrends_shouldReturn404WhenExperimentNotFound() throws Exception {
                UUID experimentId = UUID.randomUUID();
                when(experimentService.getTrends(experimentId, 20))
                                .thenThrow(new IllegalArgumentException("Experiment not found: " + experimentId));

                mockMvc.perform(get("/api/v1/experiments/{experimentId}/trends", experimentId))
                                .andExpect(status().isNotFound());
        }
}
