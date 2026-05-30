import { Fragment, useEffect, useState } from "react";
import { useParams } from "react-router";
import { format } from "date-fns";
import { ChevronDown, ChevronRight } from "lucide-react";
import { useGetTrace } from "@/lib/api/trace-controller/trace-controller";
import type {
  SpanView,
  TraceEvalJobView,
  TraceEvalJobViewStatus,
} from "@/lib/api/generated.schemas";
import { useBreadcrumbs } from "@/lib/breadcrumb-context";
import { Card, CardContent } from "@/components/ui/card";
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
import JsonDisplay from "@/components/shared/json-display";

function formatNanos(nanos: number | undefined): string {
  if (nanos == null) return "—";
  const millis = nanos / 1_000_000;
  const date = new Date(millis);
  if (Number.isNaN(date.getTime())) return "—";
  return format(date, "MMM d, yyyy h:mm:ss.SSS a");
}

function formatDurationNanos(
  start: number | undefined,
  end: number | undefined
): string {
  if (start == null || end == null) return "—";
  const millis = (end - start) / 1_000_000;
  if (millis < 1000) return `${Math.round(millis)}ms`;
  return `${(millis / 1000).toFixed(2)}s`;
}

const JOB_STATUS_LABEL: Record<TraceEvalJobViewStatus, string> = {
  PENDING: "pending",
  CLAIMED: "running",
  SUCCEEDED: "succeeded",
  FAILED: "failed",
};

const JOB_STATUS_CLASS: Record<TraceEvalJobViewStatus, string> = {
  PENDING: "bg-muted text-muted-foreground",
  CLAIMED: "bg-muted text-muted-foreground",
  SUCCEEDED: "bg-success/15 text-success",
  FAILED: "bg-destructive/15 text-destructive",
};

function JobStatusChip({ status }: { status: TraceEvalJobViewStatus }) {
  return (
    <span
      className={
        "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium " +
        JOB_STATUS_CLASS[status]
      }
    >
      {JOB_STATUS_LABEL[status]}
    </span>
  );
}

