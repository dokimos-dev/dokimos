package dev.dokimos.server.controller.v1;

import dev.dokimos.server.dto.v1.CreateLlmConnectionRequest;
import dev.dokimos.server.dto.v1.LlmConnectionView;
import dev.dokimos.server.service.LlmConnectionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public ResponseEntity<LlmConnectionView> create(@Valid @RequestBody CreateLlmConnectionRequest request) {
        LlmConnectionView view = connectionService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/llm-connections/" + view.id()))
                .body(view);
    }

    /** Lists every registered connection. Key material is never included. */
    @GetMapping
    public List<LlmConnectionView> list() {
        return connectionService.list();
    }

    /** Returns a single connection by id, or 404 if it does not exist. */
    @GetMapping("/{id}")
    public LlmConnectionView get(@PathVariable UUID id) {
        return connectionService.get(id);
    }
}
