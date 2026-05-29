package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Dataset;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DatasetRepository extends JpaRepository<Dataset, UUID> {

    Optional<Dataset> findByName(String name);

    boolean existsByName(String name);

    /**
     * Loads a dataset while acquiring a pessimistic write lock on its row. Used to serialize new
     * version creation so the {@code next = max(version) + 1} computation cannot race two callers
     * onto the same version number. The {@code (dataset_id, version)} unique constraint is the
     * backstop if the lock is bypassed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dataset d where d.id = :id")
    Optional<Dataset> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Locking lookup by name; equivalent to {@link #findByIdForUpdate} but resolved in a single
     * round trip when the caller only has the dataset name. Used by version creation so the
     * pessimistic write lock is acquired atomically with the lookup.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dataset d where d.name = :name")
    Optional<Dataset> findByNameForUpdate(@Param("name") String name);
}
