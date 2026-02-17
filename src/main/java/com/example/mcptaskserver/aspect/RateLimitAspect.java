package com.example.mcptaskserver.aspect;

import com.example.mcptaskserver.audit.AuditEventBuilder;
import com.example.mcptaskserver.audit.AuditEventType;
import com.example.mcptaskserver.audit.AuditLogger;
import com.example.mcptaskserver.config.RateLimitConfig;
import com.example.mcptaskserver.mcp.tools.McpTool;
import com.example.mcptaskserver.mcp.tools.McpErrorResult;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Aspect for rate limiting MCP tool calls.
 * Applies token bucket algorithm to prevent abuse.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitAspect {

    private final RateLimitConfig rateLimitConfig;
    private final AuditLogger auditLogger;

    /** Cache tool names to avoid calling toolDefinition() on every invocation. */
    private final ConcurrentHashMap<Class<?>, String> toolNameCache = new ConcurrentHashMap<>();

    /**
     * Apply rate limiting to all MCP tool execution methods.
     */
    @Around("execution(* com.example.mcptaskserver.mcp.tools..*Tool.execute(..))")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        String toolName = extractToolName(joinPoint);
        Bucket bucket = rateLimitConfig.resolveBucket(toolName);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        
        if (probe.isConsumed()) {
            log.debug("Rate limit check passed for tool: {} (remaining: {})", 
                toolName, probe.getRemainingTokens());
            return joinPoint.proceed();
        } else {
            long waitTimeSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            
            auditLogger.log(
                new AuditEventBuilder()
                    .eventType(AuditEventType.RATE_LIMIT_EXCEEDED)
                    .toolName(toolName)
                    .metadata("waitTimeSeconds", waitTimeSeconds)
                    .success(false)
            );

            log.warn("Rate limit exceeded for tool: {} (retry in {}s)", toolName, waitTimeSeconds);
            return McpErrorResult.rateLimitError(toolName, waitTimeSeconds);
        }
    }

    private String extractToolName(ProceedingJoinPoint joinPoint) {
        Object target = joinPoint.getTarget();
        return toolNameCache.computeIfAbsent(target.getClass(), cls -> {
            if (target instanceof McpTool tool) {
                return tool.toolDefinition().name();
            }
            // Fallback for non-McpTool targets (should not occur given the pointcut)
            String className = cls.getSimpleName();
            return "mcp-" + className
                .replace("Tool", "")
                .replaceAll("([a-z])([A-Z])", "$1-$2")
                .toLowerCase();
        });
    }
}
