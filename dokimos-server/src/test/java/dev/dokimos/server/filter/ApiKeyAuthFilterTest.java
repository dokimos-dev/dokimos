package dev.dokimos.server.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.server.config.ApiKeyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyAuthFilterTest {

    private static final String TEST_API_KEY = "test-secret-key-12345";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ApiKeyProperties apiKeyProperties;
    private ApiKeyAuthFilter filter;

    @BeforeEach
    void setUp() {
        apiKeyProperties = new ApiKeyProperties();
        filter = new ApiKeyAuthFilter(apiKeyProperties, objectMapper);
    }

    @Nested
    class WhenAuthIsDisabled {

        @BeforeEach
        void setUp() {
            // API key is not set and auth is therefore disabled
            apiKeyProperties.setApiKey(null);
        }

        @Test
        void shouldAllowGetRequestsWithoutAuth() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/projects");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(filterChain.getRequest()).isNotNull();
        }

        @Test
        void shouldAllowPostRequestsWithoutAuth() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/runs");
            request.setContentType("application/json");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(filterChain.getRequest()).isNotNull();
        }

        @Test
        void shouldAllowPatchRequestsWithoutAuth() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/runs/123");
            request.setContentType("application/json");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(filterChain.getRequest()).isNotNull();
        }

        @Test
        void shouldAllowDeleteRequestsWithoutAuth() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/runs/123");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(filterChain.getRequest()).isNotNull();
        }
    }

    @Nested
    class WhenAuthIsEnabled {

        @BeforeEach
        void setUp() {
            apiKeyProperties.setApiKey(TEST_API_KEY);
        }

        @Test
        void shouldAllowGetRequestsWithoutAuth() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/projects");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(filterChain.getRequest()).isNotNull();
        }

        @Test
        void shouldAllowHeadRequestsWithoutAuth() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("HEAD", "/api/v1/projects");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(filterChain.getRequest()).isNotNull();
        }

        @Test
        void shouldAllowOptionsRequestsWithoutAuth() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/projects");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(filterChain.getRequest()).isNotNull();
        }

        @Test
        void shouldRejectPostRequestsWithoutAuth() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/runs");
            request.setContentType("application/json");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentType()).isEqualTo("application/json");
            assertThat(response.getContentAsString()).contains("Invalid or missing API key");
            assertThat(filterChain.getRequest()).isNull();
        }

        @Test
        void shouldRejectPatchRequestsWithoutAuth() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/runs/123");
            request.setContentType("application/json");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(filterChain.getRequest()).isNull();
        }

        @Test
        void shouldRejectPutRequestsWithoutAuth() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/runs/123");
            request.setContentType("application/json");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(filterChain.getRequest()).isNull();
        }

        @Test
        void shouldRejectDeleteRequestsWithoutAuth() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/runs/123");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(filterChain.getRequest()).isNull();
        }

        @Test
        void shouldRejectPostWithInvalidApiKey() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/runs");
            request.setContentType("application/json");
            request.addHeader("Authorization", "Bearer wrong-key");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("Invalid or missing API key");
            assertThat(filterChain.getRequest()).isNull();
        }

        @Test
        void shouldRejectPostWithMalformedAuthHeader() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/runs");
            request.setContentType("application/json");
            request.addHeader("Authorization", "Basic " + TEST_API_KEY);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(filterChain.getRequest()).isNull();
        }

        @Test
        void shouldAllowPostWithValidApiKey() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/runs");
            request.setContentType("application/json");
            request.addHeader("Authorization", "Bearer " + TEST_API_KEY);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(filterChain.getRequest()).isNotNull();
        }

        @Test
        void shouldAllowPatchWithValidApiKey() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/runs/123");
            request.setContentType("application/json");
            request.addHeader("Authorization", "Bearer " + TEST_API_KEY);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(filterChain.getRequest()).isNotNull();
        }

        @Test
        void shouldAllowDeleteWithValidApiKey() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/runs/123");
            request.addHeader("Authorization", "Bearer " + TEST_API_KEY);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(filterChain.getRequest()).isNotNull();
        }
    }

    @Nested
    class PathFiltering {

        @BeforeEach
        void setUp() {
            apiKeyProperties.setApiKey(TEST_API_KEY);
        }

        @Test
        void shouldNotFilterNonApiPaths() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/actuator/health");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filter.doFilter(request, response, filterChain);

            // Filter should not apply, so request passes through
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(filterChain.getRequest()).isNotNull();
        }

        @Test
        void shouldNotFilterStaticPaths() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/index.html");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filter.doFilter(request, response, filterChain);

            // Filter should not apply, so request passes through
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(filterChain.getRequest()).isNotNull();
        }

        @Test
        void shouldFilterApiV1Paths() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/experiments/123/runs");
            request.setContentType("application/json");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filter.doFilter(request, response, filterChain);

            // Filter applies and blocks without auth
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(filterChain.getRequest()).isNull();
        }
    }

    @Nested
    class EmptyApiKey {

        @Test
        void shouldTreatEmptyStringAsDisabled() throws Exception {
            apiKeyProperties.setApiKey("");

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/runs");
            request.setContentType("application/json");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filter.doFilter(request, response, filterChain);

            // Auth disabled, request passes through
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(filterChain.getRequest()).isNotNull();
        }

        @Test
        void shouldTreatBlankStringAsDisabled() throws Exception {
            apiKeyProperties.setApiKey("   ");

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/runs");
            request.setContentType("application/json");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filter.doFilter(request, response, filterChain);

            // Auth disabled, request passes through
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(filterChain.getRequest()).isNotNull();
        }
    }
}
