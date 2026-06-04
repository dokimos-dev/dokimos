---
sidebar_position: 5
---

# Authentication

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

This page shows you how to protect the Dokimos server with API keys, so only trusted clients can write experiment results. Read access stays open by default, and you can lock down the web UI with a reverse proxy.

## How auth works

Set the `DOKIMOS_API_KEY` environment variable to turn auth on. Once it is set:

- **Write requests** (POST, PUT, PATCH, DELETE) need the key.
- **Read requests** (GET) stay open.
- Clients send the key in the `Authorization` header as `Bearer <key>`.

If you never set a key, the server stays fully open (reads and writes both work without a key).

### Why it works this way

**Writes need a guard.** Without one, any client could push fake experiment results. The key makes sure only your reporters can write.

**Reads are usually fine to share.** Inside a team, anyone looking at results is normal. Need to restrict reads too? Put a reverse proxy in front (see [UI authentication with a reverse proxy](#ui-authentication-with-a-reverse-proxy)).

**UI login is its own problem.** Teams use many identity providers (Google, GitHub, Okta, LDAP, and more). Good tools already solve this, so the server hands that job to a reverse proxy instead of doing it badly.

## Turn on API key auth

### 1. Set the key on the server

```bash
export DOKIMOS_API_KEY=your-secret-key-here
```

### 2. Give the key to the client

Pass the key to the reporter builder.

<Tabs groupId="language">
<TabItem value="java" label="Java">

```java
DokimosServerReporter reporter = DokimosServerReporter.builder()
    .serverUrl("https://dokimos.example.com")
    .projectName("my-project")
    .apiKey("your-secret-key-here")
    .build();
```

</TabItem>
<TabItem value="kotlin" label="Kotlin">

```kotlin
val reporter = DokimosServerReporter.builder()
    .serverUrl("https://dokimos.example.com")
    .projectName("my-project")
    .apiKey("your-secret-key-here")
    .build()
```

</TabItem>
</Tabs>

Or read everything from the environment. `fromEnvironment()` reads `DOKIMOS_SERVER_URL`, `DOKIMOS_PROJECT_NAME`, and `DOKIMOS_API_KEY`:

```bash
export DOKIMOS_SERVER_URL=https://dokimos.example.com
export DOKIMOS_PROJECT_NAME=my-project
export DOKIMOS_API_KEY=your-secret-key-here
```

<Tabs groupId="language">
<TabItem value="java" label="Java">

```java
DokimosServerReporter reporter = DokimosServerReporter.fromEnvironment();
```

</TabItem>
<TabItem value="kotlin" label="Kotlin">

```kotlin
val reporter = DokimosServerReporter.fromEnvironment()
```

</TabItem>
</Tabs>

### What a failed request looks like

When the key is wrong or missing on a write, the server returns HTTP `401 Unauthorized` with this body:

```json
{
  "error": "Invalid or missing API key"
}
```

## Scoped API keys and roles

One `DOKIMOS_API_KEY` is the simplest setup: a single shared secret for every write. When you need more than one credential, or different levels of access, create **scoped API keys**, each with a role. Manage them under **API keys** in the web UI (admin only), or through the API.

Every key carries one role:

| Role | Can do |
|------|--------|
| `VIEWER` | Read only |
| `EDITOR` | Reads plus writes (report runs, create connections, and so on) |
| `ADMIN` | Everything, including managing API keys |

The server stores only a hash of each key, never the key itself. The raw value comes back once, at creation. Copy it then, because you cannot see it again.

Create a key with the API:

```bash
curl -X POST http://localhost:8080/api/v1/api-keys \
  -H 'Content-Type: application/json' \
  -d '{ "name": "ci-pipeline", "role": "EDITOR" }'
```

### How the server enforces roles

The server matches the request's `Bearer` token against the stored keys and applies that key's role:

- Writes need `EDITOR` or higher.
- Managing API keys needs `ADMIN`. This includes listing keys, so key names and roles stay hidden from non-admins.
- Other reads stay open.

The deployment runs in authenticated mode when `DOKIMOS_API_KEY` is set, or when at least one scoped key exists.

Old setups keep working. With no key configured at all, the server behaves as before (reads and writes both open). A legacy `DOKIMOS_API_KEY`, if set, keeps working and counts as an admin credential, so you can move to scoped keys one step at a time.

:::note
Key management needs `ADMIN`, so always keep at least one admin credential (the legacy `DOKIMOS_API_KEY`, or an admin scoped key). If you create only non-admin scoped keys, no one can manage keys through the API anymore.
:::

## Tenant isolation

A scoped key can also carry a `tenantId`. When it does, that key reads and writes only its own tenant's data plus shared (untenanted) rows, and any row it creates gets stamped with its tenant.

Keys without a tenant, the legacy `DOKIMOS_API_KEY`, and no-key mode are all unscoped, so they see everything. Single-tenant and existing deployments stay unaffected.

```bash
curl -X POST http://localhost:8080/api/v1/api-keys \
  -H "Authorization: Bearer $ADMIN_KEY" \
  -d '{ "name": "team-acme", "role": "EDITOR", "tenantId": "acme" }'
```

There is no separate screen for creating or administering tenants yet. A tenant starts to exist the moment you scope a key to it. Shared rows (those written by an unscoped key) stay visible to every tenant.

## UI authentication with a reverse proxy

To control who reaches the web UI, put the server behind a reverse proxy that handles login. The proxy authenticates the user, then forwards approved requests to the server.

## Best practices

### Use a separate key per environment

Give development, staging or preview, and production their own keys:

```bash
# Development
DOKIMOS_API_KEY=dev-key-not-secret

# Production
DOKIMOS_API_KEY=prod-key-stored-in-secrets-manager
```

### Audit logging

The server does not yet log which API key made a request.

## Further reading

- [oauth2-proxy documentation](https://oauth2-proxy.github.io/oauth2-proxy/)
- [Authelia](https://www.authelia.com/): a self-hosted authentication server
- [Cloudflare Access](https://www.cloudflare.com/products/zero-trust/access/)
- [AWS ALB Authentication](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/listener-authenticate-users.html)
