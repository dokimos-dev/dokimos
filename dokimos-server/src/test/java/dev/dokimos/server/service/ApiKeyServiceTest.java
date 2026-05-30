package dev.dokimos.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.dokimos.server.dto.v1.ApiKeyView;
import dev.dokimos.server.dto.v1.CreateApiKeyRequest;
import dev.dokimos.server.dto.v1.CreatedApiKeyView;
import dev.dokimos.server.entity.ApiKey;
import dev.dokimos.server.filter.ApiKeyHasher;
import dev.dokimos.server.filter.Role;
import dev.dokimos.server.repository.ApiKeyRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @InjectMocks
    private ApiKeyService service;

    @Test
    void create_returnsRawKeyOnceAndStoresOnlyItsHash() {
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        CreatedApiKeyView created = service.create(new CreateApiKeyRequest("ci", Role.EDITOR, "tenant-1"));

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        ApiKey persisted = captor.getValue();

        // The raw key is present in the response but never stored; only its hash is persisted.
        assertThat(created.key()).isNotBlank();
        assertThat(persisted.getKeyHash()).isEqualTo(ApiKeyHasher.sha256Hex(created.key()));
        assertThat(persisted.getKeyHash()).isNotEqualTo(created.key());
        assertThat(created.apiKey().role()).isEqualTo(Role.EDITOR);
        assertThat(created.apiKey().tenantId()).isEqualTo("tenant-1");
        assertThat(persisted.isEnabled()).isTrue();
    }

    @Test
    void create_generatesDistinctKeysPerCall() {
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        String first =
                service.create(new CreateApiKeyRequest("a", Role.VIEWER, null)).key();
        String second =
                service.create(new CreateApiKeyRequest("b", Role.VIEWER, null)).key();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void disable_marksKeyDisabled() {
        UUID id = UUID.randomUUID();
        ApiKey key = new ApiKey("hash", "k", Role.ADMIN, null);
        when(apiKeyRepository.findById(id)).thenReturn(Optional.of(key));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        ApiKeyView view = service.disable(id);

        assertThat(view.enabled()).isFalse();
        assertThat(key.isEnabled()).isFalse();
    }

    @Test
    void disable_throwsWhenKeyMissing() {
        UUID id = UUID.randomUUID();
        when(apiKeyRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disable(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void delete_removesKey() {
        UUID id = UUID.randomUUID();
        ApiKey key = new ApiKey("hash", "k", Role.ADMIN, null);
        when(apiKeyRepository.findById(id)).thenReturn(Optional.of(key));

        service.delete(id);

        verify(apiKeyRepository).delete(key);
    }

    @Test
    void delete_throwsWhenKeyMissing() {
        UUID id = UUID.randomUUID();
        when(apiKeyRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(IllegalArgumentException.class);
        verify(apiKeyRepository, never()).delete(any());
    }
}
