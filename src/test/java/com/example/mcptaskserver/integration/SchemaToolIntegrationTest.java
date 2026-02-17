package com.example.mcptaskserver.integration;

import com.example.mcptaskserver.mcp.tools.SchemaTasksTool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.mcptaskserver.integration.CallToolResultAssert.*;

class SchemaToolIntegrationTest extends AbstractIntegrationTest {
    
    @Autowired
    private SchemaTasksTool schemaTasksTool;
    
    @Test
    void shouldReturnValidJsonSchema() {
        var result = schemaTasksTool.execute(Map.of());
        
        assertSuccess(result);
        assertContentContains(result, "\"$schema\"");
        assertContentContains(result, "\"title\"");
        assertContentContains(result, "\"status\"");
        // Status is validated via @Pattern, so the schema contains a "pattern" property
        // with the pipe-separated values rather than a JSON enum array
        assertContentContains(result, "TODO");
        assertContentContains(result, "IN_PROGRESS");
        assertContentContains(result, "DONE");
    }
    
    @Test
    void shouldIncludeRequiredFields() {
        var result = schemaTasksTool.execute(Map.of());
        
        assertSuccess(result);
        assertContentContains(result, "\"required\"");
        assertContentContains(result, "\"title\"");
        assertContentContains(result, "\"status\"");
    }
    
    @Test
    void shouldIncludePropertyConstraints() {
        var result = schemaTasksTool.execute(Map.of());
        
        assertSuccess(result);
        assertContentContains(result, "\"maxLength\"");
        assertContentContains(result, "255");
        assertContentContains(result, "2000");
    }
}
