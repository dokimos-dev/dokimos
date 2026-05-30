package dev.dokimos.server.controller.v1;

import dev.dokimos.server.dto.v1.DiffView;
import dev.dokimos.server.service.DiffService;
import dev.dokimos.server.tenant.TenantScopeResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exposes the per-case run-diff view: the same comparison the CI gate runs, presented as a full,
 * paginated table of every case with per-evaluator deltas. This is a read-only GET and is therefore
 * allowed by the auth filter without an API key.
 */
@RestController
@RequestMapping("/api/v1/experiments")
public class DiffController {

    private static final Set<String> ALLOWED_STATUS = Set.of("ALL", "REGRESSED", "IMPROVED", "CHANGED");

    private final DiffService diffService;

    public DiffController(DiffService diffService) {
        this.diffService = diffService;
    }

    /**
     * Returns the per-case diff between a candidate run and an explicit baseline run.
     *
     * @param experimentId  the experiment both runs belong to
     * @param candidateRunId the candidate run (the "new" side)
     * @param baselineRunId  the baseline run (the "old" side); required
     * @param status         optional case filter: ALL (default), REGRESSED, IMPROVED, or CHANGED
     * @param pageable       pagination over the filtered, sorted case list
     * @return HTTP 200 with the summary and the requested page of cases. 400 when
     *     {@code baselineRunId} is missing or {@code status} is not a recognized value; 404 when the
     *     experiment or a run is missing or a run does not belong to the experiment; 409 when either
     *     run is not terminal
     */
    @GetMapping("/{experimentId}/runs/{candidateRunId}/diff")
    public DiffView diff(
            @PathVariable UUID experimentId,
            @PathVariable UUID candidateRunId,
            @RequestParam UUID baselineRunId,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            Pageable pageable,
            HttpServletRequest http) {
        if (status != null && !ALLOWED_STATUS.contains(status.trim().toUpperCase())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unknown status filter: " + status + " (expected ALL, REGRESSED, IMPROVED, or CHANGED)");
        }
        return diffService.listDiff(
                experimentId, candidateRunId, baselineRunId, status, pageable, TenantScopeResolver.scope(http));
    }
}
