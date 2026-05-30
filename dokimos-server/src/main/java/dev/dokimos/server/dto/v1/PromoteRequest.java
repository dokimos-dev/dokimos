package dev.dokimos.server.dto.v1;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Payload for promoting run item results into a new version of an existing dataset.
 *
 * @param datasetName the existing dataset to append a new version to (required)
 * @param description an optional description for the new version
 * @param items the run item results to promote, in the order they should appear (at least one)
 */
public record PromoteRequest(
        @NotBlank String datasetName,
        @Size(max = 2_000) String description,
        @NotEmpty @Valid List<PromoteItem> items) {

    /**
     * A single run item result to promote.
     *
     * @param itemResultId the item result to source inputs, expected output, and metadata from
     *     (required)
     * @param overriddenExpectedOutput an expected output to use instead of the item result's, or null
     *     to keep the item result's expected output
     */
    public record PromoteItem(@NotNull UUID itemResultId, Map<String, Object> overriddenExpectedOutput) {}
}
