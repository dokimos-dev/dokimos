---
sidebar_position: 13
---

# Comparing runs

The diff view compares two runs of the same experiment item by item, so you can see exactly what a change moved before it ships. It is the same comparison the [CI gate](./ci-gate) and [regression alerting](./alerting) act on, shown as a table you can read.

## Opening a diff

From a run, open the comparison against its baseline to land on `/experiments/{experimentId}/runs/{candidateRunId}/diff` in the web UI. One run is the **candidate** (the one under review) and the other is the **baseline** (what you are comparing against, usually the previous successful run).

Through the API:

```bash
curl 'http://localhost:8080/api/v1/experiments/{experimentId}/runs/{candidateRunId}/diff?baselineRunId={baselineRunId}&status=REGRESSED'
```

`baselineRunId` is required. Both runs must be terminal (a comparison of an in-flight run would be misleading). `status` filters the case list to `ALL` (default), `REGRESSED`, `IMPROVED`, or `CHANGED`, and the cases are pageable.

## Reading the result

The response has a summary and a page of cases. The summary reports the headline movement:

| Field | Meaning |
|-------|---------|
| `baselinePassRate`, `candidatePassRate`, `passRateDelta` | Pass rate on each side and the change |
| `significant` | Whether the pass-rate change is statistically significant, not noise |
| `improvedCount`, `regressedCount`, `unchangedCount` | How items moved between the runs |
| `addedCount`, `removedCount` | Items present in only one of the two runs |
| `pairing` | Whether items were matched one to one across the runs |

Each case shows its status (`IMPROVED`, `REGRESSED`, or `CHANGED`), whether the item flipped between pass and fail, its input, and the per-evaluator deltas so you can see which evaluator moved.

## Significance gating

A change counts as a regression only when it is both beyond a small epsilon and statistically significant: McNemar's test for single-run pass/fail flips, and a paired permutation test with a bootstrap interval otherwise. A noisy judge nudging one item does not register as a regression, which is what keeps the gate and alerts from flaking. The `significant` flag in the summary is that same gate, surfaced so you can tell a real move from sampling noise.

## Next steps

- [CI regression gate](./ci-gate): turn this comparison into a build pass or fail
- [Regression alerting](./alerting): get a webhook when a comparison regresses
