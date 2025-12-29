package dev.dokimos.server.controller.v1;

import dev.dokimos.server.controller.GlobalExceptionHandler;
import dev.dokimos.server.dto.v1.AddItemsRequest;
import dev.dokimos.server.dto.v1.RunDetails;
import dev.dokimos.server.dto.v1.UpdateRunRequest;
import dev.dokimos.server.entity.RunStatus;
import dev.dokimos.server.service.RunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class RunControllerTest extends AbstractControllerTest {

        @Mock
        private RunService runService;

        @BeforeEach
        void setUp() {
                RunController controller = new RunController(runService);
                mockMvc = MockMvcBuilders.standaloneSetup(controller)
                                .setControllerAdvice(new GlobalExceptionHandler())
                                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                                .build();
        }

        @Test
        void getRunDetails_shouldReturnDetails() throws Exception {
                UUID runId = UUID.randomUUID();

                PageRequest pageRequest = PageRequest.of(0, 10);
                Page<RunDetails.ItemSummary> emptyPage = new PageImpl<>(List.of(), pageRequest, 0);
                UUID experimentId = UUID.randomUUID();

                RunDetails details = new RunDetails(
                                runId, experimentId, "my-experiment", "my-project", RunStatus.SUCCESS,
                                Map.of(), 10, 8, 0.8, Instant.now(), Instant.now(), emptyPage);

                when(runService.getRunDetails(eq(runId), any(Pageable.class))).thenReturn(details);

                mockMvc.perform(get("/api/v1/runs/{runId}", runId))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(runId.toString()))
                                .andExpect(jsonPath("$.experimentName").value("my-experiment"))
                                .andExpect(jsonPath("$.projectName").value("my-project"))
                                .andExpect(jsonPath("$.status").value("SUCCESS"))
                                .andExpect(jsonPath("$.passRate").value(0.8));
        }

        @Test
        void getRunDetails_shouldReturn404WhenNotFound() throws Exception {
                UUID runId = UUID.randomUUID();
                when(runService.getRunDetails(eq(runId), any(Pageable.class)))
                                .thenThrow(new IllegalArgumentException("Run not found: " + runId));

                mockMvc.perform(get("/api/v1/runs/{runId}", runId))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.message").value("Run not found: " + runId));
        }

        @Test
        void addItems_shouldReturnOk() throws Exception {
                UUID runId = UUID.randomUUID();
                AddItemsRequest request = new AddItemsRequest(List.of(
                                new AddItemsRequest.ItemData(
                                                Map.of("input", "test"),
                                                Map.of("output", "expected"),
                                                Map.of("output", "actual"),
                                                List.of(new AddItemsRequest.EvalData("eval", 1.0, 0.9, true, "pass",
                                                                Map.of())),
                                                true)));

                doNothing().when(runService).addItems(eq(runId), any(AddItemsRequest.class));

                mockMvc.perform(post("/api/v1/runs/{runId}/items", runId)
                                .contentType(MediaType.APPLICATION_JSON_VALUE)
                                .content(toJson(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.status").value("ok"));

                verify(runService).addItems(eq(runId), any(AddItemsRequest.class));
        }

        @Test
        void addItems_shouldReturn404WhenRunNotFound() throws Exception {
                UUID runId = UUID.randomUUID();
                AddItemsRequest request = new AddItemsRequest(List.of(
                                new AddItemsRequest.ItemData(
                                                Map.of("input", "test"),
                                                Map.of("output", "expected"),
                                                Map.of("output", "actual"),
                                                null,
                                                true)));

                doThrow(new IllegalArgumentException("Run not found: " + runId))
                                .when(runService).addItems(eq(runId), any(AddItemsRequest.class));

                mockMvc.perform(post("/api/v1/runs/{runId}/items", runId)
                                .contentType(MediaType.APPLICATION_JSON_VALUE)
                                .content(toJson(request)))
                                .andExpect(status().isNotFound());
        }

        @Test
        void updateRun_shouldReturnUpdated() throws Exception {
                UUID runId = UUID.randomUUID();
                UpdateRunRequest request = new UpdateRunRequest(RunStatus.SUCCESS);

                doNothing().when(runService).updateRun(eq(runId), any(UpdateRunRequest.class));

                mockMvc.perform(patch("/api/v1/runs/{runId}", runId)
                                .contentType(MediaType.APPLICATION_JSON_VALUE)
                                .content(toJson(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("updated"));

                verify(runService).updateRun(eq(runId), any(UpdateRunRequest.class));
        }

        @Test
        void updateRun_shouldReturn404WhenNotFound() throws Exception {
                UUID runId = UUID.randomUUID();
                UpdateRunRequest request = new UpdateRunRequest(RunStatus.SUCCESS);

                doThrow(new IllegalArgumentException("Run not found: " + runId))
                                .when(runService).updateRun(eq(runId), any(UpdateRunRequest.class));

                mockMvc.perform(patch("/api/v1/runs/{runId}", runId)
                                .contentType(MediaType.APPLICATION_JSON_VALUE)
                                .content(toJson(request)))
                                .andExpect(status().isNotFound());
        }
}
