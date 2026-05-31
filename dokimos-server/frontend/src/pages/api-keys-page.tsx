import {
  useEffect,
  useRef,
  useState,
  type FormEvent,
  type ReactNode,
} from "react";
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

const ROLE_BADGE_CLASSES: Record<CreateApiKeyRequestRole, string> = {
  [CreateApiKeyRequestRole.VIEWER]:
    "border-success/40 bg-pass-tint text-success",
  [CreateApiKeyRequestRole.EDITOR]:
    "border-warning/40 bg-warn-tint text-warning",
  [CreateApiKeyRequestRole.ADMIN]:
    "border-primary/40 bg-accent-tint text-primary",
};

function roleBadgeClass(role?: CreateApiKeyRequestRole): string {
  if (role && ROLE_BADGE_CLASSES[role]) {
    return ROLE_BADGE_CLASSES[role];
  }
  return "border-border text-muted-foreground";
}

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
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h1 className="text-2xl font-bold">API keys</h1>
          <p className="font-prose text-sm text-muted-foreground mt-1 max-w-2xl">
            Scoped keys for the REST API, SDK reporters, and CI. Role determines
            write access. Read operations are always open.
          </p>
        </div>
        {keys.length > 0 && (
          <Button
            className="shrink-0"
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
        <div className="rounded-lg border border-border bg-card overflow-hidden">
          <div className="flex items-center justify-between border-b border-border px-4 py-2.5">
            <Skeleton className="h-3 w-20" />
          </div>
          {[1, 2, 3].map((i) => (
            <div
              key={i}
              className="flex items-center gap-4 px-4 py-4 border-b border-border last:border-b-0"
            >
              <div className="min-w-0 flex-1">
                <Skeleton className="h-4 w-40 mb-2" />
                <Skeleton className="h-3 w-72" />
              </div>
              <Skeleton className="h-7 w-28 shrink-0" />
            </div>
          ))}
        </div>
      ) : error ? (
        <div className="rounded-lg border border-destructive/40 bg-fail-tint px-4 py-3">
          <p className="text-sm text-destructive">
            Error loading API keys: {error.message}
          </p>
        </div>
      ) : keys.length === 0 ? (
        <EmptyState
          onCreate={() => {
            setCreateNonce((n) => n + 1);
            setCreateOpen(true);
          }}
        />
      ) : (
        <>
          <ApiKeyList keys={keys} onChanged={handleChanged} />
          <RoleReference />
        </>
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

function SectionHeader({
  title,
  right,
}: {
  title: string;
  right?: ReactNode;
}) {
  return (
    <div className="flex items-center justify-between border-b border-border px-4 py-2.5">
      <span className="text-[11px] uppercase tracking-wider text-muted-foreground">
        {title}
      </span>
      {right ? (
        <span className="text-[11px] text-muted-foreground tabular-nums">
          {right}
        </span>
      ) : null}
    </div>
  );
}

function RoleBadge({ role }: { role?: CreateApiKeyRequestRole }) {
  const label = role ? ROLE_LABELS[role] : "Unknown";
  return (
    <span
      className={`inline-flex items-center justify-center rounded-md border px-2 py-0.5 text-[11px] ${roleBadgeClass(
        role
      )}`}
    >
      {label}
    </span>
  );
}

function RoleReference() {
  return (
    <section className="rounded-lg border border-border bg-card overflow-hidden">
      <SectionHeader title="Roles" right="read is always open" />
      <div className="divide-y divide-border">
        <div className="flex items-center gap-3 px-4 py-3">
          <RoleBadge role={CreateApiKeyRequestRole.VIEWER} />
          <span className="font-prose text-sm text-muted-foreground">
            Read-only access to runs, traces, and datasets.
          </span>
        </div>
        <div className="flex items-center gap-3 px-4 py-3">
          <RoleBadge role={CreateApiKeyRequestRole.EDITOR} />
          <span className="font-prose text-sm text-muted-foreground">
            Write runs, datasets, and annotations.
          </span>
        </div>
        <div className="flex items-center gap-3 px-4 py-3">
          <RoleBadge role={CreateApiKeyRequestRole.ADMIN} />
          <span className="font-prose text-sm text-muted-foreground">
            Manage keys, connections, and webhooks.
          </span>
        </div>
      </div>
    </section>
  );
}

function EmptyState({ onCreate }: { onCreate: () => void }) {
  return (
    <div className="rounded-lg border border-border bg-card p-10 text-center">
      <h2 className="text-lg font-semibold mb-2">No API keys yet</h2>
      <p className="font-prose text-muted-foreground text-sm mb-6 max-w-md mx-auto">
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
    <section className="rounded-lg border border-border bg-card overflow-hidden">
      <div className="flex items-center justify-between border-b border-border px-4 py-2.5">
        <span className="text-[11px] uppercase tracking-wider text-muted-foreground">
          Key created{name ? ` · ${name}` : ""}
        </span>
      </div>
      <div className="p-4 space-y-3">
        <div className="flex items-start gap-2 rounded-md border border-warning/40 bg-warn-tint px-3 py-2 text-sm text-warning">
          <span aria-hidden="true">⚠</span>
          <span>
            Copy this key now and store it somewhere safe. For security reasons
            it will not be shown again.
          </span>
        </div>
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
          <code className="flex-1 min-w-0 break-all rounded-md border border-border-strong bg-background px-3 py-2 text-[12.5px] font-mono">
            {rawKey}
          </code>
          <div className="flex items-center gap-2 shrink-0">
            <Button variant="outline" size="sm" onClick={handleCopy}>
              {copied ? "Copied" : "Copy"}
            </Button>
            <Button size="sm" onClick={onDismiss}>
              Done
            </Button>
          </div>
        </div>
      </div>
    </section>
  );
}

interface ApiKeyListProps {
  keys: ApiKeyView[];
  onChanged: () => void;
}

function ApiKeyList({ keys, onChanged }: ApiKeyListProps) {
  return (
    <section className="rounded-lg border border-border bg-card overflow-hidden">
      <SectionHeader
        title="Keys"
        right={`${keys.length} ${keys.length === 1 ? "key" : "keys"}`}
      />
      <div>
        {keys.map((apiKey) => (
          <ApiKeyRow key={apiKey.id} apiKey={apiKey} onChanged={onChanged} />
        ))}
      </div>
    </section>
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
    <div
      className={`px-4 py-4 border-b border-border last:border-b-0 ${
        apiKey.enabled === false ? "opacity-80" : ""
      }`}
    >
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between sm:gap-4">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-[13.5px] font-semibold truncate">
              {apiKey.name}
            </p>
            <RoleBadge role={apiKey.role} />
            {apiKey.enabled === false && (
              <span className="inline-flex items-center justify-center rounded-md border border-destructive/40 bg-fail-tint px-2 py-0.5 text-[11px] text-destructive">
                Disabled
              </span>
            )}
          </div>
          <p className="font-mono text-xs text-muted-foreground mt-1.5 tabular-nums">
            created {formatTimestamp(apiKey.createdAt)} · last used{" "}
            {formatTimestamp(apiKey.lastUsedAt)}
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
                className="text-destructive hover:text-destructive"
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
      className="rounded-md border border-border p-0 bg-popover text-popover-foreground max-w-md w-[calc(100%-2rem)] shadow-xl backdrop:bg-black/50"
    >
      <form onSubmit={handleSubmit}>
        <div className="flex items-center justify-between border-b border-border px-4 py-3">
          <h3 className="text-[12px] font-semibold uppercase tracking-wider">
            New API key
          </h3>
          <button
            type="button"
            onClick={onClose}
            disabled={isMutating}
            aria-label="Close"
            className="text-muted-foreground hover:text-foreground"
          >
            ✕
          </button>
        </div>
        <div className="p-4 space-y-4">
          <div>
            <label
              htmlFor="key-name"
              className="block text-[11px] font-semibold text-muted-foreground uppercase tracking-wider mb-1.5"
            >
              Name
            </label>
            <input
              id="key-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="ci-nightly"
              className="w-full border border-border-strong rounded-md px-3 py-2 text-sm font-mono bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-ring/40"
              autoFocus
              required
            />
            <p className="font-prose text-[11px] text-muted-foreground mt-1.5">
              A label to recognise this key later. It is not part of the
              credential.
            </p>
          </div>
          <div>
            <label
              htmlFor="key-role"
              className="block text-[11px] font-semibold text-muted-foreground uppercase tracking-wider mb-1.5"
            >
              Role
            </label>
            <select
              id="key-role"
              value={role}
              onChange={(e) =>
                setRole(e.target.value as CreateApiKeyRequestRole)
              }
              className="w-full border border-border-strong rounded-md px-3 py-2 text-sm bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-ring/40"
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
            <p className="font-prose text-[11px] text-muted-foreground mt-1.5">
              Read operations are open to all keys. Write access is gated by
              role.
            </p>
          </div>
          {submitError && (
            <p className="text-sm text-destructive">{submitError}</p>
          )}
        </div>
        <div className="flex justify-end gap-2 border-t border-border px-4 py-3">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={onClose}
            disabled={isMutating}
          >
            Cancel
          </Button>
          <Button type="submit" size="sm" disabled={isMutating}>
            {isMutating ? "Creating..." : "Create key"}
          </Button>
        </div>
      </form>
    </dialog>
  );
}
