package dev.dokimos.server.controller.v1;

import dev.dokimos.server.dto.v1.ReviewQueueItem;
import dev.dokimos.server.service.ReviewQueueService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/review-queue")
public class ReviewQueueController {

    private final ReviewQueueService reviewQueueService;

    public ReviewQueueController(ReviewQueueService reviewQueueService) {
        this.reviewQueueService = reviewQueueService;
    }

    /**
     * Lists run items still awaiting a human verdict, oldest first. The optional filters narrow the
     * queue to a project, experiment, or run; omitting all three returns the global queue.
     */
    @GetMapping
    public Page<ReviewQueueItem> list(
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) UUID experimentId,
            @RequestParam(required = false) UUID runId,
            @PageableDefault(size = 50) Pageable pageable) {
        return reviewQueueService.list(projectName, experimentId, runId, pageable);
    }
}
