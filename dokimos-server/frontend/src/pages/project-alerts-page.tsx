import { useEffect, useRef, useState, type FormEvent } from "react";
import { useParams } from "react-router";
import { formatDistanceToNow } from "date-fns";
import axios from "axios";
import { useListProjects } from "@/lib/api/project-controller/project-controller";
import {
  useListAlertWebhooks,
  useCreateAlertWebhook,
  useUpdateAlertWebhook,
  useDeleteAlertWebhook,
} from "@/lib/api/alert-webhook-controller/alert-webhook-controller";
import type {
  AlertWebhookView,
  CreateAlertWebhookRequest,
  UpdateAlertWebhookRequest,
} from "@/lib/api/generated.schemas";
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

export default function ProjectAlertsPage() {
  const { name } = useParams<{ name: string }>();
  const { setBreadcrumbs } = useBreadcrumbs();
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<AlertWebhookView | null>(null);

  const { data: projectsResponse, isLoading: projectsLoading } =
    useListProjects();
  const project = projectsResponse?.data?.find((p) => p.name === name);
  const projectId = project?.id ?? "";

  const {
    data: response,
    error,
    isLoading,
    mutate,
  } = useListAlertWebhooks(projectId, { swr: { enabled: !!projectId } });
  const webhooks = response?.data ?? [];

  useEffect(() => {
    if (name) {
      setBreadcrumbs([
        { label: "Home", href: "/" },
        { label: name, href: `/projects/${encodeURIComponent(name)}` },
        {
          label: "Alerts",
          href: `/projects/${encodeURIComponent(name)}/alerts`,
        },
      ]);
    }
  }, [name, setBreadcrumbs]);

  const handleSaved = async () => {
    await mutate();
  };

  const resolvingProject = projectsLoading || (!!name && !project);

  return (
    <div>
      <div className="flex items-start justify-between gap-4 mb-1">
        <div className="min-w-0">
          <h1 className="text-xl font-semibold tracking-tight">
            Alert webhooks
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Webhooks for <span className="font-mono">{name}</span> receive a
            POST when a run regresses against its baseline.
          </p>
        </div>
        {projectId && webhooks.length > 0 && (
          <Button
            size="sm"
            onClick={() => setCreateOpen(true)}
            className="shrink-0"
          >
            New webhook
          </Button>
        )}
      </div>
      <div className="mb-6" />

      {projectsLoading ? (
        <WebhookListSkeleton />
      ) : !project ? (
        <div className="rounded-lg border border-border bg-card p-6 text-sm text-muted-foreground">
          Project not found. Return to the project list and try again.
        </div>
      ) : isLoading || resolvingProject ? (
        <WebhookListSkeleton />
      ) : error ? (
        <div className="rounded-lg border border-border bg-card p-6 text-sm text-destructive">
          Error loading webhooks: {error.message}
        </div>
      ) : webhooks.length === 0 ? (
        <EmptyState onCreate={() => setCreateOpen(true)} />
      ) : (
        <WebhookList
          projectId={projectId}
          webhooks={webhooks}
          onEdit={setEditing}
          onChanged={handleSaved}
        />
      )}

      {projectId && (
        <WebhookDialog
          key="create"
          mode="create"
          projectId={projectId}
          open={createOpen}
          onClose={() => setCreateOpen(false)}
          onSaved={handleSaved}
        />
      )}
      {projectId && editing && (
        <WebhookDialog
          key={editing.id ?? "edit"}
          mode="edit"
          projectId={projectId}
          webhook={editing}
          open
          onClose={() => setEditing(null)}
          onSaved={handleSaved}
        />
      )}
    </div>
  );
}

