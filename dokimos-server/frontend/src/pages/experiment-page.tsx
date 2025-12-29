import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router";
import { format } from "date-fns";
import { LineChart, Line, XAxis, YAxis } from "recharts";
import { useListRuns, useGetTrends } from "@/lib/api/experiment-controller/experiment-controller";
import { useBreadcrumbs } from "@/lib/breadcrumb-context";
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
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

const chartConfig = {
  passRate: {
    label: "Pass Rate",
    color: "var(--chart-1)",
  },
} satisfies ChartConfig;

function formatDuration(startedAt: string | undefined, completedAt: string | undefined): string {
  if (!startedAt || !completedAt) return "—";

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

export default function ExperimentPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { setBreadcrumbs } = useBreadcrumbs();
  const [currentPage, setCurrentPage] = useState(0);

  const { data: runsResponse, error: runsError, isLoading: runsLoading } = useListRuns(id ?? "", {
    swr: { enabled: !!id },
  });
  const runs = runsResponse?.data;

  const { data: trendsResponse, error: trendsError, isLoading: trendsLoading } = useGetTrends(
    id ?? "",
    { limit: 20 },
    { swr: { enabled: !!id } }
  );
  const trends = trendsResponse?.data;

  const isLoading = runsLoading || trendsLoading;
  const error = runsError || trendsError;

  useEffect(() => {
    if (trends?.experimentName && trends?.projectName) {
      setBreadcrumbs([
        { label: "Home", href: "/" },
        {
          label: trends.projectName,
          href: `/projects/${encodeURIComponent(trends.projectName)}`,
        },
        {
          label: trends.experimentName,
          href: `/experiments/${id}`,
        },
      ]);
    }
  }, [trends, id, setBreadcrumbs]);

  if (isLoading) {
    return (
      <div>
        <Skeleton className="h-8 w-48 mb-6" />
        <Card className="mb-6">
          <CardHeader>
            <Skeleton className="h-5 w-32" />
          </CardHeader>
          <CardContent>
            <Skeleton className="h-50 w-full" />
          </CardContent>
        </Card>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Date</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Pass Rate</TableHead>
              <TableHead>Items</TableHead>
              <TableHead>Duration</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {[1, 2, 3].map((i) => (
              <TableRow key={i}>
                <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                <TableCell><Skeleton className="h-5 w-16" /></TableCell>
                <TableCell><Skeleton className="h-4 w-12" /></TableCell>
                <TableCell><Skeleton className="h-4 w-8" /></TableCell>
                <TableCell><Skeleton className="h-4 w-16" /></TableCell>
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
        <h1 className="text-2xl font-bold mb-6">Experiment</h1>
        <p className="text-destructive">Error loading experiment: {error.message}</p>
      </div>
    );
  }

  if (!runs) {
    return (
      <div>
        <h1 className="text-2xl font-bold mb-6">Experiment</h1>
        <p className="text-muted-foreground">Experiment not found.</p>
      </div>
    );
  }

  // Prepare chart data from trends
  const chartData = (trends?.runs ?? [])
    .filter((run) => run.passRate != null)
    .map((run) => ({
      date: run.startedAt ? format(new Date(run.startedAt), "MMM d") : "",
      passRate: Math.round((run.passRate ?? 0) * 100),
    }));

  const hasEnoughDataForChart = chartData.length >= 2;

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">{trends?.experimentName ?? "Experiment"}</h1>

      <Card className="mb-6">
        <CardHeader>
          <CardTitle className="text-base">Pass Rate Trend</CardTitle>
        </CardHeader>
        <CardContent>
          {hasEnoughDataForChart ? (
            <ChartContainer config={chartConfig} className="h-50 w-full">
              <LineChart data={chartData} margin={{ top: 5, right: 10, left: 10, bottom: 5 }}>
                <XAxis
                  dataKey="date"
                  tickLine={false}
                  axisLine={false}
                  tickMargin={8}
                />
                <YAxis
                  domain={[0, 100]}
                  tickLine={false}
                  axisLine={false}
                  tickMargin={8}
                  tickFormatter={(value) => `${value}%`}
                />
                <ChartTooltip
                  content={
                    <ChartTooltipContent
                      formatter={(value) => [`${value}%`, "Pass Rate"]}
                    />
                  }
                />
                <Line
                  type="monotone"
                  dataKey="passRate"
                  stroke="var(--color-passRate)"
                  strokeWidth={2}
                  dot={{ fill: "var(--color-passRate)", r: 4 }}
                  activeDot={{ r: 6 }}
                />
              </LineChart>
            </ChartContainer>
          ) : (
            <p className="text-muted-foreground text-sm py-8 text-center">
              Not enough data for trend chart
            </p>
          )}
        </CardContent>
      </Card>

      {runs.length === 0 ? (
        <p className="text-muted-foreground">
          No runs yet. Run this experiment to see results here.
        </p>
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Date</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Pass Rate</TableHead>
                <TableHead>Items</TableHead>
                <TableHead>Duration</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {runs.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE).map((run) => (
                <TableRow
                  key={run.id}
                  className="cursor-pointer hover:bg-accent/50"
                  onClick={() => navigate(`/runs/${run.id}`)}
                >
                  <TableCell>
                    {run.startedAt && format(new Date(run.startedAt), "MMM d, h:mm a")}
                  </TableCell>
                  <TableCell>
                    {run.status && <StatusBadge status={run.status} />}
                  </TableCell>
                  <TableCell>
                    <PassRate rate={run.passRate} />
                  </TableCell>
                  <TableCell>{run.itemCount}</TableCell>
                  <TableCell className="text-muted-foreground">
                    {formatDuration(run.startedAt, run.completedAt)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <Pagination
            currentPage={currentPage}
            totalItems={runs.length}
            pageSize={PAGE_SIZE}
            onPageChange={setCurrentPage}
          />
        </>
      )}
    </div>
  );
}
