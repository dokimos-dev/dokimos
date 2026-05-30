package dev.dokimos.server.controller.v1;

import dev.dokimos.server.dto.v1.RunSummary;
import dev.dokimos.server.dto.v1.TrendData;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.service.ExperimentService;
import dev.dokimos.server.service.RunService;
import dev.dokimos.server.tenant.TenantScope;
import dev.dokimos.server.tenant.TenantScopeResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
    public List<RunSummary> listRuns(@PathVariable UUID experimentId, HttpServletRequest http) {
        TenantScope scope = TenantScopeResolver.scope(http);
        Experiment experiment = experimentService.getExperiment(experimentId, scope);
        return runService.listRuns(experiment, scope);
    }

    @GetMapping("/{experimentId}/trends")
    public TrendData getTrends(
            @PathVariable UUID experimentId, @RequestParam(defaultValue = "20") int limit, HttpServletRequest http) {
        return experimentService.getTrends(experimentId, limit, TenantScopeResolver.scope(http));
    }

    @DeleteMapping("/{experimentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExperiment(@PathVariable UUID experimentId, HttpServletRequest http) {
        experimentService.deleteExperiment(experimentId, TenantScopeResolver.scope(http));
    }
}
