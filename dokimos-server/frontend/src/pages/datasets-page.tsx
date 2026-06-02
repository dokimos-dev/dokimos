import { useEffect, useState, useRef, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router";
import { useListDatasets, useCreateDataset } from "@/lib/api/dataset-controller/dataset-controller";
import type { DatasetSummary } from "@/lib/api/generated.schemas";
import { useBreadcrumbs } from "@/lib/breadcrumb-context";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import DatasetMainPane from "@/components/datasets/dataset-main-pane";

export default function DatasetsPage() {
  const { name: routeName } = useParams<{ name: string }>();
  const navigate = useNavigate();
  const { setBreadcrumbs } = useBreadcrumbs();
  const [dialogOpen, setDialogOpen] = useState(false);

  const { data: response, error, isLoading, mutate } = useListDatasets();
  const datasets = response?.data;

  useEffect(() => {
    if (routeName) {
      setBreadcrumbs([
        { label: "Home", href: "/" },
        { label: "Datasets", href: "/datasets" },
        { label: routeName, href: `/datasets/${encodeURIComponent(routeName)}` },
      ]);
    } else {
      setBreadcrumbs([
        { label: "Home", href: "/" },
        { label: "Datasets", href: "/datasets" },
      ]);
    }
  }, [routeName, setBreadcrumbs]);

  const selectDataset = (name: string) => {
    navigate(`/datasets/${encodeURIComponent(name)}`);
  };

  const handleCreated = async (created: DatasetSummary) => {
    await mutate();
    if (created.name) {
      selectDataset(created.name);
    }
  };

  if (isLoading) {
    return (
      <div>
        <PageHeader onCreate={() => setDialogOpen(true)} />
        <div className="grid grid-cols-1 md:grid-cols-[236px_1fr] gap-6 items-start">
          <div className="rounded-lg border border-border bg-card overflow-hidden">
            <div className="px-4 pt-4 pb-3 border-b border-border">
              <span className="text-[11px] uppercase tracking-wider text-muted-foreground font-semibold">
                Datasets
              </span>
            </div>
            <div className="flex flex-col gap-0.5 p-2">
              {[1, 2, 3, 4].map((i) => (
                <div key={i} className="px-2 h-[34px] flex items-center">
                  <Skeleton className="h-3.5 w-28" />
                </div>
              ))}
            </div>
          </div>
          <div className="rounded-lg border border-border bg-card p-5">
            <Skeleton className="h-4 w-40 mb-4" />
            <Skeleton className="h-3.5 w-64 mb-6" />
            <Skeleton className="h-48 w-full" />
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div>
        <PageHeader onCreate={() => setDialogOpen(true)} />
        <div className="rounded-lg border border-border bg-card p-6">
          <p className="text-sm text-destructive">Error loading datasets: {error.message}</p>
        </div>
      </div>
    );
  }

  const list = datasets ?? [];
  const selected = routeName
    ? list.find((d) => d.name === routeName)
    : undefined;

  return (
    <div>
      <PageHeader onCreate={() => setDialogOpen(true)} />

      {/* Mobile dropdown */}
      <div className="md:hidden mb-4">
        <label
          htmlFor="dataset-select"
          className="block text-[11px] uppercase tracking-wider text-muted-foreground font-semibold mb-1.5"
        >
          Dataset
        </label>
        <select
          id="dataset-select"
          className="w-full min-h-10 border border-border rounded-md px-3 py-2 text-sm font-mono bg-card text-foreground"
          value={routeName ?? ""}
          onChange={(e) => {
            if (e.target.value) selectDataset(e.target.value);
          }}
        >
          <option value="">Select a dataset...</option>
          {list.map((d) => (
            <option key={d.id ?? d.name} value={d.name ?? ""}>
              {d.name} ({d.latestItemCount ?? 0})
            </option>
          ))}
        </select>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-[236px_1fr] gap-6 items-start">
        {/* Desktop dataset list */}
        <nav
          className="hidden md:block rounded-lg border border-border bg-card overflow-hidden"
          aria-label="Datasets"
        >
          <div className="flex items-center gap-2 px-4 pt-4 pb-3 border-b border-border">
            <span className="text-[11px] uppercase tracking-wider text-muted-foreground font-semibold">
              Datasets
            </span>
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              onClick={() => setDialogOpen(true)}
              aria-label="New dataset"
              title="New dataset"
              className="ml-auto -my-1"
            >
              <PlusIcon className="size-4" />
            </Button>
          </div>
          <div className="flex flex-col gap-0.5 p-2">
            {list.length === 0 ? (
              <div className="px-2 py-3 text-sm text-muted-foreground">No datasets yet.</div>
            ) : (
              list.map((d) => {
                const isCurrent = d.name === routeName;
                return (
                  <button
                    key={d.id ?? d.name}
                    type="button"
                    onClick={() => d.name && selectDataset(d.name)}
                    aria-current={isCurrent || undefined}
                    className={
                      "w-full flex items-center gap-2 px-2 h-[34px] text-left rounded-md border-l-2 transition-colors " +
                      (isCurrent
                        ? "bg-accent-tint border-l-primary text-foreground"
                        : "border-l-transparent text-text-2 hover:bg-accent hover:text-foreground")
                    }
                  >
                    <span className="truncate font-mono text-[12.5px]">{d.name}</span>
                    <span
                      className={
                        "ml-auto shrink-0 text-[11px] tabular-nums " +
                        (isCurrent ? "text-text-2" : "text-muted-foreground")
                      }
                    >
                      {d.latestItemCount ?? 0}
                    </span>
                  </button>
                );
              })
            )}
          </div>
        </nav>

        {/* Main pane */}
        <section aria-label={selected?.name ?? "Dataset"}>
          {routeName ? (
            <DatasetMainPane key={routeName} datasetName={routeName} />
          ) : (
            <EmptyState onCreate={() => setDialogOpen(true)} />
          )}
        </section>
      </div>

      <CreateDatasetDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        onCreated={handleCreated}
      />
    </div>
  );
}

function PlusIcon({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      <path d="M12 5v14" />
      <path d="M5 12h14" />
    </svg>
  );
}

function PageHeader({ onCreate }: { onCreate: () => void }) {
  return (
    <div className="flex flex-col gap-4 mb-6 sm:flex-row sm:items-start sm:justify-between">
      <div>
        <h1 className="text-2xl font-bold">Datasets</h1>
        <p className="font-prose text-[12.5px] text-muted-foreground mt-1 max-w-2xl">
          Versioned evaluation sets pushed from the SDK or curated from run items. Select a
          dataset to browse its examples.
        </p>
      </div>
      <Button size="sm" onClick={onCreate} className="self-start">
        <PlusIcon className="size-4" />
        New dataset
      </Button>
    </div>
  );
}

function EmptyState({ onCreate }: { onCreate: () => void }) {
  return (
    <div className="rounded-lg border border-border bg-card p-10 flex flex-col items-center text-center gap-4">
      <div className="flex size-11 items-center justify-center rounded-md border border-border bg-muted text-muted-foreground">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.5"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="size-5"
          aria-hidden="true"
        >
          <ellipse cx="12" cy="5" rx="8" ry="3" />
          <path d="M4 5v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5" />
        </svg>
      </div>
      <div>
        <h2 className="text-base font-semibold mb-1">No dataset selected</h2>
        <p className="font-prose text-muted-foreground text-[12.5px] max-w-sm">
          Pick one from the list, or create a new one to start curating golden examples.
        </p>
      </div>
      <Button size="sm" onClick={onCreate}>
        <PlusIcon className="size-4" />
        New dataset
      </Button>
    </div>
  );
}

interface CreateDatasetDialogProps {
  open: boolean;
  onClose: () => void;
  onCreated: (created: DatasetSummary) => void;
}

function CreateDatasetDialog({ open, onClose, onCreated }: CreateDatasetDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [submitError, setSubmitError] = useState<string | null>(null);

  const { trigger, isMutating } = useCreateDataset();

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
    setName("");
    setDescription("");
    setSubmitError(null);
    onClose();
  };

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!name.trim()) {
      setSubmitError("Name is required.");
      return;
    }
    setSubmitError(null);
    try {
      const res = await trigger({
        name: name.trim(),
        description: description.trim() || undefined,
      });
      if (res?.data) {
        onCreated(res.data);
        onClose();
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to create dataset.";
      setSubmitError(message);
    }
  };

  return (
    <dialog
      ref={dialogRef}
      onClose={handleClose}
      className="border border-border rounded-md p-0 bg-popover text-popover-foreground max-w-md w-[calc(100%-2rem)] shadow-xl backdrop:bg-black/50"
    >
      <form onSubmit={handleSubmit}>
        <div className="flex items-center justify-between gap-2 px-5 py-3.5 border-b border-border">
          <h3 className="text-[11px] font-semibold uppercase tracking-wider">New dataset</h3>
          <button type="button" onClick={onClose} className="grid size-6 place-items-center rounded-md text-muted-foreground hover:bg-accent hover:text-foreground" aria-label="Close"><svg viewBox="0 0 24 24" className="size-3.5" fill="none" stroke="currentColor" strokeWidth="2"><path d="M18 6 6 18M6 6l12 12"/></svg></button>
        </div>
        <div className="p-5">
          <p className="font-prose text-[12.5px] text-muted-foreground mb-4">
            Give your dataset a short, lowercase name. You can add items in the next step.
          </p>
          <div className="mb-3">
            <label
              htmlFor="ds-name"
              className="block text-[11px] font-semibold text-muted-foreground uppercase tracking-wider mb-1.5"
            >
              Name
            </label>
            <input
              id="ds-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="refund-qa"
              className="w-full border border-border rounded-md px-3 py-2 text-sm font-mono bg-background text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/50"
              autoFocus
              required
            />
          </div>
          <div className="mb-3">
            <label
              htmlFor="ds-desc"
              className="block text-[11px] font-semibold text-muted-foreground uppercase tracking-wider mb-1.5"
            >
              Description
            </label>
            <textarea
              id="ds-desc"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Optional"
              className="w-full border border-border rounded-md px-3 py-2 text-sm bg-background text-foreground min-h-16 resize-y focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/50"
            />
          </div>
          {submitError && (
            <p className="text-sm text-destructive mb-3">{submitError}</p>
          )}
        </div>
        <div className="flex justify-end gap-2 px-5 py-3.5 border-t border-border">
          <Button type="button" variant="outline" size="sm" onClick={onClose} disabled={isMutating}>
            Cancel
          </Button>
          <Button type="submit" size="sm" disabled={isMutating}>
            {isMutating ? "Creating..." : "Create dataset"}
          </Button>
        </div>
      </form>
    </dialog>
  );
}
