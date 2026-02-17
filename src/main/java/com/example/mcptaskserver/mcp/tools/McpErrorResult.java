package com.example.mcptaskserver.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import java.util.List;

/**
 * Factory for structured MCP error responses.
 *
 * <p>MCP spec recommends structured error information instead of plain text.
 * All error responses carry an {@code error} message and a machine-readable {@code code}.
 */
public final class McpErrorResult {

    private McpErrorResult() {}

    /** Input failed validation (missing / malformed parameters). */
    public static CallToolResult validationError(String message) {
        return build(message, "VALIDATION_ERROR");
    }

    /** Requested resource (e.g. job) does not exist. */
    public static CallToolResult notFoundError(String message) {
        return build(message, "NOT_FOUND");
    }

    /** Unexpected server-side error; details are intentionally omitted for the caller. */
    public static CallToolResult internalError(String message) {
        return build(message, "INTERNAL_ERROR");
    }

    /** Tool rate limit exceeded; includes retry timing for the caller. */
    public static CallToolResult rateLimitError(String toolName, long retryAfterSeconds) {
        String message = "Rate limit exceeded for tool: " + toolName +
            ". Please retry in " + retryAfterSeconds + " seconds.";
        String json = "{\"error\":\"" + jsonEscape(message) + "\",\"code\":\"RATE_LIMIT_EXCEEDED\",\"retryAfterSeconds\":" + retryAfterSeconds + "}";
        return new CallToolResult(List.of(new McpSchema.TextContent(json)), true);
    }

    private static CallToolResult build(String message, String code) {
        String json = "{\"error\":\"" + jsonEscape(message) + "\",\"code\":\"" + code + "\"}";
        return new CallToolResult(List.of(new McpSchema.TextContent(json)), true);
    }

    /**
     * Escapes a string for safe embedding in a JSON string value.
     * Handles all characters required by RFC 8259 §7:
     * backslash, double-quote, and control characters U+0000–U+001F.
     */
    static String jsonEscape(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
