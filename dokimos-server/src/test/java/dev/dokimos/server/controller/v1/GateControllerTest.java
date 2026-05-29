package dev.dokimos.server.controller.v1;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.dokimos.server.controller.GlobalExceptionHandler;
import dev.dokimos.server.dto.v1.GateRequest;
import dev.dokimos.server.dto.v1.GateResult;
import dev.dokimos.server.service.GateService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class GateControllerTest extends AbstractControllerTest {

    @Mock
    private GateService gateService;

    @BeforeEach
    void setUp() {
        GateController controller = new GateController(gateService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Test
    void evaluateGate_returns200WithVerdict() throws Exception {
        UUID experimentId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        UUID baselineId = UUID.randomUUID();
        GateResult verdict = new GateResult(
                "FAIL",
                false,
                candidateId,
                baselineId,
                "dataset_item_id",
                0.9,
                0.5,
                -0.4,
                true,
                0,
                3,
                1,
                0,
                0,
                List.of(),
                List.of(),
                false);
        when(gateService.evaluateGate(eq(experimentId), any(GateRequest.class))).thenReturn(verdict);

        GateRequest request = new GateRequest(candidateId, null, null);
        mockMvc.perform(post("/api/v1/experiments/{experimentId}/gate", experimentId)
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.pairing").value("dataset_item_id"))
                .andExpect(jsonPath("$.regressedCount").value(3));
    }

    @Test
    void evaluateGate_returns404WhenExperimentOrRunMissing() throws Exception {
        UUID experimentId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        when(gateService.evaluateGate(eq(experimentId), any(GateRequest.class)))
                .thenThrow(new IllegalArgumentException("Candidate run not found: " + candidateId));

        GateRequest request = new GateRequest(candidateId, null, null);
        mockMvc.perform(post("/api/v1/experiments/{experimentId}/gate", experimentId)
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Candidate run not found: " + candidateId));
    }

    @Test
    void evaluateGate_returns409WhenCandidateNotTerminal() throws Exception {
        UUID experimentId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        when(gateService.evaluateGate(eq(experimentId), any(GateRequest.class)))
                .thenThrow(new IllegalStateException("Candidate run is still RUNNING: " + candidateId));

        GateRequest request = new GateRequest(candidateId, null, null);
        mockMvc.perform(post("/api/v1/experiments/{experimentId}/gate", experimentId)
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Candidate run is still RUNNING: " + candidateId));
    }

    @Test
    void evaluateGate_returns400WhenCandidateRunIdMissing() throws Exception {
        UUID experimentId = UUID.randomUUID();
        // candidateRunId omitted; @NotNull bean validation must reject before the service is hit.
        GateRequest request = new GateRequest(null, null, null);
        mockMvc.perform(post("/api/v1/experiments/{experimentId}/gate", experimentId)
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }
}
