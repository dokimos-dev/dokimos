package dev.dokimos.server.filter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dokimos.server.config.ApiKeyProperties;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiKeyAuthenticatorTest {

    private static final String TEST_API_KEY = "test-secret-key-12345";

    private ApiKeyProperties apiKeyProperties;
    private ApiKeyAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        apiKeyProperties = new ApiKeyProperties();
        authenticator = new ApiKeyAuthenticator(apiKeyProperties);
    }

    @Test
    void shouldReturnPrincipalWhenAuthDisabled() {
        apiKeyProperties.setApiKey(null);

        Optional<Principal> result = authenticator.authenticate("POST", null);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("system");
        assertThat(result.get().tenantId()).isNull();
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
