package com.example.mcptaskserver.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration for rate limiting MCP tools.
 * Limits are configurable per tool in application.yml.
 */
@Configuration
@ConfigurationProperties(prefix = "rate-limit")
@Getter
@Slf4j
public class RateLimitConfig {

    private final Map<String, ToolLimit> tools = new ConcurrentHashMap<>();
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Warns at startup that the current rate limiting implementation is in-memory
     * and not suitable for clustered deployments without a distributed backend.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warnIfInMemoryRateLimiting() {
        log.warn("Rate limiting ist in-memory (Bucket4j). Für Cluster-Deployments Redis-Backend konfigurieren.");
    }

    /**
     * Get or create a rate limit bucket for a specific tool.
     *
     * @param toolName the name of the tool
     * @return the bucket for rate limiting
     */
    public Bucket resolveBucket(String toolName) {
        return buckets.computeIfAbsent(toolName, key -> {
            ToolLimit limit = tools.getOrDefault(key, ToolLimit.defaultLimit());
            Bandwidth bandwidth = Bandwidth.classic(
                limit.getCapacity(),
                Refill.intervally(limit.getTokens(), Duration.ofMinutes(limit.getRefillMinutes()))
            );
            return Bucket.builder()
                .addLimit(bandwidth)
                .build();
        });
    }

    /**
     * Configuration for individual tool rate limits.
     */
    @Getter
    public static class ToolLimit {
        private long capacity = 100;
        private long tokens = 100;
        private long refillMinutes = 1;

        public void setCapacity(long capacity) {
            this.capacity = capacity;
        }

        public void setTokens(long tokens) {
            this.tokens = tokens;
        }

        public void setRefillMinutes(long refillMinutes) {
            this.refillMinutes = refillMinutes;
        }

        static ToolLimit defaultLimit() {
            return new ToolLimit();
        }
    }
}
