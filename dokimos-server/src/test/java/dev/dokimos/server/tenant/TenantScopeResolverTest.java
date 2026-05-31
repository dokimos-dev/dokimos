package dev.dokimos.server.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dokimos.server.filter.ApiKeyAuthFilter;
import dev.dokimos.server.filter.Principal;
import dev.dokimos.server.filter.Role;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Verifies the principal-to-scope mapping the resolver applies, with emphasis on the fail-closed
 * behavior when the auth filter set no principal and the regression guard that a request carrying the
 * system principal still resolves to the unrestricted scope (no-key and legacy single-key mode).
 */
class TenantScopeResolverTest {

    @Test
    void scopeFailsClosedToSharedOnlyWhenNoPrincipalAttribute() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        TenantScope scope = TenantScopeResolver.scope(request);

        assertThat(scope.restricted()).isTrue();
        assertThat(scope.tenantId()).isNull();
    }

    @Test
    void scopeStaysUnrestrictedForSystemPrincipal() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ApiKeyAuthFilter.PRINCIPAL_ATTRIBUTE, Principal.system());

        TenantScope scope = TenantScopeResolver.scope(request);

        assertThat(scope.restricted()).isFalse();
        assertThat(scope).isEqualTo(TenantScope.unrestricted());
    }

    @Test
    void scopeResolvesToTenantForScopedKeyPrincipal() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ApiKeyAuthFilter.PRINCIPAL_ATTRIBUTE, new Principal("key-7", Role.EDITOR, "tenant-acme"));

        TenantScope scope = TenantScopeResolver.scope(request);

        assertThat(scope.restricted()).isTrue();
        assertThat(scope.tenantId()).isEqualTo("tenant-acme");
    }

    @Test
    void scopeForAnonymousReaderCollapsesToSharedOnly() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ApiKeyAuthFilter.PRINCIPAL_ATTRIBUTE, Principal.anonymous());

        TenantScope scope = TenantScopeResolver.scope(request);

        assertThat(scope.restricted()).isTrue();
        assertThat(scope.tenantId()).isNull();
    }

    @Test
    void principalFallsBackToSystemWhenNoAttribute() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        Principal principal = TenantScopeResolver.principal(request);

        assertThat(principal.isSystem()).isTrue();
    }

    @Test
    void principalIdIsNullWhenNoAttribute() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(TenantScopeResolver.principalId(request)).isNull();
    }
}
