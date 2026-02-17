package com.example.mcptaskserver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Main application class for MCP Task Server.
 * 
 * Supports multiple transport modes:
 * - STDIO: Communicates via stdin/stdout (default)
 * - HTTP: Provides REST API with JSON-RPC 2.0 and SSE
 * - BOTH: Runs both transports simultaneously
 *
 * Transport mode is configured via MCP_TRANSPORT environment variable.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@Slf4j
public class McpTaskServerApplication {

    static final String ENV_TRANSPORT_MODE = "MCP_TRANSPORT";
    static final String DEFAULT_TRANSPORT_MODE = "stdio";

    public static void main(String[] args) {
        String transportMode = System.getenv().getOrDefault(ENV_TRANSPORT_MODE, DEFAULT_TRANSPORT_MODE);
        
        log.info("Starting MCP Task Server with transport mode: {}", transportMode);
        
        // Determine web application type based on transport mode
        WebApplicationType webType = determineWebApplicationType(transportMode);
        
        new SpringApplicationBuilder(McpTaskServerApplication.class)
            .web(webType)
            .run(args);
    }

    /**
     * Determine web application type based on transport mode
     */
    private static WebApplicationType determineWebApplicationType(String transportMode) {
        return switch (transportMode.toLowerCase()) {
            case "http", "both" -> {
                log.info("Enabling web server for HTTP transport");
                yield WebApplicationType.SERVLET;
            }
            case "stdio" -> {
                log.info("Running in STDIO mode (no web server)");
                yield WebApplicationType.NONE;
            }
            default -> {
                log.warn("Unknown transport mode '{}', defaulting to STDIO", transportMode);
                yield WebApplicationType.NONE;
            }
        };
    }
}
