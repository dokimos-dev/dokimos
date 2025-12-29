package dev.dokimos.server.controller.v1;

import dev.dokimos.server.dto.v1.AddItemsRequest;
import dev.dokimos.server.dto.v1.RunDetails;
import dev.dokimos.server.dto.v1.UpdateRunRequest;
import dev.dokimos.server.service.RunService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/runs")
public class RunController {

    private final RunService runService;

    public RunController(RunService runService) {
        this.runService = runService;
    }

    @GetMapping("/{runId}")
    public RunDetails getRunDetails(@PathVariable UUID runId,
                                    @PageableDefault(size = 50) Pageable pageable) {
        return runService.getRunDetails(runId, pageable);
    }

    @PostMapping("/{runId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> addItems(@PathVariable UUID runId,
                                         @Valid @RequestBody AddItemsRequest request) {
        runService.addItems(runId, request);
        return Map.of("status", "ok");
    }

    @PatchMapping("/{runId}")
    public Map<String, String> updateRun(@PathVariable UUID runId,
                                          @Valid @RequestBody UpdateRunRequest request) {
        runService.updateRun(runId, request);
        return Map.of("status", "updated");
    }
}
