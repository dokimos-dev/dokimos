import { Check, X } from "lucide-react";
import { cn } from "@/lib/utils";

interface ScoreCellProps {
  score: number;
  success: boolean;
}

export default function ScoreCell({ score, success }: ScoreCellProps) {
  return (
    <span
      aria-label={`${score.toFixed(2)}, ${success ? "pass" : "fail"}`}
      className={cn(
        "inline-flex items-center gap-1 tabular-nums",
        success ? "text-success" : "text-destructive"
      )}
    >
      {success ? (
        <Check className="h-3.5 w-3.5" aria-hidden="true" />
      ) : (
        <X className="h-3.5 w-3.5" aria-hidden="true" />
      )}
      <span aria-hidden="true">{score.toFixed(2)}</span>
    </span>
  );
}
