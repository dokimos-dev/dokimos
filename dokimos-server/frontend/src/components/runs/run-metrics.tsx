import { Card, CardContent } from "@/components/ui/card";
import type { RunDetails } from "@/lib/api/generated.schemas";

function formatTokens(value: number): string {
  return value.toLocaleString();
}

function formatCost(value: number): string {
  return `$${value.toFixed(4)}`;
}

function formatLatency(value: number): string {
  return `${Math.round(value)}ms`;
}

interface RunMetricCardsProps {
  run: RunDetails;
}

export function RunMetricCards({ run }: RunMetricCardsProps) {
  const hasTokens = run.totalTokensIn != null || run.totalTokensOut != null;
  const hasCost = run.totalCostUsd != null;
  const hasLatency = run.avgLatencyMs != null;
  const showCoverage =
    run.pricedItemCount != null &&
    run.tokenizedItemCount != null &&
    run.pricedItemCount < run.tokenizedItemCount;

  if (!hasTokens && !hasCost && !hasLatency) {
    return null;
  }

  return (
    <div className="grid grid-cols-2 md:grid-cols-3 gap-4 mb-6">
      {hasTokens && (
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm text-muted-foreground">Total Tokens</p>
            <p className="text-2xl font-bold tabular-nums">
              {formatTokens((run.totalTokensIn ?? 0) + (run.totalTokensOut ?? 0))}
            </p>
            <p className="text-xs text-muted-foreground mt-1">
              {formatTokens(run.totalTokensIn ?? 0)} in /{" "}
              {formatTokens(run.totalTokensOut ?? 0)} out
            </p>
          </CardContent>
        </Card>
      )}
      {hasCost && (
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm text-muted-foreground">Total Cost</p>
            <p className="text-2xl font-bold tabular-nums">
              {formatCost(run.totalCostUsd ?? 0)}
            </p>
            {showCoverage && (
              <p className="text-xs text-muted-foreground mt-1">
                {run.pricedItemCount}/{run.tokenizedItemCount} items priced
              </p>
            )}
          </CardContent>
        </Card>
      )}
      {hasLatency && (
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm text-muted-foreground">Avg Latency</p>
            <p className="text-2xl font-bold tabular-nums">
              {formatLatency(run.avgLatencyMs ?? 0)}
            </p>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

interface ItemMetricsProps {
  tokensIn?: number;
  tokensOut?: number;
  costUsd?: number;
  latencyMs?: number;
}

export function ItemMetrics({
  tokensIn,
  tokensOut,
  costUsd,
  latencyMs,
}: ItemMetricsProps) {
  const parts: { label: string; value: string }[] = [];
  if (tokensIn != null) {
    parts.push({ label: "Tokens in", value: formatTokens(tokensIn) });
  }
  if (tokensOut != null) {
    parts.push({ label: "Tokens out", value: formatTokens(tokensOut) });
  }
  if (costUsd != null) {
    parts.push({ label: "Cost", value: formatCost(costUsd) });
  }
  if (latencyMs != null) {
    parts.push({ label: "Latency", value: formatLatency(latencyMs) });
  }

  if (parts.length === 0) {
    return null;
  }

  return (
    <div>
      <h4 className="text-sm font-medium mb-2">Metrics</h4>
      <div className="flex items-center gap-4 flex-wrap text-sm">
        {parts.map((part) => (
          <span key={part.label} className="text-muted-foreground">
            {part.label}:{" "}
            <span className="text-foreground tabular-nums">{part.value}</span>
          </span>
        ))}
      </div>
    </div>
  );
}
