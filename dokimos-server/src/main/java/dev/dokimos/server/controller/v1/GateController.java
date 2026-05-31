package dev.dokimos.server.controller.v1;

import dev.dokimos.server.dto.v1.GateRequest;
import dev.dokimos.server.dto.v1.GateResult;
import dev.dokimos.server.service.GateService;
import dev.dokimos.server.tenant.TenantScopeResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the CI regression gate. The single endpoint compares an already-ingested candidate run
 * against a resolved baseline and returns a pass/fail verdict CI can branch on.
 *
 * <p>This is a POST but read-only on stored data; it sits under {@code /api/v1/**} and so is subject
 * to the existing API-key auth filter for write methods.
 */
@RestController
@RequestMapping("/api/v1/experiments")
public class GateController {

    private final GateService gateService;

    public GateController(GateService gateService) {
        this.gateService = gateService;
    }

    /**
     * Evaluates the regression gate for a candidate run.
     *
     * @param experimentId the experiment the candidate belongs to
     * @param request      the gate request; {@code candidateRunId} is required
     * @return the gate verdict; HTTP 200 on PASS, FAIL, and NO_BASELINE alike (the verdict is in the
     *     body). 404 when the experiment or a referenced run is missing, 409 when the candidate run
     *     is not terminal, 400 when {@code candidateRunId} is absent
     */
    @PostMapping("/{experimentId}/gate")
    public GateResult evaluateGate(
            @PathVariable UUID experimentId, @Valid @RequestBody GateRequest request, HttpServletRequest http) {
        return gateService.evaluateGate(experimentId, request, TenantScopeResolver.scope(http));
    }
}
