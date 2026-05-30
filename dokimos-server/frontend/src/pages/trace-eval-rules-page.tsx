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
      <div className="flex items-center justify-between mb-2 gap-4">
        <h1 className="text-2xl font-bold">Trace eval rules</h1>
        <div className="flex items-center gap-2">
          <select
            className="h-9 rounded-md border bg-background px-3 text-sm"
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
      <p className="text-muted-foreground mb-6">
        Rules that trigger online LLM evaluations on matching spans as traces
        are ingested for a project.
      </p>

      {!projectId ? (
        <p className="text-muted-foreground">
          Select a project to manage its trace eval rules.
        </p>
      ) : isLoading ? (
        <div className="rounded-xl border bg-card overflow-hidden">
          {[1, 2, 3].map((i) => (
            <div key={i} className="px-4 py-4 border-b last:border-b-0">
              <Skeleton className="h-4 w-48 mb-2" />
              <Skeleton className="h-3 w-72" />
            </div>
          ))}
        </div>
      ) : error ? (
        <p className="text-destructive">Error loading rules: {error.message}</p>
      ) : rules.length === 0 ? (
        <EmptyState onCreate={() => setCreateOpen(true)} />
      ) : (
        <div className="rounded-xl border bg-card overflow-hidden">
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
    <div className="rounded-xl border bg-card p-10 text-center">
      <h2 className="text-lg font-semibold mb-2">No trace eval rules yet</h2>
      <p className="text-muted-foreground text-sm mb-6">
        Add a rule to evaluate matching spans with an LLM judge as traces arrive.
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

  const { trigger: deleteTrigger, isMutating: isDeleting } =
    useDeleteTraceEvalRule(projectId, rule.id ?? "");

  const connectionName =
    connections.find((c) => c.id === rule.connectionId)?.name ??
    rule.connectionId ??
    "—";

  const matchLabel =
    rule.matchType === MatchType.ATTRIBUTE
      ? `attribute ${rule.matchKey ?? "?"} = ${rule.matchValue ?? "?"}`
      : `span name = ${rule.matchValue ?? "?"}`;

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
    <div className="px-4 py-4 border-b last:border-b-0">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <p className="text-sm font-semibold truncate">{rule.name}</p>
            {rule.enabled === false && (
              <span className="inline-flex items-center rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground">
                disabled
              </span>
            )}
          </div>
          <p className="text-sm text-muted-foreground truncate">
            {rule.evaluatorName} on {matchLabel}
          </p>
          <p className="text-xs text-muted-foreground truncate mt-1">
            Connection: {connectionName} · Threshold: {rule.threshold ?? "—"} ·
            Score {rule.minScore ?? 0} to {rule.maxScore ?? 1}
          </p>
          {rule.criteria && (
            <p className="text-xs text-muted-foreground truncate mt-1">
              {rule.criteria}
            </p>
          )}
        </div>
        <div className="flex items-center gap-2 shrink-0">
          {confirming ? (
            <>
              <span className="text-xs text-muted-foreground">Delete?</span>
              <Button
                variant="destructive"
                size="sm"
                onClick={handleDelete}
                disabled={isDeleting}
              >
                {isDeleting ? "Deleting..." : "Confirm"}
              </Button>
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
            </>
          ) : (
            <>
              <Button variant="outline" size="sm" onClick={() => onEdit(rule)}>
                Edit
              </Button>
              <Button
                variant="outline"
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
      {deleteError && (
        <p className="text-sm text-destructive mt-2">{deleteError}</p>
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
    "w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground";
  const labelClass =
    "block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1";

  return (
    <dialog
      ref={dialogRef}
      onClose={onClose}
      className="border rounded-xl p-0 bg-card text-foreground max-w-lg w-[calc(100%-2rem)] backdrop:bg-black/50"
    >
      <form onSubmit={handleSubmit} className="p-5">
        <h3 className="text-base font-bold mb-1">
          {mode === "edit" ? "Edit rule" : "New rule"}
        </h3>
        <p className="text-sm text-muted-foreground mb-4">
          Match spans and evaluate them with an LLM judge as traces arrive.
        </p>

        <div className="mb-3">
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

        <div className="mb-3 grid grid-cols-1 sm:grid-cols-2 gap-3">
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

        <div className="mb-3">
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

        <div className="mb-3">
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

        <div className="mb-3">
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

        <div className="mb-3">
          <label htmlFor="rule-criteria" className={labelClass}>
            Criteria
          </label>
          <textarea
            id="rule-criteria"
            value={criteria}
            onChange={(e) => setCriteria(e.target.value)}
            placeholder="The output should directly answer the user question."
            className={inputClass + " min-h-20 resize-y"}
            required
          />
        </div>

        <div className="mb-3 grid grid-cols-3 gap-3">
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
              className={inputClass}
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
              className={inputClass}
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
              className={inputClass}
            />
          </div>
        </div>

        <label className="flex items-center gap-2 text-sm mb-3">
          <input
            type="checkbox"
            checked={enabled}
            onChange={(e) => setEnabled(e.target.checked)}
            className="h-4 w-4"
          />
          Enabled
        </label>

        {submitError && (
          <p className="text-sm text-destructive mb-3">{submitError}</p>
        )}
        <div className="flex justify-end gap-2 mt-4">
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
