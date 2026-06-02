import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import axios from "axios";
import {
  useListTraceEvalRules,
  useCreateTraceEvalRule,
  useUpdateTraceEvalRule,
  useDeleteTraceEvalRule,
} from "@/lib/api/trace-eval-rule-controller/trace-eval-rule-controller";
import { useListProjects } from "@/lib/api/project-controller/project-controller";
import { useList1 as useListConnections } from "@/lib/api/llm-connection-controller/llm-connection-controller";
import type {
  TraceEvalRuleView,
  CreateTraceEvalRuleRequest,
  CreateTraceEvalRuleRequestMatchType,
  LlmConnectionView,
} from "@/lib/api/generated.schemas";
import { CreateTraceEvalRuleRequestMatchType as MatchType } from "@/lib/api/generated.schemas";
import { useBreadcrumbs } from "@/lib/breadcrumb-context";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";

function errorMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as { message?: string } | undefined;
    if (data?.message) {
      return data.message;
    }
    return err.message;
  }
  if (err instanceof Error) {
    return err.message;
  }
  return fallback;
}

export default function TraceEvalRulesPage() {
  const { setBreadcrumbs } = useBreadcrumbs();
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<TraceEvalRuleView | null>(null);

  useEffect(() => {
    setBreadcrumbs([
      { label: "Home", href: "/" },
      { label: "Trace eval rules", href: "/trace-eval-rules" },
    ]);
  }, [setBreadcrumbs]);

  const { data: projectsResponse } = useListProjects();
  const projects = useMemo(
    () => projectsResponse?.data ?? [],
    [projectsResponse]
  );

  const projectId =
    selectedProjectId || (projects.length > 0 ? projects[0].id ?? "" : "");
  const setProjectId = setSelectedProjectId;

  const { data: connectionsResponse } = useListConnections();
  const connections = connectionsResponse?.data ?? [];

  const {
    data: rulesResponse,
    error,
    isLoading,
    mutate,
  } = useListTraceEvalRules(projectId, { swr: { enabled: !!projectId } });
  const rules = rulesResponse?.data ?? [];

  const handleSaved = async () => {
    await mutate();
  };

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between mb-6 gap-4">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">
            Trace eval rules
          </h1>
          <p className="text-sm text-muted-foreground font-prose mt-1 max-w-prose">
            Rules that trigger online LLM evaluations on matching spans as
            traces are ingested for a project.
          </p>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <select
            className="h-9 rounded-md border border-border bg-background px-3 text-sm font-mono"
            value={projectId}
            onChange={(e) => setProjectId(e.target.value)}
          >
            {projects.length === 0 && <option value="">No projects</option>}
            {projects.map((project) => (
              <option key={project.id} value={project.id}>
                {project.name}
              </option>
            ))}
          </select>
          {projectId && rules.length > 0 && (
            <Button onClick={() => setCreateOpen(true)}>New rule</Button>
          )}
        </div>
      </div>

      {!projectId ? (
        <p className="text-sm text-muted-foreground">
          Select a project to manage its trace eval rules.
        </p>
      ) : isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {[1, 2, 3].map((i) => (
            <div
              key={i}
              className="rounded-lg border border-border bg-card p-4 space-y-3"
            >
              <Skeleton className="h-4 w-2/5" />
              <Skeleton className="h-3 w-3/4" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-3 w-1/3" />
            </div>
          ))}
        </div>
      ) : error ? (
        <div className="rounded-lg border border-border bg-card p-10 text-center">
          <p className="text-sm text-destructive">
            Error loading rules: {error.message}
          </p>
        </div>
      ) : rules.length === 0 ? (
        <EmptyState onCreate={() => setCreateOpen(true)} />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {rules.map((rule) => (
            <RuleRow
              key={rule.id}
              projectId={projectId}
              rule={rule}
              connections={connections}
              onEdit={setEditing}
              onChanged={handleSaved}
            />
          ))}
        </div>
      )}

      {projectId && (
        <RuleDialog
          key={`create-${projectId}`}
          mode="create"
          projectId={projectId}
          open={createOpen}
          connections={connections}
          onClose={() => setCreateOpen(false)}
          onSaved={handleSaved}
        />
      )}
      {editing && projectId && (
        <RuleDialog
          key={editing.id ?? "edit"}
          mode="edit"
          projectId={projectId}
          rule={editing}
          connections={connections}
          open
          onClose={() => setEditing(null)}
          onSaved={handleSaved}
        />
      )}
    </div>
  );
}

