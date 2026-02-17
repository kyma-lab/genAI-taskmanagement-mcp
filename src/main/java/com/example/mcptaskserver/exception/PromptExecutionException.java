package com.example.mcptaskserver.exception;

import lombok.Getter;

/**
 * Exception thrown when MCP prompt execution fails.
 *
 * Carries the {@code promptName} so callers can distinguish which prompt failed
 * without needing to parse the message string.
 */
@Getter
public class PromptExecutionException extends RuntimeException {

    private final String promptName;

    public PromptExecutionException(String promptName) {
        super("Prompt execution failed: " + promptName);
        this.promptName = promptName;
    }

    public PromptExecutionException(String promptName, Throwable cause) {
        super("Prompt execution failed: " + promptName, cause);
        this.promptName = promptName;
    }
}
