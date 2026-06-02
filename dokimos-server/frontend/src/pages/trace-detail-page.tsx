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
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import MetricCard, { MetricGrid } from "@/components/shared/metric-card";
import ScoreCell from "@/components/shared/score-cell";
import JsonDisplay from "@/components/shared/json-display";

/** Orders spans into a parent→child tree (DFS, siblings by start time) so the
 *  waterfall nests by depth. Spans whose parent is missing render as roots. */
function orderSpans(spans: SpanView[]): { span: SpanView; depth: number }[] {
  const byId = new Map<string, SpanView>();
  for (const s of spans) if (s.spanId) byId.set(s.spanId, s);
  const children = new Map<string, SpanView[]>();
  const roots: SpanView[] = [];
  for (const s of spans) {
    const parent = s.parentSpanId;
    if (parent && byId.has(parent) && parent !== s.spanId) {
      const list = children.get(parent) ?? [];
      list.push(s);
      children.set(parent, list);
    } else {
      roots.push(s);
    }
  }
  const byStart = (a: SpanView, b: SpanView) =>
    (a.startTimeUnixNano ?? 0) - (b.startTimeUnixNano ?? 0);
  const out: { span: SpanView; depth: number }[] = [];
  const seen = new Set<SpanView>();
  const visit = (s: SpanView, depth: number) => {
    if (seen.has(s)) return;
    seen.add(s);
    out.push({ span: s, depth });
    for (const k of (children.get(s.spanId ?? "") ?? []).slice().sort(byStart)) {
      visit(k, depth + 1);
    }
  };
  for (const r of roots.slice().sort(byStart)) visit(r, 0);
  for (const s of spans) if (!seen.has(s)) out.push({ span: s, depth: 0 });
  return out;
}

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

/** Maps a span kind string to the kind-* color utilities. */
const KIND_KEYS = [
  "llm",
  "retriever",
  "tool",
  "agent",
  "judge",
  "guardrail",
] as const;

function kindKey(kind: string | undefined): (typeof KIND_KEYS)[number] {
  const k = (kind ?? "").toLowerCase();
  for (const key of KIND_KEYS) {
    if (k.includes(key)) return key;
  }
  return "agent";
}

const KIND_EDGE: Record<(typeof KIND_KEYS)[number], string> = {
  llm: "bg-kind-llm",
  retriever: "bg-kind-retriever",
  tool: "bg-kind-tool",
  agent: "bg-kind-agent",
  judge: "bg-kind-judge",
  guardrail: "bg-kind-guardrail",
};

const KIND_TEXT: Record<(typeof KIND_KEYS)[number], string> = {
  llm: "text-kind-llm",
  retriever: "text-kind-retriever",
  tool: "text-kind-tool",
  agent: "text-kind-agent",
  judge: "text-kind-judge",
  guardrail: "text-kind-guardrail",
};

function JobStatusChip({ status }: { status: TraceEvalJobViewStatus }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium uppercase tracking-wider",
        JOB_STATUS_CLASS[status]
      )}
    >
      {JOB_STATUS_LABEL[status]}
    </span>
  );
}

function EvalJobsCard({ jobs }: { jobs: TraceEvalJobView[] }) {
  if (jobs.length === 0) return null;
  return (
    <section className="rounded-lg border bg-card">
      <div className="flex items-center justify-between border-b border-border px-4 py-3">
        <span className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
          Online evaluations
        </span>
        <span className="font-mono text-[11px] text-muted-foreground tabular-nums">
          {jobs.length} {jobs.length === 1 ? "job" : "jobs"}
        </span>
      </div>
      <div className="space-y-3 p-3">
        {jobs.map((job) => (
          <div
            key={job.id}
            className="overflow-hidden rounded-md border border-border"
          >
            <div className="flex flex-wrap items-center gap-3 bg-muted/40 px-4 py-2.5">
              <span className="text-[13px] font-semibold">
                {job.evaluatorName ?? "evaluator"}
              </span>
              {job.spanId && (
                <span className="font-mono text-[11px] text-muted-foreground">
                  span {job.spanId}
                </span>
              )}
              <span className="grow" />
              {job.score != null && (
                <ScoreCell score={job.score} success={job.success ?? false} />
              )}
              {job.status && <JobStatusChip status={job.status} />}
            </div>
            {(job.attemptCount != null ||
              job.reason ||
              job.lastError) && (
              <div className="flex flex-col gap-2 border-t border-border bg-card px-4 py-3">
                {job.attemptCount != null && job.attemptCount > 1 && (
                  <div className="flex flex-wrap gap-1.5">
                    <span className="inline-flex items-center rounded border border-border bg-muted/40 px-1.5 py-0.5 font-mono text-[11px] text-muted-foreground tabular-nums">
                      attempts {job.attemptCount}
                    </span>
                  </div>
                )}
                {job.reason && (
                  <p className="text-[12px] leading-relaxed text-muted-foreground break-words font-prose">
                    {job.reason}
                  </p>
                )}
                {job.lastError && (
                  <p className="text-[12px] leading-relaxed text-destructive break-words">
                    {job.lastError}
                  </p>
                )}
              </div>
            )}
          </div>
        ))}
      </div>
    </section>
  );
}

