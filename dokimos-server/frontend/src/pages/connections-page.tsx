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
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">LLM connections</h1>
        {connections.length > 0 && (
          <Button onClick={() => setCreateOpen(true)}>New connection</Button>
        )}
      </div>

      {isLoading ? (
        <div className="rounded-xl border bg-card overflow-hidden">
          {[1, 2, 3].map((i) => (
            <div key={i} className="px-4 py-4 border-b last:border-b-0">
              <Skeleton className="h-4 w-48 mb-2" />
              <Skeleton className="h-3 w-72" />
            </div>
          ))}
        </div>
      ) : error ? (
        <p className="text-destructive">
          Error loading connections: {error.message}
        </p>
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
    <div className="rounded-xl border bg-card p-10 text-center">
      <h2 className="text-lg font-semibold mb-2">No LLM connections yet</h2>
      <p className="text-muted-foreground text-sm mb-6">
        Add a connection to an OpenAI compatible endpoint so you can run LLM
        judges against your runs.
      </p>
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
    <div className="rounded-xl border bg-card overflow-hidden">
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
    <div className="px-4 py-4 border-b last:border-b-0">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="text-sm font-semibold truncate">{connection.name}</p>
          <p className="text-sm text-muted-foreground truncate">
            {connection.model}
          </p>
          <p className="text-xs text-muted-foreground truncate mt-1">
            {connection.baseUrl}
          </p>
          <p className="text-xs text-muted-foreground mt-1">
            Credential: {credentialSource}
          </p>
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
              <Button
                variant="outline"
                size="sm"
                onClick={() => onEdit(connection)}
              >
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

  return (
    <dialog
      ref={dialogRef}
      onClose={onClose}
      className="border rounded-xl p-0 bg-card text-foreground max-w-md w-[calc(100%-2rem)] backdrop:bg-black/50"
    >
      <form onSubmit={handleSubmit} className="p-5">
        <h3 className="text-base font-bold mb-1">
          {mode === "edit" ? "Edit connection" : "New connection"}
        </h3>
        <p className="text-sm text-muted-foreground mb-4">
          Connect to an OpenAI compatible endpoint used by LLM judges.
        </p>
        <div className="mb-3">
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
            autoFocus
            required
          />
        </div>
        <div className="mb-3">
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
            required
          />
        </div>
        <div className="mb-3">
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
            required
          />
        </div>
        <div className="mb-3">
          <label
            htmlFor="conn-protocol"
            className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1"
          >
            API
          </label>
          <select
            id="conn-protocol"
            value={protocol}
            onChange={(e) =>
              setProtocol(e.target.value as CreateLlmConnectionRequestProtocol)
            }
            className="w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground"
          >
            <option value={CreateLlmConnectionRequestProtocol.RESPONSES}>
              Responses (recommended)
            </option>
            <option value={CreateLlmConnectionRequestProtocol.CHAT_COMPLETIONS}>
              Chat Completions
            </option>
          </select>
        </div>
        <div className="mb-3">
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
            placeholder={
              mode === "edit" ? "leave blank to keep current key" : "sk-..."
            }
            className="w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground"
          />
        </div>
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
