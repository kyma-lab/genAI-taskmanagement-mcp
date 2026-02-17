package com.example.mcptaskserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Infrastructure beans shared across the application (ObjectMapper, MeterRegistry).
 * Extracted from McpServerConfig to avoid circular dependency with McpTool beans.
 */
@Configuration
public class InfrastructureConfig {

    /**
     * Provides ObjectMapper bean with JSR310 module for LocalDateTime serialization.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Provides a simple in-memory MeterRegistry when no other registry is configured.
     * Add spring-boot-starter-actuator + micrometer-registry-prometheus to the classpath
     * to replace this with a Prometheus-enabled registry automatically.
     */
    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}
