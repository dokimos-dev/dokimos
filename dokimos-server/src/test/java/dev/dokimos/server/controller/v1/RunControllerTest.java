package dev.dokimos.server.controller.v1;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.dokimos.server.controller.GlobalExceptionHandler;
import dev.dokimos.server.dto.v1.AddItemsRequest;
import dev.dokimos.server.dto.v1.RunDetails;
import dev.dokimos.server.dto.v1.UpdateRunRequest;
import dev.dokimos.server.entity.RunStatus;
import dev.dokimos.server.service.RunService;
import dev.dokimos.server.tenant.TenantScope;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
        RunDetails.ItemSummary itemSummary = new RunDetails.ItemSummary(
                UUID.randomUUID(),
                Map.of("input", "q"),
                Map.of("output", "a"),
                Map.of("output", "a"),
                null,
                List.of(),
                Instant.now(),
                null,
                null,
                100,
                50,
                0.002,
                430L);
        Page<RunDetails.ItemSummary> page = new PageImpl<>(List.of(itemSummary), pageRequest, 1);
        UUID experimentId = UUID.randomUUID();

        RunDetails details = new RunDetails(
                runId,
                experimentId,
                "my-experiment",
                "my-project",
                RunStatus.SUCCESS,
                Map.of(),
                10,
                8,
                0.8,
                Instant.now(),
                Instant.now(),
                null,
                null,
                300L,
                150L,
                0.006,
                412.5,
                null,
                null,
                page);

        when(runService.getRunDetails(eq(runId), any(Pageable.class), any(TenantScope.class)))
                .thenReturn(details);

        mockMvc.perform(get("/api/v1/runs/{runId}", runId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId.toString()))
                .andExpect(jsonPath("$.experimentName").value("my-experiment"))
                .andExpect(jsonPath("$.projectName").value("my-project"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.passRate").value(0.8))
                .andExpect(jsonPath("$.totalTokensIn").value(300))
                .andExpect(jsonPath("$.totalTokensOut").value(150))
                .andExpect(jsonPath("$.totalCostUsd").value(0.006))
                .andExpect(jsonPath("$.avgLatencyMs").value(412.5))
                .andExpect(jsonPath("$.items.content[0].tokensIn").value(100))
                .andExpect(jsonPath("$.items.content[0].tokensOut").value(50))
                .andExpect(jsonPath("$.items.content[0].costUsd").value(0.002))
                .andExpect(jsonPath("$.items.content[0].latencyMs").value(430));
    }

    @Test
    void getRunDetails_shouldReturn404WhenNotFound() throws Exception {
        UUID runId = UUID.randomUUID();
        when(runService.getRunDetails(eq(runId), any(Pageable.class), any(TenantScope.class)))
                .thenThrow(new IllegalArgumentException("Run not found: " + runId));

        mockMvc.perform(get("/api/v1/runs/{runId}", runId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Run not found: " + runId));
    }

    @Test
    void addItems_shouldReturnOk() throws Exception {
        UUID runId = UUID.randomUUID();
        AddItemsRequest request = new AddItemsRequest(List.of(new AddItemsRequest.ItemData(
                Map.of("input", "test"),
                Map.of("output", "expected"),
                Map.of("output", "actual"),
                List.of(new AddItemsRequest.EvalData("eval", 1.0, 0.9, true, "pass", Map.of())),
                true)));

        doNothing().when(runService).addItems(eq(runId), any(AddItemsRequest.class), any(), any(TenantScope.class));

        mockMvc.perform(post("/api/v1/runs/{runId}/items", runId)
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ok"));

        verify(runService).addItems(eq(runId), any(AddItemsRequest.class), any(), any(TenantScope.class));
    }

    @Test
    void addItems_shouldAcceptMetricFields() throws Exception {
        UUID runId = UUID.randomUUID();
        String body = """
                {"items":[{"inputs":{"input":"q"},"actualOutputs":{"output":"a"},"success":true,\
                "tokensIn":100,"tokensOut":50,"costUsd":0.002,"latencyMs":430}]}""";

        ArgumentCaptor<AddItemsRequest> captor = ArgumentCaptor.forClass(AddItemsRequest.class);
        doNothing().when(runService).addItems(eq(runId), captor.capture(), any(), any(TenantScope.class));

        mockMvc.perform(post("/api/v1/runs/{runId}/items", runId)
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ok"));

        AddItemsRequest.ItemData item = captor.getValue().items().get(0);
        org.assertj.core.api.Assertions.assertThat(item.tokensIn()).isEqualTo(100);
        org.assertj.core.api.Assertions.assertThat(item.tokensOut()).isEqualTo(50);
        org.assertj.core.api.Assertions.assertThat(item.costUsd()).isEqualTo(0.002);
        org.assertj.core.api.Assertions.assertThat(item.latencyMs()).isEqualTo(430L);
    }

    @Test
    void addItems_shouldPassIdempotencyKeyHeaderToService() throws Exception {
        UUID runId = UUID.randomUUID();
        AddItemsRequest request = new AddItemsRequest(List.of(new AddItemsRequest.ItemData(
                Map.of("input", "test"),
                Map.of("output", "expected"),
                Map.of("output", "actual"),
                List.of(new AddItemsRequest.EvalData("eval", 1.0, 0.9, true, "pass", Map.of())),
                true)));

        doNothing()
                .when(runService)
                .addItems(eq(runId), any(AddItemsRequest.class), eq("batch-key-1"), any(TenantScope.class));

        mockMvc.perform(post("/api/v1/runs/{runId}/items", runId)
                        .header("Idempotency-Key", "batch-key-1")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ok"));

        verify(runService).addItems(eq(runId), any(AddItemsRequest.class), eq("batch-key-1"), any(TenantScope.class));
    }

    @Test
    void addItems_shouldReturn400WhenDatasetItemIdMalformed() throws Exception {
        UUID runId = UUID.randomUUID();
        String body = """
                {"items":[{"inputs":{"input":"x"},"actualOutputs":{"output":"y"},"datasetItemId":"not-a-uuid","success":true}]}""";

        mockMvc.perform(post("/api/v1/runs/{runId}/items", runId)
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(runService, never()).addItems(any(), any(), any(), any());
    }

    @Test
    void addItems_shouldReturn404WhenRunNotFound() throws Exception {
        UUID runId = UUID.randomUUID();
        AddItemsRequest request = new AddItemsRequest(List.of(new AddItemsRequest.ItemData(
                Map.of("input", "test"), Map.of("output", "expected"), Map.of("output", "actual"), null, true)));

        doThrow(new IllegalArgumentException("Run not found: " + runId))
                .when(runService)
                .addItems(eq(runId), any(AddItemsRequest.class), any(), any(TenantScope.class));

        mockMvc.perform(post("/api/v1/runs/{runId}/items", runId)
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void addItems_shouldReturn409WhenRunNotRunning() throws Exception {
        UUID runId = UUID.randomUUID();
        AddItemsRequest request = new AddItemsRequest(List.of(new AddItemsRequest.ItemData(
                Map.of("input", "test"), Map.of("output", "expected"), Map.of("output", "actual"), null, true)));

        doThrow(new IllegalStateException("Cannot add items to a run that is not RUNNING: " + runId))
                .when(runService)
                .addItems(eq(runId), any(AddItemsRequest.class), any(), any(TenantScope.class));

        mockMvc.perform(post("/api/v1/runs/{runId}/items", runId)
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot add items to a run that is not RUNNING: " + runId));
    }

    @Test
    void updateRun_shouldReturnUpdated() throws Exception {
        UUID runId = UUID.randomUUID();
        UpdateRunRequest request = new UpdateRunRequest(RunStatus.SUCCESS);

        doNothing().when(runService).updateRun(eq(runId), any(UpdateRunRequest.class), any(TenantScope.class));

        mockMvc.perform(patch("/api/v1/runs/{runId}", runId)
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("updated"));

        verify(runService).updateRun(eq(runId), any(UpdateRunRequest.class), any(TenantScope.class));
    }

    @Test
    void updateRun_shouldReturn404WhenNotFound() throws Exception {
        UUID runId = UUID.randomUUID();
        UpdateRunRequest request = new UpdateRunRequest(RunStatus.SUCCESS);

        doThrow(new IllegalArgumentException("Run not found: " + runId))
                .when(runService)
                .updateRun(eq(runId), any(UpdateRunRequest.class), any(TenantScope.class));

        mockMvc.perform(patch("/api/v1/runs/{runId}", runId)
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRun_shouldReturn204OnSuccess() throws Exception {
        UUID runId = UUID.randomUUID();
        doNothing().when(runService).deleteRun(eq(runId), any(TenantScope.class));

        mockMvc.perform(delete("/api/v1/runs/{runId}", runId)).andExpect(status().isNoContent());

        verify(runService).deleteRun(eq(runId), any(TenantScope.class));
    }

    @Test
    void deleteRun_shouldReturn404WhenNotFound() throws Exception {
        UUID runId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Run not found: " + runId))
                .when(runService)
                .deleteRun(eq(runId), any(TenantScope.class));

        mockMvc.perform(delete("/api/v1/runs/{runId}", runId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Run not found: " + runId));
    }
}
