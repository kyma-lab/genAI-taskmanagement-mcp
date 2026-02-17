package com.example.mcptaskserver.unit.aspect;

import com.example.mcptaskserver.aspect.RateLimitAspect;
import com.example.mcptaskserver.audit.AuditEventBuilder;
import com.example.mcptaskserver.audit.AuditLogger;
import com.example.mcptaskserver.config.RateLimitConfig;
import com.example.mcptaskserver.mcp.tools.McpTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

    @Mock private RateLimitConfig rateLimitConfig;
    @Mock private AuditLogger auditLogger;
    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private McpTool mockTool;
    @Mock private Tool toolDefinition;
    @Mock private Bucket bucket;
    @Mock private ConsumptionProbe probe;

    private RateLimitAspect aspect;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        aspect = new RateLimitAspect(rateLimitConfig, auditLogger);
        when(joinPoint.getTarget()).thenReturn(mockTool);
        when(mockTool.toolDefinition()).thenReturn(toolDefinition);
        when(toolDefinition.name()).thenReturn("test-tool");
        when(rateLimitConfig.resolveBucket("test-tool")).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
    }

    @Test
    void checkRateLimit_returnsStructuredErrorResult_whenRateLimitExceeded() throws Throwable {
        // Given - bucket depleted
        when(probe.isConsumed()).thenReturn(false);
        when(probe.getNanosToWaitForRefill()).thenReturn(30_000_000_000L); // 30 seconds

        // When
        Object result = aspect.checkRateLimit(joinPoint);

        // Then - must be a structured MCP error result, not a thrown exception
        assertThat(result).isInstanceOf(CallToolResult.class);
        CallToolResult toolResult = (CallToolResult) result;
        assertThat(toolResult.isError()).isTrue();

        JsonNode json = parseContent(toolResult);
        assertThat(json.get("code").asText()).isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(json.get("error").asText()).contains("test-tool");
        assertThat(json.get("retryAfterSeconds").asLong()).isEqualTo(30L);

        // Underlying method must NOT be called
        verify(joinPoint, never()).proceed();
    }

    @Test
    void checkRateLimit_structuredErrorAllowsClientToDistinguishRateLimit() throws Throwable {
        // Given
        when(probe.isConsumed()).thenReturn(false);
        when(probe.getNanosToWaitForRefill()).thenReturn(5_000_000_000L);

        // When
        Object result = aspect.checkRateLimit(joinPoint);

        // Then - clients can identify the error type via the machine-readable code
        CallToolResult toolResult = (CallToolResult) result;
        JsonNode json = parseContent(toolResult);
        assertThat(json.get("code").asText()).isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(json.get("retryAfterSeconds").asLong()).isEqualTo(5L);
    }

    @Test
    void checkRateLimit_proceedsNormally_whenWithinRateLimit() throws Throwable {
        // Given - bucket has tokens
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(9L);

        CallToolResult expected = new CallToolResult(
            List.of(new McpSchema.TextContent("OK")), false);
        when(joinPoint.proceed()).thenReturn(expected);

        // When
        Object result = aspect.checkRateLimit(joinPoint);

        // Then
        assertThat(result).isEqualTo(expected);
        verify(joinPoint).proceed();
        verify(auditLogger, never()).log(any(AuditEventBuilder.class));
    }

    @Test
    void checkRateLimit_logsAuditEvent_whenRateLimitExceeded() throws Throwable {
        // Given
        when(probe.isConsumed()).thenReturn(false);
        when(probe.getNanosToWaitForRefill()).thenReturn(0L);

        // When
        aspect.checkRateLimit(joinPoint);

        // Then - audit event must be recorded
        verify(auditLogger).log(any(AuditEventBuilder.class));
    }

    private JsonNode parseContent(CallToolResult result) throws Exception {
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        return objectMapper.readTree(text);
    }

    @Test
    void checkRateLimit_cachedToolName_onRepeatedCalls() throws Throwable {
        // Given
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(5L);
        when(joinPoint.proceed()).thenReturn(new CallToolResult(List.of(), false));

        // When - two calls with the same target
        aspect.checkRateLimit(joinPoint);
        aspect.checkRateLimit(joinPoint);

        // Then - toolDefinition() resolved once (cached after first call)
        verify(mockTool, times(1)).toolDefinition();
    }
}
