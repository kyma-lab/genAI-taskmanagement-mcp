package com.example.mcptaskserver.mcp.tools;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.util.Map;

/**
 * Common interface for all MCP tool implementations.
 * Eliminates instanceof chains in server configuration.
 */
public interface McpTool {

    Tool toolDefinition();

    /**
     * Executes the tool. The exchange gives access to the MCP session
     * (e.g. for logging notifications). May be null in unit tests.
     */
    CallToolResult execute(McpSyncServerExchange exchange, Map<String, Object> arguments);

    /**
     * Convenience overload for callers without a session context (e.g. tests).
     */
    default CallToolResult execute(Map<String, Object> arguments) {
        return execute(null, arguments);
    }

    default McpServerFeatures.SyncToolSpecification toSyncToolSpecification() {
        return new McpServerFeatures.SyncToolSpecification(
            toolDefinition(),
            (exchange, args) -> execute(exchange, args)
        );
    }
}
