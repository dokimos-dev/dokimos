---
sidebar_position: 5
---

# Authentication

The Dokimos server has a simple authentication model designed for flexibility: API keys protect write operations, while UI authentication is handled by a reverse proxy.

## Authentication Model

### API Key for Writes

When `DOKIMOS_API_KEY` is configured:

- **Write operations** (POST, PUT, PATCH, DELETE) require the API key
- **Read operations** (GET) are open by default
- The key is passed via the `Authorization` header: `Bearer <key>`

### Why This Design?

**Writes need protection**: You don't want arbitrary clients pushing fake experiment results. The API key ensures only authorized reporters can write data.

**Reads are often open**: Within a team or organization, viewing experiment results is usually fine for anyone. If you need to restrict read access, use a reverse proxy.

**UI auth varies widely**: Organizations use different identity providers (Google, GitHub, Okta, LDAP, etc.). Rather than implementing all of these poorly, we let you use the right tool for the job.

## Configuring API Key Authentication

### Enable Authentication

Set the API key environment variable:

```bash
export DOKIMOS_API_KEY=your-secret-key-here
```

Generate a secure key:

```bash
# Using OpenSSL
openssl rand -hex 32

# Using Python
python3 -c "import secrets; print(secrets.token_hex(32))"
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

For restricting access to the web UI, place the server behind a reverse proxy that handles authentication.

### nginx with Basic Auth

Simple password protection:

```nginx
# Generate password file
# htpasswd -c /etc/nginx/.htpasswd username

upstream dokimos {
    server dokimos-server:8080;
}

server {
    listen 443 ssl;
    server_name dokimos.example.com;

    ssl_certificate /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;

    # Basic auth for all requests
    auth_basic "Dokimos";
    auth_basic_user_file /etc/nginx/.htpasswd;

    # Allow API writes without basic auth (uses API key instead)
    location ~ ^/api/v1/.+$ {
        auth_basic off;
        proxy_pass http://dokimos;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # All other requests require basic auth
    location / {
        proxy_pass http://dokimos;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### oauth2-proxy with GitHub

Use [oauth2-proxy](https://oauth2-proxy.github.io/oauth2-proxy/) for GitHub/Google/etc. authentication:

```yaml
# docker-compose.yml
services:
  oauth2-proxy:
    image: quay.io/oauth2-proxy/oauth2-proxy:v7.5.1
    container_name: oauth2-proxy
    environment:
      OAUTH2_PROXY_PROVIDER: github
      OAUTH2_PROXY_CLIENT_ID: your-github-client-id
      OAUTH2_PROXY_CLIENT_SECRET: your-github-client-secret
      OAUTH2_PROXY_COOKIE_SECRET: generate-a-32-byte-secret
      OAUTH2_PROXY_EMAIL_DOMAINS: "*"
      OAUTH2_PROXY_GITHUB_ORG: your-github-org
      OAUTH2_PROXY_UPSTREAMS: http://dokimos-server:8080
      OAUTH2_PROXY_HTTP_ADDRESS: 0.0.0.0:4180
      OAUTH2_PROXY_REDIRECT_URL: https://dokimos.example.com/oauth2/callback
      OAUTH2_PROXY_COOKIE_SECURE: "true"
      # Skip auth for API endpoints (uses API key)
      OAUTH2_PROXY_SKIP_AUTH_ROUTES: "^/api/.*"
    ports:
      - "4180:4180"

  dokimos-server:
    image: dokimos-server:latest
    environment:
      DOKIMOS_API_KEY: your-api-key
      # ... database config
```

Generate the cookie secret:

```bash
python3 -c "import secrets; print(secrets.token_urlsafe(32))"
```

Create a GitHub OAuth App:
1. Go to GitHub Settings → Developer settings → OAuth Apps
2. Create new OAuth App
3. Set Authorization callback URL to `https://dokimos.example.com/oauth2/callback`
4. Use the Client ID and Client Secret in your config

### Traefik with Forward Auth

Using Traefik with an authentication service:

```yaml
# docker-compose.yml with Traefik labels
services:
  dokimos-server:
    image: dokimos-server:latest
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.dokimos.rule=Host(`dokimos.example.com`)"
      - "traefik.http.routers.dokimos.entrypoints=websecure"
      - "traefik.http.routers.dokimos.tls=true"
      # Apply auth middleware to UI routes only
      - "traefik.http.routers.dokimos.middlewares=auth@file"
      # API routes skip auth middleware
      - "traefik.http.routers.dokimos-api.rule=Host(`dokimos.example.com`) && PathPrefix(`/api`)"
      - "traefik.http.routers.dokimos-api.entrypoints=websecure"
      - "traefik.http.routers.dokimos-api.tls=true"
```

## Cloud Provider Authentication

### AWS ALB with Cognito

AWS Application Load Balancer can authenticate users via Cognito:

1. Create a Cognito User Pool
2. Configure ALB listener rule with authentication action
3. Skip authentication for `/api/*` paths

### Cloudflare Access

Cloudflare Access provides zero-trust authentication:

1. Add your domain to Cloudflare
2. Create an Access application for `dokimos.example.com`
3. Configure identity providers (Google, GitHub, SAML, etc.)
4. Create a bypass policy for `/api/*` paths

### GCP IAP

Google Cloud Identity-Aware Proxy:

1. Enable IAP on your Cloud Run service or GKE ingress
2. Configure OAuth consent screen
3. Add users/groups with IAP-secured Web App User role

## Best Practices

### Separate Keys per Environment

Use different API keys for development, staging, and production:

```bash
# Development
DOKIMOS_API_KEY=dev-key-not-secret

# Production
DOKIMOS_API_KEY=prod-key-stored-in-secrets-manager
```

### Rotate Keys Periodically

Rotate API keys periodically. The process:

1. Generate new key
2. Update all clients to use new key
3. Update server with new key
4. Old key stops working immediately (no grace period)

For zero-downtime rotation, you could implement multiple valid keys (not currently supported, but a future enhancement).

### Audit Logging

The server doesn't currently log which API key was used for requests. If you need audit trails:

1. Use your reverse proxy's access logs
2. Include client identifiers in request headers
3. Consider a dedicated audit logging solution

## Further Reading

- [oauth2-proxy documentation](https://oauth2-proxy.github.io/oauth2-proxy/)
- [Authelia](https://www.authelia.com/) - Self-hosted authentication server
- [Cloudflare Access](https://www.cloudflare.com/products/zero-trust/access/)
- [AWS ALB Authentication](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/listener-authenticate-users.html)
