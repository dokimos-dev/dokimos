# Dokimos Server

The Dokimos server stores experiment results, provides a web UI for viewing and comparing runs, and tracks quality over time. It allows teams to centralize their evaluation data, share findings, and debug failures across experiments.

## Quick Start

Get up and running with a pre-built Docker image:

```bash
curl -O https://raw.githubusercontent.com/dokimos-dev/dokimos/master/docker-compose.yml
docker compose up -d
```

Open [http://localhost:8080](http://localhost:8080) to see the UI.

## Configuration

Configure the server using environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | PostgreSQL host | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `dokimos` |
| `DB_USERNAME` | Database username | `dokimos` |
| `DB_PASSWORD` | Database password | `dokimos` |
| `DOKIMOS_API_KEY` | API key for write operations (optional) | _(disabled)_ |
| `SERVER_PORT` | Server port | `8080` |
| `LOG_LEVEL` | Application log level | `INFO` |

## Authentication

### API Key Authentication

When `DOKIMOS_API_KEY` is set, write operations (POST, PUT, PATCH, DELETE) require the API key in the `Authorization` header:

```
Authorization: Bearer your-secret-key
```

**Read operations** (GET requests, including the web UI) are always open by default.

If `DOKIMOS_API_KEY` is not set or empty, authentication is disabled and all requests are allowed.

### UI Authentication

The web UI does not have built-in authentication. For production deployments where you need to restrict access to the dashboard, we recommend placing the server behind a reverse proxy (nginx, Traefik, Cloudflare Access, etc.) that handles authentication.

For more details, see the [authentication documentation](https://dokimos.dev/server/authentication).

## Connecting the Client

Use `DokimosServerReporter` to send experiment results to the server:

```java
DokimosServerReporter reporter = DokimosServerReporter.builder()
    .serverUrl("http://localhost:8080")
    .projectName("my-project")
    .apiKey("your-secret-key")  // Optional, only if auth is enabled
    .build();

ExperimentResult result = Experiment.builder()
    .name("my-experiment")
    .dataset(dataset)
    .task(task)
    .evaluators(evaluators)
    .reporter(reporter)
    .build()
    .run();
```

Or configure from environment variables:

```bash
export DOKIMOS_SERVER_URL=http://localhost:8080
export DOKIMOS_PROJECT_NAME=my-project
export DOKIMOS_API_KEY=your-secret-key  # Optional
```

```java
DokimosServerReporter reporter = DokimosServerReporter.fromEnvironment();
```

For more details, see the [client documentation](https://dokimos.dev/server/client).

## Deployment Options

### Pre-built Image (Recommended)

The root `docker-compose.yml` pulls the pre-built image from GitHub Container Registry:

```bash
curl -O https://raw.githubusercontent.com/dokimos-dev/dokimos/master/docker-compose.yml
docker compose up -d
```

You can pin a specific version:

```yaml
services:
  server:
    image: ghcr.io/dokimos-dev/dokimos-server:0.1.0  # Pin to specific version
```

### Docker (Bring Your Own Database)

If you have an existing PostgreSQL instance, run just the server container:

```bash
docker run -d \
  -p 8080:8080 \
  -e DB_HOST=your-postgres-host \
  -e DB_PORT=5432 \
  -e DB_NAME=dokimos \
  -e DB_USERNAME=your-user \
  -e DB_PASSWORD=your-password \
  -e DOKIMOS_API_KEY=your-secret-key \
  ghcr.io/dokimos-dev/dokimos-server:latest
```

### Kubernetes

A Helm chart is planned. For now, use standard Kubernetes manifests with the Docker image and configure via environment variables.

## API Documentation

Swagger UI is available at [/swagger-ui.html](http://localhost:8080/swagger-ui.html) when the server is running.

The OpenAPI spec is available at `/v3/api-docs`.

---

## For Contributors

### Building from Source

Use the development compose file to build locally:

```bash
git clone https://github.com/dokimos-dev/dokimos.git
cd dokimos/dokimos-server
docker compose -f docker-compose.dev.yml up --build
```

### Running Locally (without Docker)

1. Start PostgreSQL:

```bash
docker run -d \
  --name dokimos-postgres \
  -e POSTGRES_DB=dokimos \
  -e POSTGRES_USER=dokimos \
  -e POSTGRES_PASSWORD=dokimos \
  -p 5432:5432 \
  postgres:16-alpine
```

2. Build and run the server:

```bash
# From the repository root
mvn clean install -DskipTests

cd dokimos-server
mvn spring-boot:run
```

The server starts at [http://localhost:8080](http://localhost:8080).

### Running Tests

```bash
mvn test -pl dokimos-server        # Unit tests
```

### Building the Docker Image

```bash
# From the repository root
docker build -t dokimos-server -f dokimos-server/Dockerfile .
```

## Further Reading

- [Server Overview](https://dokimos.dev/server/overview)
- [Configuration Reference](https://dokimos.dev/server/configuration)
- [Client Documentation](https://dokimos.dev/server/client)
- [Authentication](https://dokimos.dev/server/authentication)
