---
sidebar_position: 4
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Deployment

This page shows you how to run the Dokimos server, from your laptop to production. One pre-built Docker image works everywhere. You add configuration as your needs grow.

## Run it locally

Start here to try things out or for individual use. Two commands:

```bash
curl -O https://raw.githubusercontent.com/dokimos-dev/dokimos/master/docker-compose.yml
docker compose up -d
```

Open [http://localhost:8080](http://localhost:8080). Done.

You now have:

- A PostgreSQL database with persistent storage.
- The Dokimos server on port 8080.
- No authentication (open access).

## Run it for your team

Run the server on a shared machine or VM so your team sees the same results. Two steps: turn on an API key, then pin a version.

### Turn on API key authentication

Add one line to `docker-compose.yml`. It protects write operations, so only clients with the key can submit results. Read operations stay open.

```yaml
# docker-compose.yml
services:
  server:
    image: ghcr.io/dokimos-dev/dokimos-server:latest
    environment:
      # ... other env vars ...
      DOKIMOS_API_KEY: your-secret-key  # Add this line
```

Restart the server, then point your clients at it and pass the key:

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
DokimosServerReporter reporter = DokimosServerReporter.builder()
    .serverUrl("http://your-team-server:8080")
    .projectName("my-project")
    .apiKey("your-secret-key")
    .build();
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
val reporter = DokimosServerReporter.builder()
    .serverUrl("http://your-team-server:8080")
    .projectName("my-project")
    .apiKey("your-secret-key")
    .build()
```

  </TabItem>
</Tabs>

See [Authentication](./authentication) for the full setup.

### Pin a version

The `latest` tag moves. Pin a release so upgrades never surprise you:

```yaml
services:
  server:
    image: ghcr.io/dokimos-dev/dokimos-server:0.20.0  # Pin version
```

## Run it in production

For production, swap in a managed database and put a load balancer in front for TLS.

### Use a managed database

Replace the bundled PostgreSQL with a managed service (for example AWS RDS). Set the `DB_*` variables to point at it:

```yaml
# docker-compose.yml (production)
services:
  server:
    image: ghcr.io/dokimos-dev/dokimos-server:0.20.0
    ports:
      - "8080:8080"
    environment:
      DB_HOST: your-rds-endpoint.amazonaws.com
      DB_PORT: 5432
      DB_NAME: dokimos
      DB_USERNAME: dokimos
      DB_PASSWORD: ${DB_PASSWORD}  # Read from an environment variable
      DOKIMOS_API_KEY: ${DOKIMOS_API_KEY}
```

For TLS, put a cloud load balancer in front (AWS ALB, GCP Load Balancer). It terminates TLS for you.

### Run the container directly

No Docker Compose? Run the image yourself and pass the same variables as flags:

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
  ghcr.io/dokimos-dev/dokimos-server:0.20.0
```

## Run it on Kubernetes

Apply this manifest. It creates a Deployment with two replicas plus a LoadBalancer Service. Database password and API key come from a Secret named `dokimos-secrets`.

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
        image: ghcr.io/dokimos-dev/dokimos-server:0.20.0
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

## Health checks

The server exposes two endpoints for load balancers and orchestrators:

- `/actuator/health` is the liveness check.
- `/actuator/health/readiness` is the readiness check.

Point your load balancer at the health path:

```
Health check path: /actuator/health
Interval: 30s
Timeout: 5s
Healthy threshold: 2
Unhealthy threshold: 3
```
