package dev.dokimos.server.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that authenticates {@code /api/v1/**} by delegating credential resolution to an
 * {@link Authenticator}, then enforces role-based authorization on the resolved {@link Principal}.
 *
 * <p>Authorization is additive over the existing seam. Reads pass through (the authenticator returns a
 * principal for them). Write methods (POST, PUT, PATCH, DELETE) require {@link Role#EDITOR} or higher.
 * The API key management endpoints under {@code /api/v1/api-keys} require {@link Role#ADMIN}. In an
 * unauthenticated deployment the authenticator resolves every request to the
 * {@linkplain Principal#system() system principal}, which is ADMIN, so these checks are satisfied and
 * behavior is unchanged.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    /** Request attribute holding the authenticated {@link Principal}. */
    public static final String PRINCIPAL_ATTRIBUTE = "dokimos.principal";

    /** Path prefix for the API key management endpoints, which require ADMIN. */
    public static final String API_KEYS_PATH_PREFIX = "/api/v1/api-keys";

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static final Set<String> WRITE_METHODS =
            Set.of(HttpMethod.POST.name(), HttpMethod.PUT.name(), HttpMethod.PATCH.name(), HttpMethod.DELETE.name());

    private final Authenticator authenticator;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(Authenticator authenticator, ObjectMapper objectMapper) {
        this.authenticator = authenticator;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        Optional<Principal> resolved = authenticator.authenticate(request.getMethod(), authHeader);

        if (resolved.isEmpty()) {
            sendError(response, HttpStatus.UNAUTHORIZED, "Invalid or missing API key");
            return;
        }

        Principal principal = resolved.get();
        Role required = requiredRole(request.getMethod(), request.getRequestURI());
        if (!principal.role().atLeast(required)) {
            sendError(response, HttpStatus.FORBIDDEN, "Insufficient role: " + required + " required");
            return;
        }

        request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
        filterChain.doFilter(request, response);
    }

    /**
     * Returns the minimum role a request must carry. API key management requires ADMIN for every
     * method, including listing, so key names and roles are not readable by an unauthenticated caller.
     * Other writes require EDITOR, and other reads require only VIEWER.
     */
    private Role requiredRole(String method, String path) {
        if (path.startsWith(API_KEYS_PATH_PREFIX)) {
            return Role.ADMIN;
        }
        if (WRITE_METHODS.contains(method)) {
            return Role.EDITOR;
        }
        return Role.VIEWER;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/");
    }

    private void sendError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, String> errorBody = Map.of("error", message);
        objectMapper.writeValue(response.getWriter(), errorBody);
    }
}
