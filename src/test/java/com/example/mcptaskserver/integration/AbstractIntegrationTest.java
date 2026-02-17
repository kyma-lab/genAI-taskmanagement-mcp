package com.example.mcptaskserver.integration;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests using Testcontainers.
 * 
 * The PostgreSQL container is started exactly once via a static initializer and
 * is intentionally never stopped between test classes.  Using @Container / @Testcontainers
 * for a shared static container causes the Testcontainers JUnit 5 extension to call
 * stop() after every test class, which with Podman + Ryuk-disabled breaks the "reuse"
 * contract and leaves subsequent classes unable to reach the container.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("testdb")
                .withUsername("testuser")
                .withPassword("testpass")
                .withReuse(true);
        postgres.start();
    }

    /**
     * Safety net: if a subclass somehow receives its own class-loader cycle the
     * container might not be running yet.  This is almost always a no-op.
     */
    @BeforeAll
    static void ensureContainerRunning() {
        if (!postgres.isRunning()) {
            postgres.start();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> 
                postgres.getJdbcUrl() + "?reWriteBatchedInserts=true");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
