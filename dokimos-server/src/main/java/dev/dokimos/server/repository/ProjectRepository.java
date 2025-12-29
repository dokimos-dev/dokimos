package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByName(String name);

    @Query("""
            SELECT p, COUNT(e) as experimentCount
            FROM Project p
            LEFT JOIN p.experiments e
            GROUP BY p
            ORDER BY p.createdAt DESC
            """)
    List<Object[]> findAllWithExperimentCount();
}
