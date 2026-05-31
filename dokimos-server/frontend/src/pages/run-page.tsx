import { Fragment, useEffect, useState } from "react";
import { useParams } from "react-router";
import { format } from "date-fns";
import { ChevronDown, ChevronRight } from "lucide-react";
import { useGetRunDetails } from "@/lib/api/run-controller/run-controller";
import type {
  ItemSummary,
  AnnotationViewVerdict,
} from "@/lib/api/generated.schemas";
import { useBreadcrumbs } from "@/lib/breadcrumb-context";
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
import PassRate from "@/components/shared/pass-rate";
import ScoreCell from "@/components/shared/score-cell";
import TruncatedText from "@/components/shared/truncated-text";
import JsonDisplay from "@/components/shared/json-display";
import Pagination from "@/components/shared/pagination";
import AnnotationControls from "@/components/runs/annotation-controls";
import PromoteDialog from "@/components/runs/promote-dialog";
import AlignmentCard from "@/components/runs/alignment-card";
import JudgeJobs from "@/components/runs/judge-jobs";
import JudgeDialog from "@/components/runs/judge-dialog";
import { RunMetricCards, ItemMetrics } from "@/components/runs/run-metrics";

function formatDuration(
  startedAt: string | undefined,
  completedAt: string | undefined
): string {
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

function stringify(value: unknown, fallback = ""): string {
  if (value == null) return fallback;
  if (typeof value === "string") return value;
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

function VerdictChip({ verdict }: { verdict: AnnotationViewVerdict }) {
  const config: Record<AnnotationViewVerdict, { label: string; className: string }> = {
    CORRECT: {
      label: "correct",
      className: "border-success/30 bg-success/10 text-success",
    },
    INCORRECT: {
      label: "incorrect",
      className: "border-destructive/30 bg-destructive/10 text-destructive",
    },
    UNSURE: {
      label: "unsure",
      className: "border-border bg-muted text-muted-foreground",
    },
  };
  const { label, className } = config[verdict];
  return (
    <span
      className={
        "inline-flex shrink-0 items-center rounded border px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wider " +
        className
      }
    >
      {label}
    </span>
  );
}

function getUniqueEvaluatorNames(items: ItemSummary[]): string[] {
  const names = new Set<string>();
  items.forEach((item) => {
    item.evalResults?.forEach((evalResult) => {
      if (evalResult.evaluatorName) {
        names.add(evalResult.evaluatorName);
      }
    });
  });
  return Array.from(names).sort();
}

export default function RunPage() {
  const { id } = useParams<{ id: string }>();
  const { setBreadcrumbs } = useBreadcrumbs();
  const [expandedRows, setExpandedRows] = useState<Set<string>>(new Set());
  const [currentPage, setCurrentPage] = useState(0);
  const [promoteItemId, setPromoteItemId] = useState<string | null>(null);
  const [judgeDialogOpen, setJudgeDialogOpen] = useState(false);

  const {
    data: response,
    error,
    isLoading,
    mutate,
  } = useGetRunDetails(
    id ?? "",
    { pageable: { page: currentPage, size: 50 } },
    { swr: { enabled: !!id } }
  );
  const run = response?.data;

  useEffect(() => {
    if (run) {
      setBreadcrumbs([
        { label: "Home", href: "/" },
        {
          label: run.projectName ?? "Project",
          href: `/projects/${encodeURIComponent(run.projectName ?? "")}`,
        },
        {
          label: run.experimentName ?? "Experiment",
          href: `/experiments/${run.experimentId}`,
        },
        {
          label: run.startedAt
            ? `Run ${format(new Date(run.startedAt), "MMM d")}`
            : "Run",
          href: `/runs/${run.id}`,
        },
      ]);
    }
  }, [run, setBreadcrumbs]);

  const toggleRow = (itemId: string) => {
    setExpandedRows((prev) => {
      const next = new Set(prev);
      if (next.has(itemId)) {
        next.delete(itemId);
      } else {
        next.add(itemId);
      }
      return next;
    });
  };

  const handlePageChange = (newPage: number) => {
    setCurrentPage(newPage);
    setExpandedRows(new Set());
  };

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-64" />
        <MetricGrid>
          {[1, 2, 3, 4].map((i) => (
            <div
              key={i}
              className="rounded-lg border bg-card px-4 py-3.5"
            >
              <Skeleton className="h-2.5 w-16" />
              <Skeleton className="mt-3 h-6 w-20" />
            </div>
          ))}
        </MetricGrid>
        <div className="overflow-hidden rounded-lg border bg-card">
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-8"></TableHead>
                  <TableHead>Input</TableHead>
                  <TableHead>Expected</TableHead>
                  <TableHead>Actual</TableHead>
                  <TableHead>Score</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {[1, 2, 3].map((i) => (
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
                    <TableCell>
                      <Skeleton className="h-4 w-32" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-4 w-12" />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-lg border bg-card p-10 text-center">
        <p className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
          Run
        </p>
        <p className="mt-2 text-sm text-destructive">
          Error loading run: {error.message}
        </p>
      </div>
    );
  }

  if (!run) {
    return (
      <div className="rounded-lg border bg-card p-10 text-center">
        <p className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
          Run
        </p>
        <p className="mt-2 text-sm text-muted-foreground">Run not found.</p>
      </div>
    );
  }

  const items = run.items?.content ?? [];
  const evaluatorNames = getUniqueEvaluatorNames(items);
  const pageNumber = run.items?.number ?? 0;
  const promoteItem =
    promoteItemId !== null
      ? items.find((item) => (item.id ?? "") === promoteItemId)
      : undefined;

  const passRateValue = run.passRate ?? 0;
  const passRateTone: "success" | "warning" | "destructive" =
    passRateValue >= 0.8
      ? "success"
      : passRateValue >= 0.5
        ? "warning"
        : "destructive";
  const failedItems = (run.totalItems ?? 0) - (run.passedItems ?? 0);

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h1 className="text-xl font-semibold tracking-tight">
            {run.startedAt
              ? `Run · ${format(new Date(run.startedAt), "MMM d, h:mm a")}`
              : "Run"}
          </h1>
          {run.experimentName && (
            <p className="mt-1 font-mono text-xs text-muted-foreground">
              {run.experimentName}
            </p>
          )}
        </div>
        <Button
          variant="outline"
          className="shrink-0"
          onClick={() => setJudgeDialogOpen(true)}
        >
          Run LLM judge
        </Button>
      </div>

      <MetricGrid>
        <MetricCard
          label="Total items"
          value={run.totalItems ?? 0}
          sub={`${evaluatorNames.length} ${
            evaluatorNames.length === 1 ? "evaluator" : "evaluators"
          }`}
          accent
        />
        <MetricCard
          label="Passed"
          value={run.passedItems ?? 0}
          sub={`${failedItems} failed`}
          tone="success"
        />
        <MetricCard
          label="Pass rate"
          value={<PassRate rate={run.passRate} />}
          tone={passRateTone}
        />
        <MetricCard
          label="Duration"
          value={formatDuration(run.startedAt, run.completedAt)}
          sub={
            run.avgLatencyMs != null
              ? `${Math.round(run.avgLatencyMs)}ms avg latency`
              : undefined
          }
        />
      </MetricGrid>

      <RunMetricCards run={run} />

      <AlignmentCard runId={run.id ?? ""} />

      <JudgeJobs runId={run.id ?? ""} />

      {items.length === 0 ? (
        <p className="text-muted-foreground">No items in this run.</p>
      ) : (
        <>
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-8"></TableHead>
                  <TableHead>Input</TableHead>
                  <TableHead>Expected</TableHead>
                  <TableHead>Actual</TableHead>
                  {evaluatorNames.map((name) => (
                    <TableHead key={name}>{name}</TableHead>
                  ))}
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((item) => {
                  const itemId = item.id ?? "";
                  const isExpanded = expandedRows.has(itemId);
                  return (
                    <Fragment key={itemId}>
                      <TableRow
                        className="cursor-pointer hover:bg-accent/50"
                        onClick={() => toggleRow(itemId)}
                      >
                        <TableCell>
                          {isExpanded ? (
                            <ChevronDown className="h-4 w-4" />
                          ) : (
                            <ChevronRight className="h-4 w-4" />
                          )}
                        </TableCell>
                        <TableCell className="max-w-xs align-top">
                          <div className="flex items-center gap-2 min-w-0">
                            {item.annotation?.verdict && (
                              <VerdictChip verdict={item.annotation.verdict} />
                            )}
                            <TruncatedText
                              text={stringify(item.input)}
                              maxLength={100}
                            />
                          </div>
                        </TableCell>
                        <TableCell className="max-w-xs align-top">
                          <TruncatedText
                            text={stringify(item.expectedOutput, "—")}
                            maxLength={80}
                          />
                        </TableCell>
                        <TableCell className="max-w-xs align-top">
                          <TruncatedText
                            text={stringify(item.actualOutput)}
                            maxLength={80}
                          />
                        </TableCell>
                        {evaluatorNames.map((name) => {
                          const evalResult = item.evalResults?.find(
                            (e) => e.evaluatorName === name
                          );
                          return (
                            <TableCell key={name}>
                              {evalResult ? (
                                <ScoreCell
                                  score={evalResult.score ?? 0}
                                  success={evalResult.success ?? false}
                                />
                              ) : (
                                "—"
                              )}
                            </TableCell>
                          );
                        })}
                      </TableRow>
                      {isExpanded && (
                        <TableRow>
                          <TableCell
                            colSpan={4 + evaluatorNames.length}
                            className="bg-muted/50"
                          >
                            <div className="p-4 space-y-4">
                              <div>
                                <h4 className="text-sm font-medium mb-2">
                                  Input
                                </h4>
                                <JsonDisplay data={item.input} />
                              </div>
                              {item.expectedOutput && (
                                <div>
                                  <h4 className="text-sm font-medium mb-2">
                                    Expected Output
                                  </h4>
                                  <JsonDisplay data={item.expectedOutput} />
                                </div>
                              )}
                              <div>
                                <h4 className="text-sm font-medium mb-2">
                                  Actual Output
                                </h4>
                                <JsonDisplay data={item.actualOutput} />
                              </div>
                              <ItemMetrics
                                tokensIn={item.tokensIn}
                                tokensOut={item.tokensOut}
                                costUsd={item.costUsd}
                                latencyMs={item.latencyMs}
                              />
                              {item.evalResults &&
                                item.evalResults.length > 0 && (
                                  <div>
                                    <h4 className="text-sm font-medium mb-2">
                                      Evaluations
                                    </h4>
                                    <div className="space-y-2">
                                      {item.evalResults.map(
                                        (evalResult, idx) => (
                                          <div
                                            key={idx}
                                            className="bg-background rounded-md p-3 border text-sm"
                                          >
                                            <div className="flex items-center gap-4 flex-wrap">
                                              <span className="font-medium">
                                                {evalResult.evaluatorName}
                                              </span>
                                              <span>
                                                Score:{" "}
                                                <ScoreCell
                                                  score={evalResult.score ?? 0}
                                                  success={
                                                    evalResult.success ?? false
                                                  }
                                                />
                                              </span>
                                              {evalResult.threshold != null && (
                                                <span className="text-muted-foreground">
                                                  Threshold:{" "}
                                                  {evalResult.threshold}
                                                </span>
                                              )}
                                              <span
                                                className={
                                                  evalResult.success
                                                    ? "text-success"
                                                    : "text-destructive"
                                                }
                                              >
                                                {evalResult.success
                                                  ? "Passed"
                                                  : "Failed"}
                                              </span>
                                            </div>
                                            {evalResult.reason && (
                                              <p className="text-muted-foreground mt-2 break-words">
                                                {evalResult.reason}
                                              </p>
                                            )}
                                          </div>
                                        )
                                      )}
                                    </div>
                                  </div>
                                )}
                              <AnnotationControls
                                key={`${itemId}-${item.annotation?.id ?? "none"}`}
                                runId={run.id ?? ""}
                                itemResultId={itemId}
                                annotation={item.annotation}
                                onChanged={() => mutate()}
                              />
                              <div>
                                <Button
                                  variant="outline"
                                  size="sm"
                                  onClick={() => setPromoteItemId(itemId)}
                                >
                                  Promote to dataset
                                </Button>
                              </div>
                            </div>
                          </TableCell>
                        </TableRow>
                      )}
                    </Fragment>
                  );
                })}
              </TableBody>
            </Table>
          </div>
          <Pagination
            currentPage={pageNumber}
            totalItems={run.items?.totalElements ?? 0}
            pageSize={run.items?.size ?? 50}
            onPageChange={handlePageChange}
          />
          {promoteItem && (
            <PromoteDialog
              key={promoteItem.id}
              open={promoteItemId !== null}
              onClose={() => setPromoteItemId(null)}
              itemResultId={promoteItem.id ?? ""}
              input={promoteItem.input}
              defaultExpected={
                promoteItem.annotation?.overriddenExpectedOutput ??
                promoteItem.expectedOutput
              }
              onPromoted={() => mutate()}
            />
          )}
        </>
      )}

      <JudgeDialog
        open={judgeDialogOpen}
        onClose={() => setJudgeDialogOpen(false)}
        runId={run.id ?? ""}
        onEnqueued={() => mutate()}
      />
    </div>
  );
}
