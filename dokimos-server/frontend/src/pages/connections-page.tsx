import { useEffect, useRef, useState, type FormEvent } from "react";
import axios from "axios";
import {
  useList1,
  useCreate,
  useUpdate,
  useDelete1,
} from "@/lib/api/llm-connection-controller/llm-connection-controller";
import type {
  LlmConnectionView,
  CreateLlmConnectionRequest,
  UpdateLlmConnectionRequest,
} from "@/lib/api/generated.schemas";
import { CreateLlmConnectionRequestProtocol } from "@/lib/api/generated.schemas";
import { useBreadcrumbs } from "@/lib/breadcrumb-context";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
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

const DEFAULT_BASE_URL = "https://api.openai.com/v1";
const DEFAULT_MODEL = "gpt-4o-mini";

function protocolLabel(protocol: string | undefined): string {
  return protocol === "CHAT_COMPLETIONS" ? "Chat Completions" : "Responses";
}

export default function ConnectionsPage() {
  const { setBreadcrumbs } = useBreadcrumbs();
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<LlmConnectionView | null>(null);

  const { data: response, error, isLoading, mutate } = useList1();
  const connections = response?.data ?? [];

  useEffect(() => {
    setBreadcrumbs([
      { label: "Home", href: "/" },
      { label: "LLM connections", href: "/llm-connections" },
    ]);
  }, [setBreadcrumbs]);

  const handleSaved = async () => {
    await mutate();
  };

  return (
    <div>
      <div className="flex items-start justify-between gap-4 mb-6">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">
            LLM connections
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            OpenAI-compatible endpoints used by judges and trace eval rules.
          </p>
        </div>
        {connections.length > 0 && (
          <Button onClick={() => setCreateOpen(true)}>New connection</Button>
        )}
      </div>

      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {[1, 2, 3].map((i) => (
            <div
              key={i}
              className="rounded-lg border border-border bg-card p-5 flex flex-col gap-4"
            >
              <div className="flex items-center gap-3">
                <Skeleton className="h-3 w-3 rounded-full" />
                <Skeleton className="h-3.5 w-24" />
                <Skeleton className="h-3 w-20" />
                <Skeleton className="h-4 w-18 ml-auto rounded-full" />
              </div>
              <div className="flex flex-col gap-2">
                <Skeleton className="h-3 w-[70%]" />
                <Skeleton className="h-3 w-[50%]" />
              </div>
            </div>
          ))}
        </div>
      ) : error ? (
        <div className="rounded-lg border border-border bg-card p-10 text-center">
          <h2 className="text-base font-semibold mb-2">
            Couldn't load connections
          </h2>
          <p className="text-destructive text-sm">{error.message}</p>
        </div>
      ) : connections.length === 0 ? (
        <EmptyState onCreate={() => setCreateOpen(true)} />
      ) : (
        <ConnectionList
          connections={connections}
          onEdit={setEditing}
          onChanged={handleSaved}
        />
      )}

      <ConnectionDialog
        key="create"
        mode="create"
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onSaved={handleSaved}
      />
      {editing && (
        <ConnectionDialog
          key={editing.id ?? "edit"}
          mode="edit"
          connection={editing}
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
    <div className="rounded-lg border border-border bg-card p-12 flex flex-col items-center text-center gap-4">
      <div className="flex h-12 w-12 items-center justify-center rounded-md border border-border bg-accent text-muted-foreground">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.5"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="h-6 w-6"
        >
          <path d="M9 2v6" />
          <path d="M15 2v6" />
          <path d="M7 8h10v3a5 5 0 0 1-10 0z" />
          <path d="M12 16v6" />
        </svg>
      </div>
      <div>
        <h2 className="text-base font-semibold mb-1">No LLM connections yet</h2>
        <p className="text-muted-foreground text-sm">
          Add a connection to an OpenAI compatible endpoint so you can run LLM
          judges against your runs.
        </p>
      </div>
      <Button onClick={onCreate}>New connection</Button>
    </div>
  );
}

interface ConnectionListProps {
  connections: LlmConnectionView[];
  onEdit: (connection: LlmConnectionView) => void;
  onChanged: () => void;
}

