package dev.dokimos.server.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import dev.dokimos.server.config.ApiKeyProperties;
import dev.dokimos.server.entity.ApiKey;
import dev.dokimos.server.repository.ApiKeyRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticatorTest {

    private static final String TEST_API_KEY = "test-secret-key-12345";

    @Mock
    private ApiKeyRepository apiKeyRepository;

    private ApiKeyProperties apiKeyProperties;
    private ApiKeyAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        apiKeyProperties = new ApiKeyProperties();
        authenticator = new ApiKeyAuthenticator(apiKeyProperties, apiKeyRepository);
        lenient().when(apiKeyRepository.existsByEnabledTrue()).thenReturn(false);
        lenient()
                .when(apiKeyRepository.findByKeyHashAndEnabledTrue(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    void shouldReturnPrincipalWhenAuthDisabled() {
        apiKeyProperties.setApiKey(null);

        Optional<Principal> result = authenticator.authenticate("POST", null);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("system");
        assertThat(result.get().role()).isEqualTo(Role.ADMIN);
        assertThat(result.get().tenantId()).isNull();
    }

    @Test
    void shouldResolveScopedKeyToItsRoleAndTenant() {
        // No legacy key, but an enabled scoped key exists, so the deployment is in authenticated mode.
        apiKeyProperties.setApiKey(null);
        ApiKey stored = new ApiKey(ApiKeyHasher.sha256Hex("raw-editor-key"), "ci", Role.EDITOR, "tenant-7");
        when(apiKeyRepository.existsByEnabledTrue()).thenReturn(true);
        when(apiKeyRepository.findByKeyHashAndEnabledTrue(ApiKeyHasher.sha256Hex("raw-editor-key")))
                .thenReturn(Optional.of(stored));

        Optional<Principal> result = authenticator.authenticate("POST", "Bearer raw-editor-key");

        assertThat(result).isPresent();
        assertThat(result.get().role()).isEqualTo(Role.EDITOR);
        assertThat(result.get().tenantId()).isEqualTo("tenant-7");
        assertThat(stored.getLastUsedAt()).isNotNull();
    }

    @Test
    void shouldRejectWriteInAuthenticatedModeWhenScopedKeyUnknown() {
        // A scoped key exists (authenticated mode), but the presented key matches nothing enabled.
        apiKeyProperties.setApiKey(null);
        when(apiKeyRepository.existsByEnabledTrue()).thenReturn(true);
        when(apiKeyRepository.findByKeyHashAndEnabledTrue(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());

        Optional<Principal> result = authenticator.authenticate("POST", "Bearer no-such-key");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldKeepReadsOpenInAuthenticatedMode() {
        apiKeyProperties.setApiKey(null);
        when(apiKeyRepository.existsByEnabledTrue()).thenReturn(true);

        Optional<Principal> result = authenticator.authenticate("GET", null);

        assertThat(result).isPresent();
        assertThat(result.get().role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void legacyKeyMapsToAdmin() {
        apiKeyProperties.setApiKey(TEST_API_KEY);

        Optional<Principal> result = authenticator.authenticate("POST", "Bearer " + TEST_API_KEY);

        assertThat(result).isPresent();
        assertThat(result.get().role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void shouldReturnPrincipalForReadMethod() {
        apiKeyProperties.setApiKey(TEST_API_KEY);

        Optional<Principal> result = authenticator.authenticate("GET", null);

        assertThat(result).isPresent();
    }

    @Test
    void shouldReturnPrincipalForWriteWithValidKey() {
        apiKeyProperties.setApiKey(TEST_API_KEY);

        Optional<Principal> result = authenticator.authenticate("POST", "Bearer " + TEST_API_KEY);

        assertThat(result).isPresent();
    }

    @Test
    void shouldReturnEmptyForWriteWithMissingHeader() {
        apiKeyProperties.setApiKey(TEST_API_KEY);

        Optional<Principal> result = authenticator.authenticate("POST", null);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyForWriteWithInvalidKey() {
        apiKeyProperties.setApiKey(TEST_API_KEY);

        Optional<Principal> result = authenticator.authenticate("DELETE", "Bearer wrong-key");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyForWriteWithMalformedHeader() {
        apiKeyProperties.setApiKey(TEST_API_KEY);

        Optional<Principal> result = authenticator.authenticate("POST", "Basic " + TEST_API_KEY);

        assertThat(result).isEmpty();
    }
}
