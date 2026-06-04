---
sidebar_position: 7
---

# CI regression gate

Fail a build when an eval run scores worse than a baseline run. You call one endpoint with the run you just ingested, and the server returns a single `passed` boolean your pipeline can branch on.

The gate only fails on a real regression. A change counts as a regression only when it clears a small epsilon and passes a significance test (McNemar for single-run pass/fail, a paired permutation test with a bootstrap interval otherwise). A noisy judge will not flake your pipeline.

## Call the endpoint

```
POST /api/v1/experiments/{experimentId}/gate
```

Send the run you want to check:

```json
{
  "candidateRunId": "<run you just ingested>",
  "baselineRunId": "<optional explicit baseline>",
  "baselineBranch": "<optional, e.g. master>"
}
```

`candidateRunId` is the only required field. The run must be terminal (SUCCESS or FAILED).

Leave `baselineRunId` out and the server picks one for you. It resolves the most recent successful run of the same experiment on the same dataset version. Set `baselineBranch` to limit that search to one branch.

When no baseline exists, the verdict is `NO_BASELINE` and `passed` is `true`. A first run cannot regress.

The gate is a `POST`, so it needs a write-capable API key when the server has `DOKIMOS_API_KEY` set.

## Read the response

The response is a flat `GateResult`. Branch your build on `passed`:

```json
{
  "status": "PASS | FAIL | NO_BASELINE",
  "passed": true,
  "candidateRunId": "...",
  "baselineRunId": "...",
  "pairing": "dataset_item_id | positional | none",
  "baselinePassRate": 0.88,
  "candidatePassRate": 0.82,
  "passRateDelta": -0.06,
  "significant": true,
  "improvedCount": 3,
  "regressedCount": 5,
  "unchangedCount": 40,
  "addedCount": 0,
  "removedCount": 0,
  "regressedEvaluators": [
    {
      "evaluator": "faithfulness",
      "baselineMean": 0.91,
      "candidateMean": 0.70,
      "delta": -0.21,
      "pValue": 0.011
    }
  ],
  "cases": [
    {
      "datasetItemId": "...",
      "index": "...",
      "evaluatorDrops": [
        {
          "evaluator": "faithfulness",
          "baselineMean": 1.0,
          "candidateMean": 0.0,
          "delta": -1.0
        }
      ]
    }
  ],
  "casesTruncated": false
}
```

What the key fields mean:

| Field | Meaning |
| --- | --- |
| `passed` | The single boolean CI branches on. `false` only when `status` is `FAIL`. |
| `status` | `PASS`, `FAIL`, or `NO_BASELINE`. |
| `pairing` | How items were matched: `dataset_item_id`, `positional`, or `none` (for `NO_BASELINE`). |
| `passRateDelta` | Candidate pass rate minus baseline pass rate. |
| `significant` | Whether the pass-rate change passed the significance test. |
| `regressedCount` | The authoritative count of significantly regressed items. |
| `regressedEvaluators` | Every evaluator flagged as a significant regression. |
| `cases` | Up to 50 regressed items with their per-evaluator score drops. |
| `casesTruncated` | `true` when `regressedCount` is larger than the returned `cases` list. |

Cases pair by `dataset_item_id` when both runs ran against the same dataset version and every item is linked. Otherwise pairing falls back to position. The `cases` list is capped at 50, so read `regressedCount` for the real total and check `casesTruncated` to know whether the cap was hit.

## Run it from GitHub Actions

A composite action under `.github/actions/eval-gate` calls the endpoint for you. It writes a job summary, posts a sticky pull-request comment, and fails the step on a `FAIL` verdict.

```yaml
- name: Eval gate
  uses: dokimos-dev/dokimos/.github/actions/eval-gate@v0
  with:
    server-url: ${{ secrets.DOKIMOS_SERVER_URL }}
    api-key: ${{ secrets.DOKIMOS_API_KEY }}
    experiment-id: ${{ env.EXPERIMENT_ID }}
    candidate-run-id: ${{ env.RUN_ID }}
    baseline-branch: master
```

`candidate-run-id` is the run id you get back when your test job reports results through `DokimosServerReporter`.

Two inputs let you soften the gate:

- Set `fail-on-regression: "false"` to post the comment without blocking the merge.
- Set `comment: "false"` to skip the PR comment.

## Next steps

- [Comparing runs](./diff): read the same comparison item by item in the web UI
- [Regression alerting](./alerting): get a webhook on the same regression the gate fails on
- [Server datasets](./datasets): pin a run to a dataset version so the gate compares like for like
