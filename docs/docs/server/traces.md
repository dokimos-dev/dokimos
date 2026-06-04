---
sidebar_position: 9
---

# Production traces

Send traces from your running app to the server, and the server scores them the same way it scores your offline experiments. You get quality monitoring on live traffic without changing how you evaluate.

Traces live on their own path, separate from the experiment store. High volume ingestion never competes with your experiment data.

## Ingest a trace

Send traces to `POST /api/v1/traces` using an `ExportTraceServiceRequest`. That is the standard OpenTelemetry trace export shape, so any OTLP exporter pointed at this endpoint works.

The endpoint accepts both OTLP encodings:

- JSON, with `Content-Type: application/json`.
- Protobuf binary, with `Content-Type: application/x-protobuf` (the OpenTelemetry default).

Both encodings give you the same span counts, the same derived input and output text, and the same project link, whichever one you send. (A JSON exporter that writes enums as integers instead of names can store different `kind` and `status.code` strings, but those fields drive neither matching nor the derived fields.)

Start with JSON. It is the easiest to copy and run. Paste this:

```bash
curl -X POST http://localhost:8080/api/v1/traces \
  -H 'Content-Type: application/json' \
  -d '{
    "resourceSpans": [{
      "resource": { "attributes": [
        { "key": "dokimos.project", "value": { "stringValue": "my-llm-app" } }
      ]},
      "scopeSpans": [{
        "spans": [{
          "traceId": "0af7651916cd43dd8448eb211c80319c",
          "spanId": "b7ad6b7169203331",
          "name": "llm.generate",
          "startTimeUnixNano": "1700000000000000000",
          "endTimeUnixNano": "1700000002000000000",
          "attributes": [
            { "key": "input",  "value": { "stringValue": "What is the capital of France?" } },
            { "key": "output", "value": { "stringValue": "The capital of France is Paris." } }
          ]
        }]
      }]
    }]
  }'
```

The response tells you how many spans were accepted, how many were rejected, and how many traces resulted:

```json
{ "acceptedSpans": 1, "rejectedSpans": 0, "traces": 1 }
```

A malformed span (missing trace id, span id, or name) is skipped and counted as rejected. One bad span never fails the rest of the batch.

For protobuf, point an OTLP/HTTP exporter at the same endpoint. It sends `application/x-protobuf` for you.

### Derived fields

The server reads each span's input and output text from your attributes, so an online eval has something to score without re-parsing. It uses the first key it finds in each list, in order:

- **Input**: `dokimos.input`, `input.value`, `gen_ai.prompt`, `llm.input`, `input`, `prompt`
- **Output**: `dokimos.output`, `output.value`, `gen_ai.completion`, `llm.output`, `output`, `completion`

Set a `dokimos.project` (or `dokimos.project.name`) **resource** attribute to link the trace to a project, so that project's eval rules apply. To see ingested traces, open **Traces** in the web UI. Click one to view its spans, attributes, and online eval results.

### Retention

Each trace gets an expiry stamp. A background sweeper deletes expired traces and cascades the delete to their spans and eval jobs. You can set the retention window and the sweep interval. The retention default is 30 days (`DOKIMOS_TRACE_RETENTION_DAYS`). See [Configuration](./configuration).

## Online evaluations

A **trace eval rule** runs an LLM judge on matching spans as traces come in. Manage rules per project under **Trace eval rules** in the web UI, or through the API. A rule matches a span by name or by an attribute, then points at an [LLM connection](./llm-judge) and an evaluator.

Create a rule:

```bash
curl -X POST http://localhost:8080/api/v1/projects/{projectId}/trace-eval-rules \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "helpfulness",
    "enabled": true,
    "matchType": "SPAN_NAME",
    "matchValue": "llm.generate",
    "connectionId": "<llm-connection-id>",
    "evaluatorName": "helpfulness",
    "criteria": "The response correctly and helpfully answers the question.",
    "minScore": 0,
    "maxScore": 1,
    "threshold": 0.5
  }'
```

Set `matchType` to one of two values:

- `SPAN_NAME`: compare `matchValue` to the span name.
- `ATTRIBUTE`: compare `matchValue` to the attribute named by `matchKey`.

When an ingested trace has a matching span with scorable output, the server enqueues an online evaluation. A background worker scores it through the same judge machinery as run evaluations. It honors the connection's Responses or Chat Completions protocol, with the same poll and claim, retry ceiling, and credential handling. The result shows up on the trace detail page.

## The loop

```
production trace ingested -> matched by a rule -> online eval enqueued -> scored -> visible
```

## Next steps

- [LLM judge](./llm-judge): connections and judge configuration
- [Regression alerting](./alerting): get notified when quality drops
