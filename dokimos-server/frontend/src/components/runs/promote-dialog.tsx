import { useEffect, useRef, useState, type FormEvent } from "react";
import { useListDatasets, usePromote } from "@/lib/api/dataset-controller/dataset-controller";
import type { PromoteItemOverriddenExpectedOutput } from "@/lib/api/generated.schemas";
import { Button } from "@/components/ui/button";
import JsonDisplay from "@/components/shared/json-display";

interface PromoteDialogProps {
  open: boolean;
  onClose: () => void;
  itemResultId: string;
  input: unknown;
  defaultExpected: unknown;
  onPromoted: () => void;
}

export default function PromoteDialog({
  open,
  onClose,
  itemResultId,
  input,
  defaultExpected,
  onPromoted,
}: PromoteDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [datasetName, setDatasetName] = useState("");
  const [description, setDescription] = useState("");
  const [expectedText, setExpectedText] = useState(() =>
    JSON.stringify(defaultExpected ?? null, null, 2)
  );
  const [submitError, setSubmitError] = useState<string | null>(null);

  const { data: datasetsResponse } = useListDatasets();
  const datasets = datasetsResponse?.data ?? [];

  const { trigger, isMutating } = usePromote();

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
    setDatasetName("");
    setDescription("");
    setExpectedText("");
    setSubmitError(null);
    onClose();
  };

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!datasetName) {
      setSubmitError("Target dataset is required.");
      return;
    }

    let parsedExpected: unknown;
    try {
      parsedExpected = JSON.parse(expectedText);
    } catch {
      setSubmitError("Expected output must be valid JSON.");
      return;
    }

    // The server stores expected output as a JSON object (a key/value map), so the
    // edited value must be an object regardless of the input's shape.
    const parsedIsObject =
      typeof parsedExpected === "object" &&
      parsedExpected !== null &&
      !Array.isArray(parsedExpected);
    if (!parsedIsObject) {
      setSubmitError("Expected output must be a JSON object.");
      return;
    }

    setSubmitError(null);
    try {
      await trigger({
        datasetName,
        description: description || undefined,
        items: [
          {
            itemResultId,
            overriddenExpectedOutput:
              parsedExpected as PromoteItemOverriddenExpectedOutput,
          },
        ],
      });
      onPromoted();
      onClose();
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Failed to promote item.";
      setSubmitError(message);
    }
  };

  return (
    <dialog
      ref={dialogRef}
      onClose={handleClose}
      className="border rounded-xl p-0 bg-card text-foreground max-w-lg w-[calc(100%-2rem)] backdrop:bg-black/50"
    >
      <form onSubmit={handleSubmit} className="p-5">
        <h3 className="text-base font-bold mb-1">Promote to dataset</h3>
        <p className="text-sm text-muted-foreground mb-4">
          Add this item as a golden example to an existing dataset.
        </p>

        <div className="mb-3">
          <label
            htmlFor="promote-dataset"
            className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1"
          >
            Target dataset
          </label>
          <select
            id="promote-dataset"
            value={datasetName}
            onChange={(e) => setDatasetName(e.target.value)}
            className="w-full min-h-10 border rounded-md px-3 py-2 text-sm bg-background text-foreground"
            required
          >
            <option value="">Select a dataset...</option>
            {datasets.map((d) => (
              <option key={d.id ?? d.name} value={d.name ?? ""}>
                {d.name}
              </option>
            ))}
          </select>
        </div>

        <div className="mb-3">
          <label
            htmlFor="promote-description"
            className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1"
          >
            Description
          </label>
          <input
            id="promote-description"
            type="text"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Optional"
            className="w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground"
          />
        </div>

        <div className="mb-3">
          <span className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1">
            Input
          </span>
          <JsonDisplay data={input as object | string | null | undefined} />
        </div>

        <div className="mb-3">
          <label
            htmlFor="promote-expected"
            className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1"
          >
            Expected output
          </label>
          <textarea
            id="promote-expected"
            value={expectedText}
            onChange={(e) => setExpectedText(e.target.value)}
            className="w-full border rounded-md px-3 py-2 text-sm font-mono bg-background text-foreground min-h-32 resize-y"
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
          <Button type="submit" disabled={isMutating || !datasetName}>
            {isMutating ? "Promoting..." : "Promote"}
          </Button>
        </div>
      </form>
    </dialog>
  );
}
