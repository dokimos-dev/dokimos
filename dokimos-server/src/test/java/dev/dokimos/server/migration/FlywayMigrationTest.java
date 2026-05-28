package dev.dokimos.server.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Verifies the V1 to V2 Flyway migration against a real PostgreSQL instance. Self-skips when Docker
 * is unavailable so it stays safe in the normal build. Not tagged as an integration test: when
 * Docker is present it runs as part of {@code mvn test}.
 *
 * <p>A single container is shared across the test methods and the schema is dropped (Flyway clean)
 * before each test so every test starts from an empty database.
 */
class FlywayMigrationTest {

    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void startContainer() throws Exception {
        assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available, skipping migration verification");
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
        awaitConnectable();
    }

    /**
     * Polls the mapped JDBC port until it accepts a connection. The container readiness probe runs
     * inside the Docker network, but the host side of the port forward (for example under Colima)
     * can lag by a moment, so we wait until the host can actually open a JDBC connection.
     */
    private static void awaitConnectable() throws Exception {
        DataSource ds = dataSource();
        Exception last = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            try (Connection ignored = ds.getConnection()) {
                return;
            } catch (Exception e) {
                last = e;
                Thread.sleep(500);
            }
        }
        throw new IllegalStateException("Postgres did not become connectable in time", last);
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void resetSchema() {
        assumeTrue(postgres != null && postgres.isRunning(), "Postgres container is not running");
        Flyway.configure().dataSource(dataSource()).cleanDisabled(false).load().clean();
    }

