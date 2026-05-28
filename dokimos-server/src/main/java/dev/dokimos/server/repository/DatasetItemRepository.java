package dev.dokimos.server.repository;

import dev.dokimos.server.entity.DatasetItem;
import dev.dokimos.server.entity.DatasetVersion;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasetItemRepository extends JpaRepository<DatasetItem, UUID> {

    Page<DatasetItem> findByDatasetVersionOrderByOrdinalAsc(DatasetVersion datasetVersion, Pageable pageable);
}
