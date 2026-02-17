package com.example.mcptaskserver.service;

import com.example.mcptaskserver.dto.TaskDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationOption;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for generating JSON Schema for Task objects.
 * 
 * Automatically generates schemas from DTOs with validation annotations,
 * eliminating manual HashMap construction and ensuring schema/validation sync.
 */
@Service
public class SchemaService {

    private final SchemaGenerator schemaGenerator;

    public SchemaService() {
        JakartaValidationModule validationModule = new JakartaValidationModule(
            JakartaValidationOption.NOT_NULLABLE_FIELD_IS_REQUIRED,
            JakartaValidationOption.INCLUDE_PATTERN_EXPRESSIONS
        );
        
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
            SchemaVersion.DRAFT_2020_12,
            OptionPreset.PLAIN_JSON
        );
        
        SchemaGeneratorConfig config = configBuilder
            .with(validationModule)
            .with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT)
            .build();
        
        this.schemaGenerator = new SchemaGenerator(config);
    }

    /**
     * Generates JSON Schema for Task objects from TaskDto class.
     * Schema is automatically derived from validation annotations.
     * 
     * @return JSON Schema as a Map
     */
    public Map<String, Object> generateTaskSchema() {
        JsonNode schemaNode = schemaGenerator.generateSchema(TaskDto.class);
        return convertToMap(schemaNode);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToMap(JsonNode node) {
        return new com.fasterxml.jackson.databind.ObjectMapper()
            .convertValue(node, Map.class);
    }
}
