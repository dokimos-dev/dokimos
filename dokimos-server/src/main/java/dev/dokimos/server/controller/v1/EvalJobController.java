package dev.dokimos.server.controller.v1;

import dev.dokimos.server.dto.v1.EnqueueJudgeRequest;
import dev.dokimos.server.dto.v1.EvalJobView;
import dev.dokimos.server.service.EvalJobService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/runs/{runId}/judge-jobs")
public class EvalJobController {

    private final EvalJobService jobService;

    public EvalJobController(EvalJobService jobService) {
        this.jobService = jobService;
    }

    /** Enqueues server-side scoring for a run. Returns 201 with the created job. */
    @PostMapping
    public ResponseEntity<EvalJobView> enqueue(
            @PathVariable UUID runId, @Valid @RequestBody EnqueueJudgeRequest request) {
        EvalJobView view = jobService.enqueue(runId, request);
        return ResponseEntity.created(URI.create("/api/v1/runs/" + runId + "/judge-jobs/" + view.id()))
                .body(view);
    }

    /** Lists the judge jobs registered for a run, oldest first. */
    @GetMapping
    public List<EvalJobView> list(@PathVariable UUID runId) {
        return jobService.getJobsForRun(runId);
    }
}
