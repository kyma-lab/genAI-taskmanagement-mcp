package com.example.mcptaskserver.config;

import com.example.mcptaskserver.mcp.prompts.TaskPromptProvider;
import com.example.mcptaskserver.mcp.resources.TaskResourceProvider;
import com.example.mcptaskserver.mcp.tools.McpTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * MCP Server configuration using official MCP SDK.
 * 
 * Supports three transport modes:
 * - stdio: STDIO transport only (trusted local channel, no API key auth)
 * - http:  HTTP/SSE transport only (authenticated via API keys)
 * - both:  Both transports active simultaneously
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class McpServerConfig {

    private final TransportConfig transportConfig;
    private final List<McpTool> allTools;
    private final TaskResourceProvider taskResourceProvider;
    private final TaskPromptProvider taskPromptProvider;

    @Value("${security.api-key.enabled:true}")
    private boolean apiKeyEnabled;

    private static final String SERVER_NAME = "mcp-task-server";
    private static final String SERVER_VERSION = "1.0.0";

    /**
     * Builds server capabilities shared by both STDIO and HTTP transports.
     */
    private ServerCapabilities buildCapabilities() {
        return ServerCapabilities.builder()
            .tools(true)
            .resources(false, true)   // subscribe=false (no handler), listChanged=true
            .prompts(true)
            .logging()
            .experimental(Map.of("asyncBatch", new McpSchema.Implementation("asyncBatch", "1.0.0")))
            .build();
    }

    /**
     * Builds an McpSyncServer from a pre-configured SDK spec and registers all capabilities.
     * Shared by both STDIO and HTTP server factory methods.
     */
    private McpSyncServer buildServer(McpServer.SyncSpecification<?> spec) {
        McpSyncServer server = spec
            .serverInfo(SERVER_NAME, SERVER_VERSION)
            .capabilities(buildCapabilities())
            .immediateExecution(true)  // Preserve thread-locals for Spring
            .build();
        registerCapabilities(server);
        return server;
    }

    /**
     * Creates the MCP Sync Server with STDIO transport.
     * Only created when STDIO transport is enabled.
     */
    @Bean("mcpStdioSyncServer")
    @ConditionalOnStdioEnabled
    public McpSyncServer mcpStdioSyncServer(ObjectMapper objectMapper) {
        log.info("Initializing MCP Server with STDIO transport...");
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);
        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(jsonMapper);
        McpSyncServer server = buildServer(McpServer.sync(transportProvider));
        log.info("MCP Server initialized: {} v{}", SERVER_NAME, SERVER_VERSION);
        return server;
    }

    /**
     * Creates the HttpServletStreamableServerTransportProvider for HTTP transport.
     * Implements the MCP Streamable HTTP spec (2025-06-18):
     * - POST /mcp  → JSON-RPC messages
     * - GET  /mcp  → SSE stream
     * - DELETE /mcp → close session
     */
    @Bean
    @ConditionalOnHttpEnabled
    public HttpServletStreamableServerTransportProvider mcpHttpTransportProvider(ObjectMapper objectMapper) {
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);
        return HttpServletStreamableServerTransportProvider.builder()
            .jsonMapper(jsonMapper)
            .mcpEndpoint("/mcp")
            .build();
    }

    /**
     * Creates the MCP Sync Server with HTTP transport.
     * Only created when HTTP transport is enabled.
     */
    @Bean("mcpHttpSyncServer")
    @ConditionalOnHttpEnabled
    public McpSyncServer mcpHttpSyncServer(HttpServletStreamableServerTransportProvider transportProvider) {
        log.info("Initializing MCP Server with HTTP transport...");
        if (apiKeyEnabled && transportConfig.getHttp().getSecurity().getApiKeys().isEmpty()) {
            throw new IllegalStateException(
                "HTTP transport requires at least one API key. " +
                "Configure mcp.transport.http.security.api-keys or set security.api-key.enabled=false " +
                "to explicitly disable authentication (dev/test only).");
        }
        if (!apiKeyEnabled) {
            log.warn("SECURITY: API key authentication is disabled. This must only be used in dev/test environments.");
        }
        McpSyncServer server = buildServer(McpServer.sync(transportProvider));
        log.info("MCP HTTP Server initialized: {} v{}", SERVER_NAME, SERVER_VERSION);
        return server;
    }

    /**
     * Registers the HttpServletStreamableServerTransportProvider as a servlet at /mcp.
     */
    @Bean
    @ConditionalOnHttpEnabled
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServletRegistration(
            HttpServletStreamableServerTransportProvider transportProvider) {
        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
            new ServletRegistrationBean<>(transportProvider, "/mcp");
        registration.setAsyncSupported(true);
        registration.setName("mcpStreamableServlet");
        return registration;
    }

    /**
     * Registers tools, resource templates, and prompts on the given server.
     */
    private void registerCapabilities(McpSyncServer server) {
        allTools.forEach(tool -> server.addTool(tool.toSyncToolSpecification()));
        taskResourceProvider.getStaticResourceSpecifications().forEach(server::addResource);
        taskResourceProvider.getTemplateResourceSpecifications().forEach(server::addResourceTemplate);
        taskPromptProvider.getPromptSpecifications().forEach(server::addPrompt);
        log.info("Registered {} tools, {} static resources, {} resource templates, {} prompts",
            allTools.size(),
            taskResourceProvider.getStaticResourceSpecifications().size(),
            taskResourceProvider.getTemplateResourceSpecifications().size(),
            taskPromptProvider.getPromptSpecifications().size());
    }

    /**
     * Starts the STDIO transport. Not active in HTTP-only mode.
     * Note: no API key auth — STDIO is a trusted local channel.
     */
    @Bean
    @ConditionalOnStdioEnabled
    public CommandLineRunner mcpStdioRunner(@Qualifier("mcpStdioSyncServer") McpSyncServer mcpStdioSyncServer) {
        return args -> {
            log.info("Starting MCP STDIO Server...");
            log.info("Transport mode: {}", transportConfig.getMode());
            log.info("MCP STDIO Server is running. Press Ctrl+C to shutdown.");

            if ("both".equalsIgnoreCase(transportConfig.getMode())) {
                // In 'both' mode the Tomcat thread already keeps the JVM alive.
                log.info("STDIO transport active in dual-transport mode (HTTP server keeps JVM alive)");
            } else {
                // STDIO-only mode: block main thread so the JVM does not exit
                Object lock = new Object();
                synchronized (lock) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.info("Server shutdown requested");
                    }
                }
            }
        };
    }
}
