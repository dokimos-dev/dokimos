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
        <h1 className="text-2xl font-bold mb-6">Datasets</h1>
        <div className="grid grid-cols-1 md:grid-cols-[236px_1fr] gap-6">
          <div className="rounded-xl border bg-card">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="px-3 py-3 border-b last:border-b-0">
                <Skeleton className="h-4 w-32" />
              </div>
            ))}
          </div>
          <div>
            <Skeleton className="h-6 w-40 mb-4" />
            <Skeleton className="h-4 w-64 mb-6" />
            <Skeleton className="h-48 w-full" />
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div>
        <h1 className="text-2xl font-bold mb-6">Datasets</h1>
        <p className="text-destructive">Error loading datasets: {error.message}</p>
      </div>
    );
  }

  const list = datasets ?? [];
  const selected = routeName
    ? list.find((d) => d.name === routeName)
    : undefined;

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Datasets</h1>

      {/* Mobile dropdown */}
      <div className="md:hidden mb-4">
        <label htmlFor="dataset-select" className="block text-xs text-muted-foreground mb-1">
          Dataset
        </label>
        <select
          id="dataset-select"
          className="w-full min-h-10 border rounded-md px-3 py-2 text-sm bg-card text-foreground"
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
        <nav className="hidden md:block rounded-xl border bg-card overflow-hidden" aria-label="Datasets">
          {list.length === 0 ? (
            <div className="px-3 py-4 text-sm text-muted-foreground">No datasets yet.</div>
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
                    "w-full flex items-center justify-between gap-2 px-3 py-3 text-sm text-left border-b last:border-b-0 hover:bg-accent/50 transition-colors " +
                    (isCurrent ? "bg-accent font-semibold" : "")
                  }
                >
                  <span className="truncate">{d.name}</span>
                  <span className="text-xs text-muted-foreground shrink-0">
                    {d.latestItemCount ?? 0}
                  </span>
                </button>
              );
            })
          )}
          <button
            type="button"
            onClick={() => setDialogOpen(true)}
            className="w-full px-3 py-3 text-sm text-left text-muted-foreground hover:text-foreground hover:bg-accent/30 transition-colors"
          >
            + New dataset
          </button>
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

function EmptyState({ onCreate }: { onCreate: () => void }) {
  return (
    <div className="rounded-xl border bg-card p-10 text-center">
      <h2 className="text-lg font-semibold mb-2">No dataset selected</h2>
      <p className="text-muted-foreground text-sm mb-6">
        Pick one from the list, or create a new one to start curating golden examples.
      </p>
      <Button onClick={onCreate}>+ New dataset</Button>
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
      className="border rounded-xl p-0 bg-card text-foreground max-w-md w-[calc(100%-2rem)] backdrop:bg-black/50"
    >
      <form onSubmit={handleSubmit} className="p-5">
        <h3 className="text-base font-bold mb-1">New dataset</h3>
        <p className="text-sm text-muted-foreground mb-4">
          Give your dataset a short, lowercase name. You can add items in the next step.
        </p>
        <div className="mb-3">
          <label
            htmlFor="ds-name"
            className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1"
          >
            Name
          </label>
          <input
            id="ds-name"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="refund-qa"
            className="w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground"
            autoFocus
            required
          />
        </div>
        <div className="mb-3">
          <label
            htmlFor="ds-desc"
            className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1"
          >
            Description
          </label>
          <textarea
            id="ds-desc"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Optional"
            className="w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground min-h-16 resize-y"
          />
        </div>
        {submitError && (
          <p className="text-sm text-destructive mb-3">{submitError}</p>
        )}
        <div className="flex justify-end gap-2 mt-4">
          <Button type="button" variant="outline" onClick={onClose} disabled={isMutating}>
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
