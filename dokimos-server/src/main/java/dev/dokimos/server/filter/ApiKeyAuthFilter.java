package dev.dokimos.server.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that enforces API key authentication for write operations on
 * /api/v1/** endpoints.
 * <p>
 * GET requests are always allowed.
 * POST, PUT, PATCH, DELETE requests require a valid API key in the
 * Authorization header.
 * <p>
 * Expected header format: Authorization: Bearer &lt;api-key&gt;
 * <p>
 * If authentication is disabled (no API key configured), all requests are
 * allowed.
 * <p>
 * The actual allow/reject decision is delegated to an {@link Authenticator}, so future
 * authorization schemes can be added without changing this filter.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    /** Request attribute under which the authenticated {@link Principal} is stashed for later use. */
    public static final String PRINCIPAL_ATTRIBUTE = "dokimos.principal";

    private static final String AUTHORIZATION_HEADER = "Authorization";

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
        Optional<Principal> principal = authenticator.authenticate(request.getMethod(), authHeader);

        if (principal.isEmpty()) {
            sendUnauthorizedResponse(response, "Invalid or missing API key");
            return;
        }

        request.setAttribute(PRINCIPAL_ATTRIBUTE, principal.get());
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // Only apply this filter to /api/v1/** paths
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/");
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, String> errorBody = Map.of("error", message);
        objectMapper.writeValue(response.getWriter(), errorBody);
    }
}
