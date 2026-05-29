import { useEffect } from "react";
import { useParams, useSearchParams, useNavigate } from "react-router";
import { format } from "date-fns";
import { ArrowDown, ArrowUp, Minus } from "lucide-react";
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

function caseStatus(status: string | undefined): "IMPROVED" | "REGRESSED" | "UNCHANGED" {
  const normalized = status?.toUpperCase();
  if (normalized === "IMPROVED") return "IMPROVED";
  if (normalized === "REGRESSED") return "REGRESSED";
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
    <label className="inline-flex flex-col gap-1">
      <span className="text-[10px] uppercase tracking-wide text-muted-foreground">
        {label}
      </span>
      <select
        className="h-9 min-w-56 rounded-md border border-input bg-background px-3 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
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

  const passRateDelta = summary?.passRateDelta;
  const passRateDirection =
    passRateDelta == null || passRateDelta === 0
      ? "flat"
      : passRateDelta > 0
        ? "up"
        : "down";

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Compare runs</h1>

      <div className="flex flex-wrap items-end gap-4 mb-6">
        <RunSelect
          label="baseline"
          value={baselineRunId}
          runs={runs}
          placeholder="Pick a baseline run"
          onChange={handleBaselineChange}
        />
        <span className="text-sm font-semibold text-muted-foreground pb-2">
          vs
        </span>
        <RunSelect
          label="candidate"
          value={candidateRunId ?? ""}
          runs={runs}
          onChange={handleCandidateChange}
        />
      </div>

      {!baselineRunId ? (
        <Card>
          <CardContent className="py-12 text-center">
            <p className="text-muted-foreground">
              Pick a baseline run to compare against.
            </p>
          </CardContent>
        </Card>
      ) : isLoading ? (
        <DiffSkeleton />
      ) : error ? (
        <p className="text-destructive">
          Error loading diff: {error.message}
        </p>
      ) : (
        <>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
            <Card>
              <CardContent className="pt-6">
                <p className="text-sm text-muted-foreground">Pass Rate</p>
                <p
                  className={cn("text-2xl font-bold flex items-baseline gap-1.5", {
                    "text-green-600 dark:text-green-500":
                      passRateDirection === "up",
                    "text-red-600 dark:text-red-500":
                      passRateDirection === "down",
                  })}
                >
                  {passRateDirection === "up" && (
                    <ArrowUp className="h-4 w-4" />
                  )}
                  {passRateDirection === "down" && (
                    <ArrowDown className="h-4 w-4" />
                  )}
                  {formatPct(summary?.candidatePassRate)}
                  {passRateDelta != null && passRateDelta !== 0 && (
                    <span className="text-sm font-semibold">
                      {passRateDelta > 0 ? "+" : ""}
                      {Math.round(passRateDelta * 100)}%
                    </span>
                  )}
                </p>
                <p className="text-xs text-muted-foreground mt-1">
                  was {formatPct(summary?.baselinePassRate)}
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="pt-6">
                <p className="text-sm text-muted-foreground">Improved</p>
                <p className="text-2xl font-bold text-green-600 dark:text-green-500">
                  {summary?.improvedCount ?? 0}
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="pt-6">
                <p className="text-sm text-muted-foreground">Regressed</p>
                <p className="text-2xl font-bold text-red-600 dark:text-red-500">
                  {summary?.regressedCount ?? 0}
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="pt-6">
                <p className="text-sm text-muted-foreground">Verdict</p>
                <p className="text-2xl font-bold flex items-center gap-2">
                  <span
                    className={cn("inline-block h-2.5 w-2.5 rounded-full", {
                      "bg-green-600 dark:bg-green-500":
                        summary?.significant && passRateDirection === "up",
                      "bg-red-600 dark:bg-red-500":
                        summary?.significant && passRateDirection !== "up",
                      "bg-muted-foreground": !summary?.significant,
                    })}
                  />
                  {summary?.significant ? "significant" : "ns"}
                </p>
              </CardContent>
            </Card>
          </div>

          <div className="flex items-center gap-2 mb-4">
            {STATUS_FILTERS.map((filter) => (
              <Button
                key={filter.value}
                variant={status === filter.value ? "default" : "outline"}
                size="sm"
                onClick={() => handleStatusChange(filter.value)}
              >
                {filter.label}
              </Button>
            ))}
          </div>

          {cases.length === 0 ? (
            <Card>
              <CardContent className="py-12 text-center">
                <p className="text-muted-foreground">
                  No cases match this filter.
                </p>
              </CardContent>
            </Card>
          ) : (
            <>
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="w-8"></TableHead>
                      <TableHead>Case</TableHead>
                      {evaluatorNames.map((name) => (
                        <TableHead key={name}>{name}</TableHead>
                      ))}
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {cases.map((diffCase, idx) => {
                      const cs = caseStatus(diffCase.status);
                      const evaluatorByName = new Map<string, EvaluatorDiff>();
                      for (const evaluator of diffCase.evaluators ?? []) {
                        if (evaluator.name) {
                          evaluatorByName.set(evaluator.name, evaluator);
                        }
                      }
                      return (
                        <TableRow key={diffCase.datasetItemId ?? idx}>
                          <TableCell
                            className={cn("p-0", {
                              "border-l-[3px] border-l-green-500":
                                cs === "IMPROVED",
                              "border-l-[3px] border-l-red-500":
                                cs === "REGRESSED",
                              "border-l-[3px] border-l-transparent":
                                cs === "UNCHANGED",
                            })}
                          >
                            <span className="sr-only">{cs}</span>
                            {cs === "IMPROVED" && (
                              <ArrowUp className="mx-2 h-4 w-4 text-green-600 dark:text-green-500" />
                            )}
                            {cs === "REGRESSED" && (
                              <ArrowDown className="mx-2 h-4 w-4 text-red-600 dark:text-red-500" />
                            )}
                            {cs === "UNCHANGED" && (
                              <Minus className="mx-2 h-4 w-4 text-muted-foreground" />
                            )}
                          </TableCell>
                          <TableCell
                            className={cn("max-w-xs", {
                              "text-muted-foreground": cs === "UNCHANGED",
                            })}
                          >
                            <TruncatedText
                              text={diffCase.input ?? "—"}
                              maxLength={80}
                            />
                            {diffCase.passFlip && (
                              <span className="ml-2 inline-flex items-center rounded-sm bg-red-100 px-1 py-px text-[10px] font-semibold text-red-700 dark:bg-red-950 dark:text-red-400">
                                flip
                              </span>
                            )}
                          </TableCell>
                          {evaluatorNames.map((name) => {
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
                                  <span className="text-muted-foreground">
                                    —
                                  </span>
                                )}
                              </TableCell>
                            );
                          })}
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>
              <Pagination
                currentPage={view?.cases?.number ?? 0}
                totalItems={view?.cases?.totalElements ?? 0}
                pageSize={view?.cases?.size ?? PAGE_SIZE}
                onPageChange={handlePageChange}
              />
            </>
          )}
        </>
      )}
    </div>
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
