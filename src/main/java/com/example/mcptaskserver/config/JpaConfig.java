package com.example.mcptaskserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA configuration for optimized batch inserts.
 * 
 * Additional configuration in application.yml:
 * - hibernate.jdbc.batch_size: 50
 * - hibernate.order_inserts: true
 * - hibernate.order_updates: true
 * - reWriteBatchedInserts=true in JDBC URL
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.example.mcptaskserver.repository")
@EnableTransactionManagement
public class JpaConfig {
    // Batch configuration is primarily done via application.yml
    // This class enables JPA repositories and transaction management
}
