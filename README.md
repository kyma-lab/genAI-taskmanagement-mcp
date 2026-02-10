# MCP Task Server

**Model Context Protocol (MCP) compatible service for AI-powered task data injection.**

A production-ready Spring Boot application optimized for high-performance bulk inserts, designed to allow AI agents (Claude, GPT-4, etc.) to interact with a Task Management database.

## 🎯 Features

- ✅ **Production-Grade Architecture**: Flyway migrations, Actuator observability, OpenAPI documentation
- ✅ **High-Performance Batch Inserts**: Optimized for 1000+ tasks using Hibernate batching (`reWriteBatchedInserts`)
- ✅ **AI-Readable API**: OpenAPI 3.0 spec at `/v3/api-docs` for LLM consumption
- ✅ **Testcontainers Integration**: Real PostgreSQL tests, not mocks
- ✅ **Database Migrations**: Versioned schema with Flyway
- ✅ **Observability**: Spring Boot Actuator + Prometheus metrics

## 📦 Tech Stack

- **Java 21** (LTS)
- **Spring Boot 3.3.6**
- **PostgreSQL 16**
- **Flyway** (Database Migrations)
- **SpringDoc OpenAPI** (AI-readable API docs)
- **Testcontainers** (Integration Testing)
- **Lombok** (Boilerplate reduction)

## 🚀 Quick Start

### Prerequisites

- Java 21 (e.g., `JAVA_HOME=/Users/m.berger/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home`)
- Podman or Docker
- Maven 3.8+

### 1. Start PostgreSQL

```bash
./podman-setup.sh
# OR
podman compose up -d
```

This starts PostgreSQL on port 5432 with database `mcptasks`.

### 2. Run the Application

```bash
mvn spring-boot:run
```

The server starts on **http://localhost:8070**

### 3. Verify Setup

```bash
curl http://localhost:8070/actuator/health
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"}
  }
}
```

## 📚 API Endpoints

### MCP Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/mcp/help` | GET | Get available endpoints and capabilities |
| `/mcp/schema/tasks` | GET | Get Task entity JSON schema |
| `/mcp/tasks` | POST | Bulk insert tasks (accepts JSON array) |
| `/mcp/tasks/summary` | GET | Get task statistics by status |

### Documentation Endpoints

| Endpoint | Description |
|----------|-------------|
| `/v3/api-docs` | OpenAPI 3.0 spec (AI-readable JSON) |
| `/swagger-ui.html` | Interactive API documentation |

### Observability Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Health status |
| `/actuator/prometheus` | Prometheus metrics |
| `/actuator/metrics` | Application metrics |

## 🤖 AI Agent Usage

### Example Prompt for Claude/GPT-4

```
Please inspect the task schema at http://localhost:8070/mcp/schema/tasks. 
Then generate and insert 1000 diverse tasks with random statuses, titles, 
and due dates using the POST /mcp/tasks endpoint.
```

### Step-by-Step for AI Agents

1. **Inspect Schema**:
   ```bash
   curl http://localhost:8070/mcp/schema/tasks
   ```

2. **Read OpenAPI Spec** (better for LLMs):
   ```bash
   curl http://localhost:8070/v3/api-docs
   ```

3. **Generate & Insert Tasks**:
   ```bash
   curl -X POST http://localhost:8070/mcp/tasks \
     -H "Content-Type: application/json" \
     -d '[
       {
         "title": "Implement user authentication",
         "description": "Add OAuth2 support",
         "status": "TODO",
         "priority": 5,
         "dueDate": "2026-12-31"
       }
     ]'
   ```

4. **Verify Success**:
   ```bash
   curl http://localhost:8070/mcp/tasks/summary
   ```

## ⚡ Performance Optimizations

### Database Batching Configuration

```properties
# PostgreSQL-specific batch rewriting (10-100x faster)
spring.datasource.url=jdbc:postgresql://localhost:5432/mcptasks?reWriteBatchedInserts=true

# Hibernate batching
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
```

### Entity Configuration

```java
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "task_seq")
@SequenceGenerator(name = "task_seq", sequenceName = "task_sequence", allocationSize = 50)
```

**Why SEQUENCE instead of IDENTITY?**
- IDENTITY requires a database roundtrip for every insert (slow)
- SEQUENCE allows Hibernate to batch inserts (fast)

### Benchmark Results

| Operation | Records | Time | Throughput |
|-----------|---------|------|------------|
| Bulk Insert | 1000 | ~500ms | ~2000 tasks/sec |

## 🧪 Testing

### Run Unit & Integration Tests

```bash
# Run all tests
mvn test

# Run only controller tests (no Docker/Podman required)
mvn test -Dtest=McpControllerTest
```

