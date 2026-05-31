import { useEffect, type ReactNode } from "react";
import { useParams, useSearchParams, useNavigate } from "react-router";
import { format } from "date-fns";
import {
  ArrowDown,
  ArrowRight,
  ArrowUp,
  CheckCircle2,
  Info,
  Layers,
  Minus,
} from "lucide-react";
import { useDiff } from "@/lib/api/diff-controller/diff-controller";
import {
  useListRuns,
  useGetTrends,
} from "@/lib/api/experiment-controller/experiment-controller";
import type {
  DiffCase,
  EvaluatorDiff,
  RunSummary,
} from "@/lib/api/generated.schemas";
import { useBreadcrumbs } from "@/lib/breadcrumb-context";
import { cn } from "@/lib/utils";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";
import MetricCard, { MetricGrid } from "@/components/shared/metric-card";
import DeltaCell from "@/components/shared/delta-cell";
import TruncatedText from "@/components/shared/truncated-text";
import Pagination from "@/components/shared/pagination";

const PAGE_SIZE = 50;

const STATUS_FILTERS = [
  { value: "ALL", label: "All" },
  { value: "CHANGED", label: "Changed" },
  { value: "REGRESSED", label: "Regressed" },
  { value: "IMPROVED", label: "Improved" },
] as const;

function runLabel(run: RunSummary): string {
  const shortId = run.id ? run.id.slice(0, 8) : "unknown";
  const when = run.startedAt
    ? format(new Date(run.startedAt), "MMM d, h:mm a")
    : "no date";
  const status = run.status ? ` (${run.status})` : "";
  return `${shortId} · ${when}${status}`;
}

function formatPct(rate: number | undefined): string {
  if (rate == null) return "n/a";
  return `${Math.round(rate * 100)}%`;
}

type CaseStatus =
  | "IMPROVED"
  | "REGRESSED"
  | "UNCHANGED"
  | "ADDED"
  | "REMOVED";

function caseStatus(status: string | undefined): CaseStatus {
  const normalized = status?.toUpperCase();
  if (normalized === "IMPROVED") return "IMPROVED";
  if (normalized === "REGRESSED") return "REGRESSED";
  if (normalized === "ADDED") return "ADDED";
  if (normalized === "REMOVED") return "REMOVED";
  return "UNCHANGED";
}

/**
 * Derives the union of evaluator names across the page, preserving the order
 * in which they first appear so a stable column matrix can be rendered.
 */
function deriveEvaluatorNames(cases: DiffCase[]): string[] {
  const names: string[] = [];
  const seen = new Set<string>();
  for (const c of cases) {
    for (const evaluator of c.evaluators ?? []) {
      const name = evaluator.name;
      if (name && !seen.has(name)) {
        seen.add(name);
        names.push(name);
      }
    }
  }
  return names;
}

interface RunSelectProps {
  label: string;
  value: string;
  runs: RunSummary[];
  placeholder?: string;
  onChange: (runId: string) => void;
}

function RunSelect({ label, value, runs, placeholder, onChange }: RunSelectProps) {
  return (
    <label className="inline-flex flex-col gap-1.5">
      <span className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
        {label}
      </span>
      <select
        className="h-9 min-w-56 rounded-md border border-border bg-card px-3 font-mono text-[13px] tabular-nums focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        value={value}
        onChange={(e) => onChange(e.target.value)}
      >
        {placeholder && <option value="">{placeholder}</option>}
        {runs.map((run) => (
          <option key={run.id} value={run.id}>
            {runLabel(run)}
          </option>
        ))}
      </select>
    </label>
  );
}

