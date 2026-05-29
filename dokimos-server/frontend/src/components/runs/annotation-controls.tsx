import { useState } from "react";
import { useUpsert, useDelete } from "@/lib/api/annotation-controller/annotation-controller";
import {
  AnnotationRequestVerdict,
  type AnnotationView,
  type AnnotationRequestVerdict as Verdict,
} from "@/lib/api/generated.schemas";
import { Button } from "@/components/ui/button";

interface AnnotationControlsProps {
  runId: string;
  itemResultId: string;
  annotation?: AnnotationView;
  onChanged: () => void;
}

const verdictOptions: { value: Verdict; label: string }[] = [
  { value: AnnotationRequestVerdict.CORRECT, label: "Correct" },
  { value: AnnotationRequestVerdict.INCORRECT, label: "Incorrect" },
  { value: AnnotationRequestVerdict.UNSURE, label: "Unsure" },
];

function verdictLabel(verdict: Verdict): string {
  switch (verdict) {
    case AnnotationRequestVerdict.CORRECT:
      return "Correct";
    case AnnotationRequestVerdict.INCORRECT:
      return "Incorrect";
    default:
      return "Unsure";
  }
}

export default function AnnotationControls({
  runId,
  itemResultId,
  annotation,
  onChanged,
}: AnnotationControlsProps) {
  const [verdict, setVerdict] = useState<Verdict | undefined>(
    annotation?.verdict
  );
  const [note, setNote] = useState(annotation?.note ?? "");
  const [error, setError] = useState<string | null>(null);

  const { trigger: upsert, isMutating: isUpserting } = useUpsert(
    runId,
    itemResultId
  );
  const { trigger: remove, isMutating: isDeleting } = useDelete(
    runId,
    itemResultId
  );

  const isMutating = isUpserting || isDeleting;

  const handleSave = async () => {
    if (!verdict) return;
    setError(null);
    try {
      await upsert({
        verdict,
        note: note || undefined,
        overriddenExpectedOutput: annotation?.overriddenExpectedOutput,
      });
      onChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save annotation.");
    }
  };

  const handleClear = async () => {
    setError(null);
    try {
      await remove();
      setVerdict(undefined);
      setNote("");
      onChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to clear annotation.");
    }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-2">
        <h4 className="text-sm font-medium">Annotation</h4>
        {annotation?.verdict && (
          <span className="text-xs text-muted-foreground">
            Saved: {verdictLabel(annotation.verdict)}
          </span>
        )}
      </div>
      <div className="bg-background rounded-md p-3 border space-y-3">
        <div className="flex flex-wrap gap-2">
          {verdictOptions.map((option) => (
            <Button
              key={option.value}
              type="button"
              size="sm"
              variant={verdict === option.value ? "default" : "outline"}
              aria-pressed={verdict === option.value}
              onClick={() => setVerdict(option.value)}
            >
              {option.label}
            </Button>
          ))}
        </div>
        <textarea
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="Add a note (optional)"
          className="w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground min-h-16 resize-y"
        />
        <div className="flex items-center gap-2">
          <Button
            type="button"
            size="sm"
            onClick={handleSave}
            disabled={isMutating || !verdict}
          >
            {isUpserting ? "Saving..." : "Save"}
          </Button>
          {annotation && (
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={handleClear}
              disabled={isMutating}
            >
              {isDeleting ? "Clearing..." : "Clear"}
            </Button>
          )}
        </div>
        {error && <p className="text-sm text-destructive">{error}</p>}
      </div>
    </div>
  );
}