    private static DataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        return ds;
    }

    @Test
    void backfillComputesMaterializedCountsForLegacyRuns() throws Exception {
        DataSource ds = dataSource();

        Flyway.configure().dataSource(ds).target("1").load().migrate();

        UUID projectId = UUID.randomUUID();
        UUID experimentId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID emptyRunId = UUID.randomUUID();
        UUID runningRunId = UUID.randomUUID();

        try (Connection conn = ds.getConnection()) {
            insertProject(conn, projectId, "legacy-project");
            insertExperiment(conn, experimentId, projectId, "legacy-experiment");
            insertRun(conn, runId, experimentId, "SUCCESS");
            insertRun(conn, emptyRunId, experimentId, "SUCCESS");
            insertRun(conn, runningRunId, experimentId, "RUNNING");

            // Item 1: all evals pass -> passing item.
            UUID item1 = UUID.randomUUID();
            insertItem(conn, item1, runId);
            insertEval(conn, item1, "exact", true);
            insertEval(conn, item1, "semantic", true);

            // Item 2: one eval fails -> failing item.
            UUID item2 = UUID.randomUUID();
            insertItem(conn, item2, runId);
            insertEval(conn, item2, "exact", true);
            insertEval(conn, item2, "semantic", false);

            // Item 3: no eval results -> not counted as passed (mirrors countItemsWithAllEvalsPassed).
            UUID item3 = UUID.randomUUID();
            insertItem(conn, item3, runId);

            // RUNNING run with passing items: must NOT be backfilled (counts written only at completion).
            UUID runningItem = UUID.randomUUID();
            insertItem(conn, runningItem, runningRunId);
            insertEval(conn, runningItem, "exact", true);
            insertEval(conn, runningItem, "semantic", true);
        }

        Flyway.configure().dataSource(ds).target("2").load().migrate();

        try (Connection conn = ds.getConnection()) {
            // New provenance columns exist.
            assertColumnExists(conn, "experiment_runs", "name");
            assertColumnExists(conn, "experiment_runs", "git_sha");
            assertColumnExists(conn, "experiment_runs", "git_branch");
            assertColumnExists(conn, "experiment_runs", "triggered_by");
            assertColumnExists(conn, "experiment_runs", "tenant_id");
            assertColumnExists(conn, "projects", "tenant_id");
            assertColumnExists(conn, "experiments", "tenant_id");
            assertColumnExists(conn, "eval_results", "metadata");

            // Backfill: 3 items, 1 passing.
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT item_count, passed_count, pass_rate FROM experiment_runs WHERE id = ?")) {
                ps.setObject(1, runId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt("item_count")).isEqualTo(3);
                    assertThat(rs.getInt("passed_count")).isEqualTo(1);
                    assertThat(rs.getDouble("pass_rate")).isEqualTo(1.0 / 3.0);
                }
            }

            // Empty run: pass_rate NULL.
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT item_count, passed_count, pass_rate FROM experiment_runs WHERE id = ?")) {
                ps.setObject(1, emptyRunId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt("item_count")).isZero();
                    assertThat(rs.getInt("passed_count")).isZero();
                    rs.getObject("pass_rate");
                    assertThat(rs.wasNull()).isTrue();
                }
            }

            // RUNNING run: not backfilled despite having a passing item. Materialized counts are
            // written only at completion, so it keeps the column defaults (0 / 0 / NULL).
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT item_count, passed_count, pass_rate FROM experiment_runs WHERE id = ?")) {
                ps.setObject(1, runningRunId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt("item_count")).isZero();
                    assertThat(rs.getInt("passed_count")).isZero();
                    rs.getObject("pass_rate");
                    assertThat(rs.wasNull()).isTrue();
                }
            }
        }
    }

    /**
     * Exercises the incremental V1 to V2 path for the eval_results metadata column. A row is inserted
     * under the V1 schema (no metadata column), then V2 is applied. We assert the JSONB column was
     * added cleanly to the existing table, the pre-existing row carries NULL metadata, that row can be
     * updated with a JSON value, and a freshly inserted row accepts and queries JSON too.
     */
    @Test
    void evalResultsMetadataColumnAcceptsJson() throws Exception {
        DataSource ds = dataSource();

        // Migrate to V1 only and insert an eval_results row under the pre-metadata schema.
        Flyway.configure().dataSource(ds).target("1").load().migrate();

        UUID projectId = UUID.randomUUID();
        UUID experimentId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID legacyEvalId = UUID.randomUUID();
        UUID newEvalId = UUID.randomUUID();

        try (Connection conn = ds.getConnection()) {
            insertProject(conn, projectId, "json-project");
            insertExperiment(conn, experimentId, projectId, "json-experiment");
            insertRun(conn, runId, experimentId);
            insertItem(conn, itemId, runId);
            insertEval(conn, itemId, "judge", true, legacyEvalId);
        }

        // Apply the V1 to V2 migration, adding the metadata JSONB column to the existing table.
        Flyway.configure().dataSource(ds).target("2").load().migrate();

        try (Connection conn = ds.getConnection()) {
            assertColumnExists(conn, "eval_results", "metadata");

            // The pre-existing row has NULL metadata after the column was added.
            try (PreparedStatement ps = conn.prepareStatement("SELECT metadata FROM eval_results WHERE id = ?")) {
                ps.setObject(1, legacyEvalId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    rs.getObject("metadata");
                    assertThat(rs.wasNull()).isTrue();
                }
            }

            // The old row accepts a JSON value via update and queries back.
            try (PreparedStatement ps =
                    conn.prepareStatement("UPDATE eval_results SET metadata = ?::jsonb WHERE id = ?")) {
                ps.setString(1, "{\"model\":\"gpt-3.5\",\"tokens\":10}");
                ps.setObject(2, legacyEvalId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT metadata->>'model' AS model FROM eval_results WHERE id = ?")) {
                ps.setObject(1, legacyEvalId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("model")).isEqualTo("gpt-3.5");
                }
            }

            // A freshly inserted row accepts and queries JSON too.
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO eval_results (id, item_result_id, evaluator_name, score, success, metadata) "
                            + "VALUES (?, ?, ?, ?, ?, ?::jsonb)")) {
                ps.setObject(1, newEvalId);
                ps.setObject(2, itemId);
                ps.setString(3, "judge");
                ps.setDouble(4, 0.9);
                ps.setBoolean(5, true);
                ps.setString(6, "{\"model\":\"gpt-4\",\"tokens\":42}");
                ps.executeUpdate();
            }
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT metadata->>'model' AS model FROM eval_results WHERE id = ?")) {
                ps.setObject(1, newEvalId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("model")).isEqualTo("gpt-4");
                }
            }
        }
    }

    /**
     * Verifies the V1 to V2 to V3 path creates the ingested_batches table and that its composite
     * primary key (run_id, idempotency_key) rejects a duplicate row while allowing the same key for a
     * different run.
     */
    @Test
    void ingestedBatchesTableExistsAndEnforcesPrimaryKey() throws Exception {
        DataSource ds = dataSource();

        // Apply the migrations incrementally (V1 to V2, then V3) so the test exercises V3 layered on
        // top of an already-migrated schema, matching the incremental path the other cases follow.
        Flyway.configure().dataSource(ds).target("2").load().migrate();
        Flyway.configure().dataSource(ds).target("3").load().migrate();

        UUID projectId = UUID.randomUUID();
        UUID experimentId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID otherRunId = UUID.randomUUID();

        try (Connection conn = ds.getConnection()) {
            assertColumnExists(conn, "ingested_batches", "run_id");
            assertColumnExists(conn, "ingested_batches", "idempotency_key");
            assertColumnExists(conn, "ingested_batches", "created_at");

            insertProject(conn, projectId, "batch-project");
            insertExperiment(conn, experimentId, projectId, "batch-experiment");
            insertRun(conn, runId, experimentId, "RUNNING");
            insertRun(conn, otherRunId, experimentId, "RUNNING");

            insertIngestedBatch(conn, runId, "key-1");

            // The same (run_id, idempotency_key) is rejected by the primary key.
            assertThatThrownBy(() -> insertIngestedBatch(conn, runId, "key-1"))
                    .isInstanceOf(java.sql.SQLException.class);

            // The same key for a different run is allowed.
            insertIngestedBatch(conn, otherRunId, "key-1");

            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT COUNT(*) FROM ingested_batches WHERE idempotency_key = ?")) {
                ps.setString(1, "key-1");
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(2);
                }
            }
        }
    }

    /**
     * Exercises the V3 to V4 path that converts item_results.input/expected_output/actual_output from
     * TEXT (JSON-as-string) to native JSONB. A row is inserted under the pre-V4 schema with valid JSON
     * text values, then V4 is applied. We assert the three columns report a jsonb data type afterward
     * and that a stored value round-trips as a JSON object (queried via the ->> operator).
     */
    @Test
    void itemResultsColumnsBecomeJsonbAndPreserveValues() throws Exception {
        DataSource ds = dataSource();

        // Migrate up to V3 (pre-JSONB schema) and insert an item_results row with TEXT JSON values.
        Flyway.configure().dataSource(ds).target("3").load().migrate();

        UUID projectId = UUID.randomUUID();
        UUID experimentId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        try (Connection conn = ds.getConnection()) {
            insertProject(conn, projectId, "jsonb-project");
            insertExperiment(conn, experimentId, projectId, "jsonb-experiment");
            insertRun(conn, runId, experimentId);

            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO item_results "
                    + "(id, run_id, input, expected_output, actual_output, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, itemId);
                ps.setObject(2, runId);
                ps.setString(3, "{\"q\":\"hello\"}");
                ps.setString(4, "{\"a\":\"world\"}");
                ps.setString(5, "{\"a\":\"world\"}");
                ps.setObject(6, Instant.now().atOffset(java.time.ZoneOffset.UTC));
                ps.executeUpdate();
            }
        }

        // Apply the V4 migration, converting the three TEXT columns to JSONB.
        Flyway.configure().dataSource(ds).target("4").load().migrate();

        try (Connection conn = ds.getConnection()) {
            assertColumnType(conn, "item_results", "input", "jsonb");
            assertColumnType(conn, "item_results", "expected_output", "jsonb");
            assertColumnType(conn, "item_results", "actual_output", "jsonb");

            // The pre-existing value round-trips: it is now a JSON object queryable via ->>.
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT input->>'q' AS q, expected_output->>'a' AS a " + "FROM item_results WHERE id = ?")) {
                ps.setObject(1, itemId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("q")).isEqualTo("hello");
                    assertThat(rs.getString("a")).isEqualTo("world");
                }
            }
        }
    }

    private void insertIngestedBatch(Connection conn, UUID runId, String key) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO ingested_batches (run_id, idempotency_key, created_at) VALUES (?, ?, ?)")) {
            ps.setObject(1, runId);
            ps.setString(2, key);
            ps.setObject(3, Instant.now().atOffset(java.time.ZoneOffset.UTC));
            ps.executeUpdate();
        }
    }

    private void insertProject(Connection conn, UUID id, String name) throws Exception {
        try (PreparedStatement ps =
                conn.prepareStatement("INSERT INTO projects (id, name, created_at) VALUES (?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setString(2, name);
            ps.setObject(3, Instant.now().atOffset(java.time.ZoneOffset.UTC));
            ps.executeUpdate();
        }
    }

    private void insertExperiment(Connection conn, UUID id, UUID projectId, String name) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO experiments (id, project_id, name, created_at) VALUES (?, ?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, projectId);
            ps.setString(3, name);
            ps.setObject(4, Instant.now().atOffset(java.time.ZoneOffset.UTC));
            ps.executeUpdate();
        }
    }

    private void insertRun(Connection conn, UUID id, UUID experimentId) throws Exception {
        insertRun(conn, id, experimentId, "SUCCESS");
    }

    private void insertRun(Connection conn, UUID id, UUID experimentId, String status) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO experiment_runs (id, experiment_id, status, started_at) VALUES (?, ?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, experimentId);
            ps.setString(3, status);
            ps.setObject(4, Instant.now().atOffset(java.time.ZoneOffset.UTC));
            ps.executeUpdate();
        }
    }

    private void insertItem(Connection conn, UUID id, UUID runId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO item_results (id, run_id, input, actual_output, created_at) VALUES (?, ?, ?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, runId);
            ps.setString(3, "{\"input\":\"q\"}");
            ps.setString(4, "{\"output\":\"a\"}");
            ps.setObject(5, Instant.now().atOffset(java.time.ZoneOffset.UTC));
            ps.executeUpdate();
        }
    }

    private void insertEval(Connection conn, UUID itemId, String name, boolean success) throws Exception {
        insertEval(conn, itemId, name, success, UUID.randomUUID());
    }

    private void insertEval(Connection conn, UUID itemId, String name, boolean success, UUID evalId) throws Exception {
        try (PreparedStatement ps =
                conn.prepareStatement("INSERT INTO eval_results (id, item_result_id, evaluator_name, score, success) "
                        + "VALUES (?, ?, ?, ?, ?)")) {
            ps.setObject(1, evalId);
            ps.setObject(2, itemId);
            ps.setString(3, name);
            ps.setDouble(4, success ? 1.0 : 0.0);
            ps.setBoolean(5, success);
            ps.executeUpdate();
        }
    }

    private void assertColumnExists(Connection conn, String table, String column) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT column_name FROM information_schema.columns WHERE table_name = ? AND column_name = ?")) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("column %s.%s should exist", table, column)
                        .isTrue();
            }
        }
    }

    private void assertColumnType(Connection conn, String table, String column, String expectedType) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT data_type FROM information_schema.columns WHERE table_name = ? AND column_name = ?")) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("column %s.%s should exist", table, column)
                        .isTrue();
                assertThat(rs.getString("data_type"))
                        .as("column %s.%s should be %s", table, column, expectedType)
                        .isEqualTo(expectedType);
            }
        }
    }
}
