package com.example.mcptaskserver.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class StdioEnabledCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String mode = context.getEnvironment().getProperty("mcp.transport.mode", "stdio");
        return "stdio".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode);
    }
}
