---
sidebar_position: 8
---

# Cost and Pricing

Dokimos can record what each LLM call cost, roll it up per run, and flag when a cost total only covers part of a run. This page explains how cost capture works, the pluggable pricing seam, and the partial-coverage signal you will see in the UI.

## Capturing cost

Cost, tokens, and latency are captured by switching a plain task to a measured one. A plain `Task` returns only outputs, so each `ItemResult` carries `null` metrics. A `MeasuredTask` returns a `TaskResult` that holds the outputs plus a `CallMetrics` record (`tokensIn`, `tokensOut`, `costUsd`, `latencyMs` — all nullable), and those metrics flow through to every `ItemResult.metrics()`, then to the server, and finally to the run-detail metric cards.

In a builder, the switch is one method:

```java
// before: no metrics
.task(myTask)

// after: tokens, cost, and latency captured
.measuredTask(measuredTask)
```

The full `MeasuredTask` / `CallMetrics` API is documented under [Recording tokens, cost, and latency](./experiments.md#recording-tokens-cost-and-latency). All five framework adapters wire this up:

- **LangChain4j** — `LangChain4jSupport.measuredTask(model, modelId, priceTable)` (and `measuredRagTask(...)`). Reads `TokenUsage` from the response.
- **Spring AI** — `SpringAiSupport.measuredAsyncTask(client, modelId, priceTable)`. Reads `Usage` from the `ChatResponse`.
- **Spring AI Alibaba** — `SpringAiAlibabaSupport.measuredAsyncTask(...)`. The `ReactAgent` graph path returns no typed usage, so you supply token counts via an `AlibabaAgentResponse` carrier; latency and cost are still captured.
- **Koog** — `measuredTextTask(...)`. You supply token counts via a `KoogResponse` carrier; latency and cost are captured automatically.
- **Embabel** — `EmbabelTraceCollector.callMetrics(model, priceTable)`. Reads token usage, cost, and running time off the completed agent process (see the precedence note below).

Where the framework exposes token usage on the response (LangChain4j, Spring AI), the adapter extracts it for you; where it does not surface usage on the call path (Spring AI Alibaba, Koog), you pass the counts you have. In every case latency is timed automatically and cost is composed from a supplied `PriceTable` (null when none is given).

### Embabel: framework cost takes precedence

Embabel reports its own cost on the completed agent process, so it is the one adapter where the `PriceTable` is a fallback rather than the sole cost source. `EmbabelTraceCollector.callMetrics(model, priceTable)` uses Embabel's own non-zero `totalCost()` when present, and consults the `PriceTable` only when Embabel reported `$0` and a model id is supplied.

## The PriceTable seam

No LLM framework or provider returns a dollar cost — they return token counts. So cost must be computed at capture time, where the model id is in scope. That is the job of `PriceTable`, a functional interface in `dev.dokimos.core`:

```java
@FunctionalInterface
public interface PriceTable {
    Double costUsd(String model, Integer tokensIn, Integer tokensOut);
}
```

`PriceTable` is side-effect free and returns `null` (it never throws) for an unknown model or a null token count. A null cost degrades gracefully: the Total Cost card simply stays dark for that item, rather than failing the run. The cost it returns is frozen into `CallMetrics.costUsd()` at capture time and is never recomputed downstream — the server stores and aggregates the number it was given.

## You supply the prices

**Dokimos ships no price data.** Prices change, vary by provider and region, and go stale; baking a price list into the framework would be wrong the day after it shipped. Instead, you supply a `PriceTable` — a lambda over your own price map, an internal pricing service, or the copyable reference map from `dokimos-examples`.

The reference map in `CostMetricsExample` is **illustrative** — a point-in-time snapshot you copy and pin to the current published rates for your model and provider:

```java
// ILLUSTRATIVE per-million-token rates — pin your own current figures.
private static final Map<String, double[]> REFERENCE_PRICES =
        Map.of("gpt-5-nano", new double[] {0.05, 0.40}); // { inputPerMillion, outputPerMillion }

private static final PriceTable PRICES = (model, tokensIn, tokensOut) -> {
    double[] rate = model == null ? null : REFERENCE_PRICES.get(model);
    if (rate == null || tokensIn == null || tokensOut == null) {
        return null; // unknown model or unmeasured call -> no cost
    }
    double usd = ((long) tokensIn * rate[0] + (long) tokensOut * rate[1]) / 1_000_000d;
    return Math.round(usd * 1_000_000d) / 1_000_000d; // round to 6 decimal places
};
```

## Precision: compute at 6dp, display at 4dp

Per-call costs are often a fraction of a cent. Each item's cost is rounded to **6 decimal places** at capture (`Math.round(usd * 1_000_000d) / 1_000_000d`), so a sub-cent per-call cost survives instead of rounding to zero before it can be summed. The run-detail UI then displays the rolled-up **total** to **4 decimal places** (`$x.xxxx`). Compute precise per-item; display the rounded total.

## Partial-coverage signal: "N/M items priced"

The run cost total is `SUM(costUsd)` over the run's items, and SUM skips null-cost rows. So a run that mixes priced and unpriced items would otherwise show a complete-looking total that silently omits the unpriced ones — for example, when your `PriceTable` returned `null` for a model it did not recognize.

To make that visible, the run-detail **Total Cost** card shows a muted subtitle when a run is only partially priced:

> 2/5 items priced

It renders **only** when fewer items are priced than tokenized (`pricedItemCount < tokenizedItemCount`). A fully priced run shows the cost alone, unchanged. A run with no measured items at all shows no Total Cost card.

The denominator is **tokenized** items, not all items:

- An item with **tokens but no cost** is one your `PriceTable` could not price (unknown model). It counts against coverage — this is exactly the gap the signal reports.
- An item with **no tokens at all** was never measured (a plain `.task`). It counts toward neither number — you cannot price what was never measured.

(One edge: Embabel reports its own cost independently of token usage, so an Embabel item can carry a cost without token counts. The displayed total stays correct; only the "priced ≤ tokenized" relationship the signal assumes may not strictly hold for such items.)

This is surfaced as two nullable computed fields, `pricedItemCount` and `tokenizedItemCount`, on `RunDetails` (the run-detail view) only. The run list (`RunSummary`) deliberately carries no coverage signal, so listing runs adds no per-run queries. There is **no new database column and no migration** — the counts are computed at read time from two indexed `COUNT` queries. For an in-progress run they accrue live alongside the totals; for a completed run the totals come from the run's materialized columns while the coverage counts are still computed live from the run's (now immutable) item rows.

:::note
The two TypeScript fields (`pricedItemCount`, `tokenizedItemCount`) in the frontend's generated API types are produced by orval from the server's OpenAPI spec, on the `RunDetails` type only. Regenerating with orval is the canonical path.
:::

## Not yet covered

A few things are intentionally out of scope for now, mostly because no adapter framework surfaces them uniformly:

- **Cached / prompt-cached input tokens.** `CallMetrics` and `PriceTable` model only `tokensIn`/`tokensOut`; cached-token discounts are not represented.
- **Reasoning tokens.** Reasoning/thinking tokens are not split out from the output count.
- **Non-USD currency.** `PriceTable` returns a single USD `Double`; there is no currency conversion (the stored column is `cost_usd`).
- **The zero-priced run in the UI.** When *every* tokenized item is unpriced, the run has no cost total, so the Total Cost card — and with it the "N/M items priced" subtitle — does not render. The Total Tokens card still shows the run was measured; the partial-coverage signal is for runs that are *partly* priced.
