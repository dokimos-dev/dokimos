package dev.dokimos.server.controller.v1;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.dokimos.server.controller.GlobalExceptionHandler;
import dev.dokimos.server.dto.v1.EnqueueJudgeRequest;
import dev.dokimos.server.dto.v1.EvalJobView;
import dev.dokimos.server.entity.EvalJobStatus;
import dev.dokimos.server.service.EvalJobService;
import java.time.Instant;
import java.util.List;
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
class EvalJobControllerTest extends AbstractControllerTest {

    @Mock
    private EvalJobService jobService;

    private UUID runId;
    private UUID connectionId;

    @BeforeEach
    void setUp() {
        EvalJobController controller = new EvalJobController(jobService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        runId = UUID.randomUUID();
        connectionId = UUID.randomUUID();
    }

    private EnqueueJudgeRequest request() {
        return new EnqueueJudgeRequest(connectionId, "judge", "is correct", List.of("ACTUAL_OUTPUT"), 0.0, 1.0, 0.5);
    }

    private EvalJobView view(UUID id) {
        return new EvalJobView(
                id, runId, connectionId, "judge", EvalJobStatus.PENDING, 0, null, Instant.now(), null, null);
    }

    @Test
    void enqueue_shouldReturn201() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(jobService.enqueue(eq(runId), any())).thenReturn(view(jobId));

        mockMvc.perform(post("/api/v1/runs/" + runId + "/judge-jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void enqueue_shouldReturn409OnDuplicateJob() throws Exception {
        doThrow(new IllegalStateException("A judge job already exists"))
                .when(jobService)
                .enqueue(eq(runId), any());

        mockMvc.perform(post("/api/v1/runs/" + runId + "/judge-jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request())))
                .andExpect(status().isConflict());
    }

    @Test
    void enqueue_shouldReturn400WhenParamsEmpty() throws Exception {
        EnqueueJudgeRequest invalid =
                new EnqueueJudgeRequest(connectionId, "judge", "is correct", List.of(), 0.0, 1.0, 0.5);

        mockMvc.perform(post("/api/v1/runs/" + runId + "/judge-jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_shouldReturnEmptyList() throws Exception {
        when(jobService.getJobsForRun(runId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/runs/" + runId + "/judge-jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
