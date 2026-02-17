package com.example.mcptaskserver.http.security;

import com.example.mcptaskserver.config.ConditionalOnHttpEnabled;
import com.example.mcptaskserver.config.TransportConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS configuration for HTTP transport.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class CorsConfig {

    private final TransportConfig transportConfig;

    @Bean
    @ConditionalOnHttpEnabled
    @ConditionalOnProperty(name = "mcp.transport.http.cors-enabled", havingValue = "true", matchIfMissing = true)
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allowed origins
        List<String> allowedOrigins = transportConfig.getHttp().getCorsAllowedOrigins();
        if (allowedOrigins.isEmpty()) {
            log.warn("SECURITY: CORS configured with wildcard origin (*). " +
                     "Set mcp.transport.http.cors-allowed-origins for production deployments.");
            configuration.addAllowedOriginPattern("*");
            // credentials require explicit origins — do NOT setAllowCredentials(true) with wildcard
        } else {
            log.info("CORS: Allowing origins: {}", allowedOrigins);
            allowedOrigins.forEach(configuration::addAllowedOrigin);
            configuration.setAllowCredentials(true);
        }

        // Allowed methods
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "DELETE", "OPTIONS"));

        // Allowed headers
        configuration.setAllowedHeaders(Arrays.asList(
                "Content-Type",
                "X-API-Key",
                "X-Correlation-ID",
                "Cache-Control"
        ));

        // Expose headers
        configuration.setExposedHeaders(Arrays.asList(
                "X-Correlation-ID"
        ));
        
        // Max age
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/mcp", configuration);
        source.registerCorsConfiguration("/mcp/**", configuration);

        return source;
    }
}