export default function RunDiffPage() {
  const { experimentId, candidateRunId } = useParams<{
    experimentId: string;
    candidateRunId: string;
  }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const { setBreadcrumbs } = useBreadcrumbs();

  const baselineRunId = searchParams.get("baselineRunId") ?? "";
  const status = searchParams.get("status") ?? "ALL";
  const page = Number.parseInt(searchParams.get("page") ?? "0", 10) || 0;

  const { data: runsResponse } = useListRuns(experimentId ?? "", {
    swr: { enabled: !!experimentId },
  });
  const runs = runsResponse?.data ?? [];

  const { data: trendsResponse } = useGetTrends(
    experimentId ?? "",
    undefined,
    { swr: { enabled: !!experimentId } }
  );
  const trends = trendsResponse?.data;

  const {
    data: diffResponse,
    error,
    isLoading,
  } = useDiff(
    experimentId ?? "",
    candidateRunId ?? "",
    {
      baselineRunId,
      status,
      pageable: { page, size: PAGE_SIZE },
    },
    { swr: { enabled: !!baselineRunId } }
  );
  const view = diffResponse?.data;

  useEffect(() => {
    setBreadcrumbs([
      { label: "Home", href: "/" },
      ...(trends?.projectName
        ? [
            {
              label: trends.projectName,
              href: `/projects/${encodeURIComponent(trends.projectName)}`,
            },
          ]
        : []),
      {
        label: trends?.experimentName ?? "Experiment",
        href: `/experiments/${experimentId}`,
      },
      {
        label: "Compare",
        href: `/experiments/${experimentId}/runs/${candidateRunId}/diff`,
      },
    ]);
  }, [trends, experimentId, candidateRunId, setBreadcrumbs]);

  const updateParams = (patch: Record<string, string | null>) => {
    const next = new URLSearchParams(searchParams);
    for (const [key, val] of Object.entries(patch)) {
      if (val == null || val === "") {
        next.delete(key);
      } else {
        next.set(key, val);
      }
    }
    setSearchParams(next);
  };

  const handleBaselineChange = (runId: string) => {
    updateParams({ baselineRunId: runId, page: null });
  };

  const handleCandidateChange = (runId: string) => {
    if (!runId) return;
    const query = new URLSearchParams();
    if (baselineRunId) query.set("baselineRunId", baselineRunId);
    if (status !== "ALL") query.set("status", status);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    navigate(`/experiments/${experimentId}/runs/${runId}/diff${suffix}`);
  };

  const handleStatusChange = (value: string) => {
    updateParams({ status: value === "ALL" ? null : value, page: null });
  };

  const handlePageChange = (newPage: number) => {
    updateParams({ page: newPage === 0 ? null : String(newPage) });
  };

  const summary = view?.summary;
  const cases = view?.cases?.content ?? [];
  const evaluatorNames = deriveEvaluatorNames(cases);

  const sharedCount =
    (summary?.improvedCount ?? 0) +
    (summary?.regressedCount ?? 0) +
    (summary?.unchangedCount ?? 0);
  const presenceOnlyCount =
    (summary?.addedCount ?? 0) + (summary?.removedCount ?? 0);
  // Summary counts are unfiltered, so this holds regardless of the active status
  // filter: the runs produced cases but none are comparable across both sides.
  const noSharedCases = sharedCount === 0 && presenceOnlyCount > 0;

  const passRateDelta = summary?.passRateDelta;
  const passRateDirection =
    passRateDelta == null || passRateDelta === 0
      ? "flat"
      : passRateDelta > 0
        ? "up"
        : "down";

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Compare runs</h1>
          <p className="mt-1 text-[12px] text-muted-foreground">
            Per-case delta of every evaluator score across two runs.
          </p>
        </div>
        <div className="flex flex-wrap items-end gap-3">
          <RunSelect
            label="Baseline"
            value={baselineRunId}
            runs={runs}
            placeholder="Pick a baseline run"
            onChange={handleBaselineChange}
          />
          <span className="pb-2.5 text-muted-foreground">
            <ArrowRight className="h-4 w-4" />
          </span>
          <RunSelect
            label="Candidate"
            value={candidateRunId ?? ""}
            runs={runs}
            onChange={handleCandidateChange}
          />
        </div>
      </div>

      {!baselineRunId ? (
        <Card className="border-border bg-card">
          <CardContent className="flex flex-col items-center gap-3 py-14 text-center">
            <Layers className="h-7 w-7 text-muted-foreground" />
            <p className="text-[13px] text-muted-foreground">
              Pick a baseline run to compare against.
            </p>
          </CardContent>
        </Card>
      ) : isLoading ? (
        <DiffSkeleton />
      ) : error ? (
        <Card className="border-destructive/40 bg-card">
          <CardContent className="flex items-start gap-2 py-4 text-[13px] text-destructive">
            <Info className="mt-0.5 h-4 w-4 shrink-0" />
            <span>Error loading diff: {error.message}</span>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-5">
          <MetricGrid>
            <MetricCard
              label="Pass rate"
              accent
              tone={
                passRateDirection === "up"
                  ? "success"
                  : passRateDirection === "down"
                    ? "destructive"
                    : "default"
              }
              value={
                <span className="inline-flex items-baseline gap-1.5">
                  {passRateDirection === "up" && (
                    <ArrowUp className="h-4 w-4 self-center" />
                  )}
                  {passRateDirection === "down" && (
                    <ArrowDown className="h-4 w-4 self-center" />
                  )}
                  {formatPct(summary?.candidatePassRate)}
                  {passRateDelta != null && passRateDelta !== 0 && (
                    <span className="text-sm font-semibold">
                      {passRateDelta > 0 ? "+" : ""}
                      {Math.round(passRateDelta * 100)}%
                    </span>
                  )}
                </span>
              }
              sub={`was ${formatPct(summary?.baselinePassRate)}`}
            />
            <MetricCard
              label="Improved"
              tone="success"
              value={summary?.improvedCount ?? 0}
              sub="cases gained score"
            />
            <MetricCard
              label="Regressed"
              tone="destructive"
              value={summary?.regressedCount ?? 0}
              sub="cases lost score"
            />
            <MetricCard
              label="Verdict"
              value={
                <span className="inline-flex items-center gap-2">
                  <span
                    className={cn("inline-block h-2.5 w-2.5 rounded-full", {
                      "bg-success":
                        summary?.significant && passRateDirection === "up",
                      "bg-destructive":
                        summary?.significant && passRateDirection !== "up",
                      "bg-muted-foreground": !summary?.significant,
                    })}
                  />
                  {summary?.significant ? "significant" : "ns"}
                </span>
              }
              sub={summary?.significant ? "p < 0.05" : "not significant"}
            />
          </MetricGrid>

          {summary?.pairing === "positional" && (
            <div className="flex items-start gap-2 rounded-md border border-warning/40 bg-warn-tint px-3 py-2.5 text-[12.5px] text-foreground">
              <Info className="mt-0.5 h-4 w-4 shrink-0 text-warning" />
              <span>
                Item-level diff needs both runs on one dataset version. These
                runs are paired positionally, so per-case matches may not line
                up.
              </span>
            </div>
          )}

          {summary && summary.regressedCount === 0 && sharedCount > 0 && (
              <div className="flex items-start gap-2 rounded-md border border-success/40 bg-pass-tint px-3 py-2.5 text-[12.5px] text-foreground">
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-success" />
                <span>
                  <b className="font-semibold">No significant regressions.</b>
                  {(summary.improvedCount ?? 0) > 0 &&
                    ` ${summary.improvedCount} case${summary.improvedCount === 1 ? "" : "s"} improved.`}
                </span>
              </div>
            )}

          {noSharedCases ? (
            <Card className="border-border bg-card">
              <CardContent className="flex flex-col items-center gap-3 py-14 text-center">
                <Layers className="h-7 w-7 text-muted-foreground" />
                <p className="text-[13px] text-muted-foreground">
                  These runs share no comparable cases, likely because they ran
                  on different dataset versions.
                </p>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => handleBaselineChange("")}
                >
                  Choose a different baseline
                </Button>
              </CardContent>
            </Card>
          ) : (
            <Card className="overflow-hidden border-border bg-card">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-4 py-3">
                <span className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
                  Per-case delta
                </span>
                <div className="flex flex-wrap items-center gap-1.5">
                  {STATUS_FILTERS.map((filter) => {
                    const active = status === filter.value;
                    return (
                      <button
                        key={filter.value}
                        onClick={() => handleStatusChange(filter.value)}
                        className={cn(
                          "rounded-md border px-2.5 py-1 text-[12px] font-medium transition-colors",
                          active
                            ? "border-primary bg-primary text-primary-foreground"
                            : "border-border bg-card text-muted-foreground hover:bg-accent hover:text-foreground",
                        )}
                      >
                        {filter.label}
                      </button>
                    );
                  })}
                </div>
              </div>

              {cases.length === 0 ? (
                <div className="py-14 text-center text-[13px] text-muted-foreground">
                  No cases match this filter.
                </div>
              ) : (
                <>
                  <div className="overflow-x-auto">
                    <DiffTable cases={cases} evaluatorNames={evaluatorNames} />
                  </div>
                  <div className="border-t border-border px-4">
                    <Pagination
                      currentPage={view?.cases?.number ?? 0}
                      totalItems={view?.cases?.totalElements ?? 0}
                      pageSize={view?.cases?.size ?? PAGE_SIZE}
                      onPageChange={handlePageChange}
                    />
                  </div>
                </>
              )}
            </Card>
          )}
        </div>
      )}
    </div>
  );
}

