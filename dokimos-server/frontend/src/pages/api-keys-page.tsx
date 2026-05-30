import { useEffect, useRef, useState, type FormEvent } from "react";
import axios from "axios";
import { formatDistanceToNow } from "date-fns";
import {
  useListApiKeys,
  useCreateApiKey,
  useDisableApiKey,
  useDeleteApiKey,
} from "@/lib/api/api-key-controller/api-key-controller";
import type {
  ApiKeyView,
  CreateApiKeyRequest,
  CreatedApiKeyView,
} from "@/lib/api/generated.schemas";
import { CreateApiKeyRequestRole } from "@/lib/api/generated.schemas";
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

const ROLE_LABELS: Record<CreateApiKeyRequestRole, string> = {
  [CreateApiKeyRequestRole.VIEWER]: "Viewer",
  [CreateApiKeyRequestRole.EDITOR]: "Editor",
  [CreateApiKeyRequestRole.ADMIN]: "Admin",
};

function formatTimestamp(value?: string): string {
  if (!value) {
    return "never";
  }
  return formatDistanceToNow(new Date(value), { addSuffix: true });
}

export default function ApiKeysPage() {
  const { setBreadcrumbs } = useBreadcrumbs();
  const [createOpen, setCreateOpen] = useState(false);
  const [createNonce, setCreateNonce] = useState(0);
  const [createdKey, setCreatedKey] = useState<CreatedApiKeyView | null>(null);

  const { data: response, error, isLoading, mutate } = useListApiKeys();
  const keys = response?.data ?? [];

  useEffect(() => {
    setBreadcrumbs([
      { label: "Home", href: "/" },
      { label: "API keys", href: "/api-keys" },
    ]);
  }, [setBreadcrumbs]);

  const handleChanged = async () => {
    await mutate();
  };

  const handleCreated = async (created: CreatedApiKeyView) => {
    setCreatedKey(created);
    await mutate();
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">API keys</h1>
        {keys.length > 0 && (
          <Button
            onClick={() => {
              setCreateNonce((n) => n + 1);
              setCreateOpen(true);
            }}
          >
            New API key
          </Button>
        )}
      </div>

      {createdKey && (
        <CreatedKeyPanel
          created={createdKey}
          onDismiss={() => setCreatedKey(null)}
        />
      )}

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
          Error loading API keys: {error.message}
        </p>
      ) : keys.length === 0 ? (
        <EmptyState
          onCreate={() => {
            setCreateNonce((n) => n + 1);
            setCreateOpen(true);
          }}
        />
      ) : (
        <ApiKeyList keys={keys} onChanged={handleChanged} />
      )}

      <CreateApiKeyDialog
        key={createNonce}
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={handleCreated}
      />
    </div>
  );
}

function EmptyState({ onCreate }: { onCreate: () => void }) {
  return (
    <div className="rounded-xl border bg-card p-10 text-center">
      <h2 className="text-lg font-semibold mb-2">No API keys yet</h2>
      <p className="text-muted-foreground text-sm mb-6">
        Create an API key to authenticate clients against the server. Each key
        is scoped to a role that controls what it can do.
      </p>
      <Button onClick={onCreate}>New API key</Button>
    </div>
  );
}

interface CreatedKeyPanelProps {
  created: CreatedApiKeyView;
  onDismiss: () => void;
}

function CreatedKeyPanel({ created, onDismiss }: CreatedKeyPanelProps) {
  const [copied, setCopied] = useState(false);
  const rawKey = created.key ?? "";
  const name = created.apiKey?.name;

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(rawKey);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  };

  return (
    <div className="rounded-xl border border-amber-500/40 bg-amber-500/10 p-5 mb-6">
      <h2 className="text-base font-semibold mb-1">
        API key created{name ? ` (${name})` : ""}
      </h2>
      <p className="text-sm text-muted-foreground mb-4">
        Copy this key now and store it somewhere safe. For security reasons it
        will not be shown again.
      </p>
      <div className="flex items-center gap-2">
        <code className="flex-1 min-w-0 break-all rounded-md border bg-background px-3 py-2 text-sm font-mono">
          {rawKey}
        </code>
        <Button variant="outline" size="sm" onClick={handleCopy}>
          {copied ? "Copied" : "Copy"}
        </Button>
        <Button variant="outline" size="sm" onClick={onDismiss}>
          Done
        </Button>
      </div>
    </div>
  );
}

interface ApiKeyListProps {
  keys: ApiKeyView[];
  onChanged: () => void;
}

function ApiKeyList({ keys, onChanged }: ApiKeyListProps) {
  return (
    <div className="rounded-xl border bg-card overflow-hidden">
      {keys.map((apiKey) => (
        <ApiKeyRow key={apiKey.id} apiKey={apiKey} onChanged={onChanged} />
      ))}
    </div>
  );
}

interface ApiKeyRowProps {
  apiKey: ApiKeyView;
  onChanged: () => void;
}

type ConfirmAction = "disable" | "delete";

