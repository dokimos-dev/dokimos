package dev.dokimos.server.dto.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.lang.NonNull;

public record CreateRunRequest(
        @NotBlank @NonNull String experimentName,
        Map<String, Object> metadata,
        String name,
        String gitSha,
        String gitBranch,
        String triggeredBy,
        String datasetName,
        @Min(1) Integer datasetVersion) {

    /** Backwards-compatible 6-arg constructor for callers that predate the dataset linkage. */
    public CreateRunRequest(
            String experimentName,
            Map<String, Object> metadata,
            String name,
            String gitSha,
            String gitBranch,
            String triggeredBy) {
        this(experimentName, metadata, name, gitSha, gitBranch, triggeredBy, null, null);
    }

    /**
     * Cross-field rule enforcing that {@code datasetName} and {@code datasetVersion} are either both
     * supplied or both absent. Returning {@code false} causes {@code @Valid} to surface a
     * {@code MethodArgumentNotValidException} so the user-facing path returns 400 instead of the 404
     * that would result from the defensive check in the service.
     */
    @AssertTrue(message = "datasetName and datasetVersion must be set together")
    @JsonIgnore
    public boolean isDatasetLinkageValid() {
        boolean hasName = datasetName != null && !datasetName.isBlank();
        boolean hasVersion = datasetVersion != null;
        return hasName == hasVersion;
    }
}
