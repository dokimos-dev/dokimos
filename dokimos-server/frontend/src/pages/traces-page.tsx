import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { format } from "date-fns";
import { useListTraces } from "@/lib/api/trace-controller/trace-controller";
import { useListProjects } from "@/lib/api/project-controller/project-controller";
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

  const handleProjectChange = (value: string) => {
    setProjectId(value);
    setCurrentPage(0);
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-2 gap-4">
        <h1 className="text-2xl font-bold">Traces</h1>
        <select
          className="h-9 rounded-md border bg-background px-3 text-sm"
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
      <p className="text-muted-foreground mb-6">
        OTLP traces ingested from your instrumented applications, newest first.
        Open a trace to inspect its spans and online evaluation results.
      </p>

      {isLoading ? (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Root span</TableHead>
              <TableHead>Spans</TableHead>
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
                <TableCell>
                  <Skeleton className="h-4 w-12" />
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
      ) : error ? (
        <p className="text-destructive">Error loading traces: {error.message}</p>
      ) : traces.length === 0 ? (
        <p className="text-muted-foreground">No traces have been ingested yet.</p>
      ) : (
        <>
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Root span</TableHead>
                  <TableHead>Spans</TableHead>
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
                          className="hover:underline"
                        >
                          {trace.rootSpanName ?? "trace"}
                        </Link>
                      </TableCell>
                      <TableCell className="tabular-nums">
                        {trace.spanCount ?? 0}
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {projectLabel}
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {formatTimestamp(trace.createdAt)}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>
          <Pagination
            currentPage={page?.number ?? 0}
            totalItems={page?.totalElements ?? 0}
            pageSize={page?.size ?? PAGE_SIZE}
            onPageChange={setCurrentPage}
          />
        </>
      )}
    </div>
  );
}
