---
sidebar_position: 3
---

# Configuration

This page lists every setting that controls the Dokimos server, so you can wire it up to your database, lock down writes, and tune the background workers.

You configure the server with environment variables. The defaults run out of the box with `docker compose up`, so you only set what you need to change.

## Quick start

For local development you set nothing. Start the server with the bundled PostgreSQL:

```bash
docker compose up
```

To connect to your own database and require an API key for writes, set five variables:

```bash
export DB_HOST=your-postgres-host
export DB_NAME=dokimos
export DB_USERNAME=dokimos
export DB_PASSWORD=your-secure-password
export DOKIMOS_API_KEY=your-secret-key
```

The rest of this page explains each variable and shows full example configurations.

## Environment variables

### Database connection

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | PostgreSQL hostname | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `dokimos` |
| `DB_USERNAME` | Database username | `dokimos` |
| `DB_PASSWORD` | Database password | `dokimos` |

### Server settings

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | HTTP port to listen on | `8080` |
| `DOKIMOS_API_KEY` | API key for write operations | _(disabled)_ |
| `DOKIMOS_ENCRYPTION_KEY` | Passphrase used to encrypt inline LLM connection keys at rest. Required only if you store an inline `apiKey` on a connection. | _(disabled)_ |

### Server side judge

These variables tune the background worker that scores [LLM judge](./llm-judge) jobs. The defaults work for most deployments, so change them only if you need to.

| Variable | Description | Default |
|----------|-------------|---------|
| `DOKIMOS_JUDGE_POLL_INTERVAL_MS` | How often the worker polls for pending judge jobs | `5000` |
| `DOKIMOS_JUDGE_MAX_ATTEMPTS` | Retry ceiling for a judge job before it fails | `3` |
| `DOKIMOS_JUDGE_PAGE_SIZE` | Items scored per database transaction | `50` |

### Traces and online evals

These variables control [production trace](./traces) retention and the online eval worker.

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

## Database setup

### PostgreSQL requirements

The server needs PostgreSQL 14 or higher. Flyway manages the schema for you and runs the migrations on startup.

### Connection string format

The server builds the JDBC URL from the database variables:

```
jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
```

To pass extra connection parameters, set the Spring datasource URL directly instead:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/dokimos?ssl=true&sslmode=require
```

### Create the database

To use an existing PostgreSQL instance, create the database and user first:

```sql
CREATE DATABASE dokimos;
CREATE USER dokimos WITH PASSWORD 'your-secure-password';
GRANT ALL PRIVILEGES ON DATABASE dokimos TO dokimos;

-- Connect to the dokimos database and grant schema permissions
\c dokimos
GRANT ALL ON SCHEMA public TO dokimos;
```

### Schema migrations

Migrations run automatically on startup. Flyway does three things:

- Creates tables if they do not exist.
- Applies new migrations in order.
- Never drops or modifies existing data destructively.

## API key configuration

Set `DOKIMOS_API_KEY` to require authentication on write operations:

```bash
export DOKIMOS_API_KEY=your-secret-key-here
```

Read operations stay open. See [Authentication](./authentication) for how the API key check works.

## Port and host binding

### Change the port

```bash
export SERVER_PORT=3000
```

### Bind to all interfaces

The server binds to all interfaces (`0.0.0.0`) by default.

To restrict it to localhost during local development, map the port with Docker:

```yaml
ports:
  - "127.0.0.1:8080:8080"
```

## Example configurations

### Local development

For local development, set nothing. The bundled `docker-compose` provides PostgreSQL:

```bash
docker compose up
```

### Development with an API key

To test authentication locally, set the API key before you start:

```bash
export DOKIMOS_API_KEY=dev-secret-key
docker compose up
```

### Production with an external database

To connect to a managed PostgreSQL instance, set the database variables and an API key:

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

### CI/CD environment

To point the client at a shared internal server from CI, set these variables in your pipeline:

```bash
# In your CI environment
export DOKIMOS_SERVER_URL=https://dokimos.internal.company.com
export DOKIMOS_PROJECT_NAME=my-llm-app
export DOKIMOS_API_KEY=${{ secrets.DOKIMOS_API_KEY }}
```

## Health checks

The server exposes two health endpoints:

- `/actuator/health` reports overall health status.
- `/actuator/info` reports application info.

Use these for load balancer health checks and container orchestration:

```bash
curl http://localhost:8080/actuator/health
```

## Spring Boot properties

The server is a Spring Boot application, so you can set any Spring Boot configuration property. Common ones:

```bash
# Connection timeout
export SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT=30000

# Maximum pool size
export SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=10

# Server request timeout
export SERVER_TOMCAT_CONNECTION_TIMEOUT=20000
```

See the [Spring Boot documentation](https://docs.spring.io/spring-boot/appendix/application-properties/index.html) for the full list of properties.
