package com.example.mcptaskserver.http.security;

import com.example.mcptaskserver.config.TransportConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds security response headers to all HTTP transport responses.
 *
 * Headers applied:
 * - X-Content-Type-Options: nosniff        — prevents MIME-type sniffing
 * - X-Frame-Options: DENY                  — prevents clickjacking
 * - Strict-Transport-Security             — enforces HTTPS in production
 * - Cache-Control / Pragma                — disables caching of API responses
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private final TransportConfig transportConfig;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only active when HTTP transport is enabled
        if (!transportConfig.isHttpEnabled()) {
            return true;
        }
        // Only apply to /mcp endpoints
        String path = request.getRequestURI();
        return !path.equals("/mcp") && !path.startsWith("/mcp/");
    }
}
