package dev.dokimos.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.dokimos.server.config.ApiKeyProperties;
import dev.dokimos.server.entity.ApiKey;
import dev.dokimos.server.filter.ApiKeyAuthenticator;
import dev.dokimos.server.filter.ApiKeyHasher;
import dev.dokimos.server.filter.Principal;
import dev.dokimos.server.filter.Role;
import dev.dokimos.server.repository.ApiKeyRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Proves the scoped API key path end to end against a real PostgreSQL instance (Testcontainers) with the
 * production Flyway schema, including the V12 {@code api_keys} table. A persisted enabled key resolves to
 * a principal carrying its role and tenant, a disabled key is rejected, and a presented raw key is only
 * ever matched by its SHA-256 hash (the raw key is never stored).
 *
 * <p>Self-skips when Docker is unavailable so it stays safe in the normal build.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ApiKeyAuthPostgresTest {

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

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Test
    void enabledKeyResolvesToScopedPrincipal() {
        assumeTrue(DOCKER_AVAILABLE, "Docker is not available, skipping real-Postgres API key verification");

        String rawKey = "raw-editor-key";
        ApiKey stored = new ApiKey(ApiKeyHasher.sha256Hex(rawKey), "ci", Role.EDITOR, "tenant-42");
        apiKeyRepository.saveAndFlush(stored);

        ApiKeyAuthenticator authenticator = new ApiKeyAuthenticator(new ApiKeyProperties(), apiKeyRepository);

        Optional<Principal> result = authenticator.authenticate("POST", "Bearer " + rawKey);

        assertThat(result).isPresent();
        assertThat(result.get().role()).isEqualTo(Role.EDITOR);
        assertThat(result.get().tenantId()).isEqualTo("tenant-42");
    }

    @Test
    void disabledKeyIsRejected() {
        assumeTrue(DOCKER_AVAILABLE, "Docker is not available, skipping real-Postgres API key verification");

        String rawKey = "raw-disabled-key";
        ApiKey stored = new ApiKey(ApiKeyHasher.sha256Hex(rawKey), "old", Role.ADMIN, null);
        stored.disable();
        apiKeyRepository.saveAndFlush(stored);

        ApiKeyAuthenticator authenticator = new ApiKeyAuthenticator(new ApiKeyProperties(), apiKeyRepository);

        // Authenticated mode is not entered by a disabled key alone, so an open deployment stays open.
        Optional<Principal> result = authenticator.authenticate("POST", "Bearer " + rawKey);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("system");
    }

    @Test
    void unknownKeyIsRejectedWhenAuthenticatedModeActive() {
        assumeTrue(DOCKER_AVAILABLE, "Docker is not available, skipping real-Postgres API key verification");

        ApiKey enabled = new ApiKey(ApiKeyHasher.sha256Hex("real-key"), "live", Role.EDITOR, null);
        apiKeyRepository.saveAndFlush(enabled);

        ApiKeyAuthenticator authenticator = new ApiKeyAuthenticator(new ApiKeyProperties(), apiKeyRepository);

        Optional<Principal> result = authenticator.authenticate("POST", "Bearer wrong-key");

        assertThat(result).isEmpty();
    }
}
