package dev.dokimos.server.repository;

import dev.dokimos.server.entity.EvalResult;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalResultRepository extends JpaRepository<EvalResult, UUID> {}
