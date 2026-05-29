#!/usr/bin/env bash
# Renders a GateResult JSON document (read from stdin) into a Markdown summary on stdout.
# Used by the eval-gate composite action to post a PR comment. Requires jq.
set -euo pipefail

json="$(cat)"

status=$(jq -r '.status' <<<"$json")
passed=$(jq -r '.passed' <<<"$json")
pairing=$(jq -r '.pairing' <<<"$json")
baseRate=$(jq -r 'if .baselinePassRate == null then "n/a" else (.baselinePassRate * 100 | round | tostring) + "%" end' <<<"$json")
candRate=$(jq -r '(.candidatePassRate * 100 | round | tostring) + "%"' <<<"$json")
delta=$(jq -r 'if .passRateDelta == null then "n/a" else ((.passRateDelta * 100) | round | tostring) + " pts" end' <<<"$json")
significant=$(jq -r '.significant' <<<"$json")
regressed=$(jq -r '.regressedCount' <<<"$json")
improved=$(jq -r '.improvedCount' <<<"$json")
added=$(jq -r '.addedCount' <<<"$json")
removed=$(jq -r '.removedCount' <<<"$json")

case "$status" in
  PASS) header="✅ Eval gate passed" ;;
  FAIL) header="❌ Eval gate failed" ;;
  NO_BASELINE) header="ℹ️ Eval gate: no baseline" ;;
  *) header="Eval gate: $status" ;;
esac

# The HTML marker lets the action find and update its own comment instead of stacking new ones.
echo "<!-- dokimos-eval-gate -->"
echo "## $header"
echo
if [ "$status" = "NO_BASELINE" ]; then
  echo "No comparable baseline run was found, so there is nothing to regress against. This usually means it is the first run for this dataset version or branch."
  echo
fi
echo "| metric | value |"
echo "| --- | --- |"
echo "| pass rate | $baseRate → $candRate ($delta) |"
echo "| significant | $significant |"
echo "| regressed cases | $regressed |"
echo "| improved cases | $improved |"
echo "| added / removed | $added / $removed |"
echo "| pairing | $pairing |"
echo

# Regressed evaluators, if any.
if [ "$(jq -r '.regressedEvaluators | length' <<<"$json")" -gt 0 ]; then
  echo "### Regressed evaluators"
  echo
  echo "| evaluator | baseline | candidate | delta | p |"
  echo "| --- | --- | --- | --- | --- |"
  jq -r '.regressedEvaluators[]
    | "| \(.evaluator) | \(.baselineMean) | \(.candidateMean) | \(.delta) | \(.pValue) |"' <<<"$json"
  echo
fi

# A few of the worst regressed cases.
case_count=$(jq -r '.cases | length' <<<"$json")
if [ "$case_count" -gt 0 ]; then
  echo "### Regressed cases"
  echo
  jq -r '.cases[]
    | "- " + (if .datasetItemId != null then "item " + .datasetItemId else "row " + .index end)
      + ": " + ([.evaluatorDrops[] | "\(.evaluator) \(.baselineMean)→\(.candidateMean)"] | join(", "))' <<<"$json"
  if [ "$(jq -r '.casesTruncated' <<<"$json")" = "true" ]; then
    echo
    echo "_Showing $case_count of $regressed regressed cases._"
  fi
  echo
fi
