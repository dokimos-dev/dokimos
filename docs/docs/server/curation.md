---
sidebar_position: 12
---

# Review and curation

Automated evaluators are not always right, and the cases they get wrong are exactly the ones worth adding to a dataset. The review queue gathers run items that still need a human verdict, lets you annotate them, and promotes the ones you have judged into a new dataset version. That closes the loop: a production miss becomes a regression test.

## The review queue

Open **Review queue** in the web UI to see items waiting on a human, with enough context (input, expected output, produced output, and the automated eval results) to judge each one without opening its run first. An item appears when it has never been annotated, or when it was previously marked `UNSURE`.

Through the API:

```bash
curl 'http://localhost:8080/api/v1/review-queue?projectName=my-llm-app'
```

The list is pageable and can be narrowed by `projectName`, `experimentId`, or `runId`.

## Annotating an item

A verdict is one of `CORRECT`, `INCORRECT`, or `UNSURE`. You can also record a corrected expected output and a free-text note. The annotation is keyed to the run item:

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

`PUT` creates or replaces the annotation, `GET` reads it back, and `DELETE` removes it. When authentication is enabled the annotation records which principal made it. A `CORRECT` or `INCORRECT` verdict takes the item out of the queue; `UNSURE` keeps it there for another pass.

## Promoting into a dataset

Once you have judged a batch of items, promote them into a new immutable version of an existing dataset. Each promoted item carries its input and expected output from the run, and you can override the expected output per item (for instance the correction you recorded while annotating):

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

The dataset named must already exist; promotion appends a version to it (it does not create a dataset). The response points at the new version, which you can then reference as `dataset://qa-regression@latest` from your tests. See [Server datasets](./datasets) for the dataset and version model.

## The loop

```
run item fails -> appears in review queue -> annotated -> promoted -> new dataset version -> next run is gated on it
```

## Next steps

- [Server datasets](./datasets): the dataset and version model promotion writes to
- [LLM judge](./llm-judge): compare a judge against human verdicts to trust it
