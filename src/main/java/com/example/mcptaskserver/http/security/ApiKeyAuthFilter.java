package com.example.mcptaskserver.http.security;

import com.example.mcptaskserver.config.TransportConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

/**
 * Filter for API key authentication on HTTP transport.
 * Authentication can be disabled via {@code security.api-key.enabled=false} (dev/test only).
 */
@Component
@Order(2)
@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final TransportConfig transportConfig;

    @Value("${security.api-key.enabled:true}")
    private boolean apiKeyEnabled;

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String WWW_AUTHENTICATE_HEADER = "WWW-Authenticate";

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        // Only apply filter if HTTP transport is enabled and API key auth is not disabled
        if (!transportConfig.isHttpEnabled() || !apiKeyEnabled) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String path = request.getRequestURI();
        
        // Allow health check without authentication (exact path only)
        if (path.equals("/mcp/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Get API key from header
        String apiKey = request.getHeader(API_KEY_HEADER);
        
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("Missing API key in request to {}", path);
            sendUnauthorized(response, MISSING_KEY_RESPONSE);
            return;
        }

        // Validate API key
        if (!isValidApiKey(apiKey)) {
            String hashedKey = hashApiKey(apiKey);
            log.warn("Invalid API key attempt (hashed: {})", hashedKey);
            sendUnauthorized(response, INVALID_KEY_RESPONSE);
            return;
        }

        // API key is valid, proceed
        log.debug("API key validated successfully for {}", path);
        filterChain.doFilter(request, response);
    }

    /**
     * Check if the provided API key is valid using constant-time comparison
     * to prevent timing attacks.
     */
    private boolean isValidApiKey(String apiKey) {
        List<TransportConfig.ApiKeyEntry> configuredKeys = transportConfig.getHttp().getSecurity().getApiKeys();
        boolean hasConfiguredKeys = configuredKeys.stream()
                .anyMatch(entry -> entry.getKey() != null && !entry.getKey().isEmpty());

        if (!hasConfiguredKeys) {
            log.error("SECURITY: No API keys configured for HTTP transport. Rejecting all requests.");
            return false;
        }

        byte[] incoming = apiKey.getBytes(StandardCharsets.UTF_8);
        return configuredKeys.stream()
                .map(TransportConfig.ApiKeyEntry::getKey)
                .filter(k -> k != null && !k.isEmpty())
                .anyMatch(validKey -> MessageDigest.isEqual(
                        validKey.getBytes(StandardCharsets.UTF_8),
                        incoming
                ));
    }

    /**
     * Hash API key for logging (security: never log actual keys)
     */
    private String hashApiKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            return "hash-error";
        }
    }

    /**
     * Pre-built JSON-RPC 2.0 error responses for authentication failures.
     *
     * <p>Per JSON-RPC 2.0 spec §5: "If there was an error in detecting the id in the Request
     * object (e.g. Parse error/Invalid Request), it MUST be Null." Since this filter runs before
     * the request body is parsed, the request id is always unknown → {@code "id":null} is the
     * correct and required value.
     *
     * <p>Error code -32001 is a server-defined error in the reserved range [-32099, -32000].
     *
     * <p>Using pre-built constants instead of String.format avoids JSON-injection if a message
     * string ever contains characters that need escaping (e.g. quotes, backslashes).
     */
    public static final String MISSING_KEY_RESPONSE =
            "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32001,\"message\":\"Missing API key\"},\"id\":null}";

    public static final String INVALID_KEY_RESPONSE =
            "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32001,\"message\":\"Invalid API key\"},\"id\":null}";

    private void sendUnauthorized(HttpServletResponse response, String preBuiltJson) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(WWW_AUTHENTICATE_HEADER, "ApiKey");
        response.setContentType("application/json");
        response.getWriter().write(preBuiltJson);
        response.getWriter().flush();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only filter MCP endpoints: exact /mcp and /mcp/** subpaths
        String path = request.getRequestURI();
        return !path.equals("/mcp") && !path.startsWith("/mcp/");
    }
}
