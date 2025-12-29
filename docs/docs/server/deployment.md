---
sidebar_position: 4
---

# Deployment

This guide covers different deployment options for the Dokimos server, from local development to production.

## Docker Compose (Development / Small Teams)

The simplest deployment option. Good for:
- Local development
- Small teams (< 10 people)
- Non-critical workloads

### Full docker-compose.yml

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
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 30s

volumes:
  postgres_data:
```

### Usage

```bash
# Start
docker compose up -d

# View logs
docker compose logs -f server

# Stop
docker compose down

# Stop and remove data
docker compose down -v
```

## Docker Standalone (Bring Your Own Database)

When you have an existing PostgreSQL instance or want to use a managed database.

### Build the Image

```bash
# From the repository root
docker build -t dokimos-server -f dokimos-server/Dockerfile .
```

### Run with External Database

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
  dokimos-server
```

## Production Considerations

### External PostgreSQL

For production, use a managed PostgreSQL service:

- **AWS**: RDS for PostgreSQL or Aurora PostgreSQL
- **GCP**: Cloud SQL for PostgreSQL
- **Azure**: Azure Database for PostgreSQL

Benefits:
- Automated backups
- Point-in-time recovery
- High availability
- Managed security patches

### Backups

If running PostgreSQL yourself, set up regular backups:

```bash
# Daily backup script
#!/bin/bash
BACKUP_DIR=/backups
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
pg_dump -h localhost -U dokimos dokimos | gzip > $BACKUP_DIR/dokimos_$TIMESTAMP.sql.gz

# Keep last 30 days
find $BACKUP_DIR -name "dokimos_*.sql.gz" -mtime +30 -delete
```

### Resource Sizing

**Small** (< 100 runs/day, < 10 concurrent users):
- Server: 1 vCPU, 1GB RAM
- Database: 1 vCPU, 1GB RAM, 10GB storage

**Medium** (< 1000 runs/day, < 50 concurrent users):
- Server: 2 vCPU, 2GB RAM
- Database: 2 vCPU, 4GB RAM, 50GB storage

**Large** (> 1000 runs/day, > 50 concurrent users):
- Server: 4 vCPU, 4GB RAM (consider multiple replicas)
- Database: 4 vCPU, 8GB RAM, 100GB+ storage (consider read replicas)

### Horizontal Scaling

The server is stateless and can run multiple replicas behind a load balancer:

```yaml
# Docker Compose with replicas
services:
  server:
    deploy:
      replicas: 3
```

## Cloud Deployment

### AWS ECS

Example task definition:

```json
{
  "family": "dokimos-server",
  "containerDefinitions": [
    {
      "name": "server",
      "image": "your-registry/dokimos-server:latest",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        { "name": "DB_HOST", "value": "your-rds-endpoint" },
        { "name": "DB_PORT", "value": "5432" },
        { "name": "DB_NAME", "value": "dokimos" }
      ],
      "secrets": [
        {
          "name": "DB_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:region:account:secret:dokimos-db"
        },
        {
          "name": "DOKIMOS_API_KEY",
          "valueFrom": "arn:aws:secretsmanager:region:account:secret:dokimos-api-key"
        }
      ],
      "healthCheck": {
        "command": ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"],
        "interval": 30,
        "timeout": 5,
        "retries": 3
      },
      "memory": 1024,
      "cpu": 512
    }
  ]
}
```

### GCP Cloud Run

```bash
# Deploy to Cloud Run
gcloud run deploy dokimos-server \
  --image gcr.io/your-project/dokimos-server:latest \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --set-env-vars "DB_HOST=your-cloud-sql-ip" \
  --set-env-vars "DB_NAME=dokimos" \
  --set-secrets "DB_PASSWORD=dokimos-db-password:latest" \
  --set-secrets "DOKIMOS_API_KEY=dokimos-api-key:latest" \
  --add-cloudsql-instances your-project:us-central1:dokimos-db
```

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
        image: your-registry/dokimos-server:latest
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
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
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

A Helm chart is planned for easier Kubernetes deployment.

## Health Checks and Monitoring

### Health Endpoints

The server exposes:
- `/actuator/health` - Liveness check (is the server running?)
- `/actuator/health/readiness` - Readiness check (can it handle requests?)

### Load Balancer Configuration

Configure your load balancer to check `/actuator/health`:

```
Health check path: /actuator/health
Healthy threshold: 2
Unhealthy threshold: 3
Interval: 30s
Timeout: 5s
```

### Logging

The server outputs structured logs to stdout. In production:

1. Collect logs with your preferred aggregator (CloudWatch, Stackdriver, ELK, etc.)
2. Set `LOG_LEVEL=WARN` to reduce noise
3. Monitor for `ERROR` level entries

### Metrics

The server exposes Prometheus metrics at `/actuator/prometheus` (if enabled):

```bash
# Enable Prometheus metrics
export MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,prometheus
```

## SSL/TLS

The server itself doesn't handle TLS. Use one of these approaches:

1. **Reverse proxy**: nginx, Traefik, or cloud load balancer handles TLS
2. **Cloud provider**: AWS ALB, GCP Load Balancer, etc.
3. **Service mesh**: Istio, Linkerd for mTLS

Example nginx TLS termination:

```nginx
server {
    listen 443 ssl;
    server_name dokimos.example.com;

    ssl_certificate /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;

    location / {
        proxy_pass http://dokimos-server:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```
