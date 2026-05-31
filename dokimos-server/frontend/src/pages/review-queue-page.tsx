import { Fragment, useEffect, useState } from "react";
import { Link } from "react-router";
import { ChevronDown, ChevronRight } from "lucide-react";
import { useList2 } from "@/lib/api/review-queue-controller/review-queue-controller";
import { useListProjects } from "@/lib/api/project-controller/project-controller";
import type { ReviewQueueItem } from "@/lib/api/generated.schemas";
import { useBreadcrumbs } from "@/lib/breadcrumb-context";
import EmptyState from "@/components/shared/empty-state";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";
import ScoreCell from "@/components/shared/score-cell";
import ValuePreview from "@/components/shared/value-preview";
import JsonDisplay from "@/components/shared/json-display";
import Pagination from "@/components/shared/pagination";
import AnnotationControls from "@/components/runs/annotation-controls";

const PAGE_SIZE = 50;

function evaluatorNamesOf(items: ReviewQueueItem[]): string[] {
  const names = new Set<string>();
  items.forEach((item) =>
    item.evalResults?.forEach((evalResult) => {
      if (evalResult.evaluatorName) names.add(evalResult.evaluatorName);
    })
  );
  return Array.from(names).sort();
}

export default function ReviewQueuePage() {
  const { setBreadcrumbs } = useBreadcrumbs();
  const [projectName, setProjectName] = useState("");
  const [currentPage, setCurrentPage] = useState(0);
  const [expandedRows, setExpandedRows] = useState<Set<string>>(new Set());

  useEffect(() => {
    setBreadcrumbs([
      { label: "Home", href: "/" },
      { label: "Review queue", href: "/review-queue" },
    ]);
  }, [setBreadcrumbs]);

  const { data: projectsResponse } = useListProjects();
  const projects = projectsResponse?.data ?? [];

  const {
    data: response,
    error,
    isLoading,
    mutate,
  } = useList2({
    projectName: projectName || undefined,
    pageable: { page: currentPage, size: PAGE_SIZE },
  });
  const page = response?.data;
  const items = page?.content ?? [];
  const evaluatorNames = evaluatorNamesOf(items);

  const toggleRow = (itemId: string) => {
    setExpandedRows((prev) => {
      const next = new Set(prev);
      if (next.has(itemId)) next.delete(itemId);
      else next.add(itemId);
      return next;
    });
  };

  const handlePageChange = (newPage: number) => {
    setCurrentPage(newPage);
    setExpandedRows(new Set());
  };

  const handleProjectChange = (value: string) => {
    setProjectName(value);
    setCurrentPage(0);
    setExpandedRows(new Set());
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-1">
          <h1 className="font-mono text-2xl font-semibold tracking-tight">
            Review queue
          </h1>
          <p className="max-w-2xl text-sm text-muted-foreground">
            Run items that still need a human verdict: never annotated, or
            marked unsure. Annotating an item removes it from the queue.
          </p>
        </div>
        <select
          className="h-9 shrink-0 rounded-md border border-border bg-card px-3 font-mono text-sm"
          value={projectName}
          onChange={(e) => handleProjectChange(e.target.value)}
        >
          <option value="">All projects</option>
          {projects.map((project) => (
            <option key={project.id} value={project.name}>
              {project.name}
            </option>
          ))}
        </select>
      </div>

      {isLoading ? (
        <div className="overflow-hidden rounded-lg border border-border bg-card">
          <div className="flex items-center border-b border-border px-4 py-3">
            <span className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
              Items awaiting review
            </span>
          </div>
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-8"></TableHead>
                  <TableHead className="text-[11px] uppercase tracking-wider">
                    Input
                  </TableHead>
                  <TableHead className="text-[11px] uppercase tracking-wider">
                    Expected
                  </TableHead>
                  <TableHead className="text-[11px] uppercase tracking-wider">
                    Actual
                  </TableHead>
                  <TableHead className="text-[11px] uppercase tracking-wider">
                    Run
                  </TableHead>
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
                      <Skeleton className="h-4 w-24" />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </div>
      ) : error ? (
        <p className="text-destructive">
          Error loading review queue: {error.message}
        </p>
      ) : items.length === 0 ? (
        <EmptyState
          title="All caught up"
          description="Nothing to review. Every item has a verdict."
        />
      ) : (
        <div className="overflow-hidden rounded-lg border border-border bg-card">
          <div className="flex items-center justify-between gap-3 border-b border-border px-4 py-3">
            <span className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
              Items awaiting review
            </span>
            <span className="font-mono text-[11px] tabular-nums text-muted-foreground">
              {page?.totalElements ?? items.length} pending
            </span>
          </div>
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-8"></TableHead>
                  <TableHead className="text-[11px] uppercase tracking-wider">
                    Input
                  </TableHead>
                  <TableHead className="text-[11px] uppercase tracking-wider">
                    Expected
                  </TableHead>
                  <TableHead className="text-[11px] uppercase tracking-wider">
                    Actual
                  </TableHead>
                  {evaluatorNames.map((name) => (
                    <TableHead
                      key={name}
                      className="text-right text-[11px] uppercase tracking-wider"
                    >
                      {name}
                    </TableHead>
                  ))}
                  <TableHead className="text-[11px] uppercase tracking-wider">
                    Run
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((item) => {
                  const itemId = item.itemId ?? "";
                  const isExpanded = expandedRows.has(itemId);
                  return (
                    <Fragment key={itemId}>
                      <TableRow
                        className="cursor-pointer hover:bg-accent/50"
                        onClick={() => toggleRow(itemId)}
                      >
                        <TableCell>
                          {isExpanded ? (
                            <ChevronDown className="h-4 w-4 text-muted-foreground" />
                          ) : (
                            <ChevronRight className="h-4 w-4 text-muted-foreground" />
                          )}
                        </TableCell>
                        <TableCell className="max-w-[280px] align-top">
                          <div className="flex items-center gap-2 min-w-0">
                            {item.currentVerdict === "UNSURE" && (
                              <span className="inline-flex shrink-0 items-center rounded-md border border-border px-1.5 py-0.5 font-mono text-[10px] uppercase tracking-wider text-muted-foreground">
                                unsure
                              </span>
                            )}
                            <ValuePreview value={item.input} className="min-w-0 flex-1" />
                          </div>
                        </TableCell>
                        <TableCell className="max-w-[240px] align-top text-muted-foreground">
                          <ValuePreview value={item.expectedOutput} />
                        </TableCell>
                        <TableCell className="max-w-[240px] align-top text-muted-foreground">
                          <ValuePreview value={item.actualOutput} />
                        </TableCell>
                        {evaluatorNames.map((name) => {
                          const evalResult = item.evalResults?.find(
                            (e) => e.evaluatorName === name
                          );
                          return (
                            <TableCell
                              key={name}
                              className="text-right align-top"
                            >
                              {evalResult ? (
                                <ScoreCell
                                  score={evalResult.score ?? 0}
                                  success={evalResult.success ?? false}
                                />
                              ) : (
                                <span className="text-muted-foreground">—</span>
                              )}
                            </TableCell>
                          );
                        })}
                        <TableCell
                          className="align-top"
                          onClick={(e) => e.stopPropagation()}
                        >
                          <Link
                            to={`/runs/${item.runId}`}
                            className="font-mono text-sm text-muted-foreground hover:text-primary hover:underline"
                          >
                            {item.experimentName ?? "run"}
                          </Link>
                        </TableCell>
                      </TableRow>
                      {isExpanded && (
                        <TableRow>
                          <TableCell
                            colSpan={5 + evaluatorNames.length}
                            className="bg-muted/40 p-0"
                          >
                            <div className="space-y-5 border-t border-border p-5">
                              <div className="flex flex-wrap items-center gap-2 text-sm">
                                <span className="font-mono text-muted-foreground">
                                  {item.projectName}
                                </span>
                                <span className="text-muted-foreground">/</span>
                                <Link
                                  to={`/runs/${item.runId}`}
                                  className="font-mono text-muted-foreground hover:text-primary hover:underline"
                                >
                                  {item.experimentName}
                                </Link>
                              </div>
                              <div className="grid gap-4 md:grid-cols-3">
                                <div className="space-y-2">
                                  <div className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
                                    Input
                                  </div>
                                  <JsonDisplay data={item.input} />
                                </div>
                                {item.expectedOutput && (
                                  <div className="space-y-2">
                                    <div className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
                                      Expected output
                                    </div>
                                    <JsonDisplay data={item.expectedOutput} />
                                  </div>
                                )}
                                <div className="space-y-2">
                                  <div className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
                                    Actual output
                                  </div>
                                  <JsonDisplay data={item.actualOutput} />
                                </div>
                              </div>
                              <div className="border-t border-border pt-5">
                                <AnnotationControls
                                  key={`${itemId}-${item.currentVerdict ?? "none"}`}
                                  runId={item.runId ?? ""}
                                  itemResultId={itemId}
                                  annotation={
                                    item.currentVerdict
                                      ? { verdict: item.currentVerdict }
                                      : undefined
                                  }
                                  onChanged={() => mutate()}
                                />
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
          <div className="border-t border-border px-4 py-3">
            <Pagination
              currentPage={page?.number ?? 0}
              totalItems={page?.totalElements ?? 0}
              pageSize={page?.size ?? PAGE_SIZE}
              onPageChange={handlePageChange}
            />
          </div>
        </div>
      )}
    </div>
  );
}
