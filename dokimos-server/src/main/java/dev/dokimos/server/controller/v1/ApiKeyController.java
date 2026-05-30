package dev.dokimos.server.controller.v1;

import dev.dokimos.server.dto.v1.ApiKeyView;
import dev.dokimos.server.dto.v1.CreateApiKeyRequest;
import dev.dokimos.server.dto.v1.CreatedApiKeyView;
import dev.dokimos.server.service.ApiKeyService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manages scoped API keys. Every write here requires an {@code ADMIN} principal, enforced by
 * {@link dev.dokimos.server.filter.ApiKeyAuthFilter}. Create returns the raw generated key exactly once;
 * all other responses carry metadata only.
 */
@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    /**
     * Mints a key. Returns 201 with a {@code Location} header and the raw key in the body; the raw key
     * is never retrievable again.
     */
    @PostMapping
    public ResponseEntity<CreatedApiKeyView> create(@Valid @RequestBody CreateApiKeyRequest request) {
        CreatedApiKeyView created = apiKeyService.create(request);
        return ResponseEntity.created(
                        URI.create("/api/v1/api-keys/" + created.apiKey().id()))
                .body(created);
    }

    /** Lists every key as metadata only; the raw key is never returned. */
    @GetMapping
    public List<ApiKeyView> list() {
        return apiKeyService.list();
    }

    /** Disables a key so it can no longer authenticate. Returns 404 if it does not exist. */
    @PostMapping("/{id}/disable")
    public ApiKeyView disable(@PathVariable UUID id) {
        return apiKeyService.disable(id);
    }

    /** Permanently deletes a key. Returns 404 if it does not exist. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        apiKeyService.delete(id);
    }
}