function EvalJobsCard({ jobs }: { jobs: TraceEvalJobView[] }) {
  if (jobs.length === 0) return null;
  return (
    <div className="mb-6">
      <h2 className="text-lg font-semibold mb-3">Online evaluations</h2>
      <div className="space-y-2">
        {jobs.map((job) => (
          <div
            key={job.id}
            className="bg-card rounded-md p-3 border text-sm"
          >
            <div className="flex items-center gap-4 flex-wrap">
              <span className="font-medium">
                {job.evaluatorName ?? "evaluator"}
              </span>
              {job.status && <JobStatusChip status={job.status} />}
              {job.score != null && (
                <span>
                  Score:{" "}
                  <ScoreCell
                    score={job.score}
                    success={job.success ?? false}
                  />
                </span>
              )}
              {job.attemptCount != null && job.attemptCount > 1 && (
                <span className="text-muted-foreground">
                  Attempts: {job.attemptCount}
                </span>
              )}
            </div>
            {job.reason && (
              <p className="text-muted-foreground mt-2 break-words">
                {job.reason}
              </p>
            )}
            {job.lastError && (
              <p className="text-destructive mt-2 break-words">
                {job.lastError}
              </p>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

function SpanRow({ span }: { span: SpanView }) {
  const [expanded, setExpanded] = useState(false);
  const hasAttributes =
    span.attributes && Object.keys(span.attributes).length > 0;
  return (
    <Fragment>
      <TableRow
        className="cursor-pointer hover:bg-accent/50"
        onClick={() => setExpanded((prev) => !prev)}
      >
        <TableCell>
          {expanded ? (
            <ChevronDown className="h-4 w-4" />
          ) : (
            <ChevronRight className="h-4 w-4" />
          )}
        </TableCell>
        <TableCell className="font-medium">{span.name ?? "span"}</TableCell>
        <TableCell className="text-muted-foreground">
          {span.kind ?? "—"}
        </TableCell>
        <TableCell className="text-muted-foreground">
          {span.statusCode ?? "—"}
        </TableCell>
        <TableCell className="text-muted-foreground tabular-nums">
          {formatDurationNanos(
            span.startTimeUnixNano,
            span.endTimeUnixNano
          )}
        </TableCell>
      </TableRow>
      {expanded && (
        <TableRow>
          <TableCell colSpan={5} className="bg-muted/50">
            <div className="p-4 space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
                <div>
                  <h4 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1">
                    Start
                  </h4>
                  <p className="tabular-nums">
                    {formatNanos(span.startTimeUnixNano)}
                  </p>
                </div>
                <div>
                  <h4 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1">
                    End
                  </h4>
                  <p className="tabular-nums">
                    {formatNanos(span.endTimeUnixNano)}
                  </p>
                </div>
              </div>
              {span.inputText != null && span.inputText !== "" && (
                <div>
                  <h4 className="text-sm font-medium mb-2">Input</h4>
                  <JsonDisplay data={span.inputText} />
                </div>
              )}
              {span.outputText != null && span.outputText !== "" && (
                <div>
                  <h4 className="text-sm font-medium mb-2">Output</h4>
                  <JsonDisplay data={span.outputText} />
                </div>
              )}
              {hasAttributes && (
                <div>
                  <h4 className="text-sm font-medium mb-2">Attributes</h4>
                  <JsonDisplay data={span.attributes} />
                </div>
              )}
            </div>
          </TableCell>
        </TableRow>
      )}
    </Fragment>
  );
}

export default function TraceDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { setBreadcrumbs } = useBreadcrumbs();

  const { data: response, error, isLoading } = useGetTrace(id ?? "", {
    swr: { enabled: !!id },
  });
  const trace = response?.data;

  useEffect(() => {
    setBreadcrumbs([
      { label: "Home", href: "/" },
      { label: "Traces", href: "/traces" },
      {
        label: trace?.rootSpanName ?? "Trace",
        href: `/traces/${id ?? ""}`,
      },
    ]);
  }, [trace, id, setBreadcrumbs]);

  if (isLoading) {
    return (
      <div>
        <Skeleton className="h-8 w-48 mb-6" />
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
          {[1, 2, 3, 4].map((i) => (
            <Card key={i}>
              <CardContent className="pt-6">
                <Skeleton className="h-4 w-20 mb-2" />
                <Skeleton className="h-8 w-16" />
              </CardContent>
            </Card>
          ))}
        </div>
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }

  if (error) {
    return (
      <div>
        <h1 className="text-2xl font-bold mb-6">Trace</h1>
        <p className="text-destructive">
          Error loading trace: {error.message}
        </p>
      </div>
    );
  }

  if (!trace) {
    return (
      <div>
        <h1 className="text-2xl font-bold mb-6">Trace</h1>
        <p className="text-muted-foreground">Trace not found.</p>
      </div>
    );
  }

  const spans = trace.spans ?? [];
  const jobs = trace.evalJobs ?? [];

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">
        {trace.rootSpanName ?? "Trace"}
      </h1>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm text-muted-foreground">Spans</p>
            <p className="text-2xl font-bold">{trace.spanCount ?? spans.length}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm text-muted-foreground">Duration</p>
            <p className="text-2xl font-bold">
              {formatDurationNanos(
                trace.startTimeUnixNano,
                trace.endTimeUnixNano
              )}
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm text-muted-foreground">Started</p>
            <p className="text-base font-semibold tabular-nums">
              {formatNanos(trace.startTimeUnixNano)}
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm text-muted-foreground">Online evals</p>
            <p className="text-2xl font-bold">{jobs.length}</p>
          </CardContent>
        </Card>
      </div>

      <EvalJobsCard jobs={jobs} />

      <h2 className="text-lg font-semibold mb-3">Spans</h2>
      {spans.length === 0 ? (
        <p className="text-muted-foreground">This trace has no spans.</p>
      ) : (
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-8"></TableHead>
                <TableHead>Name</TableHead>
                <TableHead>Kind</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Duration</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {spans.map((span) => (
                <SpanRow key={span.id ?? span.spanId} span={span} />
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}
