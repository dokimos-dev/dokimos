package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.CreateLlmConnectionRequest;
import dev.dokimos.server.dto.v1.LlmConnectionView;
import dev.dokimos.server.entity.LlmConnection;
import dev.dokimos.server.repository.LlmConnectionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers and reads LLM connections. Inline keys are encrypted before persistence; responses never
 * carry key material. Connection names are unique across the server.
 */
@Service
public class LlmConnectionService {

    private final LlmConnectionRepository connectionRepository;
    private final LlmCredentialService credentialService;

    public LlmConnectionService(LlmConnectionRepository connectionRepository, LlmCredentialService credentialService) {
        this.connectionRepository = connectionRepository;
        this.credentialService = credentialService;
    }

    /**
     * Registers a connection. Exactly one of an inline key or a credential reference is stored; an
     * inline key is encrypted at rest.
     *
     * @param request the connection definition
     * @return the public view of the saved connection
     * @throws IllegalStateException if a connection with the same name already exists (mapped to 409)
     */
    @Transactional
    public LlmConnectionView create(CreateLlmConnectionRequest request) {
        if (connectionRepository.existsByName(request.name())) {
            throw new IllegalStateException("Connection already exists: " + request.name());
        }

        LlmConnection connection = new LlmConnection(request.name(), request.baseUrl(), request.model());
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            credentialService.encryptInlineKey(connection, request.apiKey());
        } else {
            connection.setCredentialRef(request.credentialRef());
        }

        return LlmConnectionView.from(connectionRepository.save(connection));
    }

    @Transactional(readOnly = true)
    public List<LlmConnectionView> list() {
        return connectionRepository.findAll().stream()
                .map(LlmConnectionView::from)
                .toList();
    }

    /**
     * Returns a connection by id.
     *
     * @throws IllegalArgumentException if no connection has the id (mapped to 404)
     */
    @Transactional(readOnly = true)
    public LlmConnectionView get(UUID id) {
        return LlmConnectionView.from(loadConnection(id));
    }

    private LlmConnection loadConnection(UUID id) {
        return connectionRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + id));
    }
}
