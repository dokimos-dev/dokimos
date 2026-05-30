package dev.dokimos.server.controller.v1;

import dev.dokimos.server.dto.v1.CreateLlmConnectionRequest;
import dev.dokimos.server.dto.v1.LlmConnectionView;
import dev.dokimos.server.dto.v1.UpdateLlmConnectionRequest;
import dev.dokimos.server.service.LlmConnectionService;
import dev.dokimos.server.tenant.TenantScopeResolver;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/llm-connections")
public class LlmConnectionController {

    private final LlmConnectionService connectionService;

    public LlmConnectionController(LlmConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    /** Registers an LLM connection. Returns 201 with a {@code Location} header pointing at the connection. */
    @PostMapping
    public ResponseEntity<LlmConnectionView> create(
            @Valid @RequestBody CreateLlmConnectionRequest request, HttpServletRequest http) {
        LlmConnectionView view = connectionService.create(request, TenantScopeResolver.scope(http));
        return ResponseEntity.created(URI.create("/api/v1/llm-connections/" + view.id()))
                .body(view);
    }

    /** Lists the connections visible to the caller. Key material is never included. */
    @GetMapping
    public List<LlmConnectionView> list(HttpServletRequest http) {
        return connectionService.list(TenantScopeResolver.scope(http));
    }

    /** Returns a single connection by id, or 404 if it does not exist or belongs to another tenant. */
    @GetMapping("/{id}")
    public LlmConnectionView get(@PathVariable UUID id, HttpServletRequest http) {
        return connectionService.get(id, TenantScopeResolver.scope(http));
    }

    /** Updates a connection. Returns 404 if it does not exist or belongs to another tenant, 409 if the new name is taken. */
    @PutMapping("/{id}")
    public LlmConnectionView update(
            @PathVariable UUID id, @Valid @RequestBody UpdateLlmConnectionRequest request, HttpServletRequest http) {
        return connectionService.update(id, request, TenantScopeResolver.scope(http));
    }

    /** Deletes a connection. Returns 404 if it does not exist or belongs to another tenant, 409 if a judge job still references it. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, HttpServletRequest http) {
        connectionService.delete(id, TenantScopeResolver.scope(http));
    }
}
