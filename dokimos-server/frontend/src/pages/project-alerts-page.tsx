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
      <div className="flex items-center justify-between mb-1">
        <h1 className="text-2xl font-bold">Alert webhooks</h1>
        {projectId && webhooks.length > 0 && (
          <Button onClick={() => setCreateOpen(true)}>New webhook</Button>
        )}
      </div>
      <p className="text-sm text-muted-foreground mb-6">
        Webhooks for {name} receive a POST when a run regresses against its
        baseline.
      </p>

      {projectsLoading ? (
        <WebhookListSkeleton />
      ) : !project ? (
        <p className="text-muted-foreground">
          Project not found. Return to the project list and try again.
        </p>
      ) : isLoading || resolvingProject ? (
        <WebhookListSkeleton />
      ) : error ? (
        <p className="text-destructive">
          Error loading webhooks: {error.message}
        </p>
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
    <div className="rounded-xl border bg-card overflow-hidden">
      {[1, 2, 3].map((i) => (
        <div key={i} className="px-4 py-4 border-b last:border-b-0">
          <Skeleton className="h-4 w-64 mb-2" />
          <Skeleton className="h-3 w-40" />
        </div>
      ))}
    </div>
  );
}

function EmptyState({ onCreate }: { onCreate: () => void }) {
  return (
    <div className="rounded-xl border bg-card p-10 text-center">
      <h2 className="text-lg font-semibold mb-2">No alert webhooks yet</h2>
      <p className="text-muted-foreground text-sm mb-6">
        Add a webhook to get notified when an experiment regresses against its
        baseline.
      </p>
      <Button onClick={onCreate}>New webhook</Button>
    </div>
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
    <div className="rounded-xl border bg-card overflow-hidden">
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
    <div className="px-4 py-4 border-b last:border-b-0">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <span
              className={`inline-block h-2 w-2 rounded-full shrink-0 ${
                webhook.enabled ? "bg-green-500" : "bg-muted-foreground/40"
              }`}
            />
            <p className="text-sm font-semibold truncate">{webhook.url}</p>
          </div>
          <p className="text-xs text-muted-foreground mt-1">
            {webhook.enabled ? "Enabled" : "Disabled"}
            {" · "}
            {webhook.hasSecret ? "Signing secret set" : "No signing secret"}
          </p>
          {webhook.createdAt && (
            <p className="text-xs text-muted-foreground mt-1">
              Created{" "}
              {formatDistanceToNow(new Date(webhook.createdAt), {
                addSuffix: true,
              })}
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
              <Button variant="outline" size="sm" onClick={() => onEdit(webhook)}>
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
      className="border rounded-xl p-0 bg-card text-foreground max-w-md w-[calc(100%-2rem)] backdrop:bg-black/50"
    >
      <form onSubmit={handleSubmit} className="p-5">
        <h3 className="text-base font-bold mb-1">
          {mode === "edit" ? "Edit webhook" : "New webhook"}
        </h3>
        <p className="text-sm text-muted-foreground mb-4">
          We POST a JSON payload to this URL when a run regresses against its
          baseline.
        </p>
        <div className="mb-3">
          <label
            htmlFor="webhook-url"
            className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1"
          >
            URL
          </label>
          <input
            id="webhook-url"
            type="url"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://hooks.example.com/dokimos"
            className="w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground"
            autoFocus
            required
          />
        </div>
        <div className="mb-3">
          <label
            htmlFor="webhook-secret"
            className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1"
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
            className="w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground"
          />
          <p className="text-xs text-muted-foreground mt-1">
            Used to sign the payload so you can verify it came from us. It is
            never displayed after saving.
          </p>
        </div>
        <div className="mb-3 flex items-center gap-2">
          <input
            id="webhook-enabled"
            type="checkbox"
            checked={enabled}
            onChange={(e) => setEnabled(e.target.checked)}
            className="h-4 w-4 rounded border"
          />
          <label htmlFor="webhook-enabled" className="text-sm">
            Enabled
          </label>
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
