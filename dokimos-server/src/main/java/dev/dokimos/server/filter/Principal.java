package dev.dokimos.server.filter;

/**
 * An authenticated caller. Identifies who made an allowed request so future authorization logic
 * (for example tenant-scoped RBAC) can build on it without changing the authentication seam.
 *
 * @param id the principal identifier
 * @param tenantId the tenant the principal belongs to, or {@code null} when no tenant applies
 */
public record Principal(String id, String tenantId) {

    private static final String SYSTEM_ID = "system";

    /**
     * Returns the system principal used today for every allowed request: auth disabled, read-only
     * methods, and writes carrying a valid API key. It has no tenant.
     *
     * @return the system principal
     */
    public static Principal system() {
        return new Principal(SYSTEM_ID, null);
    }
}
