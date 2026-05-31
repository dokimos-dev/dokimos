package dev.dokimos.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.dokimos.server.dto.v1.CreateLlmConnectionRequest;
import dev.dokimos.server.dto.v1.LlmConnectionView;
import dev.dokimos.server.dto.v1.UpdateLlmConnectionRequest;
import dev.dokimos.server.entity.LlmConnection;
import dev.dokimos.server.entity.LlmConnectionProtocol;
import dev.dokimos.server.repository.EvalJobRepository;
import dev.dokimos.server.repository.LlmConnectionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LlmConnectionServiceTest {

    @Mock
    private LlmConnectionRepository connectionRepository;

    @Mock
    private LlmCredentialService credentialService;

    @Mock
    private EvalJobRepository evalJobRepository;

    @InjectMocks
    private LlmConnectionService service;

    @Test
    void create_defaultsProtocolToResponsesWhenOmitted() {
        when(connectionRepository.existsByName(eq("conn"), any())).thenReturn(false);
        when(connectionRepository.save(any(LlmConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(
                new CreateLlmConnectionRequest("conn", "https://x", "gpt-4o-mini", null, null, "OPENAI_KEY"),
                dev.dokimos.server.tenant.TenantScope.unrestricted());

        ArgumentCaptor<LlmConnection> saved = ArgumentCaptor.forClass(LlmConnection.class);
        verify(connectionRepository).save(saved.capture());
        assertThat(saved.getValue().getProtocol()).isEqualTo(LlmConnectionProtocol.RESPONSES);
    }

    @Test
    void create_honorsAnExplicitProtocol() {
        when(connectionRepository.existsByName(eq("conn"), any())).thenReturn(false);
        when(connectionRepository.save(any(LlmConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(
                new CreateLlmConnectionRequest(
                        "conn", "https://x", "gpt-4o-mini", LlmConnectionProtocol.CHAT_COMPLETIONS, null, "OPENAI_KEY"),
                dev.dokimos.server.tenant.TenantScope.unrestricted());

        ArgumentCaptor<LlmConnection> saved = ArgumentCaptor.forClass(LlmConnection.class);
        verify(connectionRepository).save(saved.capture());
        assertThat(saved.getValue().getProtocol()).isEqualTo(LlmConnectionProtocol.CHAT_COMPLETIONS);
    }

    @Test
    void update_replacesProtocolWhenSupplied() {
        UUID id = UUID.randomUUID();
        LlmConnection connection = new LlmConnection("conn", "https://x", "gpt-4o-mini");
        when(connectionRepository.findById(eq(id), any())).thenReturn(Optional.of(connection));
        when(connectionRepository.save(any(LlmConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(
                id,
                new UpdateLlmConnectionRequest(
                        "conn", "https://x", "gpt-4o-mini", LlmConnectionProtocol.CHAT_COMPLETIONS, null, null),
                dev.dokimos.server.tenant.TenantScope.unrestricted());

        assertThat(connection.getProtocol()).isEqualTo(LlmConnectionProtocol.CHAT_COMPLETIONS);
    }

    @Test
    void update_keepsProtocolWhenNull() {
        UUID id = UUID.randomUUID();
        LlmConnection connection = new LlmConnection("conn", "https://x", "gpt-4o-mini");
        connection.setProtocol(LlmConnectionProtocol.CHAT_COMPLETIONS);
        when(connectionRepository.findById(eq(id), any())).thenReturn(Optional.of(connection));
        when(connectionRepository.save(any(LlmConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(
                id,
                new UpdateLlmConnectionRequest("conn", "https://x", "gpt-4o-mini", null, null, null),
                dev.dokimos.server.tenant.TenantScope.unrestricted());

        assertThat(connection.getProtocol()).isEqualTo(LlmConnectionProtocol.CHAT_COMPLETIONS);
    }

    @Test
    void update_replacesFieldsAndKeepsKeyWhenNoneSupplied() {
        UUID id = UUID.randomUUID();
        LlmConnection connection = new LlmConnection("old", "https://old", "gpt-3.5");
        connection.setEncryptedApiKey("cipher");
        when(connectionRepository.findById(eq(id), any())).thenReturn(Optional.of(connection));
        when(connectionRepository.save(any(LlmConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        LlmConnectionView view = service.update(
                id,
                new UpdateLlmConnectionRequest("new", "https://new", "gpt-4o-mini", null, null, null),
                dev.dokimos.server.tenant.TenantScope.unrestricted());

        assertThat(view.name()).isEqualTo("new");
        assertThat(view.baseUrl()).isEqualTo("https://new");
        assertThat(view.model()).isEqualTo("gpt-4o-mini");
        assertThat(connection.getEncryptedApiKey()).isEqualTo("cipher");
        verify(credentialService, never()).encryptInlineKey(any(), any());
    }

    @Test
    void update_encryptsNewInlineKeyAndClearsCredentialRef() {
        UUID id = UUID.randomUUID();
        LlmConnection connection = new LlmConnection("conn", "https://x", "gpt-4o-mini");
        connection.setCredentialRef("OPENAI_KEY");
        when(connectionRepository.findById(eq(id), any())).thenReturn(Optional.of(connection));
        when(connectionRepository.save(any(LlmConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(
                id,
                new UpdateLlmConnectionRequest("conn", "https://x", "gpt-4o-mini", null, "sk-new", null),
                dev.dokimos.server.tenant.TenantScope.unrestricted());

        verify(credentialService).encryptInlineKey(connection, "sk-new");
        assertThat(connection.getCredentialRef()).isNull();
    }

    @Test
    void update_throwsWhenNewNameTaken() {
        UUID id = UUID.randomUUID();
        LlmConnection connection = new LlmConnection("conn", "https://x", "gpt-4o-mini");
        when(connectionRepository.findById(eq(id), any())).thenReturn(Optional.of(connection));
        when(connectionRepository.existsByName(eq("taken"), any())).thenReturn(true);

        assertThatThrownBy(() -> service.update(
                        id,
                        new UpdateLlmConnectionRequest("taken", "https://x", "gpt-4o-mini", null, null, null),
                        dev.dokimos.server.tenant.TenantScope.unrestricted()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void delete_removesConnectionAndItsQueueRecords() {
        UUID id = UUID.randomUUID();
        LlmConnection connection = new LlmConnection("conn", "https://x", "gpt-4o-mini");
        when(connectionRepository.findById(eq(id), any())).thenReturn(Optional.of(connection));

        service.delete(id, dev.dokimos.server.tenant.TenantScope.unrestricted());

        verify(evalJobRepository).deleteByConnectionId(id);
        verify(connectionRepository).delete(connection);
    }

    @Test
    void delete_throwsWhenConnectionMissing() {
        UUID id = UUID.randomUUID();
        when(connectionRepository.findById(eq(id), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id, dev.dokimos.server.tenant.TenantScope.unrestricted()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        verify(connectionRepository, never()).delete(any());
    }
}
