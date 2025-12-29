import { Fragment, useEffect, useState } from "react";
import { useParams } from "react-router";
import useSWR from "swr";
import { format } from "date-fns";
import { ChevronDown, ChevronRight } from "lucide-react";
import { fetcher, apiUrl, type RunDetails, type RunItemDetails } from "@/lib/api";
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
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import PassRate from "@/components/shared/pass-rate";
import ScoreCell from "@/components/shared/score-cell";
import TruncatedText from "@/components/shared/truncated-text";
import JsonDisplay from "@/components/shared/json-display";

function formatDuration(startedAt: string, completedAt: string | null): string {
  if (!completedAt) return "—";

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

function getUniqueEvaluatorNames(items: RunItemDetails[]): string[] {
  const names = new Set<string>();
  items.forEach((item) => {
    item.evaluations.forEach((evalResult) => {
      names.add(evalResult.evaluatorName);
    });
  });
  return Array.from(names).sort();
}

export default function RunPage() {
  const { id } = useParams<{ id: string }>();
  const { setBreadcrumbs } = useBreadcrumbs();
  const [expandedRows, setExpandedRows] = useState<Set<number>>(new Set());
  const [currentPage, setCurrentPage] = useState(0);

  const { data: run, error, isLoading } = useSWR<RunDetails>(
    id ? apiUrl(`/runs/${id}?page=${currentPage}`) : null,
    fetcher
  );

  useEffect(() => {
    if (run) {
      setBreadcrumbs([
        { label: "Home", href: "/" },
        {
          label: run.projectName,
          href: `/projects/${encodeURIComponent(run.projectName)}`,
        },
        {
          label: run.experimentName,
          href: `/experiments/${run.experimentId}`,
        },
        {
          label: `Run ${format(new Date(run.startedAt), "MMM d")}`,
          href: `/runs/${run.id}`,
        },
      ]);
    }
  }, [run, setBreadcrumbs]);

  const toggleRow = (itemId: number) => {
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
                <TableCell><Skeleton className="h-4 w-4" /></TableCell>
                <TableCell><Skeleton className="h-4 w-48" /></TableCell>
                <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                <TableCell><Skeleton className="h-4 w-12" /></TableCell>
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

  const evaluatorNames = getUniqueEvaluatorNames(run.items.items);
  const { items, page, totalPages } = run.items;

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">
        Run {format(new Date(run.startedAt), "MMM d, h:mm a")}
      </h1>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm text-muted-foreground">Total Items</p>
            <p className="text-2xl font-bold">{run.itemCount}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm text-muted-foreground">Passed</p>
            <p className="text-2xl font-bold text-green-500">{run.passedCount}</p>
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
                const isExpanded = expandedRows.has(item.id);
                return (
                  <Fragment key={item.id}>
                    <TableRow
                      className="cursor-pointer hover:bg-accent/50"
                      onClick={() => toggleRow(item.id)}
                    >
                      <TableCell>
                        {isExpanded ? (
                          <ChevronDown className="h-4 w-4" />
                        ) : (
                          <ChevronRight className="h-4 w-4" />
                        )}
                      </TableCell>
                      <TableCell>
                        <TruncatedText text={item.input} maxLength={100} />
                      </TableCell>
                      <TableCell>
                        <TruncatedText text={item.expected ?? "—"} maxLength={80} />
                      </TableCell>
                      <TableCell>
                        <TruncatedText text={item.output} maxLength={80} />
                      </TableCell>
                      {evaluatorNames.map((name) => {
                        const evalResult = item.evaluations.find(
                          (e) => e.evaluatorName === name
                        );
                        return (
                          <TableCell key={name}>
                            {evalResult ? (
                              <ScoreCell
                                score={evalResult.score}
                                success={evalResult.success}
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
                              <h4 className="text-sm font-medium mb-2">Input</h4>
                              <JsonDisplay data={item.input} />
                            </div>
                            {item.expected && (
                              <div>
                                <h4 className="text-sm font-medium mb-2">Expected Output</h4>
                                <JsonDisplay data={item.expected} />
                              </div>
                            )}
                            <div>
                              <h4 className="text-sm font-medium mb-2">Actual Output</h4>
                              <JsonDisplay data={item.output} />
                            </div>
                            <div>
                              <h4 className="text-sm font-medium mb-2">Evaluations</h4>
                              <div className="space-y-2">
                                {item.evaluations.map((evalResult, idx) => (
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
                                          score={evalResult.score}
                                          success={evalResult.success}
                                        />
                                      </span>
                                      {evalResult.threshold !== null && (
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
                                        {evalResult.success ? "Passed" : "Failed"}
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
                          </div>
                        </TableCell>
                      </TableRow>
                    )}
                  </Fragment>
                );
              })}
            </TableBody>
          </Table>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between mt-4">
              <p className="text-sm text-muted-foreground">
                Page {page + 1} of {totalPages}
              </p>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => handlePageChange(page - 1)}
                  disabled={page === 0}
                >
                  Previous
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => handlePageChange(page + 1)}
                  disabled={page >= totalPages - 1}
                >
                  Next
                </Button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