interface SpanRowProps {
  span: SpanView;
  depth: number;
  windowStart: number | undefined;
  windowEnd: number | undefined;
}

function SpanRow({ span, depth, windowStart, windowEnd }: SpanRowProps) {
  const [expanded, setExpanded] = useState(false);
  const hasAttributes =
    span.attributes && Object.keys(span.attributes).length > 0;
  const key = kindKey(span.kind);

  let left = 0;
  let width = 100;
  if (
    windowStart != null &&
    windowEnd != null &&
    windowEnd > windowStart &&
    span.startTimeUnixNano != null &&
    span.endTimeUnixNano != null
  ) {
    const total = windowEnd - windowStart;
    left = ((span.startTimeUnixNano - windowStart) / total) * 100;
    width = ((span.endTimeUnixNano - span.startTimeUnixNano) / total) * 100;
    left = Math.max(0, Math.min(100, left));
    width = Math.max(1.5, Math.min(100 - left, width));
  }
  const labelOnRight = left + width > 80;

  return (
    <Fragment>
      <div
        className="group grid cursor-pointer grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)] items-center gap-3 border-b border-border px-2 py-1.5 hover:bg-accent/50 sm:gap-6"
        onClick={() => setExpanded((prev) => !prev)}
      >
        <div
          className="flex min-w-0 items-center gap-1.5"
          style={{ paddingLeft: depth * 16 }}
        >
          {expanded ? (
            <ChevronDown className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
          ) : (
            <ChevronRight className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
          )}
          <span className={cn("h-3.5 w-0.5 shrink-0 rounded-full", KIND_EDGE[key])} />
          <span className="truncate font-mono text-[13px]">
            {span.name ?? "span"}
          </span>
          <span
            className={cn(
              "shrink-0 font-mono text-[10px] uppercase tracking-wider",
              KIND_TEXT[key]
            )}
          >
            {span.kind ?? "—"}
          </span>
          {span.statusCode && span.statusCode !== "OK" && (
            <span className="shrink-0 font-mono text-[10px] uppercase tracking-wider text-destructive">
              {span.statusCode}
            </span>
          )}
        </div>
        <div className="relative h-5 min-w-0">
          <div
            className={cn("absolute top-1/2 h-2 -translate-y-1/2 rounded-sm opacity-80", KIND_EDGE[key])}
            style={{ left: `${left}%`, width: `${width}%` }}
          />
          <span
            className={cn(
              "absolute top-1/2 -translate-y-1/2 font-mono text-[11px] text-muted-foreground tabular-nums",
              labelOnRight ? "right-0" : ""
            )}
            style={
              labelOnRight ? undefined : { left: `calc(${left + width}% + 6px)` }
            }
          >
            {formatDurationNanos(span.startTimeUnixNano, span.endTimeUnixNano)}
          </span>
        </div>
      </div>
      {expanded && (
        <div className="border-b border-border bg-muted/30">
          <div
            className="space-y-4 px-4 py-4"
            style={{ paddingLeft: depth * 16 + 28 }}
          >
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
              <div>
                <div className="text-[11px] uppercase tracking-wider text-muted-foreground">
                  Start
                </div>
                <p className="mt-1 font-mono text-[12px] tabular-nums">
                  {formatNanos(span.startTimeUnixNano)}
                </p>
              </div>
              <div>
                <div className="text-[11px] uppercase tracking-wider text-muted-foreground">
                  End
                </div>
                <p className="mt-1 font-mono text-[12px] tabular-nums">
                  {formatNanos(span.endTimeUnixNano)}
                </p>
              </div>
              <div>
                <div className="text-[11px] uppercase tracking-wider text-muted-foreground">
                  Duration
                </div>
                <p className="mt-1 font-mono text-[12px] tabular-nums">
                  {formatDurationNanos(
                    span.startTimeUnixNano,
                    span.endTimeUnixNano
                  )}
                </p>
              </div>
              <div>
                <div className="text-[11px] uppercase tracking-wider text-muted-foreground">
                  Status
                </div>
                <p className="mt-1 font-mono text-[12px]">
                  {span.statusCode ?? "—"}
                </p>
              </div>
            </div>
            {span.inputText != null && span.inputText !== "" && (
              <div>
                <div className="mb-2 text-[11px] uppercase tracking-wider text-muted-foreground">
                  Input
                </div>
                <JsonDisplay data={span.inputText} />
              </div>
            )}
            {span.outputText != null && span.outputText !== "" && (
              <div>
                <div className="mb-2 text-[11px] uppercase tracking-wider text-muted-foreground">
                  Output
                </div>
                <JsonDisplay data={span.outputText} />
              </div>
            )}
            {hasAttributes && (
              <div>
                <div className="mb-2 text-[11px] uppercase tracking-wider text-muted-foreground">
                  Attributes
                </div>
                <JsonDisplay data={span.attributes} />
              </div>
            )}
          </div>
        </div>
      )}
    </Fragment>
  );
}

