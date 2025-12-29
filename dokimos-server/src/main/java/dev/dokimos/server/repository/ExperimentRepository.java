package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Experiment;
import dev.dokimos.server.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExperimentRepository extends JpaRepository<Experiment, UUID> {

    Optional<Experiment> findByProjectAndName(Project project, String name);

    List<Experiment> findByProjectOrderByCreatedAtDesc(Project project);
}
