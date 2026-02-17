package com.example.mcptaskserver.http;

import com.example.mcptaskserver.config.ConditionalOnHttpEnabled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health check endpoint for HTTP transport mode (http or both).
 * Accessible without authentication for liveness/readiness probes.
 */
@RestController
@ConditionalOnHttpEnabled
public class McpHealthController {

    @GetMapping("/mcp/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "transport", "http");
    }
}
