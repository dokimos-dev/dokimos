---
sidebar_position: 7
---

# CI regression gate

The server can decide whether a run regressed against a baseline and fail your build when it did. The comparison is significance gated: a change is only a regression when it is both beyond a small epsilon and statistically significant (McNemar for single-run pass/fail, a paired permutation test with a bootstrap interval otherwise), so a noisy judge does not flake your pipeline.

## The endpoint

```
POST /api/v1/experiments/{experimentId}/gate
```

Body:

```json
{
  "candidateRunId": "<run you just ingested>",
  "baselineRunId": "<optional explicit baseline>",
  "baselineBranch": "<optional, e.g. master>"
}
```

`candidateRunId` is required and must be a terminal run (SUCCESS or FAILED). When `baselineRunId` is omitted the server resolves the most recent successful run of the same experiment on the same dataset version, optionally filtered to `baselineBranch`. If no baseline exists the verdict is `NO_BASELINE` and `passed` is `true` (a first run cannot regress).

The response is a flat `GateResult`:

```json
{
  "status": "PASS | FAIL | NO_BASELINE",
  "passed": true,
  "pairing": "dataset_item_id | positional | none",
  "baselinePassRate": 0.88,
  "candidatePassRate": 0.82,
  "passRateDelta": -0.06,
  "significant": true,
  "regressedCount": 5,
  "improvedCount": 3,
  "regressedEvaluators": [ { "evaluator": "faithfulness", "delta": -0.21, "pValue": 0.011 } ],
  "cases": [ { "datasetItemId": "...", "evaluatorDrops": [ ... ] } ],
  "casesTruncated": false
}
```

Cases are paired by `dataset_item_id` when both runs ran against the same dataset version and every item is linked; otherwise pairing falls back to position. `cases` is capped at 50; `regressedCount` is the authoritative total and `casesTruncated` flags the cap.

The gate is a `POST`, so it needs a write-capable API key when the server has `DOKIMOS_API_KEY` set.

## GitHub Action

A composite action under `.github/actions/eval-gate` calls the endpoint, writes a job summary, posts a sticky pull-request comment, and fails the step on a `FAIL` verdict.

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

`candidate-run-id` is the run id returned when your test job reported results through `DokimosServerReporter`. Set `fail-on-regression: "false"` to comment without blocking the merge, or `comment: "false"` to skip the PR comment.
