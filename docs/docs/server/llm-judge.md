---
sidebar_position: 8
---

# LLM judge

This page shows you how to let the server score your run items and production traces with an LLM, so no API key lives in your test code.

The server runs LLM as judge evaluations on its own. It calls a model through a stored **LLM connection** and records the result like any other evaluation.

This is separate from the client side judge you use in CI. In CI, your tests bring their own `JudgeLM` and their own key. The server side judge runs on the server instead. Use it to score an already reported run from the UI, or to evaluate production traces as they arrive.

## Step 1: Create an LLM connection

An LLM connection is a named, reusable pointer to an OpenAI compatible endpoint. It holds a base URL, a model, the API protocol, and one credential. Manage connections under **LLM connections** in the web UI, or through the API.

Create one with a single POST:

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

Responses never include key material.

### Choose one credential

A connection stores exactly one credential. Set one of these, not both:

- **`apiKey`**: an inline key, encrypted at rest. Inline keys require `DOKIMOS_ENCRYPTION_KEY` to be set (see [Configuration](./configuration)).
- **`credentialRef`**: the name of an environment variable the server reads the key from at call time, so the key never touches the database.

### Choose the API protocol

Each connection declares which API its endpoint speaks. Set `protocol` to one of:

- **`RESPONSES`** (default): the [Open Responses](https://www.openresponses.org) shape (`POST {baseUrl}/responses`). Open Responses is a vendor neutral, multi provider standard.
- **`CHAT_COMPLETIONS`**: the older Chat Completions shape (`POST {baseUrl}/chat/completions`), which most self hosted and proxy endpoints implement.

New connections default to Responses. Connections created before this feature existed keep Chat Completions, so nothing that worked before changes. Pick the one your endpoint supports. The judge builds the request and parses the reply accordingly. The server never depends on a vendor SDK. It speaks both protocols over plain HTTP.

## Step 2: Run the judge over a run

Open a run in the web UI. Choose **Run LLM judge**, pick a connection and an evaluator, and the run is queued for scoring.

The run moves to an `EVALUATING` status while the judge works. It then returns to a terminal status with the new scores attached to each item.

A background worker processes jobs. It claims one job at a time, calls the model outside any database transaction, and records each page of results in its own transaction. Transient failures (timeouts, 5xx) retry up to a ceiling. A non retryable failure (4xx) fails the job, and the run is marked accordingly. For judge settings (poll interval, retry ceiling), see [Configuration](./configuration).

## Step 3: Check judge and human agreement

Annotate items with a human verdict (correct, incorrect, unsure). The run page then shows per evaluator agreement between the judge and the human.

Agreement is the share of annotated items where the judge's pass or fail matched the human verdict. Unsure annotations are excluded. Use it to see where a judge is reliable and where it is not, before you trust it on unlabeled data. Annotating is part of the [review and curation](./curation) flow.

## Next steps

- [Production traces](./traces): evaluate production traces as they arrive
- [Review and curation](./curation): annotate items and check the judge against human verdicts
- [Configuration](./configuration): judge and encryption settings
