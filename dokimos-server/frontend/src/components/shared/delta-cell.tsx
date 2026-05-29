import { cn } from "@/lib/utils";

interface DeltaCellProps {
  baseline?: number;
  candidate?: number;
  delta?: number;
  status?: string;
  significant?: boolean;
}

function formatMean(value: number | undefined): string {
  return value == null ? "n/a" : value.toFixed(2);
}

/**
 * Renders an evaluator mean change as "0.82 -> 0.61" with the candidate value
 * emphasized. Color is applied only when the change is statistically
 * significant: green for improvements, red for regressions. Non-significant
 * changes and unchanged cases render muted.
 */
export default function DeltaCell({
  baseline,
  candidate,
  delta,
  status,
  significant,
}: DeltaCellProps) {
  const normalizedStatus = status?.toUpperCase();
  const isImproved = normalizedStatus === "IMPROVED";
  const isRegressed = normalizedStatus === "REGRESSED";

  const colored = significant === true && (isImproved || isRegressed);

  return (
    <span className="inline-flex items-baseline gap-1.5 font-mono tabular-nums">
      <span className="text-muted-foreground">{formatMean(baseline)}</span>
      <span className="text-muted-foreground">&rarr;</span>
      <span
        className={cn("font-semibold", {
          "text-green-600 dark:text-green-500": colored && isImproved,
          "text-red-600 dark:text-red-500": colored && isRegressed,
          "text-foreground": !colored,
        })}
      >
        {formatMean(candidate)}
      </span>
      {delta != null && delta !== 0 && (
        <span className="text-muted-foreground text-xs">
          ({delta > 0 ? "+" : ""}
          {delta.toFixed(2)})
        </span>
      )}
      {significant === true && (
        <span
          className={cn(
            "ml-0.5 inline-flex items-center rounded-sm px-1 py-px text-[10px] font-semibold leading-none",
            {
              "bg-green-100 text-green-700 dark:bg-green-950 dark:text-green-400":
                isImproved,
              "bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-400":
                isRegressed,
              "bg-muted text-muted-foreground": !isImproved && !isRegressed,
            }
          )}
          title="statistically significant"
        >
          sig
        </span>
      )}
    </span>
  );
}
