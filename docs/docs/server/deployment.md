---
sidebar_position: 4
---

# Deployment

The same pre-built Docker image works everywhere—from your local machine to production. This guide shows you how to progressively add configuration as your needs grow.

## Local Development

Perfect for trying things out or individual use.

```bash
curl -O https://raw.githubusercontent.com/dokimos-dev/dokimos/master/docker-compose.yml
docker compose up -d
```

Open [http://localhost:8080](http://localhost:8080). Done.

**What you get:**
- PostgreSQL database with persistent storage
- Dokimos server on port 8080
- No authentication (open access)

## Team Server

Share results across your team by running on a shared machine or VM.

### Add API Key Authentication

Protect write operations so only authorized clients can submit results:

```yaml
# docker-compose.yml
services:
  server:
    image: ghcr.io/dokimos-dev/dokimos-server:latest
    environment:
      # ... other env vars ...
      DOKIMOS_API_KEY: your-secret-key  # Add this line
```

Clients now need to include the API key:

```java
DokimosServerReporter reporter = DokimosServerReporter.builder()
    .serverUrl("http://your-team-server:8080")
    .projectName("my-project")
    .apiKey("your-secret-key")
    .build();
```

See [Authentication](./authentication) for details.

### Pin a Specific Version

Avoid surprises by pinning to a release version:

```yaml
services:
  server:
    image: ghcr.io/dokimos-dev/dokimos-server:0.1.0  # Pin version
```

## Production

For production deployments, add a managed database and reverse proxy.

### Use a Managed Database

Replace the bundled PostgreSQL with a managed service:

```yaml
# docker-compose.yml (production)
services:
  server:
    image: ghcr.io/dokimos-dev/dokimos-server:0.1.0
    ports:
      - "8080:8080"
    environment:
      DB_HOST: your-rds-endpoint.amazonaws.com
      DB_PORT: 5432
      DB_NAME: dokimos
      DB_USERNAME: dokimos
      DB_PASSWORD: ${DB_PASSWORD}  # Use environment variable
      DOKIMOS_API_KEY: ${DOKIMOS_API_KEY}
```

Or use cloud load balancers (AWS ALB, GCP Load Balancer) which handle TLS termination.

### Run with Docker

Without Docker Compose, run the container directly:

```bash
docker run -d \
  --name dokimos-server \
  -p 8080:8080 \
  -e DB_HOST=your-postgres-host \
  -e DB_PORT=5432 \
  -e DB_NAME=dokimos \
  -e DB_USERNAME=your-user \
  -e DB_PASSWORD=your-password \
  -e DOKIMOS_API_KEY=your-api-key \
  ghcr.io/dokimos-dev/dokimos-server:0.1.0
```

## Cloud Platforms

### Kubernetes

Basic deployment manifest:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: dokimos-server
spec:
  replicas: 2
  selector:
    matchLabels:
      app: dokimos-server
  template:
    metadata:
      labels:
        app: dokimos-server
    spec:
      containers:
      - name: server
        image: ghcr.io/dokimos-dev/dokimos-server:0.1.0
        ports:
        - containerPort: 8080
        env:
        - name: DB_HOST
          value: postgres-service
        - name: DB_NAME
          value: dokimos
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: dokimos-secrets
              key: db-password
        - name: DOKIMOS_API_KEY
          valueFrom:
            secretKeyRef:
              name: dokimos-secrets
              key: api-key
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 10
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
---
apiVersion: v1
kind: Service
metadata:
  name: dokimos-server
spec:
  selector:
    app: dokimos-server
  ports:
  - port: 80
    targetPort: 8080
  type: LoadBalancer
```

## Health Checks

The server exposes health endpoints for load balancers and orchestrators:

- `/actuator/health` - Liveness check
- `/actuator/health/readiness` - Readiness check

Configure your load balancer:
```
Health check path: /actuator/health
Interval: 30s
Timeout: 5s
Healthy threshold: 2
Unhealthy threshold: 3
```


