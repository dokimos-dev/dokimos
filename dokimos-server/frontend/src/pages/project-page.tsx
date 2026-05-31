import { useEffect, useState } from "react";
import { useParams, useNavigate, Link } from "react-router";
import { formatDistanceToNow } from "date-fns";
import { useListExperiments } from "@/lib/api/project-controller/project-controller";
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
import StatusBadge from "@/components/shared/status-badge";
import PassRate from "@/components/shared/pass-rate";
import Pagination from "@/components/shared/pagination";

const PAGE_SIZE = 20;

const TH = "text-[11px] font-medium uppercase tracking-wider text-muted-foreground";

export default function ProjectPage() {
  const { name } = useParams<{ name: string }>();
  const navigate = useNavigate();
  const { setBreadcrumbs } = useBreadcrumbs();
  const [currentPage, setCurrentPage] = useState(0);

  const { data: response, error, isLoading } = useListExperiments(name ?? "", {
    swr: { enabled: !!name },
  });
  const experiments = response?.data;

  useEffect(() => {
    if (name) {
      setBreadcrumbs([
        { label: "Home", href: "/" },
        { label: name, href: `/projects/${encodeURIComponent(name)}` },
      ]);
    }
  }, [name, setBreadcrumbs]);

  if (isLoading) {
    return (
      <div className="space-y-6">
        <ProjectHeader name={name} />
        <div className="overflow-hidden rounded-lg border bg-card">
          <div className="flex items-center justify-between border-b border-border px-4 py-3">
            <span className={TH}>Experiments</span>
          </div>
          <div className="w-full overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow className="border-border hover:bg-transparent">
                  <TableHead className={TH}>Experiment</TableHead>
                  <TableHead className={TH}>Last Run</TableHead>
                  <TableHead className={TH}>Status</TableHead>
                  <TableHead className={`${TH} text-right`}>Pass Rate</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {[1, 2, 3].map((i) => (
                  <TableRow key={i} className="border-border">
                    <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                    <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                    <TableCell><Skeleton className="h-5 w-16" /></TableCell>
                    <TableCell><Skeleton className="ml-auto h-4 w-12" /></TableCell>
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
      <div className="space-y-6">
        <ProjectHeader name={name} />
        <div className="rounded-lg border bg-card p-8 text-center">
          <p className="font-mono text-sm text-destructive">
            Error loading experiments: {error.message}
          </p>
        </div>
      </div>
    );
  }

  if (!experiments || experiments.length === 0) {
    return (
      <div className="space-y-6">
        <ProjectHeader name={name} />
        <div className="rounded-lg border bg-card p-12 text-center">
          <div className="text-sm font-semibold">No experiments yet</div>
          <p className="mt-1.5 text-sm text-muted-foreground">
            Run an experiment to see results here.
          </p>
        </div>
      </div>
    );
  }

  // Pagination logic
  const startIndex = currentPage * PAGE_SIZE;
  const endIndex = startIndex + PAGE_SIZE;
  const paginatedExperiments = experiments.slice(startIndex, endIndex);

  return (
    <div className="space-y-6">
      <ProjectHeader name={name} count={experiments.length} />
      <div className="overflow-hidden rounded-lg border bg-card">
        <div className="flex items-center justify-between border-b border-border px-4 py-3">
          <span className={TH}>Experiments</span>
          <span className="font-mono text-[11px] tabular-nums text-muted-foreground">
            {experiments.length} total
          </span>
        </div>
        <div className="w-full overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow className="border-border hover:bg-transparent">
                <TableHead className={TH}>Experiment</TableHead>
                <TableHead className={TH}>Last Run</TableHead>
                <TableHead className={TH}>Status</TableHead>
                <TableHead className={`${TH} text-right`}>Pass Rate</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {paginatedExperiments.map((experiment) => (
                <TableRow
                  key={experiment.id}
                  className="cursor-pointer border-border hover:bg-accent/50"
                  onClick={() => navigate(`/experiments/${experiment.id}`)}
                >
                  <TableCell className="font-mono font-medium text-primary">
                    {experiment.name}
                  </TableCell>
                  <TableCell className="font-mono text-muted-foreground tabular-nums">
                    {experiment.latestRun?.startedAt
                      ? formatDistanceToNow(new Date(experiment.latestRun.startedAt), { addSuffix: true })
                      : "—"}
                  </TableCell>
                  <TableCell>
                    {experiment.latestRun?.status ? (
                      <StatusBadge status={experiment.latestRun.status} />
                    ) : (
                      "—"
                    )}
                  </TableCell>
                  <TableCell className="text-right font-mono tabular-nums">
                    <PassRate rate={experiment.latestRun?.passRate} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
        <div className="border-t border-border px-4 py-2">
          <Pagination
            currentPage={currentPage}
            totalItems={experiments.length}
            pageSize={PAGE_SIZE}
            onPageChange={setCurrentPage}
          />
        </div>
      </div>
    </div>
  );
}

function ProjectHeader({ name, count }: { name?: string; count?: number }) {
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
      <div>
        <h1 className="font-mono text-2xl font-bold tracking-tight">{name}</h1>
        {count != null && (
          <p className="mt-1 text-sm text-muted-foreground">
            <span className="font-mono tabular-nums">{count}</span>{" "}
            {count === 1 ? "experiment" : "experiments"}
          </p>
        )}
      </div>
      {name && (
        <Link
          to={`/projects/${encodeURIComponent(name)}/alerts`}
          className="inline-flex items-center gap-1.5 self-start rounded-md border border-border px-3 py-1.5 text-[11px] font-medium uppercase tracking-wider text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
        >
          Alert webhooks
        </Link>
      )}
    </div>
  );
}
