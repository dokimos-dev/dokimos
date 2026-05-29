package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.CreateVersionRequest;
import dev.dokimos.server.dto.v1.DatasetDetails;
import dev.dokimos.server.dto.v1.DatasetSummary;
import dev.dokimos.server.entity.Dataset;
import dev.dokimos.server.entity.DatasetItem;
import dev.dokimos.server.entity.DatasetVersion;
import dev.dokimos.server.repository.DatasetItemRepository;
import dev.dokimos.server.repository.DatasetRepository;
import dev.dokimos.server.repository.DatasetVersionRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for server-owned versioned datasets. Datasets are mutable shells; versions are immutable. */
@Service
public class DatasetService {

    private final DatasetRepository datasetRepository;
    private final DatasetVersionRepository versionRepository;
    private final DatasetItemRepository itemRepository;

    public DatasetService(
            DatasetRepository datasetRepository,
            DatasetVersionRepository versionRepository,
            DatasetItemRepository itemRepository) {
        this.datasetRepository = datasetRepository;
        this.versionRepository = versionRepository;
        this.itemRepository = itemRepository;
    }

    /**
     * Creates a dataset with a globally unique name. Has no versions until {@link #createVersion} is
     * called.
     *
     * @throws IllegalStateException if a dataset with the same name already exists (mapped to 409)
     */
    @Transactional
    public Dataset createDataset(String name, String description) {
        if (datasetRepository.existsByName(name)) {
            throw new IllegalStateException("Dataset already exists: " + name);
        }
        return datasetRepository.save(new Dataset(name, description));
    }

    /**
     * Returns the dataset with the given name.
     *
     * @throws IllegalArgumentException if no such dataset exists (mapped to 404)
     */
    @Transactional(readOnly = true)
    public Dataset getDataset(String name) {
        return datasetRepository
                .findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + name));
    }

    /**
     * Returns dataset summaries with the latest version number and item count for each. Collapses
     * to two queries (one for datasets, one for the latest version row per dataset) regardless of
     * dataset count.
     */
    @Transactional(readOnly = true)
    public List<DatasetSummary> listDatasets() {
        List<Dataset> datasets = datasetRepository.findAll();
        if (datasets.isEmpty()) {
            return List.of();
        }

        List<DatasetVersion> latestVersions = versionRepository.findLatestPerDataset(datasets);
        Map<UUID, DatasetVersion> latestByDatasetId = new HashMap<>(latestVersions.size());
        for (DatasetVersion v : latestVersions) {
            latestByDatasetId.put(v.getDataset().getId(), v);
        }

        List<DatasetSummary> summaries = new ArrayList<>(datasets.size());
        for (Dataset dataset : datasets) {
            DatasetVersion latest = latestByDatasetId.get(dataset.getId());
            Integer latestVersion = latest != null ? latest.getVersion() : null;
            Integer latestItemCount = latest != null ? latest.getItemCount() : null;
            summaries.add(new DatasetSummary(
                    dataset.getId(),
                    dataset.getName(),
                    dataset.getDescription(),
                    latestVersion,
                    latestItemCount,
                    dataset.getCreatedAt(),
                    dataset.getUpdatedAt()));
        }
        return summaries;
    }

    /** Returns full dataset details including the version list, newest first. */
    @Transactional(readOnly = true)
    public DatasetDetails getDatasetDetails(String name) {
        Dataset dataset = getDataset(name);
        List<DatasetDetails.VersionSummary> versions =
                versionRepository.findByDatasetOrderByVersionDesc(dataset).stream()
                        .map(v -> new DatasetDetails.VersionSummary(
                                v.getId(),
                                v.getVersion(),
                                v.getDescription(),
                                v.getItemCount(),
                                v.getCreatedAt(),
                                v.getCreatedBy()))
                        .toList();
        return new DatasetDetails(
                dataset.getId(),
                dataset.getName(),
                dataset.getDescription(),
                dataset.getCreatedAt(),
                dataset.getUpdatedAt(),
                versions);
    }

    /**
     * Deletes a dataset. The FK cascade removes its versions and items; runs that referenced any of
     * those versions have their {@code dataset_version_id} set to NULL via the SET NULL FK so the
     * historical run is preserved unlinked.
     *
     * @throws IllegalArgumentException if no such dataset exists (mapped to 404)
     */
    @Transactional
    public void deleteDataset(String name) {
        Dataset dataset = getDataset(name);
        datasetRepository.delete(dataset);
    }

    /**
     * Creates a new immutable version of the dataset and inserts all items in one transaction. The
     * pessimistic write lock on the parent dataset row serializes concurrent version creations so the
     * {@code next = max(version) + 1} read is consistent; the {@code (dataset_id, version)} unique
     * constraint is the backstop if the lock is bypassed. Item ordinals follow the input order
     * (0..N-1).
     *
     * @throws IllegalArgumentException if no such dataset exists or items is empty (the latter is
     *     also caught by bean validation, but the service enforces it for non-controller callers)
     */
    @Transactional
    public DatasetVersion createVersion(
            String datasetName, String description, List<CreateVersionRequest.ItemPayload> items, String createdBy) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Dataset version must contain at least one item");
        }

        Dataset dataset = datasetRepository
                .findByNameForUpdate(datasetName)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + datasetName));

        Integer currentMax = versionRepository.findMaxVersion(dataset);
        int nextVersion = currentMax == null ? 1 : currentMax + 1;

        DatasetVersion version = new DatasetVersion(dataset, nextVersion, description, createdBy, items.size());
        versionRepository.save(version);

        List<DatasetItem> rows = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            CreateVersionRequest.ItemPayload payload = items.get(i);
            if (payload.inputs() == null) {
                throw new IllegalArgumentException("Item " + i + " is missing required 'inputs'");
            }
            rows.add(new DatasetItem(version, i, payload.inputs(), payload.expectedOutputs(), payload.metadata()));
        }
        itemRepository.saveAll(rows);

        dataset.touchUpdatedAt();
        datasetRepository.save(dataset);

        return version;
    }

    /**
     * Returns a specific version of the dataset.
     *
     * @throws IllegalArgumentException if the dataset or version is missing
     */
    @Transactional(readOnly = true)
    public DatasetVersion getVersion(String datasetName, int version) {
        Dataset dataset = getDataset(datasetName);
        return versionRepository
                .findByDatasetAndVersion(dataset, version)
                .orElseThrow(() ->
                        new IllegalArgumentException("Dataset version not found: " + datasetName + " v" + version));
    }

    /**
     * Returns the latest version of the dataset.
     *
     * @throws IllegalArgumentException if the dataset has no versions
     */
    @Transactional(readOnly = true)
    public DatasetVersion getLatestVersion(String datasetName) {
        Dataset dataset = getDataset(datasetName);
        return versionRepository
                .findFirstByDatasetOrderByVersionDesc(dataset)
                .orElseThrow(() -> new IllegalArgumentException("Dataset has no versions: " + datasetName));
    }

    /** Returns items for the given version ordered by ordinal, paginated. */
    @Transactional(readOnly = true)
    public Page<DatasetItem> listItems(String datasetName, int version, Pageable pageable) {
        DatasetVersion datasetVersion = getVersion(datasetName, version);
        return listItems(datasetVersion, pageable);
    }

    /**
     * Overload for callers that have already resolved a {@link DatasetVersion}; avoids the
     * double-fetch path through {@link #getVersion}.
     */
    @Transactional(readOnly = true)
    public Page<DatasetItem> listItems(DatasetVersion version, Pageable pageable) {
        return itemRepository.findByDatasetVersionOrderByOrdinalAsc(version, pageable);
    }
}
