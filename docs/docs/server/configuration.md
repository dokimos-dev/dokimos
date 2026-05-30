---
sidebar_position: 3
---

# Configuration

The Dokimos server is configured through environment variables. This page covers all available settings.

## Environment Variables

### Database Connection

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | PostgreSQL hostname | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `dokimos` |
| `DB_USERNAME` | Database username | `dokimos` |
| `DB_PASSWORD` | Database password | `dokimos` |

### Server Settings

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | HTTP port to listen on | `8080` |
| `DOKIMOS_API_KEY` | API key for write operations | _(disabled)_ |
| `DOKIMOS_ENCRYPTION_KEY` | Passphrase used to encrypt inline LLM connection keys at rest. Required only if you store an inline `apiKey` on a connection. | _(disabled)_ |

### Server side judge

Tune the background worker that scores [LLM judge](./llm-judge) jobs. The defaults are fine for most deployments.

| Variable | Description | Default |
|----------|-------------|---------|
| `DOKIMOS_JUDGE_POLL_INTERVAL_MS` | How often the worker polls for pending judge jobs | `5000` |
| `DOKIMOS_JUDGE_MAX_ATTEMPTS` | Retry ceiling for a judge job before it fails | `3` |
| `DOKIMOS_JUDGE_PAGE_SIZE` | Items scored per database transaction | `50` |

### Traces and online evals

Control [production trace](./traces) retention and the online eval worker.

| Variable | Description | Default |
|----------|-------------|---------|
| `DOKIMOS_TRACE_RETENTION_DAYS` | Days an ingested trace is kept before the sweeper deletes it | `30` |
| `DOKIMOS_TRACE_SWEEP_INTERVAL_MS` | How often the retention sweeper runs | `3600000` |
| `DOKIMOS_TRACE_EVAL_POLL_INTERVAL_MS` | How often the worker polls for pending trace eval jobs | `5000` |
| `DOKIMOS_TRACE_EVAL_MAX_ATTEMPTS` | Retry ceiling for a trace eval job before it fails | `3` |
| `DOKIMOS_TRACE_EVAL_CLAIM_TIMEOUT_MS` | How long a claimed trace eval job can run before it is requeued | `600000` |

### Logging

| Variable | Description | Default |
|----------|-------------|---------|
| `LOG_LEVEL` | Application log level | `INFO` |
| `SQL_LOG_LEVEL` | Hibernate SQL logging level | `WARN` |

## Database Setup

### PostgreSQL Requirements

The server requires PostgreSQL 14 or higher. The database schema is managed automatically via Flyway migrations.

### Connection String Format

The server constructs the JDBC URL from individual components:

```
jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
```

If you need to specify a full connection string with additional parameters, you can set the Spring datasource URL directly:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/dokimos?ssl=true&sslmode=require
```

### Creating the Database

If you're using an existing PostgreSQL instance, create the database and user:

```sql
CREATE DATABASE dokimos;
CREATE USER dokimos WITH PASSWORD 'your-secure-password';
GRANT ALL PRIVILEGES ON DATABASE dokimos TO dokimos;

-- Connect to the dokimos database and grant schema permissions
\c dokimos
GRANT ALL ON SCHEMA public TO dokimos;
```

### Schema Migrations

Migrations run automatically on startup. The server uses Flyway with the following behavior:

- Creates tables if they don't exist
- Applies new migrations in order
- Never drops or modifies existing data destructively

## API Key Configuration

When `DOKIMOS_API_KEY` is set, write operations require authentication:

```bash
export DOKIMOS_API_KEY=your-secret-key-here
```

See [Authentication](./authentication) for details on how API key authentication works.

## Port and Host Binding

### Changing the Port

```bash
export SERVER_PORT=3000
```

### Binding to All Interfaces

By default, the server binds to all interfaces (`0.0.0.0`). 

For local development, if you want to restrict to localhost only, use Docker's port mapping:

```yaml
ports:
  - "127.0.0.1:8080:8080"
```

## Example Configurations

### Local Development

Minimal configuration for local development:

```bash
# No special configuration needed with docker-compose
docker compose up
```

### Development with API Key

Test authentication locally:

```bash
export DOKIMOS_API_KEY=dev-secret-key
docker compose up
```

### Production with External Database

Connect to a managed PostgreSQL instance:

```bash
export DB_HOST=your-postgres-host.amazonaws.com
export DB_PORT=5432
export DB_NAME=dokimos_prod
export DB_USERNAME=dokimos_app
export DB_PASSWORD=secure-password-here
export DOKIMOS_API_KEY=production-api-key
export LOG_LEVEL=WARN

docker run -d \
  -p 8080:8080 \
  -e DB_HOST -e DB_PORT -e DB_NAME -e DB_USERNAME -e DB_PASSWORD \
  -e DOKIMOS_API_KEY -e LOG_LEVEL \
  dokimos-server
```

### CI/CD Environment

If you'd like to configure the client to report to a shared internal server:

```bash
# In your CI environment
export DOKIMOS_SERVER_URL=https://dokimos.internal.company.com
export DOKIMOS_PROJECT_NAME=my-llm-app
export DOKIMOS_API_KEY=${{ secrets.DOKIMOS_API_KEY }}
```

## Health Checks

The server exposes health endpoints at:

- `/actuator/health` - Overall health status
- `/actuator/info` - Application info

These are useful for load balancer health checks and container orchestration.

```bash
curl http://localhost:8080/actuator/health
```

## Spring Boot Properties

The server is a Spring Boot application, so you can use any Spring Boot configuration property. Common ones:

```bash
# Connection timeout
export SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT=30000

# Maximum pool size
export SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=10

# Server request timeout
export SERVER_TOMCAT_CONNECTION_TIMEOUT=20000
```

See [Spring Boot documentation](https://docs.spring.io/spring-boot/appendix/application-properties/index.html) for all available properties.
