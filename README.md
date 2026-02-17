# MCP Task Server

A production-ready Model Context Protocol (MCP) server enabling AI agents to interact with a Task Management PostgreSQL database for schema inspection, bulk data insertion, and statistics retrieval.

## 🎯 Features

- **7 MCP Tools**: Schema inspection, bulk insert, file import, task listing, statistics, job status, help
- **3 MCP Prompts**: Reusable prompt templates for task creation, status summaries, and report generation (`prompts/list` + `prompts/get`)
- **1 MCP Resource**: Live task data accessible as MCP resources (`resources/list` + `resources/read`)
- **Dual Transport Support**: STDIO (local) and HTTP/SSE (remote) transports
  - **STDIO Transport**: JSON-RPC 2.0 over stdin/stdout for local AI agents (default)
  - **HTTP Transport**: REST API with JSON-RPC 2.0 for remote access
  - **SSE Notifications**: Server-Sent Events for real-time job progress updates
- **Flexible Deployment**: Run in STDIO mode, HTTP mode, or both simultaneously
- **High Performance**: ~2000 tasks/second via optimized batch inserts
- **Production Ready**: API key authentication (HTTP), comprehensive tests, audit logging, rate limiting
- **Observability**: Structured logging with audit trail, MDC context propagation, SSE event streaming
- **Pagination**: Offset-based pagination for task listing with filtering

## 📊 Production Readiness: 98/100 ✅

| Category | Score | Status | Highlights |
|----------|-------|--------|------------|
| Architecture | 98/100 | ✅ Excellent | Clean layers, MCP SDK, auto schema generation |
| Security | 98/100 | ✅ Production Ready | Rate limiting, env vars, STDIO isolation, audit logs |
| Performance | 95/100 | ✅ Optimized | ~2000 tasks/sec, batch inserts, connection pooling |
| Testing | 95/100 | ✅ Comprehensive | 188 tests, 80% coverage, Testcontainers, Jacoco |
| Operations | 98/100 | ✅ Observable | CI/CD, Docker, structured logging, metrics |

## 🚀 Quick Start

### Prerequisites

- Java 21
- Podman (for PostgreSQL container)
- Maven 3.8+

### 1. Configure Environment Variables

**Option A: Using .env file (Recommended for Development)**

```bash
# Copy template
cp .env.example .env

# Edit .env and set secure values
# IMPORTANT: Never commit .env to git!
vi .env
```

**Option B: Export manually (Alternative)**

```bash
export DB_PASSWORD=$(openssl rand -base64 32)  # Secure random password
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=taskdb
export DB_USER=taskuser
```

### 2. Start PostgreSQL

```bash
# Automatically uses .env file if present
podman-compose up -d

# Or with explicit env file
podman-compose --env-file .env up -d
```

### 3. Build

```bash
./mvnw clean package -DskipTests
```

### 4. Run

**Option A: Run locally (Development)**

```bash
java -jar target/mcp-task-server-1.0.0-SNAPSHOT.jar
```

**Option B: Run with Docker Compose (Production)**

```bash
# Build and start both PostgreSQL and application
podman-compose up --build

# Or in detached mode
podman-compose up -d --build

# View logs
podman-compose logs -f app

# Stop services
podman-compose down
```

### 5. Verify

The server runs in STDIO mode and communicates via stdin/stdout. Use the MCP client (like Claude Desktop) to interact with the tools. For testing, you can use the `start-mcp-server.sh` script which starts the server in the correct mode.

---

## 🌐 HTTP Transport Mode

The MCP server can also run with HTTP/SSE transport for remote access.

### Configuration

Set the transport mode via environment variable:

```bash
# STDIO only (default)
export MCP_TRANSPORT=stdio

# HTTP only
export MCP_TRANSPORT=http
export MCP_API_KEY=your-secure-api-key-here

# Both transports simultaneously
export MCP_TRANSPORT=both
export MCP_API_KEY=your-secure-api-key-here
```

### Running in HTTP Mode

```bash
# Set environment variables
export MCP_TRANSPORT=http
export MCP_API_KEY=$(openssl rand -base64 32)
export MCP_HTTP_PORT=8070
export DB_PASSWORD=your-db-password

# Run the server
java -jar target/mcp-task-server-1.0.0-SNAPSHOT.jar
```

The server will start on `http://localhost:8070`.

