FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy every module pom.xml so Maven can parse the multi-module reactor
COPY pom.xml ./pom.xml
COPY dokimos-core/pom.xml ./dokimos-core/pom.xml
COPY dokimos-junit/pom.xml ./dokimos-junit/pom.xml
COPY dokimos-langchain4j/pom.xml ./dokimos-langchain4j/pom.xml
COPY dokimos-spring-ai/pom.xml ./dokimos-spring-ai/pom.xml
COPY dokimos-spring-ai-alibaba/pom.xml ./dokimos-spring-ai-alibaba/pom.xml
COPY dokimos-openai/pom.xml ./dokimos-openai/pom.xml
COPY dokimos-koog/pom.xml ./dokimos-koog/pom.xml
COPY dokimos-kotlin/pom.xml ./dokimos-kotlin/pom.xml
COPY dokimos-server-client/pom.xml ./dokimos-server-client/pom.xml
COPY dokimos-server/pom.xml ./dokimos-server/pom.xml
COPY dokimos-examples/pom.xml ./dokimos-examples/pom.xml
COPY dokimos-mcp-server/pom.xml ./dokimos-mcp-server/pom.xml

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl dokimos-mcp-server -am -q || true

# Copy source needed for the MCP server and its dependency
COPY dokimos-core/src ./dokimos-core/src
COPY dokimos-mcp-server/src ./dokimos-mcp-server/src

# Build the shaded executable JAR
RUN mvn clean package -pl dokimos-mcp-server -am -DskipTests

# Extract the shaded JAR (skip sources, javadoc, and the unshaded original)
RUN find dokimos-mcp-server/target -maxdepth 1 -name "dokimos-mcp-server-*.jar" \
    ! -name "*-sources.jar" ! -name "*-javadoc.jar" \
    | head -1 | xargs -I{} cp {} /app/app.jar

# Runtime stage
FROM eclipse-temurin:17-jre

WORKDIR /app

# Run as a non-root user with a writable home for the results store
RUN groupadd -r dokimos && useradd -r -g dokimos -m -d /home/dokimos dokimos
ENV HOME=/home/dokimos

COPY --from=builder /app/app.jar app.jar

# Run results persist here; mount a volume to keep them across container runs
RUN mkdir -p /home/dokimos/.dokimos && chown -R dokimos:dokimos /home/dokimos /app

USER dokimos

# The server speaks JSON-RPC over stdio. Run with: docker run -i --rm ...
ENTRYPOINT ["java", "-jar", "app.jar"]
