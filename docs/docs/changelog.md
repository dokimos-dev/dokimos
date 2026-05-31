---
sidebar_position: 20
title: Changelog
description: What shipped and when.
---

# Changelog

What shipped and when. For exact version diffs, see the
[GitHub releases](https://github.com/dokimos-dev/dokimos/releases).

## 0.17.0 (2026-05-31)

- **Tenant data isolation.** A scoped API key can carry a tenant and then reads
  and writes only its own tenant's data plus shared rows. Tenant repositories
  expose only scoped finders, so an unscoped load does not compile, and a
  keyless read sees shared rows only. No-key and legacy single-key deployments
  are unchanged.
- **Protobuf OTLP traces.** `POST /api/v1/traces` accepts the
  `application/x-protobuf` encoding alongside JSON, so a standard OpenTelemetry
  SDK or collector works without reconfiguring its exporter.

## 0.16.0 (2026-05-30)

- **Server datasets.** Hold datasets on the server, versioned and shared, and
  pin a test to an exact version with a `dataset://name@version` URI. The SDK
  resolver caches offline, so a pinned version still resolves when the server
  is briefly unreachable.
- **CI regression gate and run diff.** The server fails your build when a run
  regresses against its baseline, significance-gated so a noisy judge does not
  flake the pipeline, with an item-by-item diff view and a reusable GitHub
  Action.
- **Server LLM judge.** Score runs and traces on the server with a stored
  connection that speaks the vendor-neutral Open Responses API (Chat
  Completions as fallback), plus a judge-vs-human alignment metric.
- **Production traces and online evals.** Ingest OTLP traces from your running
  app and score matching spans as they arrive, using the same judge as offline
  experiments.
- **Regression alerting.** Get a signed webhook when a run regresses, on the
  same comparison the CI gate acts on.
- **Review and curation.** Review the items evaluators got wrong, annotate
  them, and promote them into a new dataset version.
- **Role-scoped API keys.** Issue VIEWER / EDITOR / ADMIN keys alongside the
  single-key mode. Reads stay open, writes need EDITOR, key management needs
  ADMIN.
- **Per-item cost, token, and latency metrics.** Track spend and speed next to
  quality on every item result.
