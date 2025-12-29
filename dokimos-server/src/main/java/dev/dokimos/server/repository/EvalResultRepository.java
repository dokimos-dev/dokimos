package dev.dokimos.server.repository;

import dev.dokimos.server.entity.EvalResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EvalResultRepository extends JpaRepository<EvalResult, UUID> {
}
