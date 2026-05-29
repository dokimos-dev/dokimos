package dev.dokimos.server.controller.v1;

import dev.dokimos.server.dto.v1.CreateDatasetRequest;
import dev.dokimos.server.dto.v1.CreateVersionRequest;
import dev.dokimos.server.dto.v1.DatasetDetails;
import dev.dokimos.server.dto.v1.DatasetItemView;
import dev.dokimos.server.dto.v1.DatasetSummary;
import dev.dokimos.server.dto.v1.DatasetVersionDetails;
import dev.dokimos.server.dto.v1.PageResponse;
import dev.dokimos.server.entity.Dataset;
import dev.dokimos.server.entity.DatasetItem;
import dev.dokimos.server.entity.DatasetVersion;
import dev.dokimos.server.filter.ApiKeyAuthFilter;
import dev.dokimos.server.filter.Principal;
import dev.dokimos.server.service.DatasetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/datasets")
public class DatasetController {

    /** Path segment that resolves to the dataset's latest version on read endpoints. */
    private static final String LATEST_VERSION = "latest";

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    /** Lists every dataset on the server with its latest version number and item count. */
    @GetMapping
    public List<DatasetSummary> listDatasets() {
        return datasetService.listDatasets();
    }

    /**
     * Creates a dataset shell with no versions. Returns 201 with a {@code Location} header pointing
     * at the new dataset.
     */
    @PostMapping
    public ResponseEntity<DatasetSummary> createDataset(@Valid @RequestBody CreateDatasetRequest request) {
        Dataset dataset = datasetService.createDataset(request.name(), request.description());
        DatasetSummary summary = new DatasetSummary(
                dataset.getId(),
                dataset.getName(),
                dataset.getDescription(),
                null,
                null,
                dataset.getCreatedAt(),
                dataset.getUpdatedAt());
        return ResponseEntity.created(URI.create("/api/v1/datasets/" + dataset.getName()))
                .body(summary);
    }

    /** Returns the dataset and every one of its versions, newest first. */
    @GetMapping("/{name}")
    public DatasetDetails getDataset(@PathVariable String name) {
        return datasetService.getDatasetDetails(name);
    }

    /** Deletes a dataset and its versions; runs that referenced any version become unlinked. */
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> deleteDataset(@PathVariable String name) {
        datasetService.deleteDataset(name);
        return ResponseEntity.noContent().build();
    }

    /**
     * Appends a new immutable version to the dataset with the supplied items. The {@code created_by}
     * field is taken from the authenticated principal when present.
     */
    @PostMapping("/{name}/versions")
    public ResponseEntity<DatasetVersionDetails> createVersion(
            @PathVariable String name, @Valid @RequestBody CreateVersionRequest request, HttpServletRequest http) {
        String createdBy = currentPrincipalId(http);
        DatasetVersion version = datasetService.createVersion(name, request.description(), request.items(), createdBy);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(toDetails(version, name));
    }

    /**
     * Returns a specific version of the dataset. The literal path {@code latest} resolves to the
     * highest existing version; numeric paths are parsed as the explicit version number.
     */
    @GetMapping("/{name}/versions/{version}")
    public DatasetVersionDetails getVersion(@PathVariable String name, @PathVariable String version) {
        DatasetVersion datasetVersion = resolveVersion(name, version);
        return toDetails(datasetVersion, name);
    }

    /** Returns the items of a dataset version ordered by ordinal, paginated. */
    @GetMapping("/{name}/versions/{version}/items")
    public PageResponse<DatasetItemView> listItems(
            @PathVariable String name, @PathVariable String version, @PageableDefault(size = 50) Pageable pageable) {
        DatasetVersion datasetVersion = resolveVersion(name, version);
        return PageResponse.of(
                datasetService.listItems(datasetVersion, pageable).map(DatasetController::toItemView));
    }

    private DatasetVersion resolveVersion(String name, String version) {
        if (LATEST_VERSION.equalsIgnoreCase(version)) {
            return datasetService.getLatestVersion(name);
        }
        int versionNumber;
        try {
            versionNumber = Integer.parseInt(version);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid version: " + version);
        }
        return datasetService.getVersion(name, versionNumber);
    }

    private static DatasetVersionDetails toDetails(DatasetVersion version, String datasetName) {
        return new DatasetVersionDetails(
                version.getId(),
                datasetName,
                version.getVersion(),
                version.getDescription(),
                version.getItemCount(),
                version.getCreatedAt(),
                version.getCreatedBy());
    }

    private static DatasetItemView toItemView(DatasetItem item) {
        return new DatasetItemView(
                item.getId(), item.getOrdinal(), item.getInputs(), item.getExpectedOutputs(), item.getMetadata());
    }

    private static String currentPrincipalId(HttpServletRequest request) {
        Object attr = request.getAttribute(ApiKeyAuthFilter.PRINCIPAL_ATTRIBUTE);
        return attr instanceof Principal principal ? principal.id() : null;
    }
}
