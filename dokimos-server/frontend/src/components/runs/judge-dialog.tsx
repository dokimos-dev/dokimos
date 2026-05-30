import {
  useEffect,
  useRef,
  useState,
  type FormEvent,
} from "react";
import axios from "axios";
import { useSWRConfig } from "swr";
import {
  useList1,
  useCreate,
} from "@/lib/api/llm-connection-controller/llm-connection-controller";
import {
  useEnqueue,
  getListKey,
} from "@/lib/api/eval-job-controller/eval-job-controller";
import type { LlmConnectionView } from "@/lib/api/generated.schemas";
import { Button } from "@/components/ui/button";

const EVALUATION_PARAMS = [
  "INPUT",
  "EXPECTED_OUTPUT",
  "ACTUAL_OUTPUT",
] as const;

function errorMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    const status = err.response?.status;
    if (status === 409) {
      return "A judge job already exists for this run and evaluator.";
    }
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

interface CreateConnectionFormProps {
  onCreated: (connection: LlmConnectionView) => void;
  onCancel?: () => void;
}

function CreateConnectionForm({
  onCreated,
  onCancel,
}: CreateConnectionFormProps) {
  const [name, setName] = useState("");
  const [baseUrl, setBaseUrl] = useState("https://api.openai.com/v1");
  const [model, setModel] = useState("gpt-4o-mini");
  const [apiKey, setApiKey] = useState("");
  const [error, setError] = useState<string | null>(null);

  const { trigger, isMutating } = useCreate();

  const handleCreate = async () => {
    if (!name.trim()) {
      setError("Name is required.");
      return;
    }
    setError(null);
    try {
      const res = await trigger({
        name: name.trim(),
        baseUrl: baseUrl.trim(),
        model: model.trim(),
        apiKey: apiKey.trim() || undefined,
      });
      if (res?.data) {
        onCreated(res.data);
      }
    } catch (err) {
      setError(errorMessage(err, "Failed to create connection."));
    }
  };

  return (
    <div className="rounded-md border bg-background p-3 space-y-3">
      <p className="text-sm font-medium">New LLM connection</p>
      <div>
        <label
          htmlFor="conn-name"
          className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1"
        >
          Name
        </label>
        <input
          id="conn-name"
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="openai-judge"
          className="w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground"
        />
      </div>
      <div>
        <label
          htmlFor="conn-base-url"
          className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1"
        >
          Base URL
        </label>
        <input
          id="conn-base-url"
          type="text"
          value={baseUrl}
          onChange={(e) => setBaseUrl(e.target.value)}
          className="w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground"
        />
      </div>
      <div>
        <label
          htmlFor="conn-model"
          className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1"
        >
          Model
        </label>
        <input
          id="conn-model"
          type="text"
          value={model}
          onChange={(e) => setModel(e.target.value)}
          className="w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground"
        />
      </div>
      <div>
        <label
          htmlFor="conn-api-key"
          className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1"
        >
          API key
        </label>
        <input
          id="conn-api-key"
          type="password"
          value={apiKey}
          onChange={(e) => setApiKey(e.target.value)}
          placeholder="sk-..."
          className="w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground"
        />
      </div>
      {error && <p className="text-sm text-destructive">{error}</p>}
      <div className="flex justify-end gap-2">
        {onCancel && (
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={onCancel}
            disabled={isMutating}
          >
            Cancel
          </Button>
        )}
        <Button
          type="button"
          size="sm"
          onClick={handleCreate}
          disabled={isMutating}
        >
          {isMutating ? "Saving..." : "Save connection"}
        </Button>
      </div>
    </div>
  );
}

interface JudgeDialogProps {
  open: boolean;
  onClose: () => void;
  runId: string;
  onEnqueued: () => void;
}

