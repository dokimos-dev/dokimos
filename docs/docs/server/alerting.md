---
sidebar_position: 10
---

# Regression alerting

Get a webhook POST the moment a run regresses, so a quality drop reaches your chat or on call tool without anyone watching a dashboard.

Alerting reuses the same comparison the [CI gate](./ci-gate) uses. An alert fires on the same regression the gate would fail on.

## Register a webhook

Webhooks are scoped to a project. Register one with a single POST.

```bash
curl -X POST http://localhost:8080/api/v1/projects/{projectId}/alert-webhooks \
  -H 'Content-Type: application/json' \
  -d '{
    "url": "https://hooks.example.com/dokimos",
    "secret": "your-signing-secret",
    "enabled": true
  }'
```

Only `url` is required. Leave out `secret` to send unsigned. Leave out `enabled` and the webhook starts enabled.

You can also manage webhooks from the project page in the web UI under **Alert webhooks**.

The signing secret is write only. It never comes back in a response. The UI shows only whether a secret is set.

## When it fires

A run reaches a terminal status. The server then resolves its baseline the way the gate does: the most recent successful run of the same experiment, scoped by dataset version and git branch. It compares the two runs. If the pass rate regressed and the drop is statistically significant, every enabled webhook for the project gets a POST.

The server decides during run completion. It delivers after the transaction commits, on a separate thread. A slow or failing receiver cannot block, lengthen, or fail the run. A delivery failure is logged and dropped.

## Payload

The POST body is JSON:

```json
{
  "projectName": "my-llm-app",
  "experimentId": "…",
  "experimentName": "customer-support-qa",
  "runId": "…",
  "baselineRunId": "…",
  "baselinePassRate": 0.92,
  "candidatePassRate": 0.78,
  "passRateDelta": -0.14,
  "regressedCaseCount": 7
}
```

| Field | Meaning |
| --- | --- |
| `projectName` | The project the run belongs to. |
| `experimentId` | The experiment the run belongs to. |
| `experimentName` | The experiment name. |
| `runId` | The candidate run that regressed. |
| `baselineRunId` | The baseline run it was compared against. |
| `baselinePassRate` | The baseline run's pass rate. |
| `candidatePassRate` | The candidate run's pass rate. |
| `passRateDelta` | Candidate minus baseline pass rate (negative on a regression). |
| `regressedCaseCount` | The number of items that regressed. |

## Verify the signature

When the webhook has a secret, the server signs the body with HMAC SHA256. It sends the lowercase hex digest in the `X-Dokimos-Signature` header.

To verify, compute the same HMAC over the raw request body with your secret, then compare it to the header value.

```java
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

String expected = sign(rawBody, "your-signing-secret");
boolean valid = expected.equals(signatureHeader);

static String sign(String body, String secret) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(digest);
}
```

Sign over the raw request body, not a re-serialized object. Re-serializing can reorder keys or change whitespace and break the comparison.

## Next steps

- [CI regression gate](./ci-gate): block a regression before it ships
- [Production traces](./traces): evaluate production traffic online
