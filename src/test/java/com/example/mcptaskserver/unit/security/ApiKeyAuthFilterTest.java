package com.example.mcptaskserver.unit.security;

import com.example.mcptaskserver.config.TransportConfig;
import com.example.mcptaskserver.http.security.ApiKeyAuthFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ApiKeyAuthFilter.
 *
 * Verifies that unauthorized responses comply with JSON-RPC 2.0 §5:
 * "id" MUST be null when the request id cannot be determined (filter runs before JSON parsing).
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

    private static final String VALID_KEY = "test-api-key-12345";
    private static final String API_KEY_HEADER = "X-API-Key";

    @Mock
    private FilterChain filterChain;

    private ApiKeyAuthFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        TransportConfig config = new TransportConfig();
        config.setMode("http");

        TransportConfig.ApiKeyEntry entry = new TransportConfig.ApiKeyEntry();
        entry.setName("test");
        entry.setKey(VALID_KEY);
        config.getHttp().getSecurity().setApiKeys(List.of(entry));

        filter = new ApiKeyAuthFilter(config);
        ReflectionTestUtils.setField(filter, "apiKeyEnabled", true);
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldPassRequest_whenApiKeyIsValid() throws Exception {
        MockHttpServletRequest request = mcpRequest();
        request.addHeader(API_KEY_HEADER, VALID_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(401);
    }

    @Test
    void shouldReturn401_whenApiKeyIsMissing() throws Exception {
        MockHttpServletRequest request = mcpRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldReturn401_whenApiKeyIsInvalid() throws Exception {
        MockHttpServletRequest request = mcpRequest();
        request.addHeader(API_KEY_HEADER, "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
    }

    @Test
    void missingKeyResponse_shouldBeValidJsonRpc_withNullId() throws Exception {
        // JSON-RPC 2.0 §5: id MUST be null when request id is unknown (pre-parse rejection)
        JsonNode json = objectMapper.readTree(ApiKeyAuthFilter.MISSING_KEY_RESPONSE);

        assertThat(json.get("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(json.get("id").isNull()).isTrue();
        assertThat(json.get("error").get("code").asInt()).isEqualTo(-32001);
        assertThat(json.get("error").get("message").asText()).isEqualTo("Missing API key");
    }

    @Test
    void invalidKeyResponse_shouldBeValidJsonRpc_withNullId() throws Exception {
        // JSON-RPC 2.0 §5: id MUST be null when request id is unknown (pre-parse rejection)
        JsonNode json = objectMapper.readTree(ApiKeyAuthFilter.INVALID_KEY_RESPONSE);

        assertThat(json.get("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(json.get("id").isNull()).isTrue();
        assertThat(json.get("error").get("code").asInt()).isEqualTo(-32001);
        assertThat(json.get("error").get("message").asText()).isEqualTo("Invalid API key");
    }

    @Test
    void shouldSetWwwAuthenticateHeader_onUnauthorized() throws Exception {
        MockHttpServletRequest request = mcpRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("ApiKey");
    }

    @Test
    void shouldSetContentTypeJson_onUnauthorized() throws Exception {
        MockHttpServletRequest request = mcpRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getContentType()).isEqualTo("application/json");
    }

    @Test
    void shouldPassRequest_whenPathIsHealthEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mcp/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldReturn401_whenPathEndsWithHealthButIsNotExactHealthEndpoint() throws Exception {
        // Regression: path.endsWith("/health") would bypass auth for arbitrary sub-paths
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mcp/evil/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldNotFilter_whenHttpTransportIsDisabled() throws Exception {
        TransportConfig stdioConfig = new TransportConfig();
        stdioConfig.setMode("stdio");
        ApiKeyAuthFilter stdioFilter = new ApiKeyAuthFilter(stdioConfig);

        MockHttpServletRequest request = mcpRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        stdioFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void unauthorizedResponse_shouldContainValidJson_parseable() throws Exception {
        MockHttpServletRequest request = mcpRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        // Must be parseable JSON (no injection artifacts)
        assertThat(response.getContentAsString()).isNotEmpty();
        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertThat(json.has("jsonrpc")).isTrue();
        assertThat(json.has("error")).isTrue();
        assertThat(json.has("id")).isTrue();
    }

    // ── Both mode (HTTP + STDIO) ───────────────────────────────────────────

    @Test
    void bothMode_shouldEnforceAuth_whenApiKeyIsMissing() throws Exception {
        // isHttpEnabled() returns true for "both" → filter must still enforce auth
        ApiKeyAuthFilter bothFilter = filterForMode("both");

        MockHttpServletRequest request = mcpRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        bothFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
    }

    @Test
    void bothMode_shouldPassRequest_whenApiKeyIsValid() throws Exception {
        ApiKeyAuthFilter bothFilter = filterForMode("both");

        MockHttpServletRequest request = mcpRequest();
        request.addHeader(API_KEY_HEADER, VALID_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        bothFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(401);
    }

    @Test
    void bothMode_shouldPassHealthEndpoint_withoutAuth() throws Exception {
        ApiKeyAuthFilter bothFilter = filterForMode("both");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mcp/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        bothFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    // ── SSE stream endpoint (GET /mcp) ────────────────────────────────────
    //
    // The MCP Streamable-HTTP transport uses GET /mcp to open the SSE stream for
    // server-push notifications, and POST /mcp for JSON-RPC requests.
    // Both methods share the same path and MUST be protected by the same filter.

    @Test
    void sseStream_shouldReturn401_whenApiKeyIsMissing() throws Exception {
        MockHttpServletRequest request = sseRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
    }

    @Test
    void sseStream_shouldPassRequest_whenApiKeyIsValid() throws Exception {
        MockHttpServletRequest request = sseRequest();
        request.addHeader(API_KEY_HEADER, VALID_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(401);
    }

    @Test
    void sseStream_bothMode_shouldReturn401_whenApiKeyIsMissing() throws Exception {
        ApiKeyAuthFilter bothFilter = filterForMode("both");

        MockHttpServletRequest request = sseRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        bothFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
    }

    // ── shouldNotFilter scope ─────────────────────────────────────────────
    //
    // The filter must only intercept /mcp and /mcp/** — all other paths are
    // outside the MCP transport and must pass through untouched.

    @Test
    void shouldSkip_nonMcpPaths() throws Exception {
        // Paths like /actuator, /api/v1, / must not be intercepted
        for (String path : List.of("/", "/actuator/health", "/api/v1/tasks", "/other")) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            // shouldNotFilter returns true → doFilterInternal is never called → chain passes through
            verify(filterChain, atLeastOnce()).doFilter(request, response);
            assertThat(response.getStatus()).isNotEqualTo(401);
        }
    }

    @Test
    void shouldFilter_allMcpSubPaths() throws Exception {
        // /mcp/messages, /mcp/session etc. are part of MCP Streamable-HTTP protocol
        for (String path : List.of("/mcp", "/mcp/messages", "/mcp/session", "/mcp/resources")) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus())
                .as("path %s should require auth", path)
                .isEqualTo(401);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────

    /** POST /mcp — JSON-RPC request endpoint */
    private MockHttpServletRequest mcpRequest() {
        return new MockHttpServletRequest("POST", "/mcp");
    }

    /** GET /mcp — SSE stream endpoint (server-push notifications) */
    private MockHttpServletRequest sseRequest() {
        return new MockHttpServletRequest("GET", "/mcp");
    }

    @Test
    void shouldPassRequest_whenApiKeyAuthIsDisabled() throws Exception {
        // security.api-key.enabled=false must bypass all API key checks (dev/test profile)
        ApiKeyAuthFilter disabledFilter = new ApiKeyAuthFilter(filter_transportConfig());
        ReflectionTestUtils.setField(disabledFilter, "apiKeyEnabled", false);

        MockHttpServletRequest request = mcpRequest(); // no API key header
        MockHttpServletResponse response = new MockHttpServletResponse();

        disabledFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(401);
    }

    private ApiKeyAuthFilter filterForMode(String mode) {
        TransportConfig config = new TransportConfig();
        config.setMode(mode);
        TransportConfig.ApiKeyEntry entry = new TransportConfig.ApiKeyEntry();
        entry.setName("test");
        entry.setKey(VALID_KEY);
        config.getHttp().getSecurity().setApiKeys(List.of(entry));
        ApiKeyAuthFilter f = new ApiKeyAuthFilter(config);
        ReflectionTestUtils.setField(f, "apiKeyEnabled", true);
        return f;
    }

    /** Returns a TransportConfig identical to the one used in setUp(). */
    private TransportConfig filter_transportConfig() {
        TransportConfig config = new TransportConfig();
        config.setMode("http");
        TransportConfig.ApiKeyEntry entry = new TransportConfig.ApiKeyEntry();
        entry.setName("test");
        entry.setKey(VALID_KEY);
        config.getHttp().getSecurity().setApiKeys(List.of(entry));
        return config;
    }
}
