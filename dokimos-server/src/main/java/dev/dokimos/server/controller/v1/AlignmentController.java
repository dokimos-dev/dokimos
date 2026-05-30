package dev.dokimos.server.controller.v1;

import dev.dokimos.server.dto.v1.AlignmentView;
import dev.dokimos.server.service.AlignmentService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the per-evaluator judge-human alignment for a run: the agreement rate between automated
 * evaluator verdicts and human annotations. This is a read-only GET and is therefore allowed by the
 * auth filter without an API key.
 */
@RestController
@RequestMapping("/api/v1/runs")
public class AlignmentController {

    private final AlignmentService alignmentService;

    public AlignmentController(AlignmentService alignmentService) {
        this.alignmentService = alignmentService;
    }

    /**
     * Returns the judge-human alignment breakdown for a run.
     *
     * @param runId the run to analyze
     * @return HTTP 200 with the per-evaluator agreement breakdown; 404 when the run does not exist
     */
    @GetMapping("/{runId}/alignment")
    public AlignmentView alignment(@PathVariable UUID runId) {
        return alignmentService.getAlignment(runId);
    }
}
