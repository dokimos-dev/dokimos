package dev.dokimos.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.dokimos.server.config.ApiKeyProperties;
import dev.dokimos.server.entity.LlmConnection;
import org.junit.jupiter.api.Test;

class LlmCredentialServiceTest {

    private static LlmCredentialService serviceWithKey(String encryptionKey) {
        ApiKeyProperties properties = new ApiKeyProperties();
        properties.setEncryptionKey(encryptionKey);
        return new LlmCredentialService(properties);
    }

    @Test
    void encryptDecryptRoundTrip() {
        LlmCredentialService service = serviceWithKey("master-secret");
        LlmConnection connection = new LlmConnection("c", "https://api.example.com", "gpt-4");

        service.encryptInlineKey(connection, "sk-secret-123");

        assertThat(connection.getEncryptedApiKey()).isNotBlank();
        assertThat(connection.getEncryptedApiKey()).doesNotContain("sk-secret-123");
        assertThat(service.resolveKey(connection)).isEqualTo("sk-secret-123");
    }

    @Test
    void encryptingProducesDistinctCiphertextForSamePlaintext() {
        LlmCredentialService service = serviceWithKey("master-secret");
        LlmConnection first = new LlmConnection("a", "https://api.example.com", "gpt-4");
        LlmConnection second = new LlmConnection("b", "https://api.example.com", "gpt-4");

        service.encryptInlineKey(first, "sk-same");
        service.encryptInlineKey(second, "sk-same");

        assertThat(first.getEncryptedApiKey()).isNotEqualTo(second.getEncryptedApiKey());
    }

    @Test
    void missingEncryptionKeyFailsForInlineKey() {
        LlmCredentialService service = serviceWithKey("");
        LlmConnection connection = new LlmConnection("c", "https://api.example.com", "gpt-4");

        assertThatThrownBy(() -> service.encryptInlineKey(connection, "sk-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DOKIMOS_ENCRYPTION_KEY");
    }

    @Test
    void resolvesKeyFromEnvironmentVariable() {
        String existingVar = "PATH";
        assumeTrue(System.getenv(existingVar) != null, "PATH not available in environment");

        LlmCredentialService service = serviceWithKey(null);
        LlmConnection connection = new LlmConnection("c", "https://api.example.com", "gpt-4");
        connection.setCredentialRef(existingVar);

        assertThat(service.resolveKey(connection)).isEqualTo(System.getenv(existingVar));
    }

    @Test
    void missingEnvironmentVariableFails() {
        LlmCredentialService service = serviceWithKey(null);
        LlmConnection connection = new LlmConnection("c", "https://api.example.com", "gpt-4");
        connection.setCredentialRef("DOKIMOS_DEFINITELY_NOT_SET_VAR_XYZ");

        assertThatThrownBy(() -> service.resolveKey(connection))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not set");
    }
}
