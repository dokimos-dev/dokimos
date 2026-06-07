import type { ReactNode } from "react";
import useDocusaurusContext from "@docusaurus/useDocusaurusContext";
import styles from "./styles.module.css";

type Group = { label: "Added" | "Changed" | "Fixed"; items: { title: string; body: ReactNode }[] };
type Release = { version: string; date: string; lead?: string; groups: Group[] };

const RELEASES: Release[] = [
  {
    version: "0.22.0",
    date: "Unreleased",
    lead: "A server-free regression gate: commit a baseline next to your test and fail the build when quality drops, with the same verdict locally and in CI — no server, account, or API key for the gate itself.",
    groups: [
      {
        label: "Added",
        items: [
          {
            title: "Server-free regression gate",
            body: (
              <>
                <code>Assertions.assertNoRegression(result, "name")</code> compares a fresh experiment
                result against a committed baseline at{" "}
                <code>src/test/resources/dokimos/baselines/&lt;name&gt;.json</code> and throws on a real
                regression. Two guards fire it: a significance test on the aggregate pass rate and
                per-evaluator means (quiet on judge noise), and a localized-severity check that catches
                a single item breaking hard. The first local run writes the baseline and fails once so
                you review and commit it.
              </>
            ),
          },
          {
            title: "Kotlin assertNoRegression",
            body: (
              <>
                <code>ExperimentResult.assertNoRegression(...)</code> is available as a Kotlin extension
                so the gate reads naturally from a Kotlin test.
              </>
            ),
          },
          {
            title: "CI report action",
            body: (
              <>
                The <code>eval-gate-report</code> composite action renders each per-baseline verdict
                JSON under <code>target/dokimos</code> into the job summary and a sticky PR comment, and
                fails the step on a regression. Pair it with <code>if: always()</code> so the comment
                posts even after a failing build.
              </>
            ),
          },
        ],
      },
    ],
  },
  {
    version: "0.21.0",
    date: "June 2026",
    lead: "Cost, token, and latency metrics across all five framework adapters with a pluggable pricing seam, plus two new agent integrations (Embabel and Spring AI Alibaba) that capture an agent run as an AgentTrace for the agent evaluators.",
    groups: [
      {
        label: "Added",
        items: [
          {
            title: "Cost, token & latency metrics",
            body: (
              <>
                Capture per-call <code>tokensIn</code>/<code>tokensOut</code>, <code>costUsd</code>,
                and <code>latencyMs</code> across all five adapters via measured tasks (
                <code>measuredTask</code>/<code>measuredAsyncTask</code>/<code>measuredTextTask</code>,
                and <code>EmbabelTraceCollector.callMetrics</code>). Cost is composed at capture time
                through a pluggable <code>PriceTable</code> seam in <code>dokimos-core</code> —
                Dokimos ships no price data, you supply the map. The run detail rolls up Total
                Tokens, Total Cost, and Avg Latency.
              </>
            ),
          },
          {
            title: "Partial cost-coverage signal",
            body: (
              <>
                When a run mixes priced and unpriced items, the run-detail Total Cost card shows an{" "}
                <code>N/M items priced</code> subtitle so a partial total is never mistaken for a
                complete one. Computed at read time on <code>RunDetails</code> — no new column, no
                migration.
              </>
            ),
          },
          {
            title: "Embabel integration",
            body: (
              <>
                <code>dokimos-embabel</code> captures an Embabel agent run as an{" "}
                <code>AgentTrace</code> through an <code>AgenticEventListener</code>:{" "}
                <code>EmbabelSupport.attach(...)</code>, run the agent, then{" "}
                <code>collector.trace()</code>. Requires Java 21, since Embabel ships Java 21
                bytecode; the rest of Dokimos stays on Java 17.
              </>
            ),
          },
          {
            title: "Spring AI Alibaba integration",
            body: (
              <>
                <code>dokimos-spring-ai-alibaba</code> folds a graph run's <code>OverAllState</code>{" "}
                messages into one <code>AgentTrace</code> with per-turn result windowing, reusing
                the Spring AI message extraction.{" "}
                <code>SpringAiAlibabaSupport.toAgentTrace(...)</code>.
              </>
            ),
          },
        ],
      },
    ],
  },
  {
    version: "0.20.0",
    date: "June 2026",
    lead: "Typed, structured outputs end to end and non-blocking async task execution: return a POJO from a task, match it structurally, and drive an experiment from suspend or reactive code without a thread per example.",
    groups: [
      {
        label: "Added",
        items: [
          {
            title: "Typed structured output",
            body: (
              <>
                <code>Task.typed(fn)</code> lets a task return a record, list, or other POJO under
                the <code>"output"</code> key, and <code>EvalTestCase.actualOutputAs(...)</code> /{" "}
                <code>expectedOutputAs(...)</code> read it back type-safely via a{" "}
                <code>Class&lt;T&gt;</code> or an <code>OutputType&lt;T&gt;</code> super-type token
                for generics like <code>List&lt;Whisky&gt;</code>. A failed conversion throws{" "}
                <code>DokimosTypeConversionException</code>.
              </>
            ),
          },
          {
            title: "StructuralMatchEvaluator",
            body: (
              <>
                Compares an expected structure against the actual one. <code>STRICT</code> requires
                the exact field set and array order; <code>LENIENT</code> allows extra fields and
                ignores array order. Both compare numbers by value. Scores the fraction of matching
                leaf paths by default, or call <code>binary()</code> for a 1.0/0.0 all-or-nothing
                score.
              </>
            ),
          },
          {
            title: "Async task execution",
            body: (
              <>
                <code>AsyncTask</code> returns a <code>CompletableFuture&lt;TaskResult&gt;</code>,
                and <code>Experiment.builder().asyncTask(...)</code> runs it through a bounded async
                path that caps in-flight invocations with <code>parallelism(int)</code> — no thread
                parked per example.
              </>
            ),
          },
          {
            title: "Async and reactive adapters",
            body: (
              <>
                Spring AI adds <code>asyncTask(...)</code> and <code>reactiveTask(...)</code>,
                LangChain4j adds <code>asyncTask(...)</code> and <code>asyncRagTask(...)</code>, and
                Koog adds <code>asTask(...)</code> / <code>asTextTask(...)</code>. Each has an
                overload that takes an <code>Executor</code> so calls run on a pool you control.
              </>
            ),
          },
          {
            title: "Kotlin task DSL and ToolCall.resultJson",
            body: (
              <>
                Kotlin adds <code>typedTask&lt;T&gt; {"{ ... }"}</code> for returning a POJO
                directly and <code>suspendTask {"{ ... }"}</code> for a suspend body, and{" "}
                <code>ToolCall.Builder.resultJson(Object)</code> serializes a structured tool result
                to compact JSON.
              </>
            ),
          },
          {
            title: "Spring AI tool-eval example",
            body: "A runnable Spring AI whisky-agent example that exercises the agent tool evaluators end to end.",
          },
        ],
      },
      {
        label: "Changed",
        items: [
          {
            title: "Judge renders structured output as JSON",
            body: (
              <>
                <code>LLMJudgeEvaluator</code> renders a non-String output as pretty-printed JSON so
                the judge sees a parseable structured value; String and primitive output is rendered
                verbatim as before.
              </>
            ),
          },
          {
            title: "task and asyncTask are mutually exclusive",
            body: (
              <>
                <code>Experiment.builder().build()</code> now rejects configuring both a synchronous{" "}
                <code>task</code>/<code>measuredTask</code> and an <code>asyncTask</code>, instead
                of silently running the async path and ignoring the sync one.
              </>
            ),
          },
          {
            title: "Consistent null handling in LangChain4j RAG tasks",
            body: (
              <>
                <code>ragTask</code> and <code>asyncRagTask</code> coerce a null model response to
                an empty string under the output key, matching <code>simpleTask</code> and{" "}
                <code>asyncTask</code>.
              </>
            ),
          },
        ],
      },
      {
        label: "Fixed",
        items: [
          {
            title: "Parallel executor shutdown",
            body: "The parallel experiment executor shuts down forcibly when a run fails, so worker threads no longer leak.",
          },
        ],
      },
    ],
  },
  {
    version: "0.19.0",
    date: "June 2, 2026",
    lead: "Hardens the core: per-item failure isolation, RFC 4180 CSV, prose-tolerant judges, retry and observability for the server reporter, and a run of correctness fixes.",
    groups: [
      {
        label: "Added",
        items: [
          {
            title: "Dataset.load",
            body: (
              <>
                <code>Dataset.load(uriOrPath)</code> resolves a dataset from a local path or a URI
                through the same resolver registry the SDK uses, so a plain path and a{" "}
                <code>dataset://name@version</code> URI load the same way.
              </>
            ),
          },
          {
            title: "Measured tasks",
            body: (
              <>
                <code>Experiment.builder().measuredTask(...)</code> takes a{" "}
                <code>MeasuredTask</code> that returns outputs plus optional{" "}
                <code>CallMetrics</code>, carried through to each <code>ItemResult</code> so cost,
                tokens, and latency land next to the score.
              </>
            ),
          },
          {
            title: "Server reporter failure visibility",
            body: (
              <>
                <code>DokimosServerReporter</code> exposes <code>getFailedItemCount()</code> and an{" "}
                <code>onItemDeliveryFailure(...)</code> callback, plus an opt-in{" "}
                <code>spoolDirectory(...)</code> that appends permanently undelivered batches to a
                durable file.
              </>
            ),
          },
          {
            title: "JUnit recorder and typed metadata",
            body: (
              <>
                A test method can take a <code>DatasetItemRecorder</code> parameter to record actual
                outputs and eval results per invocation, and <code>@MetadataEntry(key, value)</code>{" "}
                replaces the alternating-string metadata form with a typed pair.
              </>
            ),
          },
          {
            title: "Kotlin and LangChain4j helpers",
            body: (
              <>
                Kotlin adds an <code>evalCase(input, actualOutput, expectedOutput)</code> factory
                and a <code>metadata(Map)</code> DSL form; LangChain4j adds{" "}
                <code>simpleTask(model, outputKey)</code> to name the output key, and{" "}
                <code>AgentEvalCase.builder()</code> gives agent test cases a typed builder.
              </>
            ),
          },
        ],
      },
      {
        label: "Changed",
        items: [
          {
            title: "Per-item failure isolation",
            body: "An experiment isolates a failing example so one bad item records its error and the run continues instead of aborting.",
          },
          {
            title: "RFC 4180 CSV",
            body: "Dataset CSV loading parses quoted fields per RFC 4180, so a value may contain the delimiter, a newline, or a doubled quote.",
          },
          {
            title: "Prose-tolerant judges",
            body: (
              <>
                LLM judge replies are parsed by extracting the JSON, so a judge may wrap its verdict
                in preamble or trailing prose, and <code>LLMJudgeEvaluator</code> normalizes a
                custom <code>scoreRange</code> onto 0..1.
              </>
            ),
          },
          {
            title: "Trajectory compares arguments",
            body: (
              <>
                <code>ToolTrajectoryEvaluator</code> now defaults to a tolerant argument matcher;
                pass <code>ArgumentMatcher.of(ArgMatchMode.IGNORE)</code> to compare tool names and
                order only.
              </>
            ),
          },
          {
            title: "Reporter retries and stricter builder",
            body: (
              <>
                The server reporter retries an HTTP 429 with its <code>Retry-After</code> hint, and{" "}
                <code>Experiment.builder()</code> rejects an empty dataset or zero evaluators.
              </>
            ),
          },
        ],
      },
      {
        label: "Fixed",
        items: [
          {
            title: "Spring AI score",
            body: (
              <>
                <code>EvaluationResponse.getScore()</code> returns the real evaluation score instead
                of leaving the field unset.
              </>
            ),
          },
          {
            title: "Null responses",
            body: (
              <>
                LangChain4j <code>simpleTask</code> no longer throws on a null model response, and{" "}
                <code>HallucinationEvaluator</code> reports a missing verdict instead of a raw
                NullPointerException.
              </>
            ),
          },
          {
            title: "Tool-call validity numerics",
            body: "Tool-call validity accepts whole-number doubles for integer parameters and matches numeric enums by value.",
          },
          {
            title: "MCP store and client",
            body: "The MCP result store writes atomically, and the per-call OpenAI client is closed after each run.",
          },
          {
            title: "JUnit reported example",
            body: "The example reported by the JUnit extension is tied to the actual invocation.",
          },
        ],
      },
    ],
  },
  {
    version: "0.17.0",
    date: "May 31, 2026",
    lead: "Closes the production evaluation loop end to end, with full multi-tenant data isolation and standards-based OTLP trace ingestion.",
    groups: [
      {
        label: "Added",
        items: [
          {
            title: "Tenant data isolation",
            body: "A scoped API key can carry a tenant and then reads and writes only its own tenant's data plus shared rows. Tenant repositories expose only scoped finders, so an unscoped load does not compile, and a keyless read sees shared rows only. No-key and legacy single-key deployments are unchanged.",
          },
          {
            title: "Protobuf OTLP traces",
            body: (
              <>
                <code>POST /api/v1/traces</code> accepts the <code>application/x-protobuf</code>{" "}
                encoding alongside JSON, so a standard OpenTelemetry SDK or collector works without
                reconfiguring its exporter.
              </>
            ),
          },
        ],
      },
    ],
  },
  {
    version: "0.16.0",
    date: "May 30, 2026",
    lead: "The server grows from a results viewer into a production evaluation platform: server-owned datasets, a CI regression gate with run diffing, a server-side LLM judge, and trace ingestion.",
    groups: [
      {
        label: "Added",
        items: [
          {
            title: "Server datasets",
            body: (
              <>
                Hold datasets on the server, versioned and shared, and pin a test to an exact
                version with a <code>dataset://name@version</code> URI. The SDK resolver caches
                offline, so a pinned version still resolves when the server is briefly unreachable.
              </>
            ),
          },
          {
            title: "CI regression gate and run diff",
            body: "The server fails your build when a run regresses against its baseline, significance-gated so a noisy judge does not flake the pipeline, with an item-by-item diff view and a reusable GitHub Action.",
          },
          {
            title: "Server LLM judge",
            body: "Score runs and traces on the server with a stored connection that speaks the vendor-neutral Open Responses API (Chat Completions as fallback), plus a judge-vs-human alignment metric.",
          },
          {
            title: "Production traces and online evals",
            body: "Ingest OTLP traces from your running app and score matching spans as they arrive, using the same judge as offline experiments.",
          },
          {
            title: "Regression alerting",
            body: "Get a signed webhook when a run regresses, on the same comparison the CI gate acts on.",
          },
          {
            title: "Review and curation",
            body: "Review the items evaluators got wrong, annotate them, and promote them into a new dataset version.",
          },
          {
            title: "Role-scoped API keys",
            body: "Issue VIEWER / EDITOR / ADMIN keys alongside the single-key mode. Reads stay open, writes need EDITOR, key management needs ADMIN.",
          },
          {
            title: "Per-item cost, token, and latency metrics",
            body: "Track spend and speed next to quality on every item result.",
          },
        ],
      },
    ],
  },
];

