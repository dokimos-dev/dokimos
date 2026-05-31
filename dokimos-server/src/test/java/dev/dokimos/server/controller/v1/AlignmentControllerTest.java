package dev.dokimos.server.controller.v1;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.dokimos.server.controller.GlobalExceptionHandler;
import dev.dokimos.server.dto.v1.AlignmentView;
import dev.dokimos.server.service.AlignmentService;
import dev.dokimos.server.tenant.TenantScope;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class AlignmentControllerTest extends AbstractControllerTest {

    @Mock
    private AlignmentService alignmentService;

    @BeforeEach
    void setUp() {
        AlignmentController controller = new AlignmentController(alignmentService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void alignment_returns200WithBreakdown() throws Exception {
        UUID runId = UUID.randomUUID();
        AlignmentView view = new AlignmentView(
                3,
                List.of(
                        new AlignmentView.EvaluatorAlignment("accuracy", 2, 2, 1, 1.0),
                        new AlignmentView.EvaluatorAlignment("relevance", 0, 0, 1, null)));
        when(alignmentService.getAlignment(eq(runId), any(TenantScope.class))).thenReturn(view);

        mockMvc.perform(get("/api/v1/runs/{runId}/alignment", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annotatedItems").value(3))
                .andExpect(jsonPath("$.evaluators[0].evaluatorName").value("accuracy"))
                .andExpect(jsonPath("$.evaluators[0].comparableCount").value(2))
                .andExpect(jsonPath("$.evaluators[0].agreedCount").value(2))
                .andExpect(jsonPath("$.evaluators[0].excludedUnsure").value(1))
                .andExpect(jsonPath("$.evaluators[0].alignmentRate").value(1.0))
                .andExpect(jsonPath("$.evaluators[1].alignmentRate").doesNotExist());
    }

    @Test
    void alignment_returns404WhenRunMissing() throws Exception {
        UUID runId = UUID.randomUUID();
        when(alignmentService.getAlignment(eq(runId), any(TenantScope.class)))
                .thenThrow(new IllegalArgumentException("Run not found: " + runId));

        mockMvc.perform(get("/api/v1/runs/{runId}/alignment", runId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Run not found: " + runId));
    }
}
