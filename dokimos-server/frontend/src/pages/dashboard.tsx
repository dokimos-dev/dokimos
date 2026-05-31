import { useEffect } from "react";
import { useNavigate } from "react-router";
import { formatDistanceToNow } from "date-fns";
import { useListProjects } from "@/lib/api/project-controller/project-controller";
import { useBreadcrumbs } from "@/lib/breadcrumb-context";
import MetricCard, { MetricGrid } from "@/components/shared/metric-card";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

function PageHead() {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
      <div>
        <h1 className="font-mono text-[20px] font-semibold tracking-tight">Projects</h1>
        <p className="mt-1 max-w-prose font-prose text-[13px] text-muted-foreground">
          Every project that has reported a run or ingested a trace. Open one to inspect its
          experiments, runs, and traces.
        </p>
      </div>
    </div>
  );
}

export default function Dashboard() {
  const navigate = useNavigate();
  const { setBreadcrumbs } = useBreadcrumbs();

  const { data: response, error, isLoading } = useListProjects();
  const projects = response?.data;

  useEffect(() => {
    setBreadcrumbs([]);
  }, [setBreadcrumbs]);

  if (isLoading) {
    return (
      <div>
        <PageHead />
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <Card key={i} className="p-4">
              <div className="flex items-center justify-between">
                <Skeleton className="h-4 w-32" />
                <Skeleton className="h-2.5 w-2.5 rounded-full" />
              </div>
              <Skeleton className="mt-3 h-3 w-40" />
            </Card>
          ))}
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div>
        <PageHead />
        <Card className="flex flex-col items-center gap-3 px-6 py-14 text-center">
          <div className="font-mono text-[13px] font-semibold">Couldn't load projects</div>
          <p className="max-w-prose font-prose text-[13px] text-destructive">
            Error loading projects: {error.message}
          </p>
        </Card>
      </div>
    );
  }

  if (!projects || projects.length === 0) {
    return (
      <div>
        <PageHead />
        <Card className="flex flex-col items-center gap-3 px-6 py-14 text-center">
          <div className="font-mono text-[13px] font-semibold">No projects yet</div>
          <p className="max-w-prose font-prose text-[13px] text-muted-foreground">
            Run an experiment with a reporter configured to get started.
          </p>
        </Card>
      </div>
    );
  }

  return (
    <div>
      <PageHead />

      <div className="mb-6">
        <MetricGrid className="lg:grid-cols-3">
          <MetricCard
            label="Projects"
            value={projects.length}
            sub={`${projects.reduce((n, p) => n + (p.experimentCount ?? 0), 0)} experiments`}
            tone="primary"
            accent
          />
          <MetricCard
            label="Experiments"
            value={projects.reduce((n, p) => n + (p.experimentCount ?? 0), 0)}
            sub="across all projects"
          />
          <MetricCard
            label="Active project"
            value={projects[0]?.name ?? "—"}
            sub="most recently created"
          />
        </MetricGrid>
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {projects.map((project) => (
          <Card
            key={project.id}
            className="group cursor-pointer rounded-lg border bg-card p-4 transition-colors hover:bg-accent"
            onClick={() => navigate(`/projects/${encodeURIComponent(project.name ?? "")}`)}
          >
            <div className="flex items-center gap-2">
              <span className="grow truncate font-mono text-[13px] font-semibold text-foreground transition-colors group-hover:text-primary">
                {project.name}
              </span>
              <span className="h-2 w-2 shrink-0 rounded-full bg-success" aria-hidden="true" />
            </div>
            <div className="mt-2 flex items-center gap-2 font-mono text-[11px] uppercase tracking-wider text-muted-foreground">
              <span className="tabular-nums">
                {project.experimentCount} experiment{project.experimentCount !== 1 ? "s" : ""}
              </span>
              {project.createdAt && (
                <>
                  <span className="text-border">·</span>
                  <span className="normal-case tracking-normal">
                    {formatDistanceToNow(new Date(project.createdAt), { addSuffix: true })}
                  </span>
                </>
              )}
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}