function ApiKeyRow({ apiKey, onChanged }: ApiKeyRowProps) {
  const [confirming, setConfirming] = useState<ConfirmAction | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const { trigger: disableTrigger, isMutating: isDisabling } = useDisableApiKey(
    apiKey.id ?? ""
  );
  const { trigger: deleteTrigger, isMutating: isDeleting } = useDeleteApiKey(
    apiKey.id ?? ""
  );

  const roleLabel = apiKey.role ? ROLE_LABELS[apiKey.role] : "Unknown";
  const isBusy = isDisabling || isDeleting;

  const handleDisable = async () => {
    setActionError(null);
    try {
      await disableTrigger();
      setConfirming(null);
      onChanged();
    } catch (err) {
      setActionError(errorMessage(err, "Failed to disable API key."));
    }
  };

  const handleDelete = async () => {
    setActionError(null);
    try {
      await deleteTrigger();
      setConfirming(null);
      onChanged();
    } catch (err) {
      setActionError(errorMessage(err, "Failed to delete API key."));
    }
  };

  return (
    <div className="px-4 py-4 border-b last:border-b-0">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <p className="text-sm font-semibold truncate">{apiKey.name}</p>
            <span className="text-xs rounded-full border px-2 py-0.5 text-muted-foreground">
              {roleLabel}
            </span>
            {apiKey.enabled === false && (
              <span className="text-xs rounded-full border border-destructive/40 px-2 py-0.5 text-destructive">
                Disabled
              </span>
            )}
          </div>
          <p className="text-xs text-muted-foreground mt-1">
            Created {formatTimestamp(apiKey.createdAt)}
          </p>
          <p className="text-xs text-muted-foreground mt-1">
            Last used {formatTimestamp(apiKey.lastUsedAt)}
          </p>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          {confirming === "disable" ? (
            <>
              <span className="text-xs text-muted-foreground">Disable?</span>
              <Button
                variant="destructive"
                size="sm"
                onClick={handleDisable}
                disabled={isBusy}
              >
                {isDisabling ? "Disabling..." : "Confirm"}
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setConfirming(null);
                  setActionError(null);
                }}
                disabled={isBusy}
              >
                Cancel
              </Button>
            </>
          ) : confirming === "delete" ? (
            <>
              <span className="text-xs text-muted-foreground">Delete?</span>
              <Button
                variant="destructive"
                size="sm"
                onClick={handleDelete}
                disabled={isBusy}
              >
                {isDeleting ? "Deleting..." : "Confirm"}
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setConfirming(null);
                  setActionError(null);
                }}
                disabled={isBusy}
              >
                Cancel
              </Button>
            </>
          ) : (
            <>
              {apiKey.enabled !== false && (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    setConfirming("disable");
                    setActionError(null);
                  }}
                >
                  Disable
                </Button>
              )}
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setConfirming("delete");
                  setActionError(null);
                }}
              >
                Delete
              </Button>
            </>
          )}
        </div>
      </div>
      {actionError && (
        <p className="text-sm text-destructive mt-2">{actionError}</p>
      )}
    </div>
  );
}

interface CreateApiKeyDialogProps {
  open: boolean;
  onClose: () => void;
  onCreated: (created: CreatedApiKeyView) => void;
}

function CreateApiKeyDialog({
  open,
  onClose,
  onCreated,
}: CreateApiKeyDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [name, setName] = useState("");
  const [role, setRole] = useState<CreateApiKeyRequestRole>(
    CreateApiKeyRequestRole.VIEWER
  );
  const [submitError, setSubmitError] = useState<string | null>(null);

  const { trigger: createTrigger, isMutating } = useCreateApiKey();

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
    setSubmitError(null);

    try {
      const payload: CreateApiKeyRequest = {
        name: name.trim(),
        role,
      };
      const result = await createTrigger(payload);
      onCreated(result.data);
      onClose();
    } catch (err) {
      setSubmitError(errorMessage(err, "Failed to create API key."));
    }
  };

  return (
    <dialog
      ref={dialogRef}
      onClose={onClose}
      className="border rounded-xl p-0 bg-card text-foreground max-w-md w-[calc(100%-2rem)] backdrop:bg-black/50"
    >
      <form onSubmit={handleSubmit} className="p-5">
        <h3 className="text-base font-bold mb-1">New API key</h3>
        <p className="text-sm text-muted-foreground mb-4">
          The generated key is shown once after creation. Store it somewhere
          safe.
        </p>
        <div className="mb-3">
          <label
            htmlFor="key-name"
            className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1"
          >
            Name
          </label>
          <input
            id="key-name"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="ci-pipeline"
            className="w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground"
            autoFocus
            required
          />
        </div>
        <div className="mb-3">
          <label
            htmlFor="key-role"
            className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1"
          >
            Role
          </label>
          <select
            id="key-role"
            value={role}
            onChange={(e) =>
              setRole(e.target.value as CreateApiKeyRequestRole)
            }
            className="w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground"
          >
            <option value={CreateApiKeyRequestRole.VIEWER}>
              Viewer (read only)
            </option>
            <option value={CreateApiKeyRequestRole.EDITOR}>
              Editor (read and write)
            </option>
            <option value={CreateApiKeyRequestRole.ADMIN}>
              Admin (full access)
            </option>
          </select>
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
            {isMutating ? "Creating..." : "Create"}
          </Button>
        </div>
      </form>
    </dialog>
  );
}