export default function JudgeDialog({
  open,
  onClose,
  runId,
  onEnqueued,
}: JudgeDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [connectionId, setConnectionId] = useState("");
  const [evaluatorName, setEvaluatorName] = useState("judge");
  const [criteria, setCriteria] = useState("");
  const [params, setParams] = useState<Set<string>>(
    () => new Set(["INPUT", "ACTUAL_OUTPUT"])
  );
  const [threshold, setThreshold] = useState("");
  const [minScore, setMinScore] = useState("0");
  const [maxScore, setMaxScore] = useState("1");
  const [forceCreateConnection, setForceCreateConnection] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const { data: connectionsResponse, mutate: mutateConnections } = useList1({
    swr: { enabled: open },
  });
  const connections = connectionsResponse?.data ?? [];

  const { trigger, isMutating } = useEnqueue(runId);
  const { mutate: globalMutate } = useSWRConfig();

  const showCreateConnection =
    forceCreateConnection || connections.length === 0;
  const selectedConnectionId = connectionId || connections[0]?.id || "";

  useEffect(() => {
    const dlg = dialogRef.current;
    if (!dlg) return;
    if (open && !dlg.open) {
      dlg.showModal();
    } else if (!open && dlg.open) {
      dlg.close();
    }
  }, [open]);

  const handleClose = () => {
    setConnectionId("");
    setEvaluatorName("judge");
    setCriteria("");
    setParams(new Set(["INPUT", "ACTUAL_OUTPUT"]));
    setThreshold("");
    setMinScore("0");
    setMaxScore("1");
    setForceCreateConnection(false);
    setSubmitError(null);
    onClose();
  };

  const toggleParam = (value: string) => {
    setParams((prev) => {
      const next = new Set(prev);
      if (next.has(value)) {
        next.delete(value);
      } else {
        next.add(value);
      }
      return next;
    });
  };

  const handleConnectionCreated = (connection: LlmConnectionView) => {
    mutateConnections();
    setConnectionId(connection.id ?? "");
    setForceCreateConnection(false);
  };

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!selectedConnectionId) {
      setSubmitError("Select an LLM connection.");
      return;
    }
    if (!evaluatorName.trim()) {
      setSubmitError("Evaluator name is required.");
      return;
    }
    if (params.size === 0) {
      setSubmitError("Select at least one evaluation parameter.");
      return;
    }

    const minScoreNum = Number(minScore);
    const maxScoreNum = Number(maxScore);
    if (Number.isNaN(minScoreNum) || Number.isNaN(maxScoreNum)) {
      setSubmitError("Min and max score must be numbers.");
      return;
    }

    let thresholdNum: number | undefined;
    if (threshold.trim() !== "") {
      thresholdNum = Number(threshold);
      if (Number.isNaN(thresholdNum)) {
        setSubmitError("Threshold must be a number.");
        return;
      }
    }

    setSubmitError(null);
    try {
      await trigger({
        connectionId: selectedConnectionId,
        evaluatorName: evaluatorName.trim(),
        criteria: criteria.trim() || undefined,
        evaluationParams: EVALUATION_PARAMS.filter((p) => params.has(p)),
        minScore: minScoreNum,
        maxScore: maxScoreNum,
        threshold: thresholdNum,
      });
      globalMutate(getListKey(runId));
      onEnqueued();
      handleClose();
    } catch (err) {
      setSubmitError(errorMessage(err, "Failed to enqueue judge job."));
    }
  };

  return (
    <dialog
      ref={dialogRef}
      onClose={handleClose}
      className="border rounded-xl p-0 bg-card text-foreground max-w-lg w-[calc(100%-2rem)] backdrop:bg-black/50"
    >
      <form onSubmit={handleSubmit} className="p-5">
        <h3 className="text-base font-bold mb-1">Run LLM judge</h3>
        <p className="text-sm text-muted-foreground mb-4">
          Score this run with an LLM judge. The job runs in the background and
          scores appear once it finishes.
        </p>

        <div className="mb-3">
          <label
            htmlFor="judge-connection"
            className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1"
          >
            LLM connection
          </label>
          {connections.length > 0 && !showCreateConnection ? (
            <div className="flex items-center gap-2">
              <select
                id="judge-connection"
                value={selectedConnectionId}
                onChange={(e) => setConnectionId(e.target.value)}
                className="w-full min-h-10 border rounded-md px-3 py-2 text-sm bg-background text-foreground"
              >
                {connections.map((c) => (
                  <option key={c.id} value={c.id ?? ""}>
                    {c.name} ({c.model})
                  </option>
                ))}
              </select>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setForceCreateConnection(true)}
              >
                New
              </Button>
            </div>
          ) : null}
        </div>

        {showCreateConnection && (
          <div className="mb-3">
            <CreateConnectionForm
              onCreated={handleConnectionCreated}
              onCancel={
                connections.length > 0
                  ? () => setForceCreateConnection(false)
                  : undefined
              }
            />
          </div>
        )}

        <div className="mb-3">
          <label
            htmlFor="judge-evaluator"
            className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1"
          >
            Evaluator name
          </label>
          <input
            id="judge-evaluator"
            type="text"
            value={evaluatorName}
            onChange={(e) => setEvaluatorName(e.target.value)}
            className="w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground"
            required
          />
        </div>

        <div className="mb-3">
          <label
            htmlFor="judge-criteria"
            className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1"
          >
            Criteria
          </label>
          <textarea
            id="judge-criteria"
            value={criteria}
            onChange={(e) => setCriteria(e.target.value)}
            placeholder="Describe how the judge should score each item."
            className="w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground min-h-24 resize-y"
          />
        </div>

        <div className="mb-3">
          <span className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1">
            Evaluation params
          </span>
          <div className="flex flex-wrap gap-4">
            {EVALUATION_PARAMS.map((param) => (
              <label
                key={param}
                className="flex items-center gap-2 text-sm cursor-pointer"
              >
                <input
                  type="checkbox"
                  checked={params.has(param)}
                  onChange={() => toggleParam(param)}
                  className="h-4 w-4"
                />
                {param}
              </label>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-3 gap-3 mb-3">
          <div>
            <label
              htmlFor="judge-min-score"
              className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1"
            >
              Min score
            </label>
            <input
              id="judge-min-score"
              type="number"
              step="any"
              value={minScore}
              onChange={(e) => setMinScore(e.target.value)}
              className="w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground"
            />
          </div>
          <div>
            <label
              htmlFor="judge-max-score"
              className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1"
            >
              Max score
            </label>
            <input
              id="judge-max-score"
              type="number"
              step="any"
              value={maxScore}
              onChange={(e) => setMaxScore(e.target.value)}
              className="w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground"
            />
          </div>
          <div>
            <label
              htmlFor="judge-threshold"
              className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1"
            >
              Threshold
            </label>
            <input
              id="judge-threshold"
              type="number"
              step="any"
              value={threshold}
              onChange={(e) => setThreshold(e.target.value)}
              placeholder="Optional"
              className="w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground"
            />
          </div>
        </div>

        {submitError && (
          <p className="text-sm text-destructive mb-3">{submitError}</p>
        )}

        <div className="flex justify-end gap-2 mt-4">
          <Button
            type="button"
            variant="outline"
            onClick={handleClose}
            disabled={isMutating}
          >
            Cancel
          </Button>
          <Button type="submit" disabled={isMutating || !selectedConnectionId}>
            {isMutating ? "Enqueuing..." : "Run judge"}
          </Button>
        </div>
      </form>
    </dialog>
  );
}
