package com.example.mcptaskserver.integration;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying that mode=both correctly instantiates
 * BOTH the STDIO and HTTP MCP server beans simultaneously.
 *
 * The STDIO server bean is verified via ApplicationContext presence.
 * The HTTP server bean is verified via live MCP protocol calls.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "mcp.transport.mode=both",
                "mcp.transport.http.security.api-keys[0].name=test",
                "mcp.transport.http.security.api-keys[0].key=test-api-key-both",
                "spring.jpa.hibernate.ddl-auto=none"
        }
)
class BothTransportIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext applicationContext;

    private static final String API_KEY = "test-api-key-both";

    private McpSyncClient client;

    @BeforeEach
    void setUpClient() {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://localhost:" + port)
                .customizeRequest(b -> b.header("X-API-Key", API_KEY))
                .build();
        client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("test-client", "1.0"))
                .build();
    }

    @AfterEach
    void tearDownClient() {
        if (client != null) {
            client.closeGracefully();
        }
    }

    // ── Bean instantiation tests ──────────────────────────────────────────

    @Test
    void bothMode_shouldInstantiateStdioServerBean() {
        assertThat(applicationContext.containsBean("mcpStdioSyncServer"))
                .as("mcpStdioSyncServer bean must be present in both mode")
                .isTrue();
    }

    @Test
    void bothMode_shouldInstantiateHttpServerBean() {
        assertThat(applicationContext.containsBean("mcpHttpSyncServer"))
                .as("mcpHttpSyncServer bean must be present in both mode")
                .isTrue();
    }

    @Test
    void bothMode_shouldInstantiateHttpTransportProvider() {
        assertThat(applicationContext.containsBean("mcpHttpTransportProvider"))
                .as("mcpHttpTransportProvider bean must be present in both mode")
                .isTrue();
    }

    @Test
    void bothMode_shouldInstantiateStdioRunner() {
        assertThat(applicationContext.containsBean("mcpStdioRunner"))
                .as("mcpStdioRunner CommandLineRunner must be present in both mode")
                .isTrue();
    }

    // ── HTTP transport functional tests (both mode) ───────────────────────

    @Test
    void bothMode_httpEndpoint_initialize_shouldSucceed() {
        McpSchema.InitializeResult result = client.initialize();

        assertThat(result).isNotNull();
        assertThat(result.serverInfo().name()).isEqualTo("mcp-task-server");
    }

    @Test
    void bothMode_httpEndpoint_listTools_shouldReturn7Tools() {
        client.initialize();

        McpSchema.ListToolsResult result = client.listTools();

        assertThat(result.tools()).hasSize(7);
    }

    @Test
    void bothMode_httpEndpoint_listPrompts_shouldReturn3Prompts() {
        client.initialize();

        McpSchema.ListPromptsResult result = client.listPrompts();

        assertThat(result.prompts()).hasSize(3);
    }

    @Test
    void bothMode_httpEndpoint_callSummaryTool_shouldSucceed() {
        client.initialize();

        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("mcp-tasks-summary", java.util.Map.of()));

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).isNotEmpty();
    }

    @Test
    void bothMode_healthEndpoint_shouldBeAccessibleWithoutAuth() throws Exception {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/mcp/health"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP");
    }
}
