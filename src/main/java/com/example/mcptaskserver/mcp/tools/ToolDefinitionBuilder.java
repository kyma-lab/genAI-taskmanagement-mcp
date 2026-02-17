package com.example.mcptaskserver.mcp.tools;

import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.experimental.UtilityClass;

/**
 * Utility class for building MCP Tool definitions with JSON schemas.
 */
@UtilityClass
public class ToolDefinitionBuilder {

    private static final JacksonMcpJsonMapper JSON_MAPPER = new JacksonMcpJsonMapper(new com.fasterxml.jackson.databind.ObjectMapper());

    /**
     * Creates a Tool definition with a JSON schema string.
     */
    public static Tool buildTool(String name, String description, String inputSchemaJson) {
        return Tool.builder()
            .name(name)
            .description(description)
            .inputSchema(JSON_MAPPER, inputSchemaJson)
            .build();
    }
}
