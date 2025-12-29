import { Fragment, useEffect, useState } from "react";
import { useParams } from "react-router";
import { format } from "date-fns";
import { ChevronDown, ChevronRight } from "lucide-react";
import { useGetRunDetails } from "@/lib/api/run-controller/run-controller";
import type { ItemSummary } from "@/lib/api/generated.schemas";
import { useBreadcrumbs } from "@/lib/breadcrumb-context";
import { Card, CardContent } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";
import PassRate from "@/components/shared/pass-rate";
import ScoreCell from "@/components/shared/score-cell";
import TruncatedText from "@/components/shared/truncated-text";
import JsonDisplay from "@/components/shared/json-display";
import Pagination from "@/components/shared/pagination";

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

  const {
    data: response,
    error,
    isLoading,
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
      <div>
        <Skeleton className="h-8 w-48 mb-6" />
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
    );
  }

  if (error) {
    return (
      <div>
        <h1 className="text-2xl font-bold mb-6">Run</h1>
        <p className="text-destructive">Error loading run: {error.message}</p>
      </div>
    );
  }

  if (!run) {
    return (
      <div>
        <h1 className="text-2xl font-bold mb-6">Run</h1>
        <p className="text-muted-foreground">Run not found.</p>
      </div>
    );
  }

  const items = run.items?.content ?? [];
  const evaluatorNames = getUniqueEvaluatorNames(items);
  const pageNumber = run.items?.number ?? 0;

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">
        {run.startedAt
          ? `Run ${format(new Date(run.startedAt), "MMM d, h:mm a")}`
          : "Run"}
      </h1>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm text-muted-foreground">Total Items</p>
            <p className="text-2xl font-bold">{run.totalItems}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm text-muted-foreground">Passed</p>
            <p className="text-2xl font-bold text-green-500">
              {run.passedItems}
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm text-muted-foreground">Pass Rate</p>
            <p className="text-2xl font-bold">
              <PassRate rate={run.passRate} />
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm text-muted-foreground">Duration</p>
            <p className="text-2xl font-bold">
              {formatDuration(run.startedAt, run.completedAt)}
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Items Table */}
      {items.length === 0 ? (
        <p className="text-muted-foreground">No items in this run.</p>
      ) : (
        <>
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
                      <TableCell>
                        <TruncatedText
                          text={item.input ?? ""}
                          maxLength={100}
                        />
                      </TableCell>
                      <TableCell>
                        <TruncatedText
                          text={item.expectedOutput ?? "—"}
                          maxLength={80}
                        />
                      </TableCell>
                      <TableCell>
                        <TruncatedText
                          text={item.actualOutput ?? ""}
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
                              <JsonDisplay data={item.input ?? ""} />
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
                              <JsonDisplay data={item.actualOutput ?? ""} />
                            </div>
                            {item.evalResults &&
                              item.evalResults.length > 0 && (
                                <div>
                                  <h4 className="text-sm font-medium mb-2">
                                    Evaluations
                                  </h4>
                                  <div className="space-y-2">
                                    {item.evalResults.map((evalResult, idx) => (
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
                                              Threshold: {evalResult.threshold}
                                            </span>
                                          )}
                                          <span
                                            className={
                                              evalResult.success
                                                ? "text-green-500"
                                                : "text-red-500"
                                            }
                                          >
                                            {evalResult.success
                                              ? "Passed"
                                              : "Failed"}
                                          </span>
                                        </div>
                                        {evalResult.reason && (
                                          <p className="text-muted-foreground mt-2">
                                            {evalResult.reason}
                                          </p>
                                        )}
                                      </div>
                                    ))}
                                  </div>
                                </div>
                              )}
                          </div>
                        </TableCell>
                      </TableRow>
                    )}
                  </Fragment>
                );
              })}
            </TableBody>
          </Table>
          <Pagination
            currentPage={pageNumber}
            totalItems={run.items?.totalElements ?? 0}
            pageSize={run.items?.size ?? 50}
            onPageChange={handlePageChange}
          />
        </>
      )}
    </div>
  );
}
