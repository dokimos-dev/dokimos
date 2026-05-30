package dev.dokimos.server.filter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Hashes raw API keys to the SHA-256 hex form stored in the database. */
public final class ApiKeyHasher {

    private ApiKeyHasher() {}

    /**
     * Returns the lowercase SHA-256 hex digest of the given key. The raw key is never persisted; this
     * digest is what gets stored and compared at authentication time.
     *
     * @param rawKey the raw API key
     * @return 64-character lowercase hex digest
     */
    public static String sha256Hex(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }
}
