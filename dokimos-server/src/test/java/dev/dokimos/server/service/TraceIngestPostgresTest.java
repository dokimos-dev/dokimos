package dev.dokimos.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.server.config.TraceProperties;
import dev.dokimos.server.dto.v1.TraceIngestResponse;
import dev.dokimos.server.dto.v1.otlp.OtlpExportTraceServiceRequest;
import dev.dokimos.server.entity.LlmConnection;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.entity.Trace;
import dev.dokimos.server.entity.TraceEvalJob;
import dev.dokimos.server.entity.TraceEvalRule;
import dev.dokimos.server.entity.TraceMatchType;
import dev.dokimos.server.repository.LlmConnectionRepository;
import dev.dokimos.server.repository.ProjectRepository;
import dev.dokimos.server.repository.TraceEvalJobRepository;
import dev.dokimos.server.repository.TraceEvalRuleRepository;
import dev.dokimos.server.repository.TraceRepository;
import dev.dokimos.server.repository.TraceSpanRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Proves trace ingestion and online-eval enqueueing against a real PostgreSQL instance (Testcontainers)
 * with the production Flyway schema, including the JSONB attributes column and the foreign-key cascades
 * the retention sweeper relies on. Self-skips when Docker is unavailable so it stays safe in the normal
 * build, mirroring {@link RunServiceDedupPostgresTest}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TraceIngestPostgresTest.TestConfig.class)
class TraceIngestPostgresTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        OtlpTraceParser otlpTraceParser() {
            return new OtlpTraceParser();
        }

        @org.springframework.context.annotation.Bean
        TraceEvalEnqueuer traceEvalEnqueuer(
                TraceEvalRuleRepository ruleRepository, TraceEvalJobRepository jobRepository) {
            return new TraceEvalEnqueuer(ruleRepository, jobRepository);
        }

        @org.springframework.context.annotation.Bean
        TraceProperties traceProperties() {
            return new TraceProperties();
        }

        @org.springframework.context.annotation.Bean
        TraceIngestService traceIngestService(
                OtlpTraceParser parser,
                TraceRepository traceRepository,
                ProjectRepository projectRepository,
                TraceEvalEnqueuer enqueuer,
                TraceProperties properties) {
            return new TraceIngestService(parser, traceRepository, projectRepository, enqueuer, properties);
        }

        @org.springframework.context.annotation.Bean
        TraceRetentionSweeper traceRetentionSweeper(TraceRepository traceRepository) {
            return new TraceRetentionSweeper(traceRepository);
        }
    }

    private static final boolean DOCKER_AVAILABLE =
            DockerClientFactory.instance().isDockerAvailable();

    private static final PostgreSQLContainer<?> postgres;

    static {
        if (DOCKER_AVAILABLE) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine");
            postgres.start();
            awaitConnectable(postgres);
        } else {
            postgres = null;
        }
    }

    private static void awaitConnectable(PostgreSQLContainer<?> container) {
        org.postgresql.ds.PGSimpleDataSource ds = new org.postgresql.ds.PGSimpleDataSource();
        ds.setUrl(container.getJdbcUrl());
        ds.setUser(container.getUsername());
        ds.setPassword(container.getPassword());
        RuntimeException last = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            try (java.sql.Connection ignored = ds.getConnection()) {
                return;
            } catch (Exception e) {
                last = new IllegalStateException("Postgres not yet connectable", e);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for Postgres", ie);
                }
            }
        }
        throw new IllegalStateException("Postgres did not become connectable in time", last);
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (!DOCKER_AVAILABLE) {
            return;
        }
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private TraceIngestService ingestService;

    @Autowired
    private TraceRetentionSweeper sweeper;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private LlmConnectionRepository connectionRepository;

    @Autowired
    private TraceEvalRuleRepository ruleRepository;

    @Autowired
    private TraceRepository traceRepository;

    @Autowired
    private TraceSpanRepository spanRepository;

    @Autowired
    private TraceEvalJobRepository jobRepository;

    @Test
    void ingestPersistsTraceAndEnqueuesMatchingEval() throws Exception {
        assumeTrue(DOCKER_AVAILABLE, "Docker is not available, skipping real-Postgres trace ingestion verification");

        Project project = projectRepository.save(new Project("checkout-" + UUID.randomUUID()));
        LlmConnection connection = new LlmConnection("conn-" + UUID.randomUUID(), "https://api.example.com", "gpt-4");
        connection.setCredentialRef("OPENAI_API_KEY");
        connection = connectionRepository.save(connection);
        ruleRepository.save(new TraceEvalRule(
                project.getId(),
                "answer-quality",
                TraceMatchType.SPAN_NAME,
                "llm.generate",
                connection,
                "judge",
                "is correct"));

        TraceIngestResponse response = ingestService.ingest(decode(otlpJson(project.getName())));

        assertThat(response.acceptedSpans()).isEqualTo(2);
        assertThat(response.rejectedSpans()).isEqualTo(1);
        assertThat(response.traces()).isEqualTo(1);

        Trace trace = traceRepository.findByTraceId("trace-abc").orElseThrow();
        assertThat(trace.getProjectId()).isEqualTo(project.getId());
        assertThat(spanRepository.findByTrace_IdOrderByStartTimeUnixNanoAsc(trace.getId()))
                .hasSize(2);

        List<TraceEvalJob> jobs = jobRepository.findByTracePk(trace.getId());
        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).getEvaluatorName()).isEqualTo("judge");
    }

    @Test
    void sweeperDeletesExpiredTracesAndCascades() throws Exception {
        assumeTrue(DOCKER_AVAILABLE, "Docker is not available, skipping real-Postgres retention verification");

        Project project = projectRepository.save(new Project("expiry-" + UUID.randomUUID()));
        ingestService.ingest(decode(otlpJson(project.getName())));
        Trace trace = traceRepository.findByTraceId("trace-abc").orElseThrow();

        trace.setExpiresAt(Instant.now().minusSeconds(60));
        traceRepository.save(trace);

        sweeper.sweep();

        assertThat(traceRepository.findByTraceId("trace-abc")).isEmpty();
        assertThat(spanRepository.findByTrace_IdOrderByStartTimeUnixNanoAsc(trace.getId()))
                .isEmpty();
    }

    private OtlpExportTraceServiceRequest decode(String json) throws Exception {
        return objectMapper.readValue(json, OtlpExportTraceServiceRequest.class);
    }

    private String otlpJson(String projectName) {
        return """
                {
                  "resourceSpans": [{
                    "resource": {"attributes": [{"key": "dokimos.project", "value": {"stringValue": "%s"}}]},
                    "scopeSpans": [{
                      "spans": [
                        {
                          "traceId": "trace-abc",
                          "spanId": "span-1",
                          "name": "llm.generate",
                          "startTimeUnixNano": "1700000000000000000",
                          "endTimeUnixNano": "1700000001000000000",
                          "attributes": [
                            {"key": "input", "value": {"stringValue": "what is 2+2"}},
                            {"key": "output", "value": {"stringValue": "4"}}
                          ]
                        },
                        {
                          "traceId": "trace-abc",
                          "spanId": "span-2",
                          "parentSpanId": "span-1",
                          "name": "db.query",
                          "attributes": [{"key": "rows", "value": {"intValue": "3"}}]
                        },
                        {"spanId": "no-trace", "name": "broken"}
                      ]
                    }]
                  }]
                }
                """.formatted(projectName);
    }
}
