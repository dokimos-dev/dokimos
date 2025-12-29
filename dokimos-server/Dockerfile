FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy all pom.xml files so Maven can parse the multi-module structure
COPY pom.xml ./pom.xml
COPY dokimos-core/pom.xml ./dokimos-core/pom.xml
COPY dokimos-junit5/pom.xml ./dokimos-junit5/pom.xml
COPY dokimos-langchain4j/pom.xml ./dokimos-langchain4j/pom.xml
COPY dokimos-spring-ai/pom.xml ./dokimos-spring-ai/pom.xml
COPY dokimos-server-client/pom.xml ./dokimos-server-client/pom.xml
COPY dokimos-server/pom.xml ./dokimos-server/pom.xml
COPY dokimos-examples/pom.xml ./dokimos-examples/pom.xml

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl dokimos-server -am -q || true

# Copy only source code needed for the server
COPY dokimos-core/src ./dokimos-core/src
COPY dokimos-server/src ./dokimos-server/src

# Build the application
RUN mvn clean package -pl dokimos-server -am -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre

WORKDIR /app

# Install curl for healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -r dokimos && useradd -r -g dokimos dokimos

COPY --from=builder /app/dokimos-server/target/*.jar app.jar

RUN chown -R dokimos:dokimos /app

USER dokimos

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
