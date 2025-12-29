package dev.dokimos.server.controller.v1;

import dev.dokimos.server.dto.v1.CreateRunRequest;
import dev.dokimos.server.dto.v1.CreateRunResponse;
import dev.dokimos.server.dto.v1.ExperimentSummary;
import dev.dokimos.server.dto.v1.ProjectSummary;
import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.ExperimentRun;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.service.ExperimentService;
import dev.dokimos.server.service.ProjectService;
import dev.dokimos.server.service.RunService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ExperimentService experimentService;
    private final RunService runService;

    public ProjectController(ProjectService projectService,
            ExperimentService experimentService,
            RunService runService) {
        this.projectService = projectService;
        this.experimentService = experimentService;
        this.runService = runService;
    }

    @GetMapping
    public List<ProjectSummary> listProjects() {
        return projectService.listProjects();
    }

    @GetMapping("/{projectName}/experiments")
    public List<ExperimentSummary> listExperiments(@PathVariable String projectName) {
        Project project = projectService.getProject(projectName);
        return experimentService.listExperiments(project);
    }

    @PostMapping("/{projectName}/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateRunResponse createRun(@PathVariable String projectName,
            @Valid @RequestBody CreateRunRequest request) {
        Project project = projectService.getOrCreateProject(projectName);
        Experiment experiment = experimentService.getOrCreateExperiment(project, request.experimentName());
        ExperimentRun run = runService.createRun(experiment, request.metadata());
        return new CreateRunResponse(run.getId());
    }
}
