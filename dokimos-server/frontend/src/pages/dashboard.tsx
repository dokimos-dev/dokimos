import { useEffect } from "react";
import { useNavigate } from "react-router";
import useSWR from "swr";
import { formatDistanceToNow } from "date-fns";
import { fetcher, apiUrl, type ProjectSummary } from "@/lib/api";
import { useBreadcrumbs } from "@/lib/breadcrumb-context";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

export default function Dashboard() {
  const navigate = useNavigate();
  const { setBreadcrumbs } = useBreadcrumbs();

  const { data: projects, error, isLoading } = useSWR<ProjectSummary[]>(
    apiUrl("/projects"),
    fetcher
  );

  useEffect(() => {
    setBreadcrumbs([]);
  }, [setBreadcrumbs]);

  if (isLoading) {
    return (
      <div>
        <h1 className="text-2xl font-bold mb-6">Projects</h1>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {[1, 2, 3].map((i) => (
            <Card key={i}>
              <CardHeader>
                <Skeleton className="h-6 w-32" />
              </CardHeader>
              <CardContent className="space-y-2">
                <Skeleton className="h-4 w-24" />
                <Skeleton className="h-4 w-28" />
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div>
        <h1 className="text-2xl font-bold mb-6">Projects</h1>
        <p className="text-destructive">Error loading projects: {error.message}</p>
      </div>
    );
  }

  if (!projects || projects.length === 0) {
    return (
      <div>
        <h1 className="text-2xl font-bold mb-6">Projects</h1>
        <p className="text-muted-foreground">
          No projects yet. Run an experiment with a reporter configured to get started.
        </p>
      </div>
    );
  }

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Projects</h1>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {projects.map((project) => (
          <Card
            key={project.id}
            className="cursor-pointer hover:bg-accent/50 transition-colors"
            onClick={() => navigate(`/projects/${encodeURIComponent(project.name)}`)}
          >
            <CardHeader>
              <CardTitle>{project.name}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-1 text-sm text-muted-foreground">
              <p>{project.experimentCount} experiment{project.experimentCount !== 1 ? "s" : ""}</p>
              <p>
                Created{" "}
                {formatDistanceToNow(new Date(project.createdAt), { addSuffix: true })}
              </p>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
