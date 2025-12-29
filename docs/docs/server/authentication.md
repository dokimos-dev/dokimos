---
sidebar_position: 5
---

# Authentication

The Dokimos server has a simple authentication model designed for flexibility: API keys protect write operations, while UI authentication can be handled by a reverse proxy.

## Authentication Model

### API Key for Writes

When `DOKIMOS_API_KEY` is configured:

- **Write operations** (POST, PUT, PATCH, DELETE) require the API key
- **Read operations** (GET) are open by default
- The key is passed via the `Authorization` header: `Bearer <key>`

### Why This Design?

**Writes need protection**: You don't want arbitrary clients pushing fake experiment results. The API key ensures only authorized reporters can write data.

**Reads are often open**: Within a team or organization, viewing experiment results is usually fine for anyone. If you need to restrict read access, use a reverse proxy.

**UI auth varies widely**: Organizations use different identity providers (Google, GitHub, Okta, LDAP, etc.). Rather than implementing all of these poorly, there are many robust tools for these use cases already available.

## Configuring API Key Authentication

### Enable Authentication

Set the API key environment variable:

```bash
export DOKIMOS_API_KEY=your-secret-key-here
```

### Client Configuration

Configure the reporter with the API key:

```java
DokimosServerReporter reporter = DokimosServerReporter.builder()
    .serverUrl("https://dokimos.example.com")
    .projectName("my-project")
    .apiKey("your-secret-key-here")
    .build();
```

Or via environment variable:

```bash
export DOKIMOS_API_KEY=your-secret-key-here
```

```java
DokimosServerReporter reporter = DokimosServerReporter.fromEnvironment();
```

### Error Response

When authentication fails, the API returns:

```json
{
  "error": "Invalid or missing API key"
}
```

HTTP status: `401 Unauthorized`

## UI Authentication with Reverse Proxy

For restricting access to the web UI, the server can be placed behind a reverse proxy that handles authentication.

## Best Practices

### Separate Keys per Environment

Use different API keys for development, staging/preview, and production:

```bash
# Development
DOKIMOS_API_KEY=dev-key-not-secret

# Production
DOKIMOS_API_KEY=prod-key-stored-in-secrets-manager
```

### Audit Logging

The server doesn't currently log which API key was used for requests.

## Further Reading

- [oauth2-proxy documentation](https://oauth2-proxy.github.io/oauth2-proxy/)
- [Authelia](https://www.authelia.com/): A self-hosted authentication server
- [Cloudflare Access](https://www.cloudflare.com/products/zero-trust/access/)
- [AWS ALB Authentication](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/listener-authenticate-users.html)
