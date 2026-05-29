package dev.dokimos.server.repository;

import dev.dokimos.server.entity.Dataset;
import dev.dokimos.server.entity.DatasetVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DatasetVersionRepository extends JpaRepository<DatasetVersion, UUID> {

    List<DatasetVersion> findByDatasetOrderByVersionDesc(Dataset dataset);

    Optional<DatasetVersion> findFirstByDatasetOrderByVersionDesc(Dataset dataset);

    Optional<DatasetVersion> findByDatasetAndVersion(Dataset dataset, int version);

    /**
     * Returns the current maximum version number for the dataset, or {@code null} if no versions
     * exist. Callers should hold a pessimistic lock on the dataset row (see
     * {@code DatasetRepository.findByIdForUpdate}) before reading this so the {@code next = max + 1}
     * write cannot race.
     */
    @Query("select max(v.version) from DatasetVersion v where v.dataset = :dataset")
    Integer findMaxVersion(@Param("dataset") Dataset dataset);

    /**
     * Returns the latest version row for every supplied dataset in a single query. The dataset is
     * eagerly joined so callers can read {@code version.getDataset().getId()} without triggering a
     * per-row lazy fetch.
     */
    @Query("select v from DatasetVersion v join fetch v.dataset where v.dataset in :datasets "
            + "and v.version = (select max(v2.version) from DatasetVersion v2 where v2.dataset = v.dataset)")
    List<DatasetVersion> findLatestPerDataset(@Param("datasets") List<Dataset> datasets);
}