function WebhookListSkeleton() {
  return (
    <section className="rounded-lg border border-border bg-card overflow-hidden">
      <div className="flex items-center justify-between px-4 py-3 border-b border-border">
        <span className="text-[11px] uppercase tracking-wider text-muted-foreground">
          Webhooks
        </span>
      </div>
      <div className="p-3 space-y-2">
        {[1, 2, 3].map((i) => (
          <div
            key={i}
            className="flex items-center gap-3 rounded-md border border-border px-4 py-3"
          >
            <Skeleton className="h-2.5 w-2.5 rounded-full shrink-0" />
            <div className="flex-1 min-w-0 space-y-2">
              <Skeleton className="h-3.5 w-64" />
              <Skeleton className="h-3 w-40" />
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

function EmptyState({ onCreate }: { onCreate: () => void }) {
  return (
    <section className="rounded-lg border border-border bg-card px-6 py-12 flex flex-col items-center text-center">
      <div className="flex h-11 w-11 items-center justify-center rounded-md border border-border text-primary mb-4">
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
          <path d="M10.3 21a1.9 1.9 0 0 0 3.4 0" />
          <path d="M21 17H3l1.6-2.3a2 2 0 0 0 .4-1.2V10a7 7 0 0 1 14 0v3.5c0 .4.1.8.4 1.2z" />
        </svg>
      </div>
      <h2 className="text-base font-semibold mb-1.5">No alert webhooks yet</h2>
      <p className="text-muted-foreground text-sm max-w-md mb-6">
        Add a webhook to get notified when an experiment regresses against its
        baseline.
      </p>
      <Button size="sm" onClick={onCreate}>
        New webhook
      </Button>
    </section>
  );
}

interface WebhookListProps {
  projectId: string;
  webhooks: AlertWebhookView[];
  onEdit: (webhook: AlertWebhookView) => void;
  onChanged: () => void;
}

function WebhookList({
  projectId,
  webhooks,
  onEdit,
  onChanged,
}: WebhookListProps) {
  return (
    <section className="rounded-lg border border-border bg-card overflow-hidden">
      <div className="flex items-center justify-between px-4 py-3 border-b border-border">
        <span className="text-[11px] uppercase tracking-wider text-muted-foreground">
          Webhooks
        </span>
        <span className="text-[11px] font-mono tabular-nums text-muted-foreground">
          {webhooks.length} {webhooks.length === 1 ? "endpoint" : "endpoints"}
        </span>
      </div>
      <div className="p-3 space-y-2">
        {webhooks.map((webhook) => (
          <WebhookRow
            key={webhook.id}
            projectId={projectId}
            webhook={webhook}
            onEdit={onEdit}
            onChanged={onChanged}
          />
        ))}
      </div>
    </section>
  );
}

interface WebhookRowProps {
  projectId: string;
  webhook: AlertWebhookView;
  onEdit: (webhook: AlertWebhookView) => void;
  onChanged: () => void;
}

function WebhookRow({ projectId, webhook, onEdit, onChanged }: WebhookRowProps) {
  const [confirming, setConfirming] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const { trigger: deleteTrigger, isMutating: isDeleting } =
    useDeleteAlertWebhook(projectId, webhook.id ?? "");

  const handleDelete = async () => {
    setDeleteError(null);
    try {
      await deleteTrigger();
      setConfirming(false);
      onChanged();
    } catch (err) {
      setDeleteError(errorMessage(err, "Failed to delete webhook."));
    }
  };

  return (
    <div
      className={`rounded-md border border-border bg-background px-4 py-3 ${
        webhook.enabled ? "" : "opacity-70"
      }`}
    >
      <div className="flex items-start justify-between gap-4">
        <div className="flex items-start gap-3 min-w-0">
          <span
            className={`mt-1.5 inline-block h-2 w-2 rounded-full shrink-0 ${
              webhook.enabled ? "bg-success" : "bg-muted-foreground/40"
            }`}
            title={webhook.enabled ? "Enabled" : "Disabled"}
          />
          <div className="min-w-0">
            <p className="text-[12.5px] font-mono text-foreground truncate">
              {webhook.url}
            </p>
            <div className="mt-1.5 flex items-center gap-2 flex-wrap text-xs">
              <span
                className={`inline-flex items-center rounded-sm border px-1.5 py-0.5 text-[11px] uppercase tracking-wide ${
                  webhook.enabled
                    ? "border-success/30 text-success bg-pass-tint"
                    : "border-destructive/30 text-destructive bg-fail-tint"
                }`}
              >
                {webhook.enabled ? "Enabled" : "Disabled"}
              </span>
              <span className="inline-flex items-center rounded-sm border border-border px-1.5 py-0.5 text-[11px] text-muted-foreground">
                {webhook.hasSecret ? "signing secret set" : "no signing secret"}
              </span>
              {webhook.createdAt && (
                <span className="text-muted-foreground">
                  created{" "}
                  {formatDistanceToNow(new Date(webhook.createdAt), {
                    addSuffix: true,
                  })}
                </span>
              )}
            </div>
          </div>
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
              <Button variant="ghost" size="sm" onClick={() => onEdit(webhook)}>
                Edit
              </Button>
              <Button
                variant="ghost"
                size="sm"
                className="text-destructive hover:text-destructive"
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

interface WebhookDialogProps {
  mode: "create" | "edit";
  projectId: string;
  open: boolean;
  webhook?: AlertWebhookView | null;
  onClose: () => void;
  onSaved: () => void;
}

function WebhookDialog({
  mode,
  projectId,
  open,
  webhook,
  onClose,
  onSaved,
}: WebhookDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [url, setUrl] = useState(webhook?.url ?? "");
  const [secret, setSecret] = useState("");
  const [enabled, setEnabled] = useState(webhook?.enabled ?? true);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const { trigger: createTrigger, isMutating: isCreating } =
    useCreateAlertWebhook(projectId);
  const { trigger: updateTrigger, isMutating: isUpdating } =
    useUpdateAlertWebhook(projectId, webhook?.id ?? "");
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
    if (!url.trim()) {
      setSubmitError("URL is required.");
      return;
    }
    setSubmitError(null);

    try {
      if (mode === "edit") {
        const payload: UpdateAlertWebhookRequest = {
          url: url.trim(),
          enabled,
        };
        if (secret.trim()) {
          payload.secret = secret.trim();
        }
        await updateTrigger(payload);
      } else {
        const payload: CreateAlertWebhookRequest = {
          url: url.trim(),
          enabled,
          secret: secret.trim() || undefined,
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
            ? "Failed to update webhook."
            : "Failed to create webhook."
        )
      );
    }
  };

  return (
    <dialog
      ref={dialogRef}
      onClose={onClose}
      className="border border-border rounded-md p-0 bg-popover text-popover-foreground font-mono max-w-md w-[calc(100%-2rem)] backdrop:bg-black/50"
    >
      <form onSubmit={handleSubmit}>
        <div className="flex items-center justify-between border-b border-border px-5 py-3">
          <h3 className="text-[11px] uppercase tracking-wider text-muted-foreground">
            {mode === "edit" ? "Edit webhook" : "New webhook"}
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
              strokeWidth="1.6"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M18 6 6 18" />
              <path d="m6 6 12 12" />
            </svg>
          </button>
        </div>
        <div className="px-5 py-4">
          <p className="text-sm text-muted-foreground mb-4">
            We POST a JSON payload to this URL when a run regresses against its
            baseline.
          </p>
          <div className="mb-3">
            <label
              htmlFor="webhook-url"
              className="block text-[11px] font-semibold text-muted-foreground uppercase tracking-wider mb-1"
            >
              URL
            </label>
            <input
              id="webhook-url"
              type="url"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://hooks.example.com/dokimos"
              className="w-full border border-border rounded-md px-3 py-2 text-sm font-mono bg-background text-foreground focus:outline-none focus:ring-1 focus:ring-ring"
              autoFocus
              required
            />
          </div>
          <div className="mb-3">
            <label
              htmlFor="webhook-secret"
              className="block text-[11px] font-semibold text-muted-foreground uppercase tracking-wider mb-1"
            >
              Signing secret
            </label>
            <input
              id="webhook-secret"
              type="password"
              value={secret}
              onChange={(e) => setSecret(e.target.value)}
              placeholder={
                mode === "edit"
                  ? webhook?.hasSecret
                    ? "leave blank to keep current secret"
                    : "optional"
                  : "optional"
              }
              className="w-full border border-border rounded-md px-3 py-2 text-sm font-mono bg-background text-foreground focus:outline-none focus:ring-1 focus:ring-ring"
            />
            <p className="text-xs text-muted-foreground mt-1">
              Used to sign the payload so you can verify it came from us. It is
              never displayed after saving.
            </p>
          </div>
          <label
            htmlFor="webhook-enabled"
            className="flex items-center gap-2 text-sm cursor-pointer"
          >
            <input
              id="webhook-enabled"
              type="checkbox"
              checked={enabled}
              onChange={(e) => setEnabled(e.target.checked)}
              className="h-4 w-4 rounded border-border accent-primary"
            />
            Enabled
          </label>
          {submitError && (
            <p className="text-sm text-destructive mt-3">{submitError}</p>
          )}
        </div>
        <div className="flex justify-end gap-2 border-t border-border px-5 py-3">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={onClose}
            disabled={isMutating}
          >
            Cancel
          </Button>
          <Button type="submit" size="sm" disabled={isMutating}>
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
