import { Fragment, useEffect, useState } from "react";
import { Link } from "react-router";
import { ChevronDown, ChevronRight } from "lucide-react";
import { useList2 } from "@/lib/api/review-queue-controller/review-queue-controller";
import { useListProjects } from "@/lib/api/project-controller/project-controller";
import type { ReviewQueueItem } from "@/lib/api/generated.schemas";
import { useBreadcrumbs } from "@/lib/breadcrumb-context";
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
import TruncatedText from "@/components/shared/truncated-text";
import JsonDisplay from "@/components/shared/json-display";
import Pagination from "@/components/shared/pagination";
import AnnotationControls from "@/components/runs/annotation-controls";

const PAGE_SIZE = 50;

function stringify(value: unknown, fallback = ""): string {
  if (value == null) return fallback;
  if (typeof value === "string") return value;
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

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
    <div>
      <div className="flex items-center justify-between mb-2 gap-4">
        <h1 className="text-2xl font-bold">Review queue</h1>
        <select
          className="h-9 rounded-md border bg-background px-3 text-sm"
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
      <p className="text-muted-foreground mb-6">
        Run items that still need a human verdict: never annotated, or marked
        unsure. Annotating an item removes it from the queue.
      </p>

      {isLoading ? (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-8"></TableHead>
              <TableHead>Input</TableHead>
              <TableHead>Expected</TableHead>
              <TableHead>Actual</TableHead>
              <TableHead>Run</TableHead>
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
      ) : error ? (
        <p className="text-destructive">
          Error loading review queue: {error.message}
        </p>
      ) : items.length === 0 ? (
        <p className="text-muted-foreground">
          Nothing to review. Every item has a verdict.
        </p>
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
                  <TableHead>Run</TableHead>
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
                            <ChevronDown className="h-4 w-4" />
                          ) : (
                            <ChevronRight className="h-4 w-4" />
                          )}
                        </TableCell>
                        <TableCell className="max-w-xs align-top">
                          <div className="flex items-center gap-2 min-w-0">
                            {item.currentVerdict === "UNSURE" && (
                              <span className="inline-flex items-center rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground">
                                unsure
                              </span>
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
                        <TableCell
                          className="align-top"
                          onClick={(e) => e.stopPropagation()}
                        >
                          <Link
                            to={`/runs/${item.runId}`}
                            className="text-sm text-muted-foreground hover:text-foreground hover:underline"
                          >
                            {item.experimentName ?? "run"}
                          </Link>
                        </TableCell>
                      </TableRow>
                      {isExpanded && (
                        <TableRow>
                          <TableCell
                            colSpan={5 + evaluatorNames.length}
                            className="bg-muted/50"
                          >
                            <div className="p-4 space-y-4">
                              <div className="text-sm text-muted-foreground">
                                {item.projectName} ·{" "}
                                <Link
                                  to={`/runs/${item.runId}`}
                                  className="hover:text-foreground hover:underline"
                                >
                                  {item.experimentName}
                                </Link>
                              </div>
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
            currentPage={page?.number ?? 0}
            totalItems={page?.totalElements ?? 0}
            pageSize={page?.size ?? PAGE_SIZE}
            onPageChange={handlePageChange}
          />
        </>
      )}
    </div>
  );
}
