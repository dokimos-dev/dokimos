package dev.dokimos.server.repository;

import dev.dokimos.server.entity.ApiKey;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    /** Looks up an enabled key by its SHA-256 hex hash. Disabled keys are intentionally excluded. */
    Optional<ApiKey> findByKeyHashAndEnabledTrue(String keyHash);

    /** Returns true when at least one enabled key exists, which puts the deployment in authenticated mode. */
    boolean existsByEnabledTrue();
}
