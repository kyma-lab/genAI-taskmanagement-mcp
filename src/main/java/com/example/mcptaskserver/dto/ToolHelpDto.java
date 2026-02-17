package com.example.mcptaskserver.dto;

/**
 * Data Transfer Object for MCP tool help documentation.
 * Used by mcp-help tool to provide AI-readable documentation.
 *
 * Java 21 record for immutability and reduced boilerplate.
 */
public record ToolHelpDto(
    String name,
    String description,
    String usage,
    String example,
    String returnType
) {
    /**
     * Builder for backward compatibility with existing code.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String name;
        private String description;
        private String usage;
        private String example;
        private String returnType;
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder usage(String usage) {
            this.usage = usage;
            return this;
        }
        
        public Builder example(String example) {
            this.example = example;
            return this;
        }
        
        public Builder returnType(String returnType) {
            this.returnType = returnType;
            return this;
        }
        
        public ToolHelpDto build() {
            return new ToolHelpDto(name, description, usage, example, returnType);
        }
    }
}
