package com.example.mcptaskserver.audit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.EnumSet;
import java.util.Set;

/**
 * Configuration properties for audit logging.
 * Mapped from application.yml audit.* properties.
 */
@Configuration
@ConfigurationProperties(prefix = "audit")
@Getter
@Setter
public class AuditConfig {
    
    /**
     * Master switch to enable/disable all audit logging.
     */
    private boolean enabled = true;
    
    /**
     * Set of enabled audit categories. Empty set means all categories are enabled.
     */
    private Set<AuditCategory> enabledCategories = EnumSet.allOf(AuditCategory.class);
    
    /**
     * Maximum length for sensitive data fields before truncation.
     */
    private int sensitiveDataMaxLength = 50;
    
    /**
     * Strategy for handling sensitive data in audit logs.
     */
    private SanitizationStrategy sensitiveDataStrategy = SanitizationStrategy.TRUNCATE;
    
    /**
     * Check if a specific audit category is enabled.
     */
    public boolean isCategoryEnabled(AuditCategory category) {
        return enabledCategories.isEmpty() || enabledCategories.contains(category);
    }
}
