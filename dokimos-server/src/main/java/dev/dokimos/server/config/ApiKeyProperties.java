package dev.dokimos.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for API key authentication.
 * <p>
 * The API key can be configured via:
 * - Environment variable: {@code DOKIMOS_API_KEY}
 * - Application property: {@code dokimos.api-key}
 * <p>
 * When the API key is not set or empty, authentication is disabled
 * and all requests are allowed.
 */
@Component
@ConfigurationProperties(prefix = "dokimos")
public class ApiKeyProperties {

    private String apiKey;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Returns true if API key authentication is enabled.
     * Authentication is enabled when the API key is set and not empty.
     */
    public boolean isAuthEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
