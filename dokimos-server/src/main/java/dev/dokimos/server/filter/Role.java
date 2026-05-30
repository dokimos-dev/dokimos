package dev.dokimos.server.filter;

/**
 * Role granted to an authenticated {@link Principal}. Roles are ordered by privilege: a {@code VIEWER}
 * may only read, an {@code EDITOR} may also perform writes, and an {@code ADMIN} may additionally manage
 * API keys. The order is used by {@link #atLeast(Role)} to express "this role or higher".
 */
public enum Role {
    VIEWER,
    EDITOR,
    ADMIN;

    /**
     * Returns true when this role is at least as privileged as the required role.
     *
     * @param required the minimum role to satisfy
     * @return true when this role meets or exceeds {@code required}
     */
    public boolean atLeast(Role required) {
        return this.ordinal() >= required.ordinal();
    }
}