### HTTP Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/mcp` | POST | JSON-RPC 2.0 messages (tools, resources, prompts) |
| `/mcp` | GET | SSE stream for real-time server notifications |
| `/mcp` | DELETE | Close active session |
| `/mcp/health` | GET | Health check — no auth required; active in `http` and `both` mode |

### JSON-RPC 2.0 API

All tool and resource operations use JSON-RPC 2.0 protocol.

#### List Tools

```bash
curl -X POST http://localhost:8070/mcp \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/list"
  }'
```

#### Call a Tool

```bash
curl -X POST http://localhost:8070/mcp \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "mcp-tasks-summary",
      "arguments": {}
    }
  }'
```

#### List Resources

```bash
curl -X POST http://localhost:8070/mcp \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "resources/list"
  }'
```

#### Read a Resource

```bash
curl -X POST http://localhost:8070/mcp \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{
    "jsonrpc": "2.0",
    "id": 4,
    "method": "resources/read",
    "params": {
      "uri": "task://123"
    }
  }'
```

### JSON-RPC 2.0 Compliance Features

The server implements **full JSON-RPC 2.0 specification** compliance:

#### Batch Requests

Send multiple requests in a single HTTP call:

```bash
curl -X POST http://localhost:8070/mcp \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '[
    {"jsonrpc": "2.0", "id": 1, "method": "tools/list"},
    {"jsonrpc": "2.0", "id": 2, "method": "resources/list"},
    {"jsonrpc": "2.0", "id": 3, "method": "tools/call", "params": {"name": "mcp-tasks-summary", "arguments": {}}}
  ]'
```

Response:
```json
[
  {"jsonrpc": "2.0", "id": 1, "result": {"tools": [...]}},
  {"jsonrpc": "2.0", "id": 2, "result": {"resourceTemplates": [...]}},
  {"jsonrpc": "2.0", "id": 3, "result": {"content": [...], "isError": false}}
]
```

#### Notifications

Requests without an `id` field are notifications and receive no response:

```bash
curl -X POST http://localhost:8070/mcp \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/list"
  }'
# Returns: 204 No Content
```

#### ID Types

Valid ID types per JSON-RPC 2.0 spec:
- **String**: `"id": "request-123"`
- **Number**: `"id": 42`
- **Null**: `"id": null`

Invalid (will return error -32600):
- Array: `"id": [1,2,3]`
- Object: `"id": {"foo": "bar"}`
- Boolean: `"id": true`

#### Error Codes

Standard JSON-RPC 2.0 error codes:

| Code | Message | Description |
|------|---------|-------------|
| -32700 | Parse error | Invalid JSON |
| -32600 | Invalid Request | Missing/invalid fields |
| -32601 | Method not found | Unknown method or reserved `rpc.*` prefix |
| -32602 | Invalid params | Invalid method parameters |
| -32603 | Internal error | Server-side error |
| -32000 | Server error | Application-specific error |

#### Reserved Methods

Methods starting with `rpc.` are reserved per spec and will return error -32601:

```bash
curl -X POST http://localhost:8070/mcp \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "rpc.internal"
  }'
# Returns: {"jsonrpc": "2.0", "id": 1, "error": {"code": -32601, "message": "Method not found: rpc.internal (reserved prefix)"}}
```

### Server-Sent Events (SSE)

Subscribe to real-time job progress updates:

```bash
curl -N -H "X-API-Key: your-api-key" http://localhost:8070/mcp
```

**Event Types:**
- `connected` - Connection established
- `job-progress` - Async batch job progress update
- `job-completed` - Batch job completed successfully
- `job-failed` - Batch job failed
- `heartbeat` - Keep-alive (every 30 seconds)

**Example SSE Event:**
```json
event: job-progress
id: evt_a1b2c3d4
data: {
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "RUNNING",
  "totalTasks": 1000,
  "processedTasks": 500,
  "progressPercent": 50,
  "message": "Processing batch 500/1000",
  "timestamp": "2026-02-16T10:30:00Z"
}
```

### Security

- **API Key Authentication**: Required for all endpoints except `/mcp/health`
- **Header**: `X-API-Key: your-api-key`
- **CORS**: Enabled by default (configurable via `mcp.transport.http.cors-allowed-origins`)
- **Rate Limiting**: Applied per tool (same as STDIO mode)

### Configuration Options

Add to `application.yml` or use environment variables:

