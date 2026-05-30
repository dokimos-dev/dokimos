package dev.dokimos.server.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.dokimos.server.controller.GlobalExceptionHandler;
import dev.dokimos.server.dto.v1.CreateDatasetRequest;
import dev.dokimos.server.dto.v1.CreateVersionRequest;
import dev.dokimos.server.dto.v1.DatasetSummary;
import dev.dokimos.server.entity.Dataset;
import dev.dokimos.server.entity.DatasetItem;
import dev.dokimos.server.entity.DatasetVersion;
import dev.dokimos.server.filter.ApiKeyAuthFilter;
import dev.dokimos.server.filter.Principal;
import dev.dokimos.server.filter.Role;
import dev.dokimos.server.service.DatasetService;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class DatasetControllerTest extends AbstractControllerTest {

    @Mock
    private DatasetService datasetService;

    @BeforeEach
    void setUp() {
        DatasetController controller = new DatasetController(datasetService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void listDatasets_returnsSummaries() throws Exception {
        DatasetSummary summary =
                new DatasetSummary(UUID.randomUUID(), "qa", "qa set", 2, 100, Instant.now(), Instant.now());
        when(datasetService.listDatasets()).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/datasets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("qa"))
                .andExpect(jsonPath("$[0].latestVersion").value(2))
                .andExpect(jsonPath("$[0].latestItemCount").value(100));
    }

    @Test
    void createDataset_returns201WithLocationHeader() throws Exception {
        Dataset dataset = dataset("qa", "qa set");
        when(datasetService.createDataset("qa", "qa set")).thenReturn(dataset);

        CreateDatasetRequest request = new CreateDatasetRequest("qa", "qa set");
        mockMvc.perform(post("/api/v1/datasets")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/datasets/qa"))
                .andExpect(jsonPath("$.name").value("qa"));
    }

    @Test
    void createDataset_returns409OnDuplicate() throws Exception {
        when(datasetService.createDataset(eq("qa"), any()))
                .thenThrow(new IllegalStateException("Dataset already exists: qa"));

        CreateDatasetRequest request = new CreateDatasetRequest("qa", null);
        mockMvc.perform(post("/api/v1/datasets")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Dataset already exists: qa"));
    }

    @Test
    void createDataset_returns400OnMissingName() throws Exception {
        CreateDatasetRequest request = new CreateDatasetRequest("", null);
        mockMvc.perform(post("/api/v1/datasets")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getDataset_returnsDetails() throws Exception {
        var details = new dev.dokimos.server.dto.v1.DatasetDetails(
                UUID.randomUUID(), "qa", "set", Instant.now(), Instant.now(), List.of());
        when(datasetService.getDatasetDetails("qa")).thenReturn(details);

        mockMvc.perform(get("/api/v1/datasets/{name}", "qa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("qa"));
    }

    @Test
    void getDataset_returns404OnUnknown() throws Exception {
        when(datasetService.getDatasetDetails("missing"))
                .thenThrow(new IllegalArgumentException("Dataset not found: missing"));

        mockMvc.perform(get("/api/v1/datasets/{name}", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Dataset not found: missing"));
    }

    @Test
    void deleteDataset_returns204() throws Exception {
        doNothing().when(datasetService).deleteDataset("qa");

        mockMvc.perform(delete("/api/v1/datasets/{name}", "qa")).andExpect(status().isNoContent());

        verify(datasetService).deleteDataset("qa");
    }

    @Test
    void deleteDataset_returns404OnUnknown() throws Exception {
        doThrow(new IllegalArgumentException("Dataset not found: missing"))
                .when(datasetService)
                .deleteDataset("missing");

        mockMvc.perform(delete("/api/v1/datasets/{name}", "missing")).andExpect(status().isNotFound());
    }

    @Test
    void createVersion_passesPrincipalAsCreatedBy() throws Exception {
        DatasetVersion version = datasetVersion("qa", 1, 2, "alice");
        when(datasetService.createVersion(eq("qa"), any(), any(), eq("alice"))).thenReturn(version);

        CreateVersionRequest request = new CreateVersionRequest(
                "v1", List.of(new CreateVersionRequest.ItemPayload(Map.of("q", "a"), null, null)));

        mockMvc.perform(post("/api/v1/datasets/{name}/versions", "qa")
                        .with(principal("alice"))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.datasetName").value("qa"))
                .andExpect(jsonPath("$.createdBy").value("alice"));
    }

    @Test
    void createVersion_passesNullCreatedByWhenNoPrincipal() throws Exception {
        DatasetVersion version = datasetVersion("qa", 1, 1, null);
        when(datasetService.createVersion(eq("qa"), any(), any(), eq(null))).thenReturn(version);

        CreateVersionRequest request = new CreateVersionRequest(
                null, List.of(new CreateVersionRequest.ItemPayload(Map.of("q", "a"), null, null)));

        mockMvc.perform(post("/api/v1/datasets/{name}/versions", "qa")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(request)))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> createdBy = ArgumentCaptor.forClass(String.class);
        verify(datasetService).createVersion(eq("qa"), any(), any(), createdBy.capture());
        assertThat(createdBy.getValue()).isNull();
    }

    @Test
    void createVersion_returns400OnEmptyItems() throws Exception {
        CreateVersionRequest request = new CreateVersionRequest(null, List.of());

        mockMvc.perform(post("/api/v1/datasets/{name}/versions", "qa")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getVersion_byNumber_returnsDetails() throws Exception {
        DatasetVersion version = datasetVersion("qa", 3, 5, "bob");
        when(datasetService.getVersion("qa", 3)).thenReturn(version);

        mockMvc.perform(get("/api/v1/datasets/{name}/versions/{version}", "qa", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.itemCount").value(5));
    }

    @Test
    void getVersion_latestAlias_returnsLatest() throws Exception {
        DatasetVersion version = datasetVersion("qa", 7, 2, "bob");
        when(datasetService.getLatestVersion("qa")).thenReturn(version);

        mockMvc.perform(get("/api/v1/datasets/{name}/versions/{version}", "qa", "latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(7));
    }

    @Test
    void getVersion_invalidNumber_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/{name}/versions/{version}", "qa", "nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Invalid version: nope"));
    }

    @Test
    void listItems_returnsPage() throws Exception {
        UUID itemId = UUID.randomUUID();
        DatasetVersion version = datasetVersion("qa", 1, 1, null);
        DatasetItem item = datasetItem(itemId, version, 0);
        Page<DatasetItem> page = new PageImpl<>(List.of(item));

        when(datasetService.getVersion("qa", 1)).thenReturn(version);
        when(datasetService.listItems(eq(version), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/datasets/{name}/versions/{version}/items", "qa", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(itemId.toString()))
                .andExpect(jsonPath("$.content[0].ordinal").value(0))
                .andExpect(jsonPath("$.totalElements").value(1))
                // PageResponse mirrors Spring's Page<T> JSON shape, including the nested sort and
                // pageable objects, so a single generated client type can deserialize both
                // envelopes (this DTO and the existing run-details Page<ItemSummary> path).
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.sort").exists())
                .andExpect(jsonPath("$.pageable").exists());
    }

    @Test
    void listItems_unknownVersion_returns404() throws Exception {
        when(datasetService.getVersion(eq("qa"), anyInt()))
                .thenThrow(new IllegalArgumentException("Dataset version not found: qa v9"));

        mockMvc.perform(get("/api/v1/datasets/{name}/versions/{version}/items", "qa", "9"))
                .andExpect(status().isNotFound());
    }

    private static RequestPostProcessor principal(String id) {
        return request -> {
            request.setAttribute(ApiKeyAuthFilter.PRINCIPAL_ATTRIBUTE, new Principal(id, Role.ADMIN, null));
            return request;
        };
    }

    private static Dataset dataset(String name, String description) {
        Dataset dataset = new Dataset(name, description);
        setField(dataset, "id", UUID.randomUUID());
        return dataset;
    }

    private static DatasetVersion datasetVersion(String datasetName, int version, int itemCount, String createdBy) {
        Dataset dataset = dataset(datasetName, null);
        DatasetVersion v = new DatasetVersion(dataset, version, "desc", createdBy, itemCount);
        setField(v, "id", UUID.randomUUID());
        return v;
    }

    private static DatasetItem datasetItem(UUID id, DatasetVersion version, int ordinal) {
        DatasetItem item = new DatasetItem(version, ordinal, Map.of("q", "a"), null, null);
        setField(item, "id", id);
        return item;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
