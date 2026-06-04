---
sidebar_position: 12
---

# Review and curation

Turn a production miss into a regression test. This page shows you how to find run items a human should check, record a verdict on each one, and promote the ones you judged into a new dataset version.

Automated evaluators get some cases wrong. Those cases are the ones worth adding to a dataset. The review queue collects items that need a human verdict, lets you annotate them, and lets you promote them into a new dataset version. Your next run is then gated on those items.

## See the queue

Open **Review queue** in the web UI. Each item shows enough context to judge it without opening its run: the input, the expected output, the produced output, and the automated eval results.

An item shows up in two cases:

- It has never been annotated.
- It was annotated `UNSURE` last time.

To read the queue from the API:

```bash
curl 'http://localhost:8080/api/v1/review-queue?projectName=my-llm-app'
```

The list is paged. Narrow it with any of these query parameters: `projectName`, `experimentId`, or `runId`. Omit all three to get the global queue.

## Annotate an item

Record a verdict for one run item. A verdict is one of `CORRECT`, `INCORRECT`, or `UNSURE`. You can also save a corrected expected output and a free-text note. The annotation is keyed to the run item:

```bash
curl -X PUT \
  http://localhost:8080/api/v1/runs/{runId}/items/{itemResultId}/annotation \
  -H 'Content-Type: application/json' \
  -d '{
    "verdict": "INCORRECT",
    "overriddenExpectedOutput": { "answer": "Paris" },
    "note": "Model answered Lyon; gold answer is Paris."
  }'
```

What each verb does on that URL:

- `PUT` creates the annotation, or replaces it if one already exists.
- `GET` reads it back.
- `DELETE` removes it.

A `CORRECT` or `INCORRECT` verdict takes the item out of the queue. `UNSURE` keeps it in the queue for another pass. When authentication is on, the annotation records which principal made it.

## Promote into a dataset

Once you have judged a batch of items, add them to a new version of an existing dataset. Each promoted item carries its input and expected output from the run. You can override the expected output per item, for example the correction you saved while annotating:

```bash
curl -X POST http://localhost:8080/api/v1/datasets/promote \
  -H 'Content-Type: application/json' \
  -d '{
    "datasetName": "qa-regression",
    "description": "Added misses from the May run",
    "items": [
      {
        "itemResultId": "<item-result-id>",
        "overriddenExpectedOutput": { "answer": "Paris" }
      }
    ]
  }'
```

The dataset must already exist. Promotion appends a new immutable version to it. It does not create a dataset. The response points at the new version. Reference it from your tests as `dataset://qa-regression@latest`. See [Server datasets](./datasets) for the dataset and version model.

## The loop

```
run item fails -> appears in review queue -> annotated -> promoted -> new dataset version -> next run is gated on it
```

## Next steps

- [Server datasets](./datasets): the dataset and version model promotion writes to
- [LLM judge](./llm-judge): compare a judge against human verdicts to trust it
