package dev.dokimos.server.service;

import dev.dokimos.server.dto.v1.CreateLlmConnectionRequest;
import dev.dokimos.server.dto.v1.LlmConnectionView;
import dev.dokimos.server.dto.v1.UpdateLlmConnectionRequest;
import dev.dokimos.server.entity.LlmConnection;
import dev.dokimos.server.repository.EvalJobRepository;
import dev.dokimos.server.repository.LlmConnectionRepository;
import dev.dokimos.server.tenant.TenantScope;
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
    private final EvalJobRepository evalJobRepository;

    public LlmConnectionService(
            LlmConnectionRepository connectionRepository,
            LlmCredentialService credentialService,
            EvalJobRepository evalJobRepository) {
        this.connectionRepository = connectionRepository;
        this.credentialService = credentialService;
        this.evalJobRepository = evalJobRepository;
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
    public LlmConnectionView create(CreateLlmConnectionRequest request, TenantScope scope) {
        if (connectionRepository.existsByName(request.name(), scope)) {
            throw new IllegalStateException("Connection already exists: " + request.name());
        }

        LlmConnection connection = new LlmConnection(request.name(), request.baseUrl(), request.model());
        connection.setTenantId(scope.stampTenantId());
        if (request.protocol() != null) {
            connection.setProtocol(request.protocol());
        }
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            credentialService.encryptInlineKey(connection, request.apiKey());
        } else {
            connection.setCredentialRef(request.credentialRef());
        }

        return LlmConnectionView.from(connectionRepository.save(connection));
    }

    @Transactional(readOnly = true)
    public List<LlmConnectionView> list(TenantScope scope) {
        return connectionRepository.findAllOrdered(scope).stream()
                .map(LlmConnectionView::from)
                .toList();
    }

    /**
     * Returns a connection by id, visible under the scope.
     *
     * @throws IllegalArgumentException if no connection has the id under the scope (mapped to 404)
     */
    @Transactional(readOnly = true)
    public LlmConnectionView get(UUID id, TenantScope scope) {
        return LlmConnectionView.from(loadConnection(id, scope));
    }

    /**
     * Replaces a connection's name, base URL, and model, and optionally its credential. A supplied
     * inline key is encrypted and replaces the credential reference; a supplied credential reference
     * replaces the inline key; supplying neither keeps the existing credential.
     *
     * @param id the connection to update
     * @param request the new connection definition
     * @return the public view of the updated connection
     * @throws IllegalArgumentException if no connection has the id (mapped to 404)
     * @throws IllegalStateException if the new name is already taken by another connection (mapped to
     *     409)
     */
    @Transactional
    public LlmConnectionView update(UUID id, UpdateLlmConnectionRequest request, TenantScope scope) {
        LlmConnection connection = loadConnection(id, scope);
        if (!connection.getName().equals(request.name()) && connectionRepository.existsByName(request.name(), scope)) {
            throw new IllegalStateException("Connection already exists: " + request.name());
        }
        connection.setName(request.name());
        connection.setBaseUrl(request.baseUrl());
        connection.setModel(request.model());
        if (request.protocol() != null) {
            connection.setProtocol(request.protocol());
        }
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            credentialService.encryptInlineKey(connection, request.apiKey());
            connection.setCredentialRef(null);
        } else if (request.credentialRef() != null && !request.credentialRef().isBlank()) {
            connection.setCredentialRef(request.credentialRef());
            connection.setEncryptedApiKey(null);
        }
        connection.touchUpdatedAt();
        return LlmConnectionView.from(connectionRepository.save(connection));
    }

    /**
     * Deletes a connection along with its judge job queue records. The eval results those jobs
     * produced remain on their run items, so deleting a connection never removes scoring history.
     *
     * @param id the connection to delete
     * @throws IllegalArgumentException if no connection has the id (mapped to 404)
     */
    @Transactional
    public void delete(UUID id, TenantScope scope) {
        LlmConnection connection = loadConnection(id, scope);
        evalJobRepository.deleteByConnectionId(id);
        connectionRepository.delete(connection);
    }

    private LlmConnection loadConnection(UUID id, TenantScope scope) {
        return connectionRepository
                .findById(id, scope)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + id));
    }
}
