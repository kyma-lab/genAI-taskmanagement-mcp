package com.example.mcptask;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(
    info = @Info(
        title = "MCP Task Server API",
        version = "1.0.0",
        description = """
            Model Context Protocol (MCP) compatible service for AI-powered task data injection.
            
            This API allows AI agents (e.g., Claude, GPT-4) to:
            - Inspect the task database schema
            - Bulk insert tasks with optimized performance
            - Query task statistics
            
            **Performance:** Optimized for bulk inserts (1000+ tasks) using Hibernate batch processing.
            
            **For AI Agents:** Start by calling `/mcp/help` to see available endpoints, 
            then `/mcp/schema/tasks` to understand the data model, or directly read this OpenAPI spec.
            """,
        contact = @Contact(name = "MCP Task Server", url = "https://example.com")
    ),
    servers = @Server(url = "http://localhost:8070", description = "Development server")
)
public class McpTaskApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpTaskApplication.class, args);
    }
}
