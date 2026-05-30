package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.CreateTraceEvalRuleRequest;
import dev.dokimos.server.dto.v1.TraceEvalRuleView;
import dev.dokimos.server.entity.LlmConnection;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.entity.TraceEvalRule;
import dev.dokimos.server.entity.TraceMatchType;
import dev.dokimos.server.repository.LlmConnectionRepository;
import dev.dokimos.server.repository.ProjectRepository;
import dev.dokimos.server.repository.TraceEvalRuleRepository;
import dev.dokimos.server.tenant.TenantScope;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages per-project trace eval rules. A rule names what to match (span name or attribute key/value)
 * and the judge configuration plus connection used to score matching spans. Rule names are unique within
 * a project. Every load is tenant-scoped so a cross-tenant id surfaces as a 404, and a new rule is
 * stamped with its owning project's tenant.
 */
@Service
public class TraceEvalRuleService {

    private final TraceEvalRuleRepository ruleRepository;
    private final ProjectRepository projectRepository;
    private final LlmConnectionRepository connectionRepository;

    public TraceEvalRuleService(
            TraceEvalRuleRepository ruleRepository,
            ProjectRepository projectRepository,
            LlmConnectionRepository connectionRepository) {
        this.ruleRepository = ruleRepository;
        this.connectionRepository = connectionRepository;
        this.projectRepository = projectRepository;
    }

    /**
     * Creates a rule for a project, stamped with the project's tenant.
     *
     * @param projectId the owning project
     * @param request the rule definition
     * @param scope the tenant scope of the caller
     * @return the public view of the created rule
     * @throws IllegalArgumentException if the project or connection does not exist under the scope
     *     (mapped to 404)
     * @throws IllegalStateException if a rule with the same name already exists in the project (mapped to
     *     409)
     */
    @Transactional
    public TraceEvalRuleView create(UUID projectId, CreateTraceEvalRuleRequest request, TenantScope scope) {
        Project project = requireProject(projectId, scope);
        if (ruleRepository.existsByProjectIdAndName(projectId, request.name(), scope)) {
            throw new IllegalStateException("A trace eval rule named '" + request.name() + "' already exists");
        }
        LlmConnection connection = loadConnection(request.connectionId(), scope);

        TraceEvalRule rule = new TraceEvalRule(
                projectId,
                request.name(),
                request.matchType(),
                request.matchValue(),
                connection,
                request.evaluatorName(),
                request.criteria());
        rule.setTenantId(project.getTenantId());
        applyEditable(rule, request);
        return TraceEvalRuleView.from(ruleRepository.save(rule));
    }

    /**
     * Lists the rules of a project, oldest first, within the scope.
     *
     * @throws IllegalArgumentException if the project does not exist under the scope (mapped to 404)
     */
    @Transactional(readOnly = true)
    public List<TraceEvalRuleView> list(UUID projectId, TenantScope scope) {
        requireProject(projectId, scope);
        return ruleRepository.findByProjectId(projectId, scope).stream()
                .map(TraceEvalRuleView::from)
                .toList();
    }

    /**
     * Replaces a rule's definition.
     *
     * @throws IllegalArgumentException if the rule, project, or connection does not exist under the scope
     *     (mapped to 404)
     * @throws IllegalStateException if the new name collides with another rule in the project (mapped to
     *     409)
     */
    @Transactional
    public TraceEvalRuleView update(
            UUID projectId, UUID ruleId, CreateTraceEvalRuleRequest request, TenantScope scope) {
        requireProject(projectId, scope);
        TraceEvalRule rule = loadRule(projectId, ruleId, scope);
        if (!rule.getName().equals(request.name())
                && ruleRepository.existsByProjectIdAndName(projectId, request.name(), scope)) {
            throw new IllegalStateException("A trace eval rule named '" + request.name() + "' already exists");
        }
        rule.setName(request.name());
        rule.setMatchType(request.matchType());
        rule.setMatchValue(request.matchValue());
        rule.setConnection(loadConnection(request.connectionId(), scope));
        rule.setEvaluatorName(request.evaluatorName());
        rule.setCriteria(request.criteria());
        applyEditable(rule, request);
        rule.touchUpdatedAt();
        return TraceEvalRuleView.from(ruleRepository.save(rule));
    }

    /**
     * Deletes a rule. Its enqueued jobs cascade away via the foreign key.
     *
     * @throws IllegalArgumentException if the rule or project does not exist under the scope (mapped to
     *     404)
     */
    @Transactional
    public void delete(UUID projectId, UUID ruleId, TenantScope scope) {
        requireProject(projectId, scope);
        TraceEvalRule rule = loadRule(projectId, ruleId, scope);
        ruleRepository.delete(rule);
    }

    private void applyEditable(TraceEvalRule rule, CreateTraceEvalRuleRequest request) {
        rule.setEnabled(request.enabledOrDefault());
        rule.setMatchKey(request.matchType() == TraceMatchType.ATTRIBUTE ? request.matchKey() : null);
        rule.setMinScore(request.minScore());
        rule.setMaxScore(request.maxScore());
        rule.setThreshold(request.threshold());
    }

    private Project requireProject(UUID projectId, TenantScope scope) {
        return projectRepository
                .findById(projectId, scope)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
    }

    private TraceEvalRule loadRule(UUID projectId, UUID ruleId, TenantScope scope) {
        TraceEvalRule rule = ruleRepository
                .findById(ruleId, scope)
                .orElseThrow(() -> new IllegalArgumentException("Trace eval rule not found: " + ruleId));
        if (!rule.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("Trace eval rule not found: " + ruleId);
        }
        return rule;
    }

    private LlmConnection loadConnection(UUID connectionId, TenantScope scope) {
        return connectionRepository
                .findById(connectionId, scope)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));
    }
}
