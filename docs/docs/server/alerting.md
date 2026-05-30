---
sidebar_position: 10
---

# Regression alerting

The server can POST to a webhook when a run regresses against its baseline, so a quality drop reaches your chat or on call tooling without anyone watching a dashboard. Alerting reuses the same comparison the [CI gate](./ci-gate) uses, so an alert fires on the same regression the gate would fail on.

## Registering a webhook

Webhooks are scoped to a project. Manage them from the project page in the web UI under **Alert webhooks**, or through the API.

```bash
curl -X POST http://localhost:8080/api/v1/projects/{projectId}/alert-webhooks \
  -H 'Content-Type: application/json' \
  -d '{
    "url": "https://hooks.example.com/dokimos",
    "secret": "your-signing-secret",
    "enabled": true
  }'
```

The signing secret is write only. It is never returned in a response; the UI shows only whether a secret is configured.

## When it fires

When a run reaches a terminal status, the server resolves its baseline the way the gate does (the most recent successful run of the same experiment, scoped by dataset version and git branch) and compares the two. If the pass rate both regressed and the drop is statistically significant, every enabled webhook for the project receives a POST.

The decision is made inside run completion, but delivery happens after the transaction commits, on a separate thread. A slow or failing receiver can never block, lengthen, or fail the run; a delivery failure is logged and dropped.

## Payload

```json
{
  "projectName": "my-llm-app",
  "experimentId": "…",
  "experimentName": "customer-support-qa",
  "runId": "…",
  "baselinePassRate": 0.92,
  "candidatePassRate": 0.78,
  "regressedCases": 7
}
```

When the webhook has a secret, the body is signed with HMAC SHA256 and the lowercase hex digest is sent in the `X-Dokimos-Signature` header. Verify it by computing the same HMAC over the raw request body with your secret and comparing.

## Next steps

- [CI regression gate](./ci-gate): block a regression before it ships
- [Production traces](./traces): evaluate production traffic online
