---
sidebar_position: 8
---

# LLM judge

The server can run LLM as judge evaluations on its own, scoring stored run items and production traces without a key living in your test code. It calls a model through a stored **LLM connection** and records the result like any other evaluation.

This is separate from the client side judge you use in CI. In CI your tests bring their own `JudgeLM` and their own key. The server side judge is for evaluations that run on the server: scoring an already reported run from the UI, or evaluating production traces as they arrive.

## LLM connections

An LLM connection is a named, reusable pointer to an OpenAI compatible endpoint: a base URL, a model, the API protocol, and a credential. Connections are managed under **LLM connections** in the web UI, or through the API.

```bash
curl -X POST http://localhost:8080/api/v1/llm-connections \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "openai-judge",
    "baseUrl": "https://api.openai.com/v1",
    "model": "gpt-4o-mini",
    "protocol": "RESPONSES",
    "apiKey": "sk-..."
  }'
```

Responses never include key material. A connection stores exactly one credential:

- **`apiKey`**: an inline key, encrypted at rest. Inline keys require `DOKIMOS_ENCRYPTION_KEY` to be set (see [Configuration](./configuration)).
- **`credentialRef`**: the name of an environment variable the server reads the key from at call time, so the key never touches the database.

### API protocol

Each connection declares which API its endpoint speaks:

- **`RESPONSES`** (default): the [Open Responses](https://www.openresponses.org) shape (`POST {baseUrl}/responses`). Open Responses is a vendor neutral, multi provider standard.
- **`CHAT_COMPLETIONS`**: the older Chat Completions shape (`POST {baseUrl}/chat/completions`), which most self hosted and proxy endpoints implement.

New connections default to Responses. Connections created before this feature existed keep Chat Completions, so nothing that worked before changes. Pick the one your endpoint supports; the judge builds the request and parses the reply accordingly. The server never depends on a vendor SDK; both protocols are spoken over plain HTTP.

## Running the judge over a run

From a run in the web UI, choose **Run LLM judge**, pick a connection and an evaluator, and the run is queued for scoring. The run moves to an `EVALUATING` status while the judge works, then back to a terminal status with the new scores attached to each item.

Jobs are processed by a background worker that claims one job at a time, calls the model outside any database transaction, and records each page of results in its own transaction. Transient failures (timeouts, 5xx) are retried up to a ceiling; a non retryable failure (4xx) fails the job and the run is marked accordingly. The judge configuration (poll interval, retry ceiling) is covered in [Configuration](./configuration).

## Judge and human agreement

When you annotate items with a human verdict (correct, incorrect, unsure), the run page shows per evaluator agreement between the judge and the human. It is the share of annotated items where the judge's pass or fail matched the human verdict, with unsure annotations excluded. Use it to see where a judge is reliable and where it is not before you trust it on unlabeled data. Annotating is part of the [review and curation](./curation) flow.

## Next steps

- [Production traces](./traces): evaluate production traces as they arrive
- [Review and curation](./curation): annotate items and check the judge against human verdicts
- [Configuration](./configuration): judge and encryption settings
