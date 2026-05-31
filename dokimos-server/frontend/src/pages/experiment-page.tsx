import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router";
import { format } from "date-fns";
import { useListRuns, useGetTrends } from "@/lib/api/experiment-controller/experiment-controller";
import { useBreadcrumbs } from "@/lib/breadcrumb-context";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { GitCompare } from "lucide-react";
import MetricCard, { MetricGrid } from "@/components/shared/metric-card";
import TrendChart from "@/components/charts/trend-chart";
import StatusBadge from "@/components/shared/status-badge";
import PassRate from "@/components/shared/pass-rate";
import Pagination from "@/components/shared/pagination";

const PAGE_SIZE = 20;

function formatDuration(startedAt: string | undefined, completedAt: string | undefined): string {
  if (!startedAt || !completedAt) return "—";

  const start = new Date(startedAt).getTime();
  const end = new Date(completedAt).getTime();
  const seconds = Math.floor((end - start) / 1000);

  if (seconds < 60) {
    return `${seconds}s`;
  }

  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  return `${minutes}m ${remainingSeconds}s`;
}

export default function ExperimentPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { setBreadcrumbs } = useBreadcrumbs();
  const [currentPage, setCurrentPage] = useState(0);

  const { data: runsResponse, error: runsError, isLoading: runsLoading } = useListRuns(id ?? "", {
    swr: { enabled: !!id },
  });
  const runs = runsResponse?.data;

  const { data: trendsResponse, error: trendsError, isLoading: trendsLoading } = useGetTrends(
    id ?? "",
    { limit: 20 },
    { swr: { enabled: !!id } }
  );
  const trends = trendsResponse?.data;

  const isLoading = runsLoading || trendsLoading;
  const error = runsError || trendsError;

  useEffect(() => {
    if (trends?.experimentName && trends?.projectName) {
      setBreadcrumbs([
        { label: "Home", href: "/" },
        {
          label: trends.projectName,
          href: `/projects/${encodeURIComponent(trends.projectName)}`,
        },
        {
          label: trends.experimentName,
          href: `/experiments/${id}`,
        },
      ]);
    }
  }, [trends, id, setBreadcrumbs]);

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-48" />
        <MetricGrid>
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="rounded-lg border bg-card px-4 py-3.5">
              <Skeleton className="h-3 w-20" />
              <Skeleton className="mt-2 h-6 w-16" />
            </div>
          ))}
        </MetricGrid>
        <Card>
          <CardHeader>
            <CardTitle className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
              Pass-rate trend
            </CardTitle>
          </CardHeader>
          <CardContent>
            <Skeleton className="h-50 w-full" />
          </CardContent>
        </Card>
        <Card className="py-0 overflow-hidden">
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="text-[11px] uppercase tracking-wider text-muted-foreground">Date</TableHead>
                  <TableHead className="text-[11px] uppercase tracking-wider text-muted-foreground">Status</TableHead>
                  <TableHead className="text-right text-[11px] uppercase tracking-wider text-muted-foreground">Pass Rate</TableHead>
                  <TableHead className="text-right text-[11px] uppercase tracking-wider text-muted-foreground">Items</TableHead>
                  <TableHead className="text-right text-[11px] uppercase tracking-wider text-muted-foreground">Duration</TableHead>
                  <TableHead className="text-right text-[11px] uppercase tracking-wider text-muted-foreground">Compare</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {[1, 2, 3].map((i) => (
                  <TableRow key={i}>
                    <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                    <TableCell><Skeleton className="h-5 w-16" /></TableCell>
                    <TableCell><Skeleton className="h-4 w-12 ml-auto" /></TableCell>
                    <TableCell><Skeleton className="h-4 w-8 ml-auto" /></TableCell>
                    <TableCell><Skeleton className="h-4 w-16 ml-auto" /></TableCell>
                    <TableCell><Skeleton className="h-8 w-20 ml-auto" /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </Card>
      </div>
    );
  }

  if (error) {
    return (
      <div>
        <h1 className="font-mono text-2xl font-semibold mb-6">Experiment</h1>
        <Card>
          <CardContent className="py-10 text-center">
            <p className="text-destructive">Error loading experiment: {error.message}</p>
          </CardContent>
        </Card>
      </div>
    );
  }

  if (!runs) {
    return (
      <div>
        <h1 className="font-mono text-2xl font-semibold mb-6">Experiment</h1>
        <Card>
          <CardContent className="py-10 text-center">
            <p className="text-muted-foreground">Experiment not found.</p>
          </CardContent>
        </Card>
      </div>
    );
  }

  // Prepare chart data from trends. Runs that fall on the same calendar day get a
  // time-qualified label so their points do not collapse onto one X-axis tick.
  const trendRuns = (trends?.runs ?? []).filter((run) => run.passRate != null);
  const runsPerDay = new Map<string, number>();
  for (const run of trendRuns) {
    if (!run.startedAt) continue;
    const day = format(new Date(run.startedAt), "MMM d");
    runsPerDay.set(day, (runsPerDay.get(day) ?? 0) + 1);
  }
  const chartData = trendRuns.flatMap((run) => {
    if (!run.startedAt) return [];
    const day = format(new Date(run.startedAt), "MMM d");
    const sharesDay = (runsPerDay.get(day) ?? 0) > 1;
    return [
      {
        label: sharesDay
          ? format(new Date(run.startedAt), "MMM d, h:mm a")
          : day,
        value: Math.round((run.passRate ?? 0) * 100),
      },
    ];
  });

  const hasEnoughDataForChart = chartData.length >= 2;

  // Headline metrics derived from the run history.
  const ratedRuns = runs.filter((run) => run.passRate != null);
  const latestRated = ratedRuns[0];
  const previousRated = ratedRuns[1];
  const latestRate = latestRated?.passRate ?? null;
  const latestDelta =
    latestRated?.passRate != null && previousRated?.passRate != null
      ? (latestRated.passRate - previousRated.passRate) * 100
      : null;
  const bestRun = ratedRuns.reduce<typeof ratedRuns[number] | undefined>((best, run) => {
    if (best == null) return run;
    return (run.passRate ?? 0) > (best.passRate ?? 0) ? run : best;
  }, undefined);
  const latestItems = runs.find((run) => run.itemCount != null)?.itemCount;
  const durations = runs
    .map((run) => {
      if (!run.startedAt || !run.completedAt) return null;
      return new Date(run.completedAt).getTime() - new Date(run.startedAt).getTime();
    })
    .filter((d): d is number => d != null && d >= 0);
  const avgDurationMs =
    durations.length > 0 ? durations.reduce((sum, d) => sum + d, 0) / durations.length : null;
  const avgDurationLabel =
    avgDurationMs != null
      ? formatDuration(new Date(0).toISOString(), new Date(avgDurationMs).toISOString())
      : "—";

  const rateTone = (rate: number | null | undefined) =>
    rate == null
      ? "default"
      : rate < 0.5
        ? "destructive"
        : rate < 0.8
          ? "warning"
          : "success";

  return (
    <div className="space-y-6">
      <h1 className="font-mono text-2xl font-semibold">{trends?.experimentName ?? "Experiment"}</h1>

      <MetricGrid>
        <MetricCard
          label="Latest pass rate"
          value={latestRate != null ? `${(latestRate * 100).toFixed(1)}%` : "—"}
          sub={
            latestDelta != null
              ? `${latestDelta >= 0 ? "▲ +" : "▼ "}${latestDelta.toFixed(1)} vs prev`
              : "first run"
          }
          tone={rateTone(latestRate)}
          accent
        />
        <MetricCard
          label="Best run"
          value={bestRun?.passRate != null ? `${(bestRun.passRate * 100).toFixed(1)}%` : "—"}
          sub={
            bestRun?.startedAt
              ? format(new Date(bestRun.startedAt), "MMM d, h:mm a")
              : undefined
          }
        />
        <MetricCard
          label="Items / run"
          value={latestItems ?? "—"}
          sub={`${runs.length} run${runs.length === 1 ? "" : "s"} tracked`}
        />
        <MetricCard
          label="Avg duration"
          value={avgDurationLabel}
          sub={`over ${durations.length} run${durations.length === 1 ? "" : "s"}`}
        />
      </MetricGrid>

      <Card>
        <CardHeader>
          <CardTitle className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
            Pass-rate trend
          </CardTitle>
        </CardHeader>
        <CardContent>
          {hasEnoughDataForChart ? (
            <TrendChart data={chartData} />
          ) : (
            <p className="text-muted-foreground text-sm py-8 text-center">
              Not enough data for trend chart
            </p>
          )}
        </CardContent>
      </Card>

      {runs.length === 0 ? (
        <Card>
          <CardContent className="py-10 text-center">
            <p className="text-muted-foreground">
              No runs yet. Run this experiment to see results here.
            </p>
          </CardContent>
        </Card>
      ) : (
        <Card className="py-0 overflow-hidden">
          <div className="flex items-center justify-between border-b px-5 py-3">
            <span className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
              Runs
            </span>
            <span className="font-mono text-[11px] text-muted-foreground tabular-nums">
              {runs.length} run{runs.length === 1 ? "" : "s"}
            </span>
          </div>
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="text-[11px] uppercase tracking-wider text-muted-foreground">Date</TableHead>
                  <TableHead className="text-[11px] uppercase tracking-wider text-muted-foreground">Status</TableHead>
                  <TableHead className="text-right text-[11px] uppercase tracking-wider text-muted-foreground">Pass Rate</TableHead>
                  <TableHead className="text-right text-[11px] uppercase tracking-wider text-muted-foreground">Items</TableHead>
                  <TableHead className="text-right text-[11px] uppercase tracking-wider text-muted-foreground">Duration</TableHead>
                  <TableHead className="text-right text-[11px] uppercase tracking-wider text-muted-foreground">Compare</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {runs.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE).map((run, idx) => {
                  const globalIndex = currentPage * PAGE_SIZE + idx;
                  const previousRun = runs[globalIndex + 1];
                  const baselineParam = previousRun?.id
                    ? `?baselineRunId=${encodeURIComponent(previousRun.id)}`
                    : "";
                  return (
                    <TableRow
                      key={run.id}
                      className="cursor-pointer hover:bg-accent/50"
                      onClick={() => navigate(`/runs/${run.id}`)}
                    >
                      <TableCell className="font-mono whitespace-nowrap">
                        {run.startedAt && format(new Date(run.startedAt), "MMM d, h:mm a")}
                      </TableCell>
                      <TableCell>
                        {run.status && <StatusBadge status={run.status} />}
                      </TableCell>
                      <TableCell className="text-right font-mono tabular-nums">
                        <PassRate rate={run.passRate} />
                      </TableCell>
                      <TableCell className="text-right font-mono tabular-nums">{run.itemCount}</TableCell>
                      <TableCell className="text-right font-mono tabular-nums text-muted-foreground">
                        {formatDuration(run.startedAt, run.completedAt)}
                      </TableCell>
                      <TableCell className="text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={(e) => {
                            e.stopPropagation();
                            navigate(
                              `/experiments/${id}/runs/${run.id}/diff${baselineParam}`
                            );
                          }}
                        >
                          <GitCompare className="h-4 w-4" />
                          Compare
                        </Button>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>
          <div className="px-5 pb-4">
            <Pagination
              currentPage={currentPage}
              totalItems={runs.length}
              pageSize={PAGE_SIZE}
              onPageChange={setCurrentPage}
            />
          </div>
        </Card>
      )}
    </div>
  );
}
