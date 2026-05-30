---
sidebar_position: 9
---

# Production traces

The server can ingest traces from your running application and evaluate them online, so quality monitoring follows the same loop as your offline experiments. Traces are stored on a dedicated path, separate from the experiment store, so high volume ingestion never competes with your experiment data.

## Ingesting traces

Send traces to `POST /api/v1/traces` using the OTLP/HTTP JSON encoding of an `ExportTraceServiceRequest`. This is the standard OpenTelemetry trace export shape, so an OTLP exporter pointed at this endpoint works. (Protobuf encoding is not supported yet; send JSON.)

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

The response reports how many spans were accepted, how many were rejected, and how many traces resulted:

```json
{ "acceptedSpans": 1, "rejectedSpans": 0, "traces": 1 }
```

A malformed span (missing trace id, span id, or name) is skipped and counted as rejected; one bad span never fails the rest of the batch.

### Derived fields

The server derives a span's input and output text from the first present of these attribute keys, so an online eval has something to score without re-parsing attributes:

- **Input**: `dokimos.input`, `input.value`, `gen_ai.prompt`, `llm.input`, `input`, `prompt`
- **Output**: `dokimos.output`, `output.value`, `gen_ai.completion`, `llm.output`, `output`, `completion`

A `dokimos.project` (or `dokimos.project.name`) **resource** attribute links the trace to a project so its eval rules apply. Browse ingested traces under **Traces** in the web UI; open one to see its spans, attributes, and online eval results.

### Retention

Each trace is stamped with an expiry and a background sweeper deletes expired traces, cascading to their spans and eval jobs. The retention window and sweep interval are configurable (`DOKIMOS_TRACE_RETENTION_DAYS`, default 30; see [Configuration](./configuration)).

## Online evaluations

A **trace eval rule** runs an LLM judge on matching spans as traces are ingested. Manage rules per project under **Trace eval rules** in the web UI, or through the API. A rule matches a span by its name or by an attribute, and points at an [LLM connection](./llm-judge) and an evaluator.

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

`matchType` is `SPAN_NAME` (compare `matchValue` to the span name) or `ATTRIBUTE` (with `matchKey` naming the attribute). When an ingested trace contains a matching span with scorable output, the server enqueues an online evaluation. A background worker scores it through the same judge machinery as run evaluations, so it honors the connection's Responses or Chat Completions protocol, with the same poll and claim, retry ceiling, and credential handling. The result appears on the trace detail page.

## The loop

```
production trace ingested -> matched by a rule -> online eval enqueued -> scored -> visible
```

## Next steps

- [LLM judge](./llm-judge): connections and judge configuration
- [Regression alerting](./alerting): get notified when quality drops
