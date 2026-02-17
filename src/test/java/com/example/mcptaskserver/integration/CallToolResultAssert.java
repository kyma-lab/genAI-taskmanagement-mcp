package com.example.mcptaskserver.integration;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Helper class for asserting CallToolResult in tests.
 */
public class CallToolResultAssert {

    /**
     * Extracts text content from CallToolResult.
     */
    public static String getContent(CallToolResult result) {
        if (result.content().isEmpty()) {
            return "";
        }
        
        McpSchema.Content content = result.content().get(0);
        if (content instanceof TextContent textContent) {
            return textContent.text();
        }
        
        return "";
    }

    /**
     * Asserts that result is successful (not an error).
     */
    public static void assertSuccess(CallToolResult result) {
        assertThat(result.isError())
            .withFailMessage("Expected successful result, but got error: %s", getContent(result))
            .isFalse();
    }

    /**
     * Asserts that result is an error.
     */
    public static void assertError(CallToolResult result) {
        assertThat(result.isError())
            .withFailMessage("Expected error result, but was successful")
            .isTrue();
    }

    /**
     * Asserts that result content contains the given text.
     */
    public static void assertContentContains(CallToolResult result, String expectedText) {
        String content = getContent(result);
        assertThat(content)
            .withFailMessage("Expected content to contain '%s', but was: %s", expectedText, content)
            .contains(expectedText);
    }

    /**
     * Asserts that result is successful and content contains the given text.
     */
    public static void assertSuccessAndContains(CallToolResult result, String expectedText) {
        assertSuccess(result);
        assertContentContains(result, expectedText);
    }

    /**
     * Asserts that result is an error and content contains the given text.
     */
    public static void assertErrorAndContains(CallToolResult result, String expectedText) {
        assertError(result);
        assertContentContains(result, expectedText);
    }

    /**
     * Asserts that result content does NOT contain the given text.
     */
    public static void assertContentDoesNotContain(CallToolResult result, String unexpectedText) {
        String content = getContent(result);
        assertThat(content)
            .withFailMessage("Expected content to NOT contain '%s', but was: %s", unexpectedText, content)
            .doesNotContain(unexpectedText);
    }
}
