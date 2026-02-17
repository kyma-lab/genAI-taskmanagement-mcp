package com.example.mcptaskserver.unit.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.mcptaskserver.mcp.tools.SchemaTasksTool;
import com.example.mcptaskserver.service.SchemaService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchemaTasksToolTest {

    @Mock
    private SchemaService schemaService;

    private SchemaTasksTool schemaTasksTool;

    @BeforeEach
    void setUp() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        ObjectMapper objectMapper = new ObjectMapper();
        schemaTasksTool = new SchemaTasksTool(schemaService, objectMapper, meterRegistry);
    }

    @Test
    void execute_shouldDelegateToSchemaService() {
        // Given
        Map<String, Object> schema = createMockSchema();
        when(schemaService.generateTaskSchema()).thenReturn(schema);

        // When
        McpSchema.CallToolResult result = schemaTasksTool.execute(Map.of());

        // Then
        verify(schemaService).generateTaskSchema();
        assertThat(result).isNotNull();
    }

    @Test
    void execute_shouldReturnFormattedJsonSchema() {
        // Given
        Map<String, Object> schema = createMockSchema();
        when(schemaService.generateTaskSchema()).thenReturn(schema);

        // When
        McpSchema.CallToolResult result = schemaTasksTool.execute(Map.of());

        // Then
        assertThat(result.content()).hasSize(1);
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        
        assertThat(content).contains("$schema");
        assertThat(content).contains("title");
        assertThat(content).contains("Task");
        assertThat(content).contains("type");
        assertThat(content).contains("object");
    }

    @Test
    void execute_shouldIncludePropertiesInOutput() {
        // Given
        Map<String, Object> schema = createMockSchema();
        when(schemaService.generateTaskSchema()).thenReturn(schema);

        // When
        McpSchema.CallToolResult result = schemaTasksTool.execute(Map.of());

        // Then
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("properties");
    }

    @Test
    void execute_shouldIncludeRequiredFieldsInOutput() {
        // Given
        Map<String, Object> schema = createMockSchema();
        when(schemaService.generateTaskSchema()).thenReturn(schema);

        // When
        McpSchema.CallToolResult result = schemaTasksTool.execute(Map.of());

        // Then
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).contains("required");
    }

    @Test
    void execute_shouldNotRequireArguments() {
        // Given
        Map<String, Object> schema = createMockSchema();
        when(schemaService.generateTaskSchema()).thenReturn(schema);

        // When
        McpSchema.CallToolResult result = schemaTasksTool.execute(new HashMap<>());

        // Then
        assertThat(result).isNotNull();
        verify(schemaService).generateTaskSchema();
    }

    @Test
    void execute_shouldHandleNullArguments() {
        // Given
        Map<String, Object> schema = createMockSchema();
        when(schemaService.generateTaskSchema()).thenReturn(schema);

        // When
        McpSchema.CallToolResult result = schemaTasksTool.execute(null);

        // Then
        assertThat(result).isNotNull();
        verify(schemaService).generateTaskSchema();
    }

    @Test
    void execute_shouldNotLeakInternalErrorDetails_whenServiceThrowsUnexpectedly() {
        // Given
        when(schemaService.generateTaskSchema())
            .thenThrow(new RuntimeException("ClassNotFoundException: com.example.internal.SchemaGenerator$Impl"));

        // When
        McpSchema.CallToolResult result = schemaTasksTool.execute(Map.of());

        // Then
        assertThat(result.isError()).isTrue();
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(content).doesNotContain("ClassNotFoundException");
        assertThat(content).doesNotContain("SchemaGenerator");
        assertThat(content).contains("Internal error");
    }

    private Map<String, Object> createMockSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("$schema", "http://json-schema.org/draft-07/schema#");
        schema.put("title", "Task");
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> titleProp = new HashMap<>();
        titleProp.put("type", "string");
        titleProp.put("maxLength", 255);
        properties.put("title", titleProp);
        
        schema.put("properties", properties);
        schema.put("required", Arrays.asList("title", "status"));
        
        return schema;
    }
}
