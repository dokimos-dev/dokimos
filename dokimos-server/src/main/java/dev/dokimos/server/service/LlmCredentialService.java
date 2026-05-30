package dev.dokimos.server.service;

import dev.dokimos.server.config.ApiKeyProperties;
import dev.dokimos.server.entity.LlmConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Resolves and protects the API key for an {@link LlmConnection}. Inline keys are encrypted with
 * AES-256-GCM under a key derived from the configured encryption secret; external keys are read from
 * the named environment variable at resolution time. The master encryption secret is required only
 * when an inline key is encrypted or decrypted, so deployments that use only environment-backed
 * connections need not configure it.
 */
@Service
public class LlmCredentialService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final ApiKeyProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public LlmCredentialService(ApiKeyProperties properties) {
        this.properties = properties;
    }

    /**
     * Returns a copy of the connection with its inline key encrypted. Used on the create path before
     * the connection is persisted.
     *
     * @param connection the connection to populate
     * @param rawKey     the plaintext API key supplied by the caller
     * @return the connection with {@code encryptedApiKey} set
     * @throws IllegalStateException if the encryption secret is not configured
     */
    public LlmConnection encryptInlineKey(LlmConnection connection, String rawKey) {
        connection.setEncryptedApiKey(encrypt(rawKey));
        return connection;
    }

    /**
     * Resolves the effective plaintext API key for a connection.
     *
     * @param connection the connection to resolve
     * @return the plaintext API key
     * @throws IllegalStateException if a referenced environment variable is absent, or if an inline key
     *     cannot be decrypted, or if neither credential source is set
     */
    public String resolveKey(LlmConnection connection) {
        if (connection.hasInlineKey()) {
            return decrypt(connection.getEncryptedApiKey());
        }
        String ref = connection.getCredentialRef();
        if (ref != null && !ref.isBlank()) {
            String value = System.getenv(ref);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Credential environment variable is not set: " + ref);
            }
            return value;
        }
        throw new IllegalStateException("Connection has no credential source: " + connection.getId());
    }

    private String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt API key", e);
        }
    }

    private String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt API key", e);
        }
    }

    private SecretKeySpec secretKey() {
        String secret = properties.getEncryptionKey();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "DOKIMOS_ENCRYPTION_KEY must be set to register or use a connection with an inline API key");
        }
        try {
            byte[] derived = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(derived, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive encryption key", e);
        }
    }
}
