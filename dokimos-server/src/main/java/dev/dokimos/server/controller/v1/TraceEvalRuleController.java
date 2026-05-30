package dev.dokimos.server.controller.v1;

import dev.dokimos.server.dto.v1.CreateTraceEvalRuleRequest;
import dev.dokimos.server.dto.v1.TraceEvalRuleView;
import dev.dokimos.server.service.TraceEvalRuleService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Manages per-project trace eval rules. Writes pass through the API key auth filter; reads are open. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/trace-eval-rules")
public class TraceEvalRuleController {

    private final TraceEvalRuleService ruleService;

    public TraceEvalRuleController(TraceEvalRuleService ruleService) {
        this.ruleService = ruleService;
    }

    /** Creates a rule. Returns 201 with a {@code Location} header pointing at the rule. */
    @PostMapping
    public ResponseEntity<TraceEvalRuleView> createTraceEvalRule(
            @PathVariable UUID projectId, @Valid @RequestBody CreateTraceEvalRuleRequest request) {
        TraceEvalRuleView view = ruleService.create(projectId, request);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/trace-eval-rules/" + view.id()))
                .body(view);
    }

    /** Lists the rules of a project, oldest first. */
    @GetMapping
    public List<TraceEvalRuleView> listTraceEvalRules(@PathVariable UUID projectId) {
        return ruleService.list(projectId);
    }

    /** Replaces a rule. Returns 404 if it does not exist, 409 if the new name is taken in the project. */
    @PutMapping("/{ruleId}")
    public TraceEvalRuleView updateTraceEvalRule(
            @PathVariable UUID projectId,
            @PathVariable UUID ruleId,
            @Valid @RequestBody CreateTraceEvalRuleRequest request) {
        return ruleService.update(projectId, ruleId, request);
    }

    /** Deletes a rule. Returns 404 if it does not exist. */
    @DeleteMapping("/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTraceEvalRule(@PathVariable UUID projectId, @PathVariable UUID ruleId) {
        ruleService.delete(projectId, ruleId);
    }
}
