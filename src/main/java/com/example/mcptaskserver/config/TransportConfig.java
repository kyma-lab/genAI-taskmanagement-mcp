package com.example.mcptaskserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for MCP transport modes (STDIO, HTTP, or both).
 */
@ConfigurationProperties(prefix = "mcp.transport")
@Getter
@Setter
public class TransportConfig {

    /**
     * Transport mode: stdio, http, or both
     */
    private String mode = "stdio";

    /**
     * HTTP transport configuration
     */
    private HttpConfig http = new HttpConfig();

    /**
     * Check if STDIO transport should be enabled
     */
    public boolean isStdioEnabled() {
        return "stdio".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode);
    }

    /**
     * Check if HTTP transport should be enabled
     */
    public boolean isHttpEnabled() {
        return "http".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode);
    }

    @Getter
    @Setter
    public static class HttpConfig {
        /**
         * HTTP server port
         */
        private int port = 8070;

        /**
         * Enable CORS support
         */
        private boolean corsEnabled = true;

        /**
         * Allowed CORS origins (empty = allow all)
         */
        private List<String> corsAllowedOrigins = new ArrayList<>();

        /**
         * SSE configuration
         */
        private SseConfig sse = new SseConfig();

        /**
         * Security configuration
         */
        private SecurityConfig security = new SecurityConfig();
    }

    @Getter
    @Setter
    public static class SseConfig {
        /**
         * SSE heartbeat interval in seconds
         */
        private int heartbeatIntervalSeconds = 30;

        /**
         * SSE connection timeout in minutes
         */
        private int connectionTimeoutMinutes = 5;

        /**
         * Maximum concurrent SSE connections
         */
        private int maxConnections = 100;
    }

    @Getter
    @Setter
    public static class SecurityConfig {
        /**
         * API keys for authentication (name -> key mapping)
         */
        private List<ApiKeyEntry> apiKeys = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class ApiKeyEntry {
        /**
         * Descriptive name for the API key
         */
        private String name;

        /**
         * The actual API key value
         */
        private String key;

        /**
         * Optional description of the key's purpose
         */
        private String description;
    }
}
