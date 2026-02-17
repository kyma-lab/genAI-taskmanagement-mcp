package com.example.mcptaskserver.unit.tools;

import com.example.mcptaskserver.mcp.tools.McpErrorResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for McpErrorResult structured error responses.
 */
class McpErrorResultTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validationError_shouldProduceStructuredJson() throws Exception {
        CallToolResult result = McpErrorResult.validationError("Missing required parameter: tasks");

        assertThat(result.isError()).isTrue();
        JsonNode json = parseContent(result);
        assertThat(json.get("error").asText()).isEqualTo("Missing required parameter: tasks");
        assertThat(json.get("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void notFoundError_shouldProduceStructuredJson() throws Exception {
        CallToolResult result = McpErrorResult.notFoundError("Job not found: abc-123");

        assertThat(result.isError()).isTrue();
        JsonNode json = parseContent(result);
        assertThat(json.get("error").asText()).isEqualTo("Job not found: abc-123");
        assertThat(json.get("code").asText()).isEqualTo("NOT_FOUND");
    }

    @Test
    void internalError_shouldProduceStructuredJson() throws Exception {
        CallToolResult result = McpErrorResult.internalError("Internal error while inserting tasks. Please check server logs.");

        assertThat(result.isError()).isTrue();
        JsonNode json = parseContent(result);
        assertThat(json.get("error").asText()).contains("Internal error");
        assertThat(json.get("code").asText()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void errorResult_shouldHaveExactlyOneContentItem() {
        CallToolResult result = McpErrorResult.validationError("some error");

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
    }

    @Test
    void errorResult_shouldContainOnlyErrorAndCodeFields() throws Exception {
        CallToolResult result = McpErrorResult.validationError("bad input");

        JsonNode json = parseContent(result);
        assertThat(json.size()).isEqualTo(2);
        assertThat(json.has("error")).isTrue();
        assertThat(json.has("code")).isTrue();
    }

    @Test
    void validationError_shouldPreserveSpecialCharactersInMessage() throws Exception {
        String message = "Value \"foo\" contains invalid char: <script>";
        CallToolResult result = McpErrorResult.validationError(message);

        JsonNode json = parseContent(result);
        assertThat(json.get("error").asText()).isEqualTo(message);
    }

    @Test
    void internalError_shouldNotLeakSensitiveInformation() throws Exception {
        // Callers supply a safe generic message; this just verifies the code value is stable
        CallToolResult result = McpErrorResult.internalError("Internal error. Please check server logs.");

        assertThat(result.isError()).isTrue();
        JsonNode json = parseContent(result);
        assertThat(json.get("code").asText()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void rateLimitError_shouldProduceStructuredJson() throws Exception {
        CallToolResult result = McpErrorResult.rateLimitError("mcp-tasks", 30L);

        assertThat(result.isError()).isTrue();
        JsonNode json = parseContent(result);
        assertThat(json.get("code").asText()).isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(json.get("error").asText()).contains("mcp-tasks");
        assertThat(json.get("error").asText()).contains("30");
        assertThat(json.get("retryAfterSeconds").asLong()).isEqualTo(30L);
    }

    @Test
    void rateLimitError_shouldExposeRetryAfterSecondsAsNumber() throws Exception {
        CallToolResult result = McpErrorResult.rateLimitError("some-tool", 120L);

        JsonNode json = parseContent(result);
        assertThat(json.get("retryAfterSeconds").isNumber()).isTrue();
        assertThat(json.get("retryAfterSeconds").asLong()).isEqualTo(120L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"VALIDATION_ERROR", "NOT_FOUND", "INTERNAL_ERROR", "RATE_LIMIT_EXCEEDED"})
    void allFactoryMethods_shouldSetIsErrorTrue(String code) {
        CallToolResult result = switch (code) {
            case "VALIDATION_ERROR" -> McpErrorResult.validationError("msg");
            case "NOT_FOUND" -> McpErrorResult.notFoundError("msg");
            case "RATE_LIMIT_EXCEEDED" -> McpErrorResult.rateLimitError("tool", 5L);
            default -> McpErrorResult.internalError("msg");
        };

        assertThat(result.isError()).isTrue();
    }

    // ── jsonEscape / control-character safety ─────────────────────────────

    @Test
    void jsonEscape_shouldEscapeNewlineAndCarriageReturn() throws Exception {
        // \n and \r inside a message must not break the JSON structure
        CallToolResult result = McpErrorResult.validationError("line1\nline2\r\nline3");

        assertThatCode(() -> parseContent(result)).doesNotThrowAnyException();
        JsonNode json = parseContent(result);
        assertThat(json.get("error").asText()).isEqualTo("line1\nline2\r\nline3");
    }

    @Test
    void jsonEscape_shouldEscapeTabAndOtherNamedControlChars() throws Exception {
        String message = "col1\tcol2\bcol3\fcol4";
        CallToolResult result = McpErrorResult.validationError(message);

        assertThatCode(() -> parseContent(result)).doesNotThrowAnyException();
        JsonNode json = parseContent(result);
        assertThat(json.get("error").asText()).isEqualTo(message);
    }

    @Test
    void jsonEscape_shouldEscapeRawControlCharacters() throws Exception {
        // U+0001 and U+001F (non-named control chars) must be unicode-escaped in output JSON
        String message = "bad\u0001char\u001Fend";
        CallToolResult result = McpErrorResult.validationError(message);

        assertThatCode(() -> parseContent(result)).doesNotThrowAnyException();
        JsonNode json = parseContent(result);
        assertThat(json.get("error").asText()).isEqualTo(message);
    }

    @Test
    void jsonEscape_shouldProduceParseableJsonForAllAsciiControlChars() {
        // Every ASCII control char (0x00–0x1F) embedded in a message must yield valid JSON
        for (int cp = 0x00; cp <= 0x1F; cp++) {
            String message = "prefix" + (char) cp + "suffix";
            CallToolResult result = McpErrorResult.internalError(message);
            String raw = ((McpSchema.TextContent) result.content().get(0)).text();
            assertThatCode(() -> objectMapper.readTree(raw))
                .as("control char U+%04X should produce parseable JSON", cp)
                .doesNotThrowAnyException();
        }
    }

    private JsonNode parseContent(CallToolResult result) throws Exception {
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        return objectMapper.readTree(text);
    }
}
