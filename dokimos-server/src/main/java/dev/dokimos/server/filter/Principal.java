package dev.dokimos.server.filter;

/**
 * Authenticated caller.
 *
 * @param id stable identifier for the caller (an API key id, or {@code "system"} for the default
 *     unscoped principal)
 * @param role privilege level granted to the caller
 * @param tenantId tenant the principal belongs to, or {@code null} when no tenant applies
 */
public record Principal(String id, Role role, String tenantId) {

    private static final String SYSTEM_ID = "system";

    /**
     * System principal used when no API key authentication is configured, or for reads in an open
     * deployment. Carries {@link Role#ADMIN} and no tenant so existing single-key and no-key
     * deployments behave exactly as before.
     */
    public static Principal system() {
        return new Principal(SYSTEM_ID, Role.ADMIN, null);
    }
}
