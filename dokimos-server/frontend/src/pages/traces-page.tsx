import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { format } from "date-fns";
import { useListTraces } from "@/lib/api/trace-controller/trace-controller";
import { useListProjects } from "@/lib/api/project-controller/project-controller";
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
import { Badge } from "@/components/ui/badge";
import MetricCard, { MetricGrid } from "@/components/shared/metric-card";
import Pagination from "@/components/shared/pagination";

const PAGE_SIZE = 50;

function formatTimestamp(createdAt: string | undefined): string {
  if (!createdAt) return "—";
  const date = new Date(createdAt);
  if (Number.isNaN(date.getTime())) return "—";
  return format(date, "MMM d, yyyy h:mm a");
}

export default function TracesPage() {
  const { setBreadcrumbs } = useBreadcrumbs();
  const [projectId, setProjectId] = useState("");
  const [currentPage, setCurrentPage] = useState(0);

  useEffect(() => {
    setBreadcrumbs([
      { label: "Home", href: "/" },
      { label: "Traces", href: "/traces" },
    ]);
  }, [setBreadcrumbs]);

  const { data: projectsResponse } = useListProjects();
  const projects = useMemo(
    () => projectsResponse?.data ?? [],
    [projectsResponse]
  );
  const projectNameById = useMemo(() => {
    const map = new Map<string, string>();
    projects.forEach((project) => {
      if (project.id) map.set(project.id, project.name ?? project.id);
    });
    return map;
  }, [projects]);

  const { data: response, error, isLoading } = useListTraces({
    projectId: projectId || undefined,
    page: currentPage,
    size: PAGE_SIZE,
  });
  const page = response?.data;
  const traces = page?.content ?? [];

  const totalTraces = page?.totalElements ?? 0;
  const spansOnPage = useMemo(
    () => traces.reduce((sum, trace) => sum + (trace.spanCount ?? 0), 0),
    [traces]
  );
  const avgSpans = traces.length ? (spansOnPage / traces.length).toFixed(1) : "—";

  const handleProjectChange = (value: string) => {
    setProjectId(value);
    setCurrentPage(0);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="font-mono text-2xl font-semibold tracking-tight">
            Traces
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            OTLP traces ingested from your instrumented applications, newest
            first. Open a trace to inspect its spans and online evaluation
            results.
          </p>
        </div>
        <select
          className="h-9 w-full rounded-md border border-border bg-card px-3 font-mono text-sm sm:w-[200px]"
          value={projectId}
          onChange={(e) => handleProjectChange(e.target.value)}
        >
          <option value="">All projects</option>
          {projects.map((project) => (
            <option key={project.id} value={project.id}>
              {project.name}
            </option>
          ))}
        </select>
      </div>

      <MetricGrid>
        <MetricCard
          label="Total traces"
          value={totalTraces.toLocaleString()}
          sub={projectId ? "in selected project" : "across all projects"}
          tone="primary"
          accent
        />
        <MetricCard
          label="On this page"
          value={traces.length.toLocaleString()}
          sub={`page ${(page?.number ?? 0) + 1}`}
        />
        <MetricCard
          label="Spans (page)"
          value={spansOnPage.toLocaleString()}
          sub="ingested spans shown"
        />
        <MetricCard
          label="Avg spans / trace"
          value={avgSpans}
          sub="on this page"
        />
      </MetricGrid>

      <div className="rounded-lg border border-border bg-card">
        <div className="flex items-center justify-between border-b border-border px-4 py-3">
          <span className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
            Recent traces
          </span>
        </div>

        {isLoading ? (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Root span</TableHead>
                  <TableHead className="text-right">Spans</TableHead>
                  <TableHead>Project</TableHead>
                  <TableHead>Received</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {[1, 2, 3].map((i) => (
                  <TableRow key={i}>
                    <TableCell>
                      <Skeleton className="h-4 w-48" />
                    </TableCell>
                    <TableCell className="text-right">
                      <Skeleton className="ml-auto h-4 w-12" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-4 w-32" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-4 w-32" />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        ) : error ? (
          <p className="px-4 py-10 text-center text-sm text-destructive">
            Error loading traces: {error.message}
          </p>
        ) : traces.length === 0 ? (
          <EmptyState
            title="No traces yet"
            description="OTLP traces from your instrumented applications will appear here once they are ingested."
          />
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Root span</TableHead>
                  <TableHead className="text-right">Spans</TableHead>
                  <TableHead>Project</TableHead>
                  <TableHead>Received</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {traces.map((trace) => {
                  const projectLabel = trace.projectId
                    ? projectNameById.get(trace.projectId) ?? trace.projectId
                    : "—";
                  return (
                    <TableRow
                      key={trace.id}
                      className="cursor-pointer hover:bg-accent/50"
                    >
                      <TableCell className="font-medium">
                        <Link
                          to={`/traces/${trace.id}`}
                          className="font-mono text-primary hover:underline"
                        >
                          {trace.rootSpanName ?? "trace"}
                        </Link>
                      </TableCell>
                      <TableCell className="text-right font-mono tabular-nums">
                        {trace.spanCount ?? 0}
                      </TableCell>
                      <TableCell>
                        {trace.projectId ? (
                          <Badge
                            variant="outline"
                            className="font-mono font-normal"
                          >
                            {projectLabel}
                          </Badge>
                        ) : (
                          <span className="text-muted-foreground">—</span>
                        )}
                      </TableCell>
                      <TableCell className="font-mono text-muted-foreground">
                        {formatTimestamp(trace.createdAt)}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>
        )}

        {!isLoading && !error && traces.length > 0 && (
          <div className="border-t border-border px-4 py-2">
            <Pagination
              currentPage={page?.number ?? 0}
              totalItems={page?.totalElements ?? 0}
              pageSize={page?.size ?? PAGE_SIZE}
              onPageChange={setCurrentPage}
            />
          </div>
        )}
      </div>
    </div>
  );
}
