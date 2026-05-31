package dev.dokimos.server.controller.v1;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.dokimos.server.controller.GlobalExceptionHandler;
import dev.dokimos.server.dto.v1.AnnotationRequest;
import dev.dokimos.server.dto.v1.AnnotationView;
import dev.dokimos.server.entity.AnnotationVerdict;
import dev.dokimos.server.filter.ApiKeyAuthFilter;
import dev.dokimos.server.filter.Principal;
import dev.dokimos.server.filter.Role;
import dev.dokimos.server.service.AnnotationService;
import dev.dokimos.server.tenant.TenantScope;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class AnnotationControllerTest extends AbstractControllerTest {

    @Mock
    private AnnotationService annotationService;

    @BeforeEach
    void setUp() {
        AnnotationController controller = new AnnotationController(annotationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Test
    void upsert_returns200WithPrincipalAsCreatedBy() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID itemResultId = UUID.randomUUID();
        AnnotationView view = view(AnnotationVerdict.INCORRECT, "alice");
        when(annotationService.upsert(
                        eq(runId), eq(itemResultId), any(AnnotationRequest.class), eq("alice"), any(TenantScope.class)))
                .thenReturn(view);

        AnnotationRequest request = new AnnotationRequest(AnnotationVerdict.INCORRECT, Map.of("a", "fixed"), "note");
        mockMvc.perform(put("/api/v1/runs/{runId}/items/{itemResultId}/annotation", runId, itemResultId)
                        .with(principal("alice"))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("INCORRECT"))
                .andExpect(jsonPath("$.createdBy").value("alice"));
    }

    @Test
    void upsert_returns400WhenVerdictMissing() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID itemResultId = UUID.randomUUID();

        AnnotationRequest request = new AnnotationRequest(null, null, null);
        mockMvc.perform(put("/api/v1/runs/{runId}/items/{itemResultId}/annotation", runId, itemResultId)
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upsert_returns404WhenItemNotInRun() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID itemResultId = UUID.randomUUID();
        when(annotationService.upsert(
                        eq(runId), eq(itemResultId), any(AnnotationRequest.class), any(), any(TenantScope.class)))
                .thenThrow(new IllegalArgumentException(
                        "Item result " + itemResultId + " does not belong to run " + runId));

        AnnotationRequest request = new AnnotationRequest(AnnotationVerdict.CORRECT, null, null);
        mockMvc.perform(put("/api/v1/runs/{runId}/items/{itemResultId}/annotation", runId, itemResultId)
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(toJson(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_returns200() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID itemResultId = UUID.randomUUID();
        when(annotationService.get(eq(runId), eq(itemResultId), any(TenantScope.class)))
                .thenReturn(view(AnnotationVerdict.UNSURE, null));

        mockMvc.perform(get("/api/v1/runs/{runId}/items/{itemResultId}/annotation", runId, itemResultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("UNSURE"));
    }

    @Test
    void get_returns404WhenMissing() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID itemResultId = UUID.randomUUID();
        when(annotationService.get(eq(runId), eq(itemResultId), any(TenantScope.class)))
                .thenThrow(new IllegalArgumentException("Annotation not found for item result: " + itemResultId));

        mockMvc.perform(get("/api/v1/runs/{runId}/items/{itemResultId}/annotation", runId, itemResultId))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns204() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID itemResultId = UUID.randomUUID();
        doNothing().when(annotationService).delete(eq(runId), eq(itemResultId), any(TenantScope.class));

        mockMvc.perform(delete("/api/v1/runs/{runId}/items/{itemResultId}/annotation", runId, itemResultId))
                .andExpect(status().isNoContent());

        verify(annotationService).delete(eq(runId), eq(itemResultId), any(TenantScope.class));
    }

    @Test
    void delete_returns404WhenItemNotInRun() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID itemResultId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Item result " + itemResultId + " does not belong to run " + runId))
                .when(annotationService)
                .delete(eq(runId), eq(itemResultId), any(TenantScope.class));

        mockMvc.perform(delete("/api/v1/runs/{runId}/items/{itemResultId}/annotation", runId, itemResultId))
                .andExpect(status().isNotFound());
    }

    private static AnnotationView view(AnnotationVerdict verdict, String createdBy) {
        Instant now = Instant.now();
        return new AnnotationView(UUID.randomUUID(), verdict, null, null, createdBy, now, now);
    }

    private static RequestPostProcessor principal(String id) {
        return request -> {
            request.setAttribute(ApiKeyAuthFilter.PRINCIPAL_ATTRIBUTE, new Principal(id, Role.ADMIN, null));
            return request;
        };
    }
}
