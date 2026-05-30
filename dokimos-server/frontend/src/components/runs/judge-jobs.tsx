import { format } from "date-fns";
import { useList } from "@/lib/api/eval-job-controller/eval-job-controller";
import { EvalJobViewStatus } from "@/lib/api/generated.schemas";
import type { EvalJobViewStatus as JobStatus } from "@/lib/api/generated.schemas";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

function StatusChip({ status }: { status?: JobStatus }) {
  const config: Record<JobStatus, { label: string; className: string }> = {
    [EvalJobViewStatus.PENDING]: {
      label: "pending",
      className: "bg-muted text-muted-foreground",
    },
    [EvalJobViewStatus.CLAIMED]: {
      label: "claimed",
      className: "bg-warning/15 text-warning",
    },
    [EvalJobViewStatus.SUCCEEDED]: {
      label: "succeeded",
      className: "bg-success/15 text-success",
    },
    [EvalJobViewStatus.FAILED]: {
      label: "failed",
      className: "bg-destructive/15 text-destructive",
    },
  };
  const entry = status ? config[status] : null;
  if (!entry) {
    return <span className="text-muted-foreground">—</span>;
  }
  return (
    <span
      className={
        "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium " +
        entry.className
      }
    >
      {entry.label}
    </span>
  );
}

interface JudgeJobsProps {
  runId: string;
}

export default function JudgeJobs({ runId }: JudgeJobsProps) {
  const { data: response } = useList(runId, {
    swr: { enabled: !!runId, refreshInterval: 5000 },
  });
  const jobs = response?.data ?? [];

  if (jobs.length === 0) {
    return null;
  }

  return (
    <Card className="mb-6">
      <CardHeader>
        <CardTitle>Judge jobs</CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        {jobs.map((job) => (
          <div
            key={job.id}
            className="rounded-md border bg-background p-3 text-sm"
          >
            <div className="flex items-center gap-3 flex-wrap">
              <span className="font-medium">{job.evaluatorName}</span>
              <StatusChip status={job.status} />
              {job.attemptCount != null && job.attemptCount > 1 && (
                <span className="text-muted-foreground text-xs">
                  attempt {job.attemptCount}
                </span>
              )}
              {job.createdAt && (
                <span className="text-muted-foreground text-xs ml-auto">
                  {format(new Date(job.createdAt), "MMM d, h:mm a")}
                </span>
              )}
            </div>
            {job.lastError && (
              <p className="text-destructive mt-2 break-words">
                {job.lastError}
              </p>
            )}
          </div>
        ))}
      </CardContent>
    </Card>
  );
}
