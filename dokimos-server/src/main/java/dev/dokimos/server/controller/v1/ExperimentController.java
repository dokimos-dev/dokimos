package dev.dokimos.server.controller.v1;

import dev.dokimos.server.dto.v1.RunSummary;
import dev.dokimos.server.dto.v1.TrendData;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.service.ExperimentService;
import dev.dokimos.server.service.RunService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/experiments")
public class ExperimentController {

    private final ExperimentService experimentService;
    private final RunService runService;

    public ExperimentController(ExperimentService experimentService, RunService runService) {
        this.experimentService = experimentService;
        this.runService = runService;
    }

    @GetMapping("/{experimentId}/runs")
    public List<RunSummary> listRuns(@PathVariable UUID experimentId) {
        Experiment experiment = experimentService.getExperiment(experimentId);
        return runService.listRuns(experiment);
    }

    @GetMapping("/{experimentId}/trends")
    public TrendData getTrends(@PathVariable UUID experimentId,
                               @RequestParam(defaultValue = "20") int limit) {
        return experimentService.getTrends(experimentId, limit);
    }
}
