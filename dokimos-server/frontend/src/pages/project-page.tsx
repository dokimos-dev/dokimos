import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router";
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
      <div>
        <h1 className="text-2xl font-bold mb-6">{name}</h1>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Experiment</TableHead>
              <TableHead>Last Run</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Pass Rate</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {[1, 2, 3].map((i) => (
              <TableRow key={i}>
                <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                <TableCell><Skeleton className="h-5 w-16" /></TableCell>
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
        <h1 className="text-2xl font-bold mb-6">{name}</h1>
        <p className="text-destructive">Error loading experiments: {error.message}</p>
      </div>
    );
  }

  if (!experiments || experiments.length === 0) {
    return (
      <div>
        <h1 className="text-2xl font-bold mb-6">{name}</h1>
        <p className="text-muted-foreground">
          No experiments yet. Run an experiment to see results here.
        </p>
      </div>
    );
  }

  // Pagination logic
  const startIndex = currentPage * PAGE_SIZE;
  const endIndex = startIndex + PAGE_SIZE;
  const paginatedExperiments = experiments.slice(startIndex, endIndex);

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">{name}</h1>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Experiment</TableHead>
            <TableHead>Last Run</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Pass Rate</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {paginatedExperiments.map((experiment) => (
            <TableRow
              key={experiment.id}
              className="cursor-pointer hover:bg-accent/50"
              onClick={() => navigate(`/experiments/${experiment.id}`)}
            >
              <TableCell className="font-medium">{experiment.name}</TableCell>
              <TableCell className="text-muted-foreground">
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
              <TableCell>
                <PassRate rate={experiment.latestRun?.passRate} />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <Pagination
        currentPage={currentPage}
        totalItems={experiments.length}
        pageSize={PAGE_SIZE}
        onPageChange={setCurrentPage}
      />
    </div>
  );
}
