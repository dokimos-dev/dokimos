package dev.dokimos.server.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.server.config.ApiKeyProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

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
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final Set<String> READ_METHODS = Set.of(
            HttpMethod.GET.name(),
            HttpMethod.HEAD.name(),
            HttpMethod.OPTIONS.name());

    private final ApiKeyProperties apiKeyProperties;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(ApiKeyProperties apiKeyProperties, ObjectMapper objectMapper) {
        this.apiKeyProperties = apiKeyProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // If auth is disabled, we allow all requests
        if (!apiKeyProperties.isAuthEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Allow read-only methods without authentication
        String method = request.getMethod();
        if (READ_METHODS.contains(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // For write operations, we check the API key
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            sendUnauthorizedResponse(response, "Invalid or missing API key");
            return;
        }

        String providedKey = authHeader.substring(BEARER_PREFIX.length());
        if (!apiKeyProperties.getApiKey().equals(providedKey)) {
            sendUnauthorizedResponse(response, "Invalid or missing API key");
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // Only apply this filter to /api/v1/** paths
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/");
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, String> errorBody = Map.of("error", message);
        objectMapper.writeValue(response.getWriter(), errorBody);
    }
}
