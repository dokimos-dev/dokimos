package dev.dokimos.server.controller.v1;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.dokimos.server.controller.GlobalExceptionHandler;
import dev.dokimos.server.dto.v1.DiffCase;
import dev.dokimos.server.dto.v1.DiffSummary;
import dev.dokimos.server.dto.v1.DiffView;
import dev.dokimos.server.dto.v1.PageResponse;
import dev.dokimos.server.service.DiffService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class DiffControllerTest extends AbstractControllerTest {

    @Mock
    private DiffService diffService;

    @BeforeEach
    void setUp() {
        DiffController controller = new DiffController(diffService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    private DiffView sampleView(UUID baselineId, UUID candidateId) {
        DiffCase regressed = new DiffCase(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "REGRESSED",
                true,
                "what is 2+2?",
                List.of(new DiffCase.EvaluatorDiff("accuracy", 1.0, 0.0, -1.0, "REGRESSED", true)));
        PageResponse<DiffCase> page = PageResponse.of(new PageImpl<>(List.of(regressed), PageRequest.of(0, 20), 1));
        DiffSummary summary =
                new DiffSummary("dataset_item_id", baselineId, candidateId, 0.9, 0.5, -0.4, true, 0, 1, 2, 0, 0);
        return new DiffView(summary, page);
    }

    @Test
    void diff_returns200WithSummaryAndCases() throws Exception {
        UUID experimentId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        UUID baselineId = UUID.randomUUID();
        when(diffService.listDiff(eq(experimentId), eq(candidateId), eq(baselineId), any(), any(Pageable.class)))
                .thenReturn(sampleView(baselineId, candidateId));

        mockMvc.perform(get("/api/v1/experiments/{e}/runs/{c}/diff", experimentId, candidateId)
                        .param("baselineRunId", baselineId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.pairing").value("dataset_item_id"))
                .andExpect(jsonPath("$.summary.regressedCount").value(1))
                .andExpect(jsonPath("$.cases.content[0].status").value("REGRESSED"))
                .andExpect(jsonPath("$.cases.content[0].input").value("what is 2+2?"))
                .andExpect(jsonPath("$.cases.content[0].evaluators[0].name").value("accuracy"))
                .andExpect(jsonPath("$.cases.totalElements").value(1));
    }

    @Test
    void diff_returns400WhenBaselineRunIdMissing() throws Exception {
        UUID experimentId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/experiments/{e}/runs/{c}/diff", experimentId, candidateId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void diff_returns400WhenStatusFilterUnknown() throws Exception {
        UUID experimentId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        UUID baselineId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/experiments/{e}/runs/{c}/diff", experimentId, candidateId)
                        .param("baselineRunId", baselineId.toString())
                        .param("status", "BOGUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void diff_returns404WhenRunMissing() throws Exception {
        UUID experimentId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        UUID baselineId = UUID.randomUUID();
        when(diffService.listDiff(eq(experimentId), eq(candidateId), eq(baselineId), any(), any(Pageable.class)))
                .thenThrow(new IllegalArgumentException("Candidate run not found: " + candidateId));

        mockMvc.perform(get("/api/v1/experiments/{e}/runs/{c}/diff", experimentId, candidateId)
                        .param("baselineRunId", baselineId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Candidate run not found: " + candidateId));
    }

    @Test
    void diff_returns409WhenRunNotTerminal() throws Exception {
        UUID experimentId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        UUID baselineId = UUID.randomUUID();
        when(diffService.listDiff(eq(experimentId), eq(candidateId), eq(baselineId), any(), any(Pageable.class)))
                .thenThrow(new IllegalStateException("Candidate run is not in a terminal SUCCESS/FAILED status"));

        mockMvc.perform(get("/api/v1/experiments/{e}/runs/{c}/diff", experimentId, candidateId)
                        .param("baselineRunId", baselineId.toString()))
                .andExpect(status().isConflict());
    }
}