function ConnectionList({ connections, onEdit, onChanged }: ConnectionListProps) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
      {connections.map((connection) => (
        <ConnectionRow
          key={connection.id}
          connection={connection}
          onEdit={onEdit}
          onChanged={onChanged}
        />
      ))}
    </div>
  );
}

interface ConnectionRowProps {
  connection: LlmConnectionView;
  onEdit: (connection: LlmConnectionView) => void;
  onChanged: () => void;
}

function ConnectionRow({ connection, onEdit, onChanged }: ConnectionRowProps) {
  const [confirming, setConfirming] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const { trigger: deleteTrigger, isMutating: isDeleting } = useDelete1(
    connection.id ?? ""
  );

  const credentialSource = connection.hasInlineKey
    ? "inline key"
    : connection.credentialRef
      ? connection.credentialRef
      : "no credential";
  const hasCredential = connection.hasInlineKey || !!connection.credentialRef;
  const isResponses = connection.protocol === "RESPONSES";

  const handleDelete = async () => {
    setDeleteError(null);
    try {
      await deleteTrigger();
      setConfirming(false);
      onChanged();
    } catch (err) {
      setDeleteError(errorMessage(err, "Failed to delete connection."));
    }
  };

  return (
    <div className="rounded-lg border border-border bg-card p-5 flex flex-col gap-4">
      <div className="flex items-center gap-3 min-w-0">
        <span
          className={`h-2 w-2 shrink-0 rounded-full ${
            hasCredential ? "bg-success" : "bg-warning"
          }`}
          aria-hidden="true"
        />
        <span className="font-mono text-sm font-semibold truncate">
          {connection.name}
        </span>
        <span className="font-mono text-xs text-muted-foreground truncate">
          {connection.model}
        </span>
        <Badge
          variant={isResponses ? "default" : "outline"}
          className="ml-auto shrink-0 rounded-md font-mono text-[10px] uppercase tracking-wider"
        >
          {protocolLabel(connection.protocol)}
        </Badge>
      </div>

      <div className="flex flex-col gap-2">
        <div className="flex items-baseline gap-3 text-xs">
          <span className="w-20 shrink-0 text-[11px] uppercase tracking-wider text-muted-foreground">
            Base URL
          </span>
          <span className="font-mono truncate text-foreground">
            {connection.baseUrl}
          </span>
        </div>
        <div className="flex items-baseline gap-3 text-xs">
          <span className="w-20 shrink-0 text-[11px] uppercase tracking-wider text-muted-foreground">
            Credential
          </span>
          <span
            className={`font-mono truncate ${
              hasCredential ? "text-foreground" : "text-warning"
            }`}
          >
            {credentialSource}
          </span>
        </div>
      </div>

      <div className="flex items-center gap-2 mt-1 pt-4 border-t border-border">
        {confirming ? (
          <>
            <span className="text-xs text-muted-foreground">Delete?</span>
            <span className="ml-auto" />
            <Button
              variant="destructive"
              size="sm"
              onClick={handleDelete}
              disabled={isDeleting}
            >
              {isDeleting ? "Deleting..." : "Confirm"}
            </Button>
            <Button
              variant="ghost"
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
            <span className="ml-auto" />
            <Button
              variant="ghost"
              size="sm"
              onClick={() => onEdit(connection)}
            >
              Edit
            </Button>
            <Button
              variant="ghost"
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
      {deleteError && (
        <p className="text-sm text-destructive">{deleteError}</p>
      )}
    </div>
  );
}

interface ConnectionDialogProps {
  mode: "create" | "edit";
  open: boolean;
  connection?: LlmConnectionView | null;
  onClose: () => void;
  onSaved: () => void;
}

function ConnectionDialog({
  mode,
  open,
  connection,
  onClose,
  onSaved,
}: ConnectionDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [name, setName] = useState(connection?.name ?? "");
  const [baseUrl, setBaseUrl] = useState(
    connection?.baseUrl ?? DEFAULT_BASE_URL
  );
  const [model, setModel] = useState(connection?.model ?? DEFAULT_MODEL);
  const [protocol, setProtocol] = useState<CreateLlmConnectionRequestProtocol>(
    (connection?.protocol as CreateLlmConnectionRequestProtocol) ??
      CreateLlmConnectionRequestProtocol.RESPONSES
  );
  const [apiKey, setApiKey] = useState("");
  const [submitError, setSubmitError] = useState<string | null>(null);

  const { trigger: createTrigger, isMutating: isCreating } = useCreate();
  const { trigger: updateTrigger, isMutating: isUpdating } = useUpdate(
    connection?.id ?? ""
  );
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
    if (!baseUrl.trim()) {
      setSubmitError("Base URL is required.");
      return;
    }
    if (!model.trim()) {
      setSubmitError("Model is required.");
      return;
    }
    setSubmitError(null);

    try {
      if (mode === "edit") {
        const payload: UpdateLlmConnectionRequest = {
          name: name.trim(),
          baseUrl: baseUrl.trim(),
          model: model.trim(),
          protocol,
        };
        if (apiKey.trim()) {
          payload.apiKey = apiKey.trim();
        }
        await updateTrigger(payload);
      } else {
        const payload: CreateLlmConnectionRequest = {
          name: name.trim(),
          baseUrl: baseUrl.trim(),
          model: model.trim(),
          protocol,
          apiKey: apiKey.trim() || undefined,
        };
        await createTrigger(payload);
      }
      onSaved();
      onClose();
    } catch (err) {
      setSubmitError(
        errorMessage(
          err,
          mode === "edit"
            ? "Failed to update connection."
            : "Failed to create connection."
        )
      );
    }
  };

  const inputClass =
    "w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-primary";
  const labelClass =
    "block text-[11px] font-semibold text-muted-foreground uppercase tracking-wider mb-1.5";

  return (
    <dialog
      ref={dialogRef}
      onClose={onClose}
      className="rounded-md border border-border p-0 bg-popover text-popover-foreground max-w-md w-[calc(100%-2rem)] backdrop:bg-black/60"
    >
      <form onSubmit={handleSubmit}>
        <div className="flex items-center justify-between gap-4 border-b border-border px-5 py-4">
          <h3 className="font-mono text-sm font-semibold uppercase tracking-wider">
            {mode === "edit" ? "Edit connection" : "New connection"}
          </h3>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="text-muted-foreground hover:text-foreground"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.6"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="h-4 w-4"
            >
              <path d="M18 6 6 18" />
              <path d="m6 6 12 12" />
            </svg>
          </button>
        </div>
        <div className="px-5 py-4 flex flex-col gap-4">
          <div>
            <label htmlFor="conn-name" className={labelClass}>
              Name
            </label>
            <input
              id="conn-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="openai-judge"
              className={inputClass}
              autoFocus
              required
            />
          </div>
          <div>
            <label htmlFor="conn-base-url" className={labelClass}>
              Base URL
            </label>
            <input
              id="conn-base-url"
              type="text"
              value={baseUrl}
              onChange={(e) => setBaseUrl(e.target.value)}
              className={inputClass}
              required
            />
          </div>
          <div>
            <label htmlFor="conn-model" className={labelClass}>
              Model
            </label>
            <input
              id="conn-model"
              type="text"
              value={model}
              onChange={(e) => setModel(e.target.value)}
              className={inputClass}
              required
            />
          </div>
          <div>
            <label htmlFor="conn-protocol" className={labelClass}>
              API
            </label>
            <select
              id="conn-protocol"
              value={protocol}
              onChange={(e) =>
                setProtocol(
                  e.target.value as CreateLlmConnectionRequestProtocol
                )
              }
              className={inputClass}
            >
              <option value={CreateLlmConnectionRequestProtocol.RESPONSES}>
                Responses (recommended)
              </option>
              <option
                value={CreateLlmConnectionRequestProtocol.CHAT_COMPLETIONS}
              >
                Chat Completions
              </option>
            </select>
          </div>
          <div>
            <label htmlFor="conn-api-key" className={labelClass}>
              API key
            </label>
            <input
              id="conn-api-key"
              type="password"
              value={apiKey}
              onChange={(e) => setApiKey(e.target.value)}
              placeholder={
                mode === "edit" ? "leave blank to keep current key" : "sk-..."
              }
              className={inputClass}
            />
          </div>
          {submitError && (
            <p className="text-sm text-destructive">{submitError}</p>
          )}
        </div>
        <div className="flex justify-end gap-2 border-t border-border px-5 py-4">
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