function slug(v: string) {
  return "v" + v.replace(/\./g, "-");
}

export default function ReleaseTimeline() {
  const { siteConfig } = useDocusaurusContext();
  const latestVersion = siteConfig.customFields?.latestVersion as string | undefined;
  return (
    <div className={styles.timeline}>
      {RELEASES.map((rel) => {
        const isLatest = rel.version === latestVersion;
        return (
        <section key={rel.version} className={`${styles.release} ${isLatest ? styles.current : ""}`}>
          <span className={styles.node} aria-hidden="true" />
          <div className={styles.head}>
            <h2 id={slug(rel.version)} className={styles.ver}>
              v{rel.version}
            </h2>
            {isLatest && <span className={styles.tag}>Latest</span>}
            <span className={styles.date}>{rel.date}</span>
          </div>
          {rel.lead && <p className={styles.lead}>{rel.lead}</p>}
          {rel.groups.map((g) => (
            <div key={g.label} className={styles.group}>
              <div className={styles.groupHead}>
                <span className={`${styles.label} ${styles[g.label.toLowerCase()]}`}>
                  {g.label}
                </span>
                <span className={styles.rule} />
              </div>
              <ul className={styles.list}>
                {g.items.map((it) => (
                  <li key={it.title}>
                    <strong>{it.title}.</strong> {it.body}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </section>
        );
      })}
    </div>
  );
}
