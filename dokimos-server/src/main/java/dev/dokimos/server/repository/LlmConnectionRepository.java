package dev.dokimos.server.repository;

import dev.dokimos.server.entity.LlmConnection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmConnectionRepository extends JpaRepository<LlmConnection, UUID> {

    Optional<LlmConnection> findByName(String name);

    boolean existsByName(String name);
}
