package com.example.mcptaskserver.integration;

import com.example.mcptaskserver.mcp.tools.McpTool;
import io.modelcontextprotocol.server.McpSyncServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying that the Spring context loads correctly in STDIO transport mode.
 *
 * The STDIO CommandLineRunner is replaced with a {@code @MockBean} to prevent it from
 * blocking the test thread (the runner calls {@code lock.wait()} in STDIO-only mode).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "mcp.transport.mode=stdio",
                "spring.flyway.enabled=false"
        }
)
class StdioTransportIntegrationTest extends AbstractIntegrationTest {

    /** Replaces the blocking STDIO CommandLineRunner with a no-op during tests. */
    @MockBean(name = "mcpStdioRunner")
    private CommandLineRunner mcpStdioRunner;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    @Qualifier("mcpStdioSyncServer")
    private McpSyncServer mcpStdioSyncServer;

    @Autowired
    private List<McpTool> allTools;

    @Test
    void stdioMode_shouldInstantiateStdioServerBean() {
        assertThat(applicationContext.containsBean("mcpStdioSyncServer"))
                .as("mcpStdioSyncServer bean must be present in stdio mode")
                .isTrue();
    }

    @Test
    void stdioMode_stdioSyncServer_shouldNotBeNull() {
        assertThat(mcpStdioSyncServer)
                .as("mcpStdioSyncServer must not be null")
                .isNotNull();
    }

    @Test
    void stdioMode_shouldNotInstantiateHttpServerBean() {
        assertThat(applicationContext.containsBean("mcpHttpSyncServer"))
                .as("mcpHttpSyncServer bean must NOT be present in stdio-only mode")
                .isFalse();
    }

    @Test
    void stdioMode_allToolsShouldBeRegistered() {
        assertThat(allTools)
                .as("All McpTool beans must be registered")
                .isNotEmpty();
        // The server registers exactly 7 tools (verified by other transport tests)
        assertThat(allTools).hasSize(7);
    }
}
