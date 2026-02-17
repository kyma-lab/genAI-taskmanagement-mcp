# Multi-stage Dockerfile for MCP Task Server
# Build Stage: Compiles the application with Maven
# Runtime Stage: Runs the application with minimal JRE

# ============================================
# Build Stage
# ============================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and dependency files
COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline

# Copy source code
COPY src src

# Build the application (skip tests for faster builds)
RUN ./mvnw clean package -DskipTests

# ============================================
# Runtime Stage
# ============================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1001 appgroup && \
    adduser -D -u 1001 -G appgroup appuser

# Copy the JAR from build stage
COPY --from=builder /app/target/*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose application port
EXPOSE 8070

# Health check (optional - requires HTTP endpoint)
# HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
#   CMD nc -z localhost 8070 || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