const KIND_LEGEND: { key: (typeof KIND_KEYS)[number]; label: string }[] = [
  { key: "agent", label: "agent" },
  { key: "retriever", label: "retriever" },
  { key: "llm", label: "llm" },
  { key: "tool", label: "tool" },
  { key: "judge", label: "judge" },
  { key: "guardrail", label: "guardrail" },
];

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
        <MetricGrid className="mb-6">
          {[1, 2, 3, 4].map((i) => (
            <Card key={i}>
              <CardContent className="pt-6">
                <Skeleton className="h-4 w-20 mb-2" />
                <Skeleton className="h-8 w-16" />
              </CardContent>
            </Card>
          ))}
        </MetricGrid>
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }

  if (error) {
    return (
      <div>
        <h1 className="mb-6 font-mono text-2xl font-bold">Trace</h1>
        <p className="text-destructive">
          Error loading trace: {error.message}
        </p>
      </div>
    );
  }

  if (!trace) {
    return (
      <div>
        <h1 className="mb-6 font-mono text-2xl font-bold">Trace</h1>
        <p className="text-muted-foreground">Trace not found.</p>
      </div>
    );
  }

  const spans = trace.spans ?? [];
  const jobs = trace.evalJobs ?? [];

  const spanStarts = spans
    .map((s) => s.startTimeUnixNano)
    .filter((v): v is number => v != null);
  const spanEnds = spans
    .map((s) => s.endTimeUnixNano)
    .filter((v): v is number => v != null);
  const windowStart =
    trace.startTimeUnixNano ??
    (spanStarts.length > 0 ? Math.min(...spanStarts) : undefined);
  const windowEnd =
    trace.endTimeUnixNano ??
    (spanEnds.length > 0 ? Math.max(...spanEnds) : undefined);

  const spanKindCount = new Set(spans.map((s) => kindKey(s.kind))).size;
  const succeededJobs = jobs.filter((j) => j.status === "SUCCEEDED").length;
  const runningJobs = jobs.filter(
    (j) => j.status === "PENDING" || j.status === "CLAIMED"
  ).length;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-mono text-2xl font-bold">
          {trace.rootSpanName ?? "Trace"}
        </h1>
        {trace.traceId && (
          <p className="mt-1 font-mono text-[12px] text-muted-foreground">
            trace_id {trace.traceId}
          </p>
        )}
      </div>

      <MetricGrid>
        <MetricCard
          label="Spans"
          value={trace.spanCount ?? spans.length}
          sub={`${spanKindCount} span ${spanKindCount === 1 ? "kind" : "kinds"}`}
          accent
        />
        <MetricCard
          label="Duration"
          value={formatDurationNanos(
            trace.startTimeUnixNano,
            trace.endTimeUnixNano
          )}
        />
        <MetricCard
          label="Started"
          value={
            <span className="text-[16px]">
              {formatNanos(trace.startTimeUnixNano)}
            </span>
          }
        />
        <MetricCard
          label="Online evals"
          value={jobs.length}
          tone={jobs.length > 0 ? "primary" : "default"}
          sub={
            jobs.length > 0
              ? `${succeededJobs} succeeded · ${runningJobs} running`
              : undefined
          }
        />
      </MetricGrid>

      <EvalJobsCard jobs={jobs} />

      <section className="rounded-lg border bg-card">
        <div className="flex items-center justify-between border-b border-border px-4 py-3">
          <span className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
            Spans
          </span>
          <span className="font-mono text-[11px] text-muted-foreground tabular-nums">
            {spans.length} total
          </span>
        </div>
        {spans.length === 0 ? (
          <p className="px-4 py-6 text-muted-foreground">
            This trace has no spans.
          </p>
        ) : (
          <>
            <div className="flex flex-wrap gap-4 border-b border-border px-4 py-2.5">
              {KIND_LEGEND.map((item) => (
                <span
                  key={item.key}
                  className="inline-flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  <i
                    className={cn(
                      "inline-block h-2 w-2 rounded-[2px]",
                      KIND_EDGE[item.key]
                    )}
                  />
                  {item.label}
                </span>
              ))}
            </div>
            <div className="overflow-x-auto">
              <div className="min-w-[560px]">
                {orderSpans(spans).map(({ span, depth }) => (
                  <SpanRow
                    key={span.spanId ?? span.id}
                    span={span}
                    depth={depth}
                    windowStart={windowStart}
                    windowEnd={windowEnd}
                  />
                ))}
              </div>
            </div>
          </>
        )}
      </section>
    </div>
  );
}
