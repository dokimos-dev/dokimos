import type { ReactNode } from "react";
import styles from "./styles.module.css";

type Group = { label: "Added" | "Changed" | "Fixed"; items: { title: string; body: ReactNode }[] };
type Release = { version: string; date: string; latest?: boolean; lead?: string; groups: Group[] };

const RELEASES: Release[] = [
  {
    version: "0.17.0",
    date: "May 31, 2026",
    latest: true,
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
                <code>POST /api/v1/traces</code> accepts the <code>application/x-protobuf</code> encoding
                alongside JSON, so a standard OpenTelemetry SDK or collector works without reconfiguring its
                exporter.
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
                Hold datasets on the server, versioned and shared, and pin a test to an exact version with a{" "}
                <code>dataset://name@version</code> URI. The SDK resolver caches offline, so a pinned version
                still resolves when the server is briefly unreachable.
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
  return (
    <div className={styles.timeline}>
      {RELEASES.map((rel) => (
        <section key={rel.version} className={`${styles.release} ${rel.latest ? styles.current : ""}`}>
          <span className={styles.node} aria-hidden="true" />
          <div className={styles.head}>
            <h2 id={slug(rel.version)} className={styles.ver}>
              v{rel.version}
            </h2>
            {rel.latest && <span className={styles.tag}>Latest</span>}
            <span className={styles.date}>{rel.date}</span>
          </div>
          {rel.lead && <p className={styles.lead}>{rel.lead}</p>}
          {rel.groups.map((g) => (
            <div key={g.label} className={styles.group}>
              <div className={styles.groupHead}>
                <span className={`${styles.label} ${styles[g.label.toLowerCase()]}`}>{g.label}</span>
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
      ))}
    </div>
  );
}
