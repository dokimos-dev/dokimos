import { useEffect } from "react";
import { useParams, useNavigate } from "react-router";
import useSWR from "swr";
import { formatDistanceToNow } from "date-fns";
import { fetcher, apiUrl, type Experiment } from "@/lib/api";
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

export default function ProjectPage() {
  const { name } = useParams<{ name: string }>();
  const navigate = useNavigate();
  const { setBreadcrumbs } = useBreadcrumbs();

  const { data: experiments, error, isLoading } = useSWR<Experiment[]>(
    name ? apiUrl(`/projects/${encodeURIComponent(name)}/experiments`) : null,
    fetcher
  );

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
              <TableHead>Items</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {[1, 2, 3].map((i) => (
              <TableRow key={i}>
                <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                <TableCell><Skeleton className="h-5 w-16" /></TableCell>
                <TableCell><Skeleton className="h-4 w-12" /></TableCell>
                <TableCell><Skeleton className="h-4 w-8" /></TableCell>
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
            <TableHead>Items</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {experiments.map((experiment) => (
            <TableRow
              key={experiment.id}
              className="cursor-pointer hover:bg-accent/50"
              onClick={() => navigate(`/experiments/${experiment.id}`)}
            >
              <TableCell className="font-medium">{experiment.name}</TableCell>
              <TableCell className="text-muted-foreground">
                {experiment.lastRunAt
                  ? formatDistanceToNow(new Date(experiment.lastRunAt), { addSuffix: true })
                  : "—"}
              </TableCell>
              <TableCell>
                <StatusBadge status={experiment.status} />
              </TableCell>
              <TableCell>
                <PassRate rate={experiment.passRate} />
              </TableCell>
              <TableCell>{experiment.itemCount}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
