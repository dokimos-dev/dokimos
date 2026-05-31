package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.CreateVersionRequest;
import dev.dokimos.server.dto.v1.DatasetDetails;
import dev.dokimos.server.dto.v1.DatasetSummary;
import dev.dokimos.server.dto.v1.DatasetVersionDetails;
import dev.dokimos.server.dto.v1.PromoteRequest;
import dev.dokimos.server.entity.Dataset;
import dev.dokimos.server.entity.DatasetItem;
import dev.dokimos.server.entity.DatasetVersion;
import dev.dokimos.server.entity.ItemResult;
import dev.dokimos.server.repository.DatasetItemRepository;
import dev.dokimos.server.repository.DatasetRepository;
import dev.dokimos.server.repository.DatasetVersionRepository;
import dev.dokimos.server.repository.ItemResultRepository;
import dev.dokimos.server.tenant.TenantScope;
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
    private final ItemResultRepository itemResultRepository;

    public DatasetService(
            DatasetRepository datasetRepository,
            DatasetVersionRepository versionRepository,
            DatasetItemRepository itemRepository,
            ItemResultRepository itemResultRepository) {
        this.datasetRepository = datasetRepository;
        this.versionRepository = versionRepository;
        this.itemRepository = itemRepository;
        this.itemResultRepository = itemResultRepository;
    }

    /**
     * Creates a dataset whose name is unique within the scope, stamped with the scope's tenant. Has no
     * versions until {@link #createVersion} is called.
     *
     * @throws IllegalStateException if a dataset with the same name already exists in the scope (mapped
     *     to 409)
     */
    @Transactional
    public Dataset createDataset(String name, String description, TenantScope scope) {
        if (datasetRepository.existsByName(name, scope)) {
            throw new IllegalStateException("Dataset already exists: " + name);
        }
        Dataset dataset = new Dataset(name, description);
        dataset.setTenantId(scope.stampTenantId());
        return datasetRepository.save(dataset);
    }

    /**
     * Returns the dataset with the given name, visible under the scope.
     *
     * @throws IllegalArgumentException if no such dataset exists under the scope (mapped to 404)
     */
    @Transactional(readOnly = true)
    public Dataset getDataset(String name, TenantScope scope) {
        return datasetRepository
                .findByName(name, scope)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + name));
    }

    /**
     * Returns dataset summaries visible under the scope with the latest version number and item count
     * for each. Collapses to two queries regardless of dataset count.
     */
    @Transactional(readOnly = true)
    public List<DatasetSummary> listDatasets(TenantScope scope) {
        List<Dataset> datasets = datasetRepository.findAllOrdered(scope);
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
    public DatasetDetails getDatasetDetails(String name, TenantScope scope) {
        Dataset dataset = getDataset(name, scope);
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
     * Deletes a dataset visible under the scope. The FK cascade removes its versions and items; runs
     * that referenced any of those versions have their {@code dataset_version_id} set to NULL via the
     * SET NULL FK so the historical run is preserved unlinked.
     *
     * @throws IllegalArgumentException if no such dataset exists under the scope (mapped to 404)
     */
    @Transactional
    public void deleteDataset(String name, TenantScope scope) {
        Dataset dataset = getDataset(name, scope);
        datasetRepository.delete(dataset);
    }

    /**
     * Creates a new immutable version of the dataset visible under the scope and inserts all items in
     * one transaction. The version and its items are stamped from the dataset's tenant so a scoped
     * parent and child query agree. The pessimistic write lock on the parent dataset row serializes
     * concurrent version creations.
     *
     * @throws IllegalArgumentException if no such dataset exists under the scope or items is empty
     */
    @Transactional
    public DatasetVersion createVersion(
            String datasetName,
            String description,
            List<CreateVersionRequest.ItemPayload> items,
            String createdBy,
            TenantScope scope) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Dataset version must contain at least one item");
        }

        Dataset dataset = datasetRepository
                .findByNameForUpdate(datasetName, scope)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + datasetName));

        Integer currentMax = versionRepository.findMaxVersion(dataset);
        int nextVersion = currentMax == null ? 1 : currentMax + 1;

        DatasetVersion version = new DatasetVersion(dataset, nextVersion, description, createdBy, items.size());
        version.setTenantId(dataset.getTenantId());
        versionRepository.save(version);

        List<DatasetItem> rows = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            CreateVersionRequest.ItemPayload payload = items.get(i);
            if (payload.inputs() == null) {
                throw new IllegalArgumentException("Item " + i + " is missing required 'inputs'");
            }
            DatasetItem datasetItem =
                    new DatasetItem(version, i, payload.inputs(), payload.expectedOutputs(), payload.metadata());
            datasetItem.setTenantId(dataset.getTenantId());
            rows.add(datasetItem);
        }
        itemRepository.saveAll(rows);

        dataset.touchUpdatedAt();
        datasetRepository.save(dataset);

        return version;
    }

    /**
     * Promotes run item results into a new version of an existing dataset. Each referenced item result
     * must be visible under the scope, so a tenant cannot promote another tenant's items. Delegates to
     * {@link #createVersion} so the version-numbering and locking semantics are identical to a direct
     * create.
     *
     * @throws IllegalArgumentException if any referenced item result or the dataset is missing or not
     *     visible under the scope (mapped to 404)
     */
    @Transactional
    public DatasetVersionDetails promote(PromoteRequest req, String createdBy, TenantScope scope) {
        List<CreateVersionRequest.ItemPayload> payloads =
                new ArrayList<>(req.items().size());
        for (PromoteRequest.PromoteItem item : req.items()) {
            ItemResult itemResult = itemResultRepository
                    .findById(item.itemResultId())
                    .filter(loaded -> visibleUnder(loaded, scope))
                    .orElseThrow(() -> new IllegalArgumentException("Item result not found: " + item.itemResultId()));

            Map<String, Object> expectedOutputs = item.overriddenExpectedOutput() != null
                    ? item.overriddenExpectedOutput()
                    : itemResult.getExpectedOutput();

            payloads.add(new CreateVersionRequest.ItemPayload(
                    itemResult.getInput(), expectedOutputs, itemResult.getMetadata()));
        }

        DatasetVersion version = createVersion(req.datasetName(), req.description(), payloads, createdBy, scope);
        return new DatasetVersionDetails(
                version.getId(),
                req.datasetName(),
                version.getVersion(),
                version.getDescription(),
                version.getItemCount(),
                version.getCreatedAt(),
                version.getCreatedBy());
    }

    /** Applies the own-plus-shared rule to an item looked up by id from the request, so {@code promote} stays scoped. */
    private static boolean visibleUnder(ItemResult item, TenantScope scope) {
        if (!scope.restricted()) {
            return true;
        }
        String tenant = item.getTenantId();
        return tenant == null || tenant.equals(scope.tenantId());
    }

    /**
     * Returns a specific version of the dataset, visible under the scope.
     *
     * @throws IllegalArgumentException if the dataset or version is missing under the scope
     */
    @Transactional(readOnly = true)
    public DatasetVersion getVersion(String datasetName, int version, TenantScope scope) {
        Dataset dataset = getDataset(datasetName, scope);
        return versionRepository
                .findByDatasetAndVersion(dataset, version)
                .orElseThrow(() ->
                        new IllegalArgumentException("Dataset version not found: " + datasetName + " v" + version));
    }

    /**
     * Returns the latest version of the dataset, visible under the scope.
     *
     * @throws IllegalArgumentException if the dataset has no versions under the scope
     */
    @Transactional(readOnly = true)
    public DatasetVersion getLatestVersion(String datasetName, TenantScope scope) {
        Dataset dataset = getDataset(datasetName, scope);
        return versionRepository
                .findFirstByDatasetOrderByVersionDesc(dataset)
                .orElseThrow(() -> new IllegalArgumentException("Dataset has no versions: " + datasetName));
    }

    /** Returns items for the given version ordered by ordinal, paginated. */
    @Transactional(readOnly = true)
    public Page<DatasetItem> listItems(String datasetName, int version, Pageable pageable, TenantScope scope) {
        DatasetVersion datasetVersion = getVersion(datasetName, version, scope);
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