function EmptyState({ onCreate }: { onCreate: () => void }) {
  return (
    <div className="rounded-lg border border-border bg-card p-10 flex flex-col items-center text-center">
      <div className="mb-4 flex h-11 w-11 items-center justify-center rounded-md border border-border bg-accent text-muted-foreground">
        <svg
          viewBox="0 0 24 24"
          width="20"
          height="20"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.5"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="m9 11 3 3L22 4" />
          <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
        </svg>
      </div>
      <h2 className="text-base font-semibold mb-1">No trace eval rules yet</h2>
      <p className="text-muted-foreground text-sm font-prose mb-6 max-w-sm">
        Add a rule to evaluate matching spans with an LLM judge as traces
        arrive.
      </p>
      <Button onClick={onCreate}>New rule</Button>
    </div>
  );
}

interface RuleRowProps {
  projectId: string;
  rule: TraceEvalRuleView;
  connections: LlmConnectionView[];
  onEdit: (rule: TraceEvalRuleView) => void;
  onChanged: () => void;
}

function RuleRow({
  projectId,
  rule,
  connections,
  onEdit,
  onChanged,
}: RuleRowProps) {
  const [confirming, setConfirming] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState(true);

  const { trigger: deleteTrigger, isMutating: isDeleting } =
    useDeleteTraceEvalRule(projectId, rule.id ?? "");

  const connectionName =
    connections.find((c) => c.id === rule.connectionId)?.name ??
    rule.connectionId ??
    "—";

  const isAttribute = rule.matchType === MatchType.ATTRIBUTE;

  const handleDelete = async () => {
    setDeleteError(null);
    try {
      await deleteTrigger();
      setConfirming(false);
      onChanged();
    } catch (err) {
      setDeleteError(errorMessage(err, "Failed to delete rule."));
    }
  };

  return (
    <div className="rounded-lg border border-border bg-card flex flex-col">
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        className="flex items-center gap-2 px-4 py-3 text-left"
      >
        <svg
          viewBox="0 0 24 24"
          width="13"
          height="13"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
          className={`shrink-0 text-muted-foreground transition-transform ${
            expanded ? "rotate-90" : ""
          }`}
        >
          <path d="m9 18 6-6-6-6" />
        </svg>
        <span className="text-sm font-semibold font-mono truncate flex-1">
          {rule.name}
        </span>
        {rule.enabled === false ? (
          <Badge className="bg-fail-tint text-destructive border-transparent text-[10px] uppercase tracking-wider">
            disabled
          </Badge>
        ) : (
          <Badge className="bg-pass-tint text-success border-transparent text-[10px] uppercase tracking-wider">
            enabled
          </Badge>
        )}
      </button>

      {expanded && (
        <div className="flex flex-col gap-4 px-4 pb-4 border-t border-border pt-4">
          <div className="text-[13px] text-muted-foreground font-prose leading-relaxed">
            <span className="font-semibold text-foreground">
              {rule.evaluatorName}
            </span>{" "}
            {isAttribute ? (
              <>
                on attribute{" "}
                <span className="font-mono text-primary">
                  {rule.matchKey ?? "?"} = {rule.matchValue ?? "?"}
                </span>
              </>
            ) : (
              <>
                on span name ={" "}
                <span className="font-mono text-primary">
                  {rule.matchValue ?? "?"}
                </span>
              </>
            )}
          </div>

          <div className="flex flex-wrap gap-x-4 gap-y-1.5 text-[11.5px] text-muted-foreground">
            <span>
              Connection{" "}
              <span className="font-mono text-text-2 tabular-nums">
                {connectionName}
              </span>
            </span>
            <span>
              Threshold{" "}
              <span className="font-mono text-text-2 tabular-nums">
                {rule.threshold ?? "—"}
              </span>
            </span>
            <span>
              Score{" "}
              <span className="font-mono text-text-2 tabular-nums">
                {rule.minScore ?? 0} → {rule.maxScore ?? 1}
              </span>
            </span>
          </div>

          {rule.criteria && (
            <div className="rounded-md border border-border bg-muted px-3 py-2 text-[12.5px] text-muted-foreground font-prose leading-relaxed">
              {rule.criteria}
            </div>
          )}

          {deleteError && (
            <p className="text-sm text-destructive">{deleteError}</p>
          )}

          <div className="flex items-center gap-2 pt-0.5">
            {confirming ? (
              <>
                <span className="text-xs text-muted-foreground font-prose">
                  Delete this rule?
                </span>
                <span className="flex-1" />
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    setConfirming(false);
                    setDeleteError(null);
                  }}
                  disabled={isDeleting}
                >
                  Cancel
                </Button>
                <Button
                  variant="destructive"
                  size="sm"
                  onClick={handleDelete}
                  disabled={isDeleting}
                >
                  {isDeleting ? "Deleting..." : "Confirm delete"}
                </Button>
              </>
            ) : (
              <>
                <span className="inline-flex items-center rounded-full border border-border bg-accent px-2 py-0.5 text-[10px] uppercase tracking-wider text-muted-foreground">
                  match: {isAttribute ? "attribute" : "span name"}
                </span>
                <span className="flex-1" />
                <Button variant="ghost" size="sm" onClick={() => onEdit(rule)}>
                  Edit
                </Button>
                <Button
                  variant="destructive"
                  size="sm"
                  onClick={() => {
                    setConfirming(true);
                    setDeleteError(null);
                  }}
                >
                  Delete
                </Button>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

interface RuleDialogProps {
  mode: "create" | "edit";
  projectId: string;
  open: boolean;
  rule?: TraceEvalRuleView | null;
  connections: LlmConnectionView[];
  onClose: () => void;
  onSaved: () => void;
}

function RuleDialog({
  mode,
  projectId,
  open,
  rule,
  connections,
  onClose,
  onSaved,
}: RuleDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [name, setName] = useState(rule?.name ?? "");
  const [enabled, setEnabled] = useState(rule?.enabled ?? true);
  const [matchType, setMatchType] = useState<CreateTraceEvalRuleRequestMatchType>(
    (rule?.matchType as CreateTraceEvalRuleRequestMatchType) ??
      MatchType.SPAN_NAME
  );
  const [matchKey, setMatchKey] = useState(rule?.matchKey ?? "");
  const [matchValue, setMatchValue] = useState(rule?.matchValue ?? "");
  const [connectionId, setConnectionId] = useState(
    rule?.connectionId ?? connections[0]?.id ?? ""
  );
  const [evaluatorName, setEvaluatorName] = useState(rule?.evaluatorName ?? "");
  const [criteria, setCriteria] = useState(rule?.criteria ?? "");
  const [minScore, setMinScore] = useState(String(rule?.minScore ?? 0));
  const [maxScore, setMaxScore] = useState(String(rule?.maxScore ?? 1));
  const [threshold, setThreshold] = useState(String(rule?.threshold ?? 0.5));
  const [submitError, setSubmitError] = useState<string | null>(null);

  const { trigger: createTrigger, isMutating: isCreating } =
    useCreateTraceEvalRule(projectId);
  const { trigger: updateTrigger, isMutating: isUpdating } =
    useUpdateTraceEvalRule(projectId, rule?.id ?? "");
  const isMutating = isCreating || isUpdating;

  useEffect(() => {
    const dlg = dialogRef.current;
    if (!dlg) return;
    if (open && !dlg.open) {
      dlg.showModal();
    } else if (!open && dlg.open) {
      dlg.close();
    }
  }, [open]);

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!name.trim()) {
      setSubmitError("Name is required.");
      return;
    }
    if (matchType === MatchType.ATTRIBUTE && !matchKey.trim()) {
      setSubmitError("Attribute key is required for attribute matches.");
      return;
    }
    if (!matchValue.trim()) {
      setSubmitError("Match value is required.");
      return;
    }
    if (!connectionId) {
      setSubmitError("An LLM connection is required.");
      return;
    }
    if (!evaluatorName.trim()) {
      setSubmitError("Evaluator name is required.");
      return;
    }
    if (!criteria.trim()) {
      setSubmitError("Criteria is required.");
      return;
    }
    const minScoreNum = Number(minScore);
    const maxScoreNum = Number(maxScore);
    const thresholdNum = Number(threshold);
    if (
      Number.isNaN(minScoreNum) ||
      Number.isNaN(maxScoreNum) ||
      Number.isNaN(thresholdNum)
    ) {
      setSubmitError("Score range and threshold must be numbers.");
      return;
    }
    setSubmitError(null);

    const payload: CreateTraceEvalRuleRequest = {
      name: name.trim(),
      enabled,
      matchType,
      matchKey:
        matchType === MatchType.ATTRIBUTE ? matchKey.trim() : undefined,
      matchValue: matchValue.trim(),
      connectionId,
      evaluatorName: evaluatorName.trim(),
      criteria: criteria.trim(),
      minScore: minScoreNum,
      maxScore: maxScoreNum,
      threshold: thresholdNum,
    };

    try {
      if (mode === "edit") {
        await updateTrigger(payload);
      } else {
        await createTrigger(payload);
      }
      onSaved();
      onClose();
    } catch (err) {
      setSubmitError(
        errorMessage(
          err,
          mode === "edit" ? "Failed to update rule." : "Failed to create rule."
        )
      );
    }
  };

  const inputClass =
    "w-full border border-border rounded-md px-3 py-2 text-sm bg-background text-foreground font-mono focus:outline-none focus:ring-2 focus:ring-ring/40";
  const labelClass =
    "block text-[11px] font-semibold text-muted-foreground uppercase tracking-wider mb-1.5";

  return (
    <dialog
      ref={dialogRef}
      onClose={onClose}
      className="border border-border rounded-md p-0 bg-popover text-popover-foreground max-w-lg w-[calc(100%-2rem)] shadow-xl backdrop:bg-black/50"
    >
      <form onSubmit={handleSubmit}>
        <div className="flex items-center justify-between border-b border-border px-5 py-3">
          <h3 className="text-[13px] font-semibold uppercase tracking-wider">
            {mode === "edit" ? "Edit rule" : "New rule"}
          </h3>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="text-muted-foreground hover:text-foreground"
          >
            <svg
              viewBox="0 0 24 24"
              width="16"
              height="16"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M18 6 6 18" />
              <path d="m6 6 12 12" />
            </svg>
          </button>
        </div>

        <div className="px-5 py-4 space-y-3 max-h-[70vh] overflow-y-auto">
          <div>
            <label htmlFor="rule-name" className={labelClass}>
              Name
            </label>
            <input
              id="rule-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="answer-relevance"
              className={inputClass}
              autoFocus
              required
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label htmlFor="rule-match-type" className={labelClass}>
                Match type
              </label>
              <select
                id="rule-match-type"
                value={matchType}
                onChange={(e) =>
                  setMatchType(
                    e.target.value as CreateTraceEvalRuleRequestMatchType
                  )
                }
                className={inputClass}
              >
                <option value={MatchType.SPAN_NAME}>Span name</option>
                <option value={MatchType.ATTRIBUTE}>Attribute</option>
              </select>
            </div>
            {matchType === MatchType.ATTRIBUTE && (
              <div>
                <label htmlFor="rule-match-key" className={labelClass}>
                  Attribute key
                </label>
                <input
                  id="rule-match-key"
                  type="text"
                  value={matchKey}
                  onChange={(e) => setMatchKey(e.target.value)}
                  placeholder="gen_ai.operation.name"
                  className={inputClass}
                />
              </div>
            )}
          </div>

          <div>
            <label htmlFor="rule-match-value" className={labelClass}>
              Match value
            </label>
            <input
              id="rule-match-value"
              type="text"
              value={matchValue}
              onChange={(e) => setMatchValue(e.target.value)}
              placeholder={
                matchType === MatchType.ATTRIBUTE ? "chat" : "llm.completion"
              }
              className={inputClass}
              required
            />
          </div>

          <div>
            <label htmlFor="rule-connection" className={labelClass}>
              LLM connection
            </label>
            <select
              id="rule-connection"
              value={connectionId}
              onChange={(e) => setConnectionId(e.target.value)}
              className={inputClass}
              required
            >
              {connections.length === 0 && (
                <option value="">No connections available</option>
              )}
              {connections.map((connection) => (
                <option key={connection.id} value={connection.id}>
                  {connection.name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="rule-evaluator" className={labelClass}>
              Evaluator name
            </label>
            <input
              id="rule-evaluator"
              type="text"
              value={evaluatorName}
              onChange={(e) => setEvaluatorName(e.target.value)}
              placeholder="g-eval"
              className={inputClass}
              required
            />
          </div>

          <div>
            <label htmlFor="rule-criteria" className={labelClass}>
              Criteria
            </label>
            <textarea
              id="rule-criteria"
              value={criteria}
              onChange={(e) => setCriteria(e.target.value)}
              placeholder="The output should directly answer the user question."
              className={inputClass + " min-h-20 resize-y font-prose"}
              required
            />
          </div>

          <div className="grid grid-cols-3 gap-3">
            <div>
              <label htmlFor="rule-min-score" className={labelClass}>
                Min score
              </label>
              <input
                id="rule-min-score"
                type="number"
                step="any"
                value={minScore}
                onChange={(e) => setMinScore(e.target.value)}
                className={inputClass + " tabular-nums"}
              />
            </div>
            <div>
              <label htmlFor="rule-max-score" className={labelClass}>
                Max score
              </label>
              <input
                id="rule-max-score"
                type="number"
                step="any"
                value={maxScore}
                onChange={(e) => setMaxScore(e.target.value)}
                className={inputClass + " tabular-nums"}
              />
            </div>
            <div>
              <label htmlFor="rule-threshold" className={labelClass}>
                Threshold
              </label>
              <input
                id="rule-threshold"
                type="number"
                step="any"
                value={threshold}
                onChange={(e) => setThreshold(e.target.value)}
                className={inputClass + " tabular-nums"}
              />
            </div>
          </div>

          <label className="flex items-center gap-2 text-sm pt-1">
            <input
              type="checkbox"
              checked={enabled}
              onChange={(e) => setEnabled(e.target.checked)}
              className="h-4 w-4 accent-primary"
            />
            Enabled
          </label>

          {submitError && (
            <p className="text-sm text-destructive">{submitError}</p>
          )}
        </div>

        <div className="flex justify-end gap-2 border-t border-border px-5 py-3">
          <Button
            type="button"
            variant="outline"
            onClick={onClose}
            disabled={isMutating}
          >
            Cancel
          </Button>
          <Button type="submit" disabled={isMutating}>
            {isMutating
              ? "Saving..."
              : mode === "edit"
                ? "Save changes"
                : "Create"}
          </Button>
        </div>
      </form>
    </dialog>
  );
}
