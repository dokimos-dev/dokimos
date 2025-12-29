# Build stage
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

# Copy parent pom and module poms for dependency resolution
COPY pom.xml ./pom.xml
COPY dokimos-core/pom.xml ./dokimos-core/pom.xml
COPY dokimos-server/pom.xml ./dokimos-server/pom.xml

# Copy source code
COPY dokimos-core/src ./dokimos-core/src
COPY dokimos-server/src ./dokimos-server/src

# Build the application
RUN apt-get update && apt-get install -y maven && \
    mvn clean package -pl dokimos-server -am -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre

WORKDIR /app

# Install curl for healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -r dokimos && useradd -r -g dokimos dokimos

# Copy the built jar
COPY --from=builder /app/dokimos-server/target/*.jar app.jar

# Change ownership
RUN chown -R dokimos:dokimos /app

USER dokimos

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