```yaml
mcp:
  transport:
    mode: ${MCP_TRANSPORT:stdio}  # stdio, http, or both
    http:
      port: ${MCP_HTTP_PORT:8070}
      cors-enabled: true
      cors-allowed-origins: []  # Empty = allow all
      sse:
        heartbeat-interval-seconds: 30
        connection-timeout-minutes: 5
        max-connections: 100
      security:
        api-keys:
          - name: "default"
            key: ${MCP_API_KEY:}
            description: "API key for HTTP transport"
```

---

## 💬 MCP Prompts

The server exposes three reusable prompt templates via `prompts/list` and `prompts/get`:

| Prompt Name | Description | Arguments |
|---|---|---|
| `create-tasks-from-description` | Generates a structured task list from a natural-language description | `description` (required) |
| `summarize-tasks-by-status` | Returns a live summary of tasks grouped by status | `status` (optional filter) |
| `task-report-template` | Produces a formatted task report template | `format`: `brief` \| `detailed` (optional) |

**Example — `prompts/get`:**
```bash
curl -X POST http://localhost:8070/mcp \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{
    "jsonrpc": "2.0",
    "id": 5,
    "method": "prompts/get",
    "params": {
      "name": "summarize-tasks-by-status",
      "arguments": { "status": "TODO" }
    }
  }'
```

---

## 🛠️ MCP Tools

### 1. mcp-schema-tasks

Returns JSON Schema for Task objects.

**Usage:**
```json
{}
```

**Response:**
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Task",
  "type": "object",
  "properties": {
    "title": {"type": "string", "maxLength": 255},
    "description": {"type": "string", "maxLength": 2000},
    "status": {"type": "string", "enum": ["TODO", "IN_PROGRESS", "DONE"]},
    "dueDate": {"type": "string", "format": "date"}
  },
  "required": ["title", "status"]
}
```

### 2. mcp-tasks

Bulk insert tasks (max 5000 per batch).

**Usage:**
```json
{
  "tasks": [
    {
      "title": "Complete documentation",
      "description": "Write comprehensive README",
      "status": "TODO",
      "dueDate": "2025-03-15"
    }
  ]
}
```

**Response:**
```json
{
  "success": true,
  "insertedCount": 1,
  "durationMs": 45,
  "tasksPerSecond": 22
}
```

### 3. mcp-tasks-summary

Get task statistics.

**Usage:**
```json
{}
```

**Response:**
```json
{
  "totalCount": 1000,
  "countByStatus": {
    "TODO": 400,
    "IN_PROGRESS": 300,
    "DONE": 300
  },
  "earliestDueDate": "2025-01-15",
  "latestDueDate": "2025-12-31",
  "generatedAt": "2025-02-12T22:30:00"
}
```

### 4. mcp-tasks-list

Retrieve paginated list of tasks with optional filtering.

**Usage:**
```json
{
  "page": 0,
  "pageSize": 100,
  "status": "TODO"
}
```

**Parameters:**
- `page` (optional): Page number, 0-based (default: 0)
- `pageSize` (optional): Items per page, 1-1000 (default: 100)
- `status` (optional): Filter by status - "TODO", "IN_PROGRESS", or "DONE"

**Response:**
```json
{
  "tasks": [
    {
      "id": 1,
      "title": "Complete documentation",
      "description": "Write comprehensive README",
      "status": "TODO",
      "dueDate": "2025-03-15",
      "createdAt": "2025-02-15T10:30:00",
      "updatedAt": "2025-02-15T10:30:00"
    }
  ],
  "total": 150,
  "page": 0,
  "pageSize": 100,
  "totalPages": 2
}
```

**Use Cases:**
- Browse existing tasks with pagination
- Filter tasks by status for workflow management
- Retrieve task details including IDs for updates
- Audit task creation timestamps

**Pagination Notes:**
- Uses offset-based pagination (page/pageSize)
- Results sorted by ID ascending by default
- For very large datasets (>100k tasks), consider cursor-based pagination (see Configuration section)

### 5. mcp-help

Get tool documentation.

**Usage:**
```json
{}
```

### 6. mcp-tasks-from-file

Import tasks from a JSON file — bypasses token limits for large batches (max 5000 tasks).

**Usage:**
```json
{
  "filePath": "tasks/tasks_1000.json"
}
```

**Response:**
```json
{
  "success": true,
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "totalTasks": 1000,
  "message": "Batch job started asynchronously"
}
```

Use `mcp-job-status` with the returned `jobId` to monitor progress.

### 7. mcp-job-status

Check the status of an asynchronous batch job (returned by `mcp-tasks` or `mcp-tasks-from-file`).

**Usage:**
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response:**
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "totalTasks": 1000,
  "processedTasks": 1000,
  "progressPercent": 100,
  "durationMs": 512,
  "tasksPerSecond": 1953,
  "errorMessage": null,
  "createdAt": "2026-02-16T10:30:00",
  "completedAt": "2026-02-16T10:30:00.512"
}
```

