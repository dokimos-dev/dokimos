package dev.dokimos.server.filter;

/**
 * Authenticated caller.
 *
 * @param tenantId tenant the principal belongs to, or {@code null} when no tenant applies
 */
public record Principal(String id, String tenantId) {

    private static final String SYSTEM_ID = "system";

    /** System principal used for every allowed request today (no tenant). */
    public static Principal system() {
        return new Principal(SYSTEM_ID, null);
    }
}