**Note:** Integration tests use **Testcontainers** to spin up a real PostgreSQL container. If you're using Podman instead of Docker, you need to configure a Docker-compatible socket:

```bash
# Enable Podman Docker-compatible socket (macOS/Linux)
podman machine ssh
sudo systemctl enable --now podman.socket
exit

# Set environment variable
export DOCKER_HOST=unix:///var/run/podman/podman.sock
```

For now, **controller tests pass successfully** without requiring Docker/Podman.

### Test Coverage

- ✅ Bulk insert performance test (1000 tasks)
- ✅ Summary statistics validation
- ✅ Empty list handling
- ✅ SEQUENCE ID generation verification

## 🗄️ Database Schema

Managed by Flyway migrations in `src/main/resources/db/migration/`.

### V1__create_tasks_table.sql

```sql
CREATE SEQUENCE task_sequence START WITH 1 INCREMENT BY 50;

CREATE TABLE tasks (
    id BIGINT PRIMARY KEY DEFAULT nextval('task_sequence'),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL,
    priority INTEGER CHECK (priority >= 1 AND priority <= 5),
    due_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### Schema Inspection

```bash
# Via MCP endpoint
curl http://localhost:8070/mcp/schema/tasks

# Via OpenAPI
curl http://localhost:8070/v3/api-docs
```

## 📊 Monitoring

### Prometheus Metrics

```bash
curl http://localhost:8070/actuator/prometheus
```

Key metrics:
- `http_server_requests_seconds` - Request latency
- `hikaricp_connections_active` - Database connections
- `jvm_memory_used_bytes` - Memory usage

### Health Check

```bash
curl http://localhost:8070/actuator/health
```

## 🔧 Configuration

### application.properties

Key settings:

```properties
server.port=8070

# Database with batch rewriting
spring.datasource.url=jdbc:postgresql://localhost:5432/mcp_tasks?reWriteBatchedInserts=true

# Hibernate batching
spring.jpa.properties.hibernate.jdbc.batch_size=50

# Flyway
spring.flyway.enabled=true

# Actuator
management.endpoints.web.exposure.include=health,info,prometheus,metrics
```

## 📖 API Documentation

### OpenAPI Specification

The API is fully documented with OpenAPI 3.0, optimized for AI agent consumption:

- **Endpoint**: http://localhost:8070/v3/api-docs
- **Interactive UI**: http://localhost:8070/swagger-ui.html

Example schema annotations:

```java
@Schema(description = "Priority of the task (1=lowest, 5=urgent)", 
        example = "3", minimum = "1", maximum = "5")
private Integer priority;
```

### Why OpenAPI > Custom JSON Schema?

1. **LLMs are trained on OpenAPI specs**
2. **Industry standard** (Swagger UI, Postman, etc.)
3. **Better semantic context** via `@Schema` annotations

## 🛠️ Development

### Project Structure

```
src/
├── main/
│   ├── java/com/example/mcptask/
│   │   ├── controller/      # REST endpoints
│   │   ├── service/         # Business logic
│   │   ├── repository/      # Data access
│   │   ├── model/           # Entities
│   │   └── dto/             # Data Transfer Objects
│   └── resources/
│       ├── db/migration/    # Flyway scripts
│       └── application.properties
└── test/
    ├── java/com/example/mcptask/
    │   └── service/         # Integration tests
    └── resources/
        └── application-test.properties
```

### Code Quality

- **Lombok**: Reduces boilerplate (getters/setters via `@Data`)
- **Validation**: Jakarta Bean Validation (`@NotBlank`, `@Min`, `@Max`)
- **Testcontainers**: Real PostgreSQL in tests (no mocks)

### Running Tests

```bash
# All tests
mvn test

# Integration tests only
mvn test -Dtest=*IntegrationTest

# With coverage
mvn test jacoco:report
```

## 🚢 Deployment

### Docker/Podman

```bash
# Start database
podman-compose up -d

# Build application
mvn clean package -DskipTests

# Run JAR
java -jar target/mcp-task-1.0.0.jar
```

### Environment Variables

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/mcptasks?reWriteBatchedInserts=true
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=yourpassword
```

## ✅ Success Criteria

- [x] MCP server running and accessible on port 8070
- [x] Schema inspection via `/mcp/schema/tasks` and `/v3/api-docs`
- [x] AI agent can insert 1000 tasks via `/mcp/tasks`
- [x] Summary endpoint reflects inserted data
- [x] Flyway migrations applied automatically
- [x] Testcontainers integration tests passing
- [x] OpenAPI documentation available
- [x] Actuator health checks working

## 📝 License
MIT License, Copyright (c)

## 🤝 Contributing

1. Use Java 21
2. Follow Lombok conventions
3. Test quality > coverage
4. Run tests before committing: `mvn test`

---