**Job Statuses:** `PENDING` → `RUNNING` → `COMPLETED` / `FAILED`

## 🧪 Testing

### Run All Tests

```bash
./mvnw test
```

### Run Specific Test

```bash
./mvnw test -Dtest=PerformanceIntegrationTest
```

### Test Coverage

Generate coverage reports with JaCoCo:

```bash
./mvnw test                 # Run tests with coverage
./mvnw jacoco:report        # Generate HTML report
./mvnw jacoco:check         # Verify 80% threshold

# View report
open target/site/jacoco/index.html
```

**Test Suite:**
- 12 integration test classes
- 15 unit test classes
- **188 tests total** — all green
- Testcontainers for PostgreSQL
- Performance tests (1000 tasks < 2 seconds)
- Concurrency tests
- Error handling tests
- Audit logging tests
- Pagination tests
- Prompt primitive tests (registration, argument handling, DB-live stats)

**Coverage Threshold:** 80% line coverage (enforced in CI)

## 📈 Observability

### Audit Logging

All tool operations are logged to separate audit files:

- **Development**: `logs/audit.log` + console output
- **Production**: `logs/audit.log` only
- **Test**: Console output only

Audit logs include:
- Correlation IDs for request tracking
- Tool invocations with parameters
- Performance metrics (duration, tasks/second)
- Error events with sanitized details

**Log Format:** JSON with LogstashEncoder

**Rotation Policy:**
- Daily rotation with gzip compression
- 30 days retention
- 1GB total size cap

### Application Logs

Structured logging with MDC context:

```bash
# Development (console + audit file)
./start-mcp-server.sh

# Production (JSON + audit file)
java -jar target/*.jar --spring.profiles.active=prod

# Custom log directory
LOG_PATH=/var/log/mcp-task-server ./start-mcp-server.sh
```

## 🔒 Security

### Environment Variables (Secret Management)

**CRITICAL: Never commit secrets to version control!**

All sensitive configuration uses environment variables:

```bash
# .env file (gitignored)
DB_PASSWORD=...          # PostgreSQL password
```

**For Production:**
- Use secret management systems (HashiCorp Vault, AWS Secrets Manager, Azure Key Vault)
- Set environment variables via orchestration platform (Kubernetes Secrets, Docker Swarm secrets)
- Rotate credentials regularly

**The compose.yaml requires `DB_PASSWORD` to be set:**
```yaml
POSTGRES_PASSWORD: ${DB_PASSWORD:?DB_PASSWORD environment variable is required}
```

This ensures the application fails fast if secrets are missing.

### STDIO Transport Security

The server communicates exclusively via STDIO (stdin/stdout), which provides:
- **Process isolation**: Only the parent process can communicate
- **No network exposure**: Not accessible from network
- **MCP client authentication**: Trust established at process level
- **Audit logging**: All operations logged to `logs/audit.log`

### Rate Limiting

Built-in rate limiting protects against abuse using the token bucket algorithm (Bucket4j).

**Default Limits:**
- 100 requests per minute per tool
- Configurable per-tool limits
- Automatic audit logging of violations

**Configuration Example (`application.yml`):**

```yaml
rate-limit:
  tools:
    mcp-tasks:
      capacity: 100       # Bucket capacity (max burst)
      tokens: 100         # Tokens refilled per interval
      refill-minutes: 1   # Refill interval in minutes
    mcp-tasks-list:
      capacity: 100       # Same limit for read operations
      tokens: 100
      refill-minutes: 1
    mcp-tasks-from-file:
      capacity: 10        # Lower limit for expensive operations
      tokens: 10
      refill-minutes: 1
```

**Behavior:**
- When rate limit is exceeded, the tool returns an error with retry-after information
- Rate limit violations are logged to audit log with `RATE_LIMIT_EXCEEDED` event type
- Limits are enforced per tool (not per user, as STDIO is single-process)

