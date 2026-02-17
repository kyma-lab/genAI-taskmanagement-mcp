# Implementation Notes — MCP Task Server

## 1. Architecture Overview

The MCP Task Server is a Spring Boot 3 application that exposes task data to AI agents via the
[Model Context Protocol](https://modelcontextprotocol.io/) (MCP spec `2025-06-18`), implemented
with the official [MCP Java SDK](https://modelcontextprotocol.io/sdk/java/mcp-overview).

### Key Design Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Protocol implementation | Official MCP Java SDK | Guarantees spec conformance; no custom JSON-RPC parsing |
| Transport | Dual (STDIO + HTTP/SSE) | Supports local agents and remote clients from a single binary |
| Execution model | `McpSyncServer` with `immediateExecution(true)` | Preserves Spring thread-locals (transactions, security context) |
| Database | PostgreSQL + Flyway | Schema versioning, production-grade JSONB support |
| Batch insert | JPA `EntityManager` with manual flush/clear | Prevents heap pressure on large batches (up to 5000 tasks) |
| Rate limiting | Bucket4j token-bucket (in-memory) | Zero-latency; Redis backend can replace it for clustered deployments |
| Observability | Micrometer metrics + structured JSON audit log | Decoupled from business logic via AOP and a dedicated `AUDIT` logger |

### Module Structure

```
src/main/java/com/example/mcptaskserver/
├── config/           # Spring configuration (transport, rate-limit, async, CORS)
├── aspect/           # Cross-cutting concerns (rate limiting via AOP)
├── audit/            # Structured audit logging (AuditLogger, AuditEvent, categories)
├── dto/              # Data Transfer Objects (TaskDto, TaskSummaryDto, …)
├── exception/        # Typed exceptions (ValidationException, PromptExecutionException, …)
├── http/             # HTTP-only beans (health endpoint)
├── mcp/
│   ├── prompts/      # TaskPromptProvider — 3 prompt templates
│   ├── resources/    # TaskResourceProvider — task://*, db://stats URIs
│   └── tools/        # McpTool implementations (7 tools)
├── model/            # JPA entities (Task, BatchJob, enums)
├── repository/       # Spring Data repositories
├── service/          # Business logic (TaskService, AsyncBatchService, BatchInsertService)
└── util/             # CorrelationIdContext (MDC propagation)
```

---

## 2. MCP Protocol Conformance

The project delegates all protocol mechanics to the official Java SDK, ensuring correctness
without custom JSON-RPC parsing.

### JSON-RPC 2.0

**Fully implemented.** JSON-RPC message processing (requests, responses, errors, notifications)
is handled by the SDK's `HttpServletStreamableServerTransportProvider` and
`StdioServerTransportProvider`. The server exposes a **single endpoint `/mcp`** for all
JSON-RPC communication, as required by the MCP spec (`McpServerConfig.java`).

### Capability Negotiation

**Fully implemented.** `McpServerConfig.registerCapabilities()` collects all `McpTool`,
resource, and prompt beans from the Spring context and registers them with the server.
The SDK automatically handles `tools/list`, `resources/list`, and `prompts/list` requests.

Capabilities advertised at handshake:

```java
ServerCapabilities.builder()
    .tools(true)
    .resources(false, true)   // subscribe=false, listChanged=true
    .prompts(true)
    .logging()
    .experimental(Map.of("asyncBatch", new McpSchema.Implementation("asyncBatch", "1.0.0")))
    .build();
```

The `asyncBatch` experimental capability signals to clients that task insertion is
asynchronous and returns a job ID rather than a final result.

### Transport Layer

**Dual-transport — both modes can run simultaneously.**

| Mode | Class | Notes |
|---|---|---|
| `stdio` | `StdioServerTransportProvider` | Local channel; no API-key auth required |
| `http` | `HttpServletStreamableServerTransportProvider` | Registered as a real servlet (outside DispatcherServlet) |
| `both` | Both beans active | HTTP keeps the JVM alive; STDIO runner does not block |

Transport selection is controlled by `mcp.transport.mode` (`stdio` / `http` / `both`),
evaluated at startup via `@ConditionalOnStdioEnabled` / `@ConditionalOnHttpEnabled` custom
condition annotations on the `@Bean` methods in `McpServerConfig`.

### Real-Time Notifications (SSE)

`TasksTool.execute()` calls `exchange.loggingNotification(...)` with progress percentages
(`0%` at job start, `100%` at completion). The SSE stream is provided by the SDK transport
layer. Notification failures are swallowed at `DEBUG` level and never fail the tool call.

After a batch commit, `AsyncBatchService` publishes a `TasksInsertedEvent`. The
`ResourceChangeNotifier` listens on `@TransactionalEventListener(AFTER_COMMIT)` and calls
`server.notifyResourcesListChanged()` on every active `McpSyncServer`, informing subscribed
clients that the `task://all` resource has changed. The notifier uses
`ObjectProvider<McpSyncServer>` to remain a no-op if no server beans are present (e.g. in
isolated unit tests).

---

## 3. Core Concepts Implementation

All [MCP Server Concepts](https://modelcontextprotocol.io/docs/learn/server-concepts) are
fully realised.

### Dual Transport

`TransportConfig` (bound to `mcp.transport.*`) is the single source of truth for which
transports are active. The helper methods `isStdioEnabled()` / `isHttpEnabled()` feed the
custom Spring condition annotations, keeping `McpServerConfig` free of `if/else` branching.

### Execution Model

`.immediateExecution(true)` on the server builder ensures tool handlers run on the
calling thread rather than a shared SDK thread pool. This is essential for Spring's
`@Transactional` and `SecurityContextHolder` to work correctly inside tool implementations.

### Startup Recovery

`AsyncBatchService.recoverStuckJobs()` runs on `ApplicationReadyEvent` (after AOP proxies
are wired) and marks any `PENDING` or `RUNNING` jobs as `FAILED`. This handles the case
where the server crashed during a batch operation, preventing jobs from staying stuck
indefinitely.

---

## 4. Core Primitives: Tools, Resources, Prompts

### Tools (7 total)

All tools implement the `McpTool` interface:

```java
public interface McpTool {
    Tool toolDefinition();
    CallToolResult execute(McpSyncServerExchange exchange, Map<String, Object> arguments);
    default McpServerFeatures.SyncToolSpecification toSyncToolSpecification() { … }
}
```

`toSyncToolSpecification()` is the adapter between the application's tool contract and the
SDK's registration API. Spring discovers all `McpTool` beans automatically; `McpServerConfig`
iterates the list and calls `server.addTool(tool.toSyncToolSpecification())`.

| Tool name | Class | Purpose |
|---|---|---|
| `mcp-tasks` | `TasksTool` | Async bulk insert (up to 5000 tasks); returns job ID |
| `mcp-tasks-from-file` | `TasksFromFileTool` | Reads a JSON file path and delegates to async insert |
| `mcp-tasks-list` | `TasksListTool` | Paginated task listing with optional status filter |
| `mcp-tasks-summary` | `TasksSummaryTool` | Aggregate counts by status |
| `mcp-schema-tasks` | `SchemaTasksTool` | Returns the JSON Schema for the Task object |
| `mcp-job-status` | `JobStatusTool` | Polls async batch job status by job ID |
| `mcp-help` | `HelpTool` | Self-describing tool catalogue |

Error responses use `McpErrorResult` — a factory that produces `CallToolResult` with
`isError=true` and a structured JSON payload, distinguishing validation errors, rate-limit
rejections, and internal errors.

### Resources

`TaskResourceProvider` exposes three URIs:

| URI | Type | Description |
|---|---|---|
| `task://all` | Static | Up to `mcp.resource.max-tasks` (default 1000) tasks as JSON array |
| `task://{id}` | Template | Single task by numeric ID |
| `db://stats` | Static | Aggregate counts from `TaskService.generateSummary()` |

The maximum task count for `task://all` is externalized via
`@Value("${mcp.resource.max-tasks:1000}")`, allowing tuning without recompilation.

Static resources are registered via `server.addResource()`; the template is registered via
`server.addResourceTemplate()`. Both are discoverable through the SDK's `listResources()` /
`listResourceTemplates()` methods.

### Prompts

`TaskPromptProvider` registers three prompt templates:

| Prompt name | Arguments | Purpose |
|---|---|---|
| `create-tasks-from-description` | `description` (required) | Generates a structured task list from natural language |
| `summarize-tasks-by-status` | `status` (optional) | Summarizes current DB state, optionally filtered by status |
| `task-report-template` | `format` (`brief`/`detailed`) | Produces a formatted task report |

All prompts load live statistics from `TaskService.generateSummary()` before constructing
the prompt message. Execution failures throw `PromptExecutionException` (a typed
`RuntimeException` with a `promptName` field), keeping the cause out of the client response
while making the failure identifiable in server logs.

---

## 5. Async Batch Processing

The insert pipeline is split across three layers to separate concerns:

```
TasksTool.execute()
  │  validates input, creates BatchJob (PENDING)
  │  submits to batchExecutor thread pool
  └─► AsyncBatchService.processAsync()   [@Async, @Transactional(REQUIRES_NEW)]
        │  sets job to RUNNING
        └─► BatchInsertService.batchInsert()  [@Transactional(MANDATORY)]
              │  EntityManager.persist() in chunks of 50
              │  flush + clear every 50 rows
              └─► sets job to COMPLETED / FAILED
                  publishes TasksInsertedEvent → ResourceChangeNotifier → SSE notification
```

**Why `REQUIRES_NEW`?** The async method runs on a different thread. `REQUIRES_NEW` ensures
it creates its own transaction independent of any caller transaction. `BatchInsertService`
uses `MANDATORY` to assert it is always called within a transaction.

**Queue-full handling:** If the `batchExecutor` queue is full (`TaskRejectedException`),
`TasksTool` immediately calls `asyncBatchService.markJobFailed()` and returns an
`McpErrorResult.internalError("Server is busy. Please retry later.")`. The client receives
a clean error instead of a stuck `PENDING` job.

**Throughput:** With `reWriteBatchedInserts=true` on the JDBC URL and Hibernate
`batch_size=50`, the server sustains approximately 2000 inserts/second on a typical
PostgreSQL instance.

---

## 6. Security

### HTTP Transport — API Key Authentication

HTTP transport enforces API-key authentication via a filter applied before the MCP servlet.
Keys are configured in `mcp.transport.http.security.api-keys` and checked against the
`X-API-Key` header. The server **fails secure**: if HTTP transport is enabled with
`security.api-key.enabled=true` (the default) and no keys are configured, startup is
aborted with an `IllegalStateException`.

Disabling authentication (`security.api-key.enabled=false`) logs a `WARN` and is intended
for development/test environments only.

### STDIO Transport

STDIO is a trusted local channel (the AI agent runs in the same OS-user context). No API
key authentication is applied.

### CORS

CORS origins for the HTTP endpoint are configured via the `MCP_CORS_ORIGINS` environment
variable (comma-separated). An empty value permits all origins — acceptable for private
networks; explicit origins must be set for any internet-facing deployment.

### Rate Limiting (AOP)

`RateLimitAspect` intercepts every `McpTool.execute()` call via an `@Around` pointcut.
It resolves a per-tool `Bucket` from `RateLimitConfig` using the Bucket4j token-bucket
algorithm. Exhausted buckets return `McpErrorResult.rateLimitError(...)` and log a
`WARN`-level audit event. Tool names are cached in a `ConcurrentHashMap` to avoid calling
`toolDefinition()` on every invocation.

Rate limits are currently in-memory and **not shared across cluster nodes**. A WARN is
emitted at startup to remind operators of this constraint:
> "Rate limiting ist in-memory (Bucket4j). Für Cluster-Deployments Redis-Backend konfigurieren."

### Security Headers

The HTTP transport adds standard defensive headers (`X-Content-Type-Options: nosniff`,
`X-Frame-Options: DENY`, `Strict-Transport-Security`, `Cache-Control: no-store`) to all
responses.

---

## 7. Observability

### Structured Audit Logging

`AuditLogger` writes to a dedicated `AUDIT` SLF4J logger (separate from the application
logger), emitting structured JSON via Logback. Each event carries:

- `eventType` — lifecycle stage (`TOOL_INVOCATION_START`, `BATCH_JOB_COMPLETED`, `RATE_LIMIT_EXCEEDED`, …)
- `category` — `SECURITY`, `TOOL`, `DATA`, `ADMIN`, or `ERROR`
- `correlationId` — propagated via `CorrelationIdContext` (MDC) across async boundaries
- `toolName`, `metadata` map, `success` flag, `errorMessage`

Sensitive fields are sanitized according to `audit.sensitive-data-strategy`
(`TRUNCATE` / `REDACT` / `FULL`).

Audit categories can be selectively disabled in `application.yml`:
```yaml
audit:
  enabled: true
  enabled-categories: [SECURITY, TOOL, DATA, ADMIN, ERROR]
```

### Micrometer Metrics

Each tool and resource provider registers its own `Timer` and `Counter` beans:

| Metric | Type | Description |
|---|---|---|
| `mcp.tool.execution` (tag: `tool`) | Timer | Per-tool execution latency |
| `mcp.tool.invocations` (tags: `tool`, `result`) | Counter | Success / error counts per tool |
| `mcp.tasks.inserted.total` | Counter | Cumulative inserted task count |
| `mcp.resource.read` | Timer | Resource read latency |
| `mcp.resource.success/error/notfound` | Counter | Resource read outcomes |
| `mcp.prompt.get` | Timer | Prompt retrieval latency |
| `mcp.prompt.success/error` | Counter | Prompt retrieval outcomes |

### Correlation ID Propagation

`CorrelationIdContext` stores a per-request UUID in SLF4J MDC. Because async batch
processing crosses thread boundaries, `MdcTaskDecorator` propagates the MDC map to the
`batchExecutor` threads, ensuring every log line for a given batch job shares the same
`correlationId`.

---

## 8. Database Layer

### Schema Management

Flyway manages schema evolution:

| Migration | Content |
|---|---|
| `V1__create_tasks_table.sql` | `tasks` table with `title`, `description`, `status` (enum), `due_date`, timestamps |
| `V2__create_batch_jobs_table.sql` | `batch_jobs` table tracking async job lifecycle (`PENDING → RUNNING → COMPLETED / FAILED`) |

The JPA `ddl-auto` is set to `validate` in production, ensuring the schema matches the
entities without automatic modifications.

### Optimized Bulk Insert

`BatchInsertService` uses `EntityManager.persist()` in chunks of 50, flushing and clearing
the first-level cache after each chunk. Combined with the JDBC driver option
`reWriteBatchedInserts=true` and Hibernate `batch_size=50`, this avoids round-trip overhead
and keeps heap usage constant regardless of batch size.

---

## 9. Testing

### Test Strategy

Test quality is prioritised over coverage. Tests verify observable behaviour rather than
internal implementation details.

### Unit Tests

Located in `src/test/java/.../unit/`. Use Mockito mocks for all dependencies; no Spring
context or database required. Cover:

- Individual tool logic (validation, error paths, edge cases)
- `AsyncBatchService` state transitions
- `BatchInsertService` chunking behaviour
- `SchemaService` schema generation

### Integration Tests

Located in `src/test/java/.../integration/`. All extend `AbstractIntegrationTest`, which
starts a shared PostgreSQL Testcontainers container (Podman-compatible) once per JVM run.

| Test class | Scope |
|---|---|
| `HttpTransportIntegrationTest` | Full HTTP/SSE stack: auth, tools, resources via SDK client |
| `BothTransportIntegrationTest` | Dual-transport: STDIO + HTTP beans both present |
| `StdioTransportIntegrationTest` | STDIO context load: server bean present, all 7 tools registered |
| `TasksToolIntegrationTest` | End-to-end async insert and job-status polling |
| `TasksFromFileToolIntegrationTest` | File-based bulk insert |
| `TasksListToolIntegrationTest` | Pagination and status filtering |
| `ConcurrentInsertIntegrationTest` | 5 threads × 100 tasks; no data corruption |
| `PerformanceIntegrationTest` | Throughput benchmark (1000 tasks) |
| `AsyncBatchIntegrationTest` | Job lifecycle and stuck-job recovery |
| `SchemaToolIntegrationTest` | JSON Schema validation |
| `SummaryToolIntegrationTest` | Aggregate count correctness |
| `TaskPromptProviderIntegrationTest` | All 3 prompts resolve without error |

`StdioTransportIntegrationTest` uses `@MockBean(name = "mcpStdioRunner")` to replace the
blocking `CommandLineRunner` (which calls `lock.wait()` in STDIO-only mode) with a no-op,
allowing the Spring context to load and assertions to run without hanging.

### Test Configuration

`application-test.yml` (active profile `test`) overrides:
- `spring.jpa.hibernate.ddl-auto: create-drop` — tables created from entities, no Flyway
- `mcp.transport.mode: http` — disables STDIO globally to prevent the blocking runner
- `security.api-key.enabled: false` — removes auth friction in unit and integration tests