const STICKY_STATUS = "sticky left-0 bg-background";
const STICKY_CASE = "sticky left-8 bg-background";

const GROUP_LABELS: Partial<Record<CaseStatus, string>> = {
  ADDED: "New cases (candidate only)",
  REMOVED: "Dropped cases (baseline only)",
};

interface DiffTableProps {
  cases: DiffCase[];
  evaluatorNames: string[];
}

function DiffTable({ cases, evaluatorNames }: DiffTableProps) {
  const totalColumns = evaluatorNames.length + 2;
  const rows: ReactNode[] = [];
  let prevStatus: CaseStatus | null = null;

  cases.forEach((diffCase, idx) => {
    const cs = caseStatus(diffCase.status);
    const key = `${cs}-${diffCase.datasetItemId ?? diffCase.index ?? idx}`;

    if (cs !== prevStatus && GROUP_LABELS[cs]) {
      rows.push(
        <TableRow key={`group-${cs}-${idx}`} className="hover:bg-transparent">
          <TableCell
            colSpan={totalColumns}
            className="bg-muted/40 py-1.5 text-[10px] font-semibold uppercase tracking-wide text-muted-foreground"
          >
            {GROUP_LABELS[cs]}
          </TableCell>
        </TableRow>
      );
    }
    prevStatus = cs;

    const isPresenceOnly = cs === "ADDED" || cs === "REMOVED";
    const evaluatorByName = new Map<string, EvaluatorDiff>();
    for (const evaluator of diffCase.evaluators ?? []) {
      if (evaluator.name) {
        evaluatorByName.set(evaluator.name, evaluator);
      }
    }

    rows.push(
      <TableRow key={key}>
        <TableCell
          className={cn(STICKY_STATUS, "z-10 w-8 p-0", {
            "border-l-[3px] border-l-success": cs === "IMPROVED",
            "border-l-[3px] border-l-destructive": cs === "REGRESSED",
            "border-l-[3px] border-l-transparent":
              cs === "UNCHANGED" || isPresenceOnly,
          })}
        >
          <span className="sr-only">{cs}</span>
          {cs === "IMPROVED" && (
            <ArrowUp className="mx-2 h-4 w-4 text-success" />
          )}
          {cs === "REGRESSED" && (
            <ArrowDown className="mx-2 h-4 w-4 text-destructive" />
          )}
          {(cs === "UNCHANGED" || isPresenceOnly) && (
            <Minus className="mx-2 h-4 w-4 text-muted-foreground" />
          )}
        </TableCell>
        <TableCell
          className={cn(STICKY_CASE, "z-10 max-w-xs", {
            "text-muted-foreground": cs === "UNCHANGED" || isPresenceOnly,
          })}
        >
          <TruncatedText text={diffCase.input ?? "—"} maxLength={80} />
          {cs === "ADDED" && (
            <span className="ml-2 inline-flex items-center rounded-sm bg-muted px-1 py-px text-[10px] font-semibold text-muted-foreground">
              new
            </span>
          )}
          {cs === "REMOVED" && (
            <span className="ml-2 inline-flex items-center rounded-sm bg-muted px-1 py-px text-[10px] font-semibold text-muted-foreground">
              dropped
            </span>
          )}
          {diffCase.passFlip && (
            <span className="ml-2 inline-flex items-center rounded-sm bg-destructive/15 px-1 py-px text-[10px] font-semibold text-destructive">
              flip
            </span>
          )}
        </TableCell>
        {isPresenceOnly && evaluatorNames.length > 0 ? (
          <TableCell
            colSpan={evaluatorNames.length}
            className="text-sm text-muted-foreground"
          >
            {cs === "ADDED"
              ? "Present only in the candidate run."
              : "Present only in the baseline run."}
          </TableCell>
        ) : (
          evaluatorNames.map((name) => {
            const evaluator = evaluatorByName.get(name);
            return (
              <TableCell key={name}>
                {evaluator ? (
                  <DeltaCell
                    baseline={evaluator.baselineMean}
                    candidate={evaluator.candidateMean}
                    delta={evaluator.delta}
                    status={evaluator.status}
                    significant={evaluator.significant}
                  />
                ) : (
                  <span className="text-muted-foreground">—</span>
                )}
              </TableCell>
            );
          })
        )}
      </TableRow>
    );
  });

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className={cn(STICKY_STATUS, "z-20 w-8 p-0")}></TableHead>
          <TableHead className={cn(STICKY_CASE, "z-20")}>Case</TableHead>
          {evaluatorNames.map((name) => (
            <TableHead key={name}>{name}</TableHead>
          ))}
        </TableRow>
      </TableHeader>
      <TableBody>{rows}</TableBody>
    </Table>
  );
}

function DiffSkeleton() {
  return (
    <div>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
        {[1, 2, 3, 4].map((i) => (
          <Card key={i}>
            <CardContent className="pt-6">
              <Skeleton className="h-4 w-20 mb-2" />
              <Skeleton className="h-8 w-16" />
            </CardContent>
          </Card>
        ))}
      </div>
      <div className="flex gap-2 mb-4">
        {[1, 2, 3, 4].map((i) => (
          <Skeleton key={i} className="h-8 w-20" />
        ))}
      </div>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-8"></TableHead>
            <TableHead>Case</TableHead>
            <TableHead>Evaluators</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {[1, 2, 3, 4, 5].map((i) => (
            <TableRow key={i}>
              <TableCell>
                <Skeleton className="h-4 w-4" />
              </TableCell>
              <TableCell>
                <Skeleton className="h-4 w-48" />
              </TableCell>
              <TableCell>
                <Skeleton className="h-4 w-32" />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