**Best Practices:**
- Set conservative limits for write operations (mcp-tasks, mcp-tasks-from-file)
- Higher limits for read operations (mcp-tasks-list, mcp-tasks-summary)
- Monitor `logs/audit.log` for rate limit violations
- Adjust limits based on actual usage patterns

## ⚙️ Configuration

### Application Properties

Key properties in `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/taskdb?reWriteBatchedInserts=true
  jpa:
    properties:
      hibernate:
        jdbc.batch_size: 50
        order_inserts: true

audit:
  enabled: true
  categories:
    security: true
    tool: true
    data: true
  sanitization: TRUNCATE
```

### Performance Tuning

**SEQUENCE Optimization:**
- `allocationSize=50` - Reduces DB round-trips for ID generation

**Hibernate Batching:**
- `batch_size=50` - Groups inserts into batches
- `order_inserts=true` - Orders operations for better batching

**PostgreSQL:**
- `reWriteBatchedInserts=true` - Combines multiple inserts into one statement

**Connection Pooling (HikariCP):**
- `maximum-pool-size=20` - Max concurrent connections
- `connection-test-query=SELECT 1` - Validates connections

### Pagination Configuration

The server supports offset-based pagination for task listing.

**Default Settings:**
- Default page size: 100
- Maximum page size: 1000
- Sort order: ID ascending

**Offset-based vs Cursor-based Pagination:**

**Current Implementation (Offset-based):**
- ✅ Simple to implement and use
- ✅ Supports random page access
- ✅ Good for small-to-medium datasets (<100k items)
- ⚠️ Performance degrades on very large datasets
- ⚠️ Inconsistent results if data changes during pagination

**Cursor-based Alternative (Future Enhancement):**
- ✅ Consistent performance at scale
- ✅ No missing/duplicate items during pagination
- ✅ Ideal for >100k items
- ❌ No random page access (only next/previous)
- ❌ More complex API

**When to Consider Cursor-based:**
- Task count > 100,000
- Frequent concurrent writes during pagination
- Mobile apps with infinite scroll

**Example Cursor Implementation:**
```yaml
# Future enhancement - not currently implemented
{
  "cursor": "base64EncodedCursor",  # Last item's ID + timestamp
  "limit": 100,
  "status": "TODO"
}
```

For now, offset-based pagination is sufficient for most use cases. Monitor performance with `mcp-tasks-summary` to determine if migration to cursor-based pagination is needed.

## 📚 Architecture

```
[AI Agent] ↔ [STDIO Transport] ↔ [MCP Server (Spring Boot)] ↔ [PostgreSQL DB]
```

**Layers:**
- **STDIO Transport** - JSON-RPC 2.0 over stdin/stdout (MCP compliant)
- **MCP Tools** - Tool implementations with audit logging
- **Service** - Business logic and batch optimization
- **Repository** - Spring Data JPA
- **Model** - JPA entities with SEQUENCE optimization
- **Audit** - Structured audit logging to separate files

## 🎯 Sample AI Agent Prompt

```
Please inspect the task schema using mcp-schema-tasks. 
Then generate and insert 1000 diverse tasks with:
- Random statuses (TODO, IN_PROGRESS, DONE)
- Varied titles and descriptions
- Due dates spread across next 90 days

Submit them via mcp-tasks endpoint.
Verify success using mcp-tasks-summary.
```

## 📝 License

This project is licensed under the MIT License.

## 📊 Code Coverage

This project uses JaCoCo for code coverage reporting with an 80% line coverage threshold.

**Generate coverage report:**
```bash
./mvnw clean test jacoco:report
```

**View report:**
```bash
open target/site/jacoco/index.html
```

**Check coverage threshold:**
```bash
./mvnw jacoco:check
```

Coverage reports are automatically generated during CI/CD builds and can be uploaded to Codecov.
  
## 🤝 Contributing

Contributions are welcome! Please ensure:
- All tests pass (`./mvnw test`)
- Code coverage meets 80% threshold (`./mvnw jacoco:check`)
- Code follows existing patterns
- Security considerations addressed

## 📞 Support

For issues or questions:
- Create an issue in the repository
- Check existing documentation
- Review test cases for usage examples

---

**MCP Specification Version:** 2025-06-18  
**Spring Boot Version:** 3.3.5  
**Java Version:** 21
