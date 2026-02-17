package com.example.mcptaskserver.integration;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for HTTP transport mode using the MCP SDK client.
 *
 * Uses RANDOM_PORT (not MockMvc) because the SDK transport is registered as a
 * real servlet outside the Spring DispatcherServlet chain.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "mcp.transport.mode=http",
                "mcp.transport.http.security.api-keys[0].name=test",
                "mcp.transport.http.security.api-keys[0].key=test-api-key-12345",
                "security.api-key.enabled=true",
                "spring.jpa.hibernate.ddl-auto=none"
        }
)
class HttpTransportIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    private static final String API_KEY = "test-api-key-12345";

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

    // ── Authentication tests (raw HTTP, no SDK) ────────────────────────────

    @Test
    void mcp_withoutApiKey_shouldReturn401() throws Exception {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{" +
                        "\"protocolVersion\":\"2025-06-18\",\"capabilities\":{}," +
                        "\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}"))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void mcp_withInvalidApiKey_shouldReturn401() throws Exception {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("X-API-Key", "wrong-key")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{" +
                        "\"protocolVersion\":\"2025-06-18\",\"capabilities\":{}," +
                        "\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}"))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
    }

    // ── Health endpoint (no auth required) ────────────────────────────────

    @Test
    void health_shouldBePublic() throws Exception {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp/health"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\"").contains("UP");
    }

    // ── MCP protocol tests (SDK client) ───────────────────────────────────

    @Test
    void initialize_shouldSucceed() {
        McpSchema.InitializeResult result = client.initialize();

        assertThat(result).isNotNull();
        assertThat(result.serverInfo()).isNotNull();
        assertThat(result.serverInfo().name()).isEqualTo("mcp-task-server");
    }

    @Test
    void listTools_shouldReturn7Tools() {
        client.initialize();

        McpSchema.ListToolsResult result = client.listTools();

        assertThat(result).isNotNull();
        assertThat(result.tools()).hasSize(7);
    }

    @Test
    void callTool_summary_shouldSucceed() {
        client.initialize();

        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("mcp-tasks-summary", java.util.Map.of()));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).isNotEmpty();
    }

    @Test
    void listResourceTemplates_shouldReturnSingleTemplate() {
        client.initialize();

        McpSchema.ListResourceTemplatesResult result = client.listResourceTemplates();

        assertThat(result).isNotNull();
        assertThat(result.resourceTemplates()).hasSize(1);
        assertThat(result.resourceTemplates().get(0).uriTemplate()).isEqualTo("task://{id}");
    }

    @Test
    void listResources_shouldReturnStaticResources() {
        client.initialize();

        McpSchema.ListResourcesResult result = client.listResources();

        assertThat(result).isNotNull();
        assertThat(result.resources()).hasSize(2);
        assertThat(result.resources())
                .extracting(McpSchema.Resource::uri)
                .containsExactlyInAnyOrder("task://all", "db://stats");
    }

    // ── Security header tests (raw HTTP) ──────────────────────────────────

    @Test
    void securityHeaders_shouldBePresentOnMcpEndpoint() throws Exception {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp/health"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.headers().firstValue("X-Content-Type-Options"))
                .hasValue("nosniff");
        assertThat(response.headers().firstValue("X-Frame-Options"))
                .hasValue("DENY");
        assertThat(response.headers().firstValue("Strict-Transport-Security"))
                .hasValue("max-age=31536000; includeSubDomains");
        assertThat(response.headers().firstValue("Cache-Control"))
                .hasValue("no-store");
    }
}
