# Dokimos Server

The Dokimos server stores experiment results, provides a web UI for viewing and comparing runs, and tracks quality over time. It allows teams to centralize their evaluation data, share findings, and debug failures across experiments.

## Quick Start

Get up and running quickly with `docker-compose`:

```bash
cd dokimos-server
docker compose up
```

Once started, open [http://localhost:8080](http://localhost:8080) to see the UI.

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

## Deployment Options

### Docker Compose (Development / Small Teams)

The included `docker-compose.yml` runs both the server and PostgreSQL:

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: dokimos-postgres
    environment:
      POSTGRES_DB: dokimos
      POSTGRES_USER: dokimos
      POSTGRES_PASSWORD: dokimos
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U dokimos -d dokimos"]
      interval: 10s
      timeout: 5s
      retries: 5

  server:
    build:
      context: ..
      dockerfile: dokimos-server/Dockerfile
    container_name: dokimos-server
    ports:
      - "8080:8080"
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: dokimos
      DB_USERNAME: dokimos
      DB_PASSWORD: dokimos
      # DOKIMOS_API_KEY: your-secret-key  # Uncomment to enable auth
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  postgres_data:
```

Start with:

```bash
docker compose up -d
```

### Docker (Bring Your Own Database)

If you have an existing PostgreSQL instance, run just the server container:

```bash
docker build -t dokimos-server -f dokimos-server/Dockerfile .

docker run -d \
  -p 8080:8080 \
  -e DB_HOST=your-postgres-host \
  -e DB_PORT=5432 \
  -e DB_NAME=dokimos \
  -e DB_USERNAME=your-user \
  -e DB_PASSWORD=your-password \
  -e DOKIMOS_API_KEY=your-secret-key \
  dokimos-server
```

### Kubernetes

A Helm chart is planned. For now, use standard Kubernetes manifests with the Docker image and configure via environment variables.

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

## API Documentation

Swagger UI is available at [/swagger-ui.html](http://localhost:8080/swagger-ui.html) when the server is running.

The OpenAPI spec is available at `/v3/api-docs`.

## Development

### Prerequisites

- JDK 17 or higher
- Maven 3.6+
- Docker (for running PostgreSQL)

### Running Locally

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

# Run the server
cd dokimos-server
mvn spring-boot:run
```

The server starts at [http://localhost:8080](http://localhost:8080).

### Running Tests

```bash
# Unit tests
mvn test -pl dokimos-server

# All tests including integration tests
mvn verify -pl dokimos-server
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
