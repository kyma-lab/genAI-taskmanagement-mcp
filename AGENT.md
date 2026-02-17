# MCP Task Server - AI Agent Guide

## METADATA

```yaml
server_name: mcp-task-server
version: 1.0.0-SNAPSHOT
mcp_spec_version: 2025-06-18
transport: STDIO (stdin/stdout) | HTTP/SSE | both
protocol: JSON-RPC 2.0
purpose: Task data management via Model Context Protocol
capabilities:
  - JSON schema inspection
  - Bulk task insertion (up to 5000 per batch)
  - File-based task import
  - Database statistics retrieval
  - Async batch job status tracking
performance:
  max_batch_size: 5000
  typical_throughput: ~2000 tasks/second
  optimal_batch_size: 500-2000
```

## SERVER OVERVIEW

**Purpose:** This MCP server provides tools for AI agents to interact with a PostgreSQL task management database. It supports bulk data generation, insertion, and retrieval operations optimized for high-throughput scenarios.

**Transport:** JSON-RPC 2.0 over STDIO (stdin/stdout), HTTP/SSE, or both simultaneously — configurable via `mcp.transport.mode` (`stdio` | `http` | `both`)

**Use Cases:**
- Generate and insert large volumes of test data
- Perform batch task creation from AI-generated content
- Query task statistics and verify data integrity
- Import tasks from pre-generated JSON files

---

## QUICK START

**First-time setup? Follow these steps:**

```
┌─────────────────────────────────────────────────────────────┐
│  Which MCP Client Are You Using?                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐     │
│  │ Claude Code  │   │    Google    │   │   Claude     │     │
│  │     CLI      │   │    Gemini    │   │   Desktop    │     │
│  └──────┬───────┘   └──────┬───────┘   └──────┬───────┘     │
│         │                  │                  │             │
│         ▼                  ▼                  ▼             │
│  claude mcp add      gemini mcp add      Manual JSON        │
│  (workspace-level)   (.gemini/...)       editing            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

1. **Identify your MCP client:**
   - Using Claude Code CLI in terminal? → [Claude Code CLI Setup](#claude-code-cli)
   - Using Google Gemini/AI Studio? → [Google Gemini Setup](#google-gemini)
   - Using Claude Desktop app? → [Claude Desktop Setup](#claude-desktop)
   - Using another client? → [Other MCP Clients](#other-mcp-clients)

2. **Verify prerequisites:**
   ```bash
   # Check PostgreSQL is running
   podman ps | grep mcp-task-postgres

   # If not running, start it
   podman-compose up -d

   # Build JAR file
   ./mvnw clean package

   # Verify .env file exists
   cat .env | grep DB_HOST
   ```

3. **Follow your client's setup instructions** (see sections below)

4. **Test the connection:**
   - Call the `mcp-help` tool from your client
   - You should receive comprehensive server documentation

---

## CLIENT SETUP

### Client Comparison

Choose the setup method based on your MCP client:

| Client | Use Case | Config Method | Config Location |
|--------|----------|---------------|-----------------|
| **Claude Code CLI** | Development, local workspace | `claude mcp add` | `~/.claude.json` (project-specific) |
| **Google Gemini** | Google AI Studio | `gemini mcp add` | `.gemini/settings.json` |
| **Claude Desktop** | Desktop app usage | Manual JSON editing | `~/Library/Application Support/Claude/claude_desktop_config.json` |
| **Other MCP Clients** | Custom integrations | STDIO transport | Client-specific |

**Common Requirements (All Clients):**
- PostgreSQL container running: `podman ps | grep mcp-task-postgres`
- JAR file built: `./mvnw clean package`
- Environment variables configured in `.env`
- Absolute path to `start-mcp-server.sh`

---

### Claude Code CLI

**Quick Setup:**

1. **Navigate to project directory:**
   ```bash
   cd /path/to/mcp-TaskV2
   ```

2. **Add server to workspace:**
   ```bash
   claude mcp add mcp-task-server $(pwd)/start-mcp-server.sh
   ```

3. **Verify connection:**
   ```bash
   claude mcp list
   ```

   Expected output:
   ```
   mcp-task-server: /path/to/start-mcp-server.sh - ✓ Connected
   ```

**Configuration Scope:**

By default, `claude mcp add` creates a **workspace-level** configuration:
- Config file: `~/.claude.json` (project-specific)
- Only available in the current project directory
- Recommended for development and testing

For global access across all projects:
```bash
claude mcp add --global mcp-task-server $(pwd)/start-mcp-server.sh
```

**Updating Configuration:**

```bash
# Remove existing configuration
claude mcp remove mcp-task-server

# Re-add with new settings
claude mcp add mcp-task-server /new/absolute/path/to/start-mcp-server.sh
```

**Important:** Always use absolute paths to avoid connection issues.

**Configuration Examples:**

View current configuration:
```bash
# List all MCP servers
claude mcp list

# View configuration file
cat ~/.claude.json  # workspace config
cat ~/.config/claude/config.json  # global config (if using --global)
```

Configuration structure (workspace-level):
```json
{
  "mcpServers": {
    "mcp-task-server": {
      "command": "/absolute/path/to/mcp-TaskV2/start-mcp-server.sh",
      "args": []
    }
  }
}
```

**Usage in Claude Code:**

Once configured, the MCP tools are automatically available in your Claude Code session. Simply refer to them by name:
- "Call mcp-help to get documentation"
- "Use mcp-schema-tasks to see the schema"
- "Insert these tasks with mcp-tasks"

**Prerequisites:**
- Claude Code CLI installed: `npm install -g @anthropics/claude-code`
- PostgreSQL container running: `podman ps | grep mcp-task-postgres`
- JAR file built: `./mvnw clean package`
- Environment variables configured in `.env`

---

### Google Gemini

**Quick Setup:**

1. **Navigate to project directory:**
   ```bash
   cd /path/to/mcp-TaskV2
   ```

2. **Remove existing configuration (if any):**
   ```bash
   gemini mcp remove mcp-task-server
   ```

3. **Add server with absolute path:**
   ```bash
   gemini mcp add mcp-task-server $(pwd)/start-mcp-server.sh
   ```

4. **Verify connection:**
   ```bash
   gemini mcp list
   ```

   Expected output:
   ```
   ✓ mcp-task-server: /path/to/start-mcp-server.sh (stdio) - Connected
   ```

**Configuration File:**

The command above creates `.gemini/settings.json`:
```json
{
  "mcpServers": {
    "mcp-task-server": {
      "command": "/absolute/path/to/start-mcp-server.sh",
      "args": []
    }
  }
}
```

**Important:** Always use absolute paths to avoid connection issues.

**Prerequisites:**
- PostgreSQL container running: `podman ps | grep mcp-task-postgres`
- JAR file built: `./mvnw clean package`
- Environment variables configured in `.env`

### Claude Desktop

Add to Claude Desktop configuration (`~/Library/Application Support/Claude/claude_desktop_config.json` on macOS):

```json
{
  "mcpServers": {
    "mcp-task-server": {
      "command": "/absolute/path/to/mcp-TaskV2/start-mcp-server.sh",
      "args": []
    }
  }
}
```

Restart Claude Desktop after configuration.

### Other MCP Clients

Any MCP-compliant client can connect via STDIO transport:

```bash
/absolute/path/to/start-mcp-server.sh
```

The server communicates via stdin/stdout using JSON-RPC 2.0 protocol.

### HTTP Transport (Remote Access)

For remote or browser-based clients, run the server in HTTP mode:

```bash
export MCP_TRANSPORT=http
export MCP_API_KEY=$(openssl rand -base64 32)
export MCP_HTTP_PORT=8070
./start-mcp-server.sh
```

Endpoints:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/mcp` | POST | JSON-RPC 2.0 messages |
| `/mcp` | GET | SSE stream for server notifications |
| `/mcp` | DELETE | Close active session |
| `/mcp/health` | GET | Health check (no auth required) |

Authenticate with `X-API-Key: <your-key>` on all requests except `/mcp/health`.

---

## AVAILABLE TOOLS

**Note:** Detailed tool schemas, parameters, and examples are available directly from the server. Use the following approach:

### Discovery Workflow

**For all MCP clients, start here after setup:**

```
1. Call mcp-help              → Get comprehensive documentation
2. Call mcp-schema-tasks      → Get Task object JSON Schema
3. Use tools/list (MCP)       → Get all available tools with schemas
```

**Testing Your Setup:**

After configuring your MCP client, verify it's working:

1. **Call `mcp-help` from your client** - Should return detailed documentation
2. **Call `mcp-schema-tasks`** - Should return the Task JSON Schema
3. **Call `mcp-tasks-summary`** - Should return database statistics (may be empty initially)

If any of these fail, check the [Troubleshooting](#troubleshooting) section for your client.

### Tool Overview

| Tool Name | Purpose | When to Use |
|-----------|---------|-------------|
| `mcp-help` | Get server documentation and workflow guide | First call to understand capabilities |
| `mcp-schema-tasks` | Get Task object JSON Schema | Before generating task data |
| `mcp-tasks` | Bulk insert tasks (async) | Insert up to 5000 tasks directly |
| `mcp-tasks-from-file` | Import tasks from JSON file (async) | Large batches (>1000 tasks) or token limit avoidance |
| `mcp-job-status` | Check async job progress | Monitor `mcp-tasks` or `mcp-tasks-from-file` completion |
| `mcp-tasks-summary` | Get database statistics | Verify insertions, check status distribution |
| `mcp-tasks-list` | Retrieve paginated task list with optional status filter | Browse tasks, filter by status, audit timestamps |

**All tools return detailed schemas via MCP protocol.** No need to hardcode them in this document.

---

## TASK OBJECT STRUCTURE

### Core Fields

Retrieve the complete JSON Schema via `mcp-schema-tasks` tool. Essential fields:

```yaml
title:
  type: string
  required: true
  max_length: 255

description:
  type: string
  required: false
  max_length: 2000

status:
  type: enum
  required: true
  values: ["TODO", "IN_PROGRESS", "DONE"]

dueDate:
  type: date
  required: false
  format: "YYYY-MM-DD"
```

### Example Task

```json
{
  "title": "Implement user authentication",
  "description": "Add JWT-based authentication to API endpoints",
  "status": "TODO",
  "dueDate": "2025-03-15"
}
```

---

## RECOMMENDED WORKFLOWS

### Workflow 1: First-Time Discovery

```
STEP 1: Call mcp-help
  → Understand server capabilities and tools

STEP 2: Call mcp-schema-tasks
  → Get Task object validation rules

STEP 3: Review returned documentation
  → Plan your data generation strategy
```

### Workflow 2: Small Batch Insertion (<1000 tasks)

```
STEP 1: Generate task objects
  → Follow schema from mcp-schema-tasks
  → Validate required fields

STEP 2: Call mcp-tasks
  → Arguments: { "tasks": [...] }
  → Capture jobId from response

STEP 3: Monitor progress
  → Call mcp-job-status with jobId
  → Wait for status: COMPLETED

STEP 4: Verify results
  → Call mcp-tasks-summary
  → Check totalCount and status distribution
```

### Workflow 3: Large Batch Import (>1000 tasks)

```
STEP 1: Generate JSON file
  → Array of task objects
  → Save to file (e.g., tasks_5000.json)

STEP 2: Call mcp-tasks-from-file
  → Arguments: { "filePath": "tasks_5000.json" }
  → Capture jobId

STEP 3: Poll job status
  → Call mcp-job-status periodically
  → Check progressPercent

STEP 4: Verify completion
  → Call mcp-tasks-summary
  → Validate totalCount matches expected
```

---

## BATCH PROCESSING

### Size Recommendations

```yaml
small_batch:
  size: 1-100 tasks
  method: mcp-tasks
  performance: ~50-100 tasks/sec

optimal_batch:
  size: 500-2000 tasks
  method: mcp-tasks
  performance: ~2000 tasks/sec
  recommended: true

large_batch:
  size: 2000-5000 tasks
  method: mcp-tasks
  performance: ~1500-2000 tasks/sec
  note: May hit token limits

very_large:
  size: 5000+ tasks
  method: mcp-tasks-from-file
  performance: ~2000 tasks/sec
  note: Bypasses token limits
```

### Async Processing

**Both `mcp-tasks` and `mcp-tasks-from-file` are asynchronous:**

1. They return immediately with a `jobId`
2. Processing happens in the background
3. Use `mcp-job-status` to monitor progress
4. Job statuses: `PENDING` → `PROCESSING` → `COMPLETED` / `FAILED`

---

## ERROR HANDLING

### Common Validation Errors

```yaml
"title must not be blank":
  cause: Missing or empty title
  fix: Ensure all tasks have non-empty title

"status must be one of [TODO, IN_PROGRESS, DONE]":
  cause: Invalid status value
  fix: Use only allowed enum values

"title size must be between 1 and 255":
  cause: Title too long
  fix: Truncate to 255 characters

"Batch size exceeds maximum of 5000":
  cause: Too many tasks in one request
  fix: Split batch or use mcp-tasks-from-file

"File not found":
  cause: Invalid file path
  fix: Use absolute path, verify file exists
```

### Error Response Structure

Errors include `success: false` and detailed messages:

```json
{
  "success": false,
  "error": "Validation failed: title must not be blank",
  "validationErrors": [
    "Task 0: title must not be blank",
    "Task 5: status must be one of [TODO, IN_PROGRESS, DONE]"
  ]
}
```

---

## BEST PRACTICES

### 1. Schema First
Always call `mcp-schema-tasks` before generating data to ensure compliance.

### 2. Optimal Batch Sizing
Use 1000-2000 tasks per batch for best throughput.

### 3. File Import for Large Datasets
Use `mcp-tasks-from-file` for >1000 tasks to avoid token limits.

### 4. Verification
Always call `mcp-tasks-summary` after insertion to confirm success.

### 5. Status Monitoring
Poll `mcp-job-status` every 2-5 seconds for async operations.

### 6. Client-Side Validation
Validate data before submission to reduce server-side failures:
- Title: 1-255 characters, not blank
- Status: One of ["TODO", "IN_PROGRESS", "DONE"]
- DueDate: YYYY-MM-DD format (if provided)
- Description: Max 2000 characters (if provided)

### 7. Use mcp-help
When in doubt, call `mcp-help` for detailed, up-to-date documentation.

### 8. Environment-Specific Configuration
Different environments may require different configurations:

**Development:**
```bash
# Use workspace-level config (recommended)
cd /path/to/project
claude mcp add mcp-task-server $(pwd)/start-mcp-server.sh
```

**Production/Shared Environments:**
- Verify `.env` file has correct credentials
- Ensure PostgreSQL is accessible from server location
- Consider using Docker/Podman for consistent environments

**Multiple Projects:**
```bash
# Option 1: Workspace-level (different configs per project)
cd /project1 && claude mcp add mcp-task-server /project1/start-mcp-server.sh
cd /project2 && claude mcp add mcp-task-server /project2/start-mcp-server.sh

# Option 2: Global (same config for all projects)
claude mcp add --global mcp-task-server /shared/start-mcp-server.sh
```

### 9. Managing Multiple MCP Servers
You can configure multiple MCP servers simultaneously:

```bash
# Add task server
claude mcp add mcp-task-server /path/to/mcp-TaskV2/start-mcp-server.sh

# Add other MCP servers
claude mcp add another-server /path/to/another-server.sh

# List all configured servers
claude mcp list

# Remove specific server
claude mcp remove mcp-task-server
```

---

## PERFORMANCE OPTIMIZATION

### Database Optimizations

The server uses:
- **Sequence allocation** (allocationSize=50) for efficient ID generation
- **Hibernate batch inserts** (batch_size=50) for grouped operations
- **HikariCP connection pooling** (max_pool_size=20)
- **PostgreSQL batch rewrites** for multi-row INSERT statements

### Throughput Expectations

```yaml
typical_performance:
  small_batches: "50-100 tasks/second"
  optimal_batches: "~2000 tasks/second"
  large_batches: "1500-2000 tasks/second"

factors_affecting_performance:
  - Batch size
  - Network latency
  - Database load
  - Container resources
```

---

## TROUBLESHOOTING

### Server Won't Start

```bash
# Check if PostgreSQL is running
podman ps | grep mcp-task-postgres

# If not, start it
podman-compose up -d

# Verify JAR exists
ls -l target/mcp-task-server-1.0.0-SNAPSHOT.jar

# Rebuild if needed
./mvnw clean package
```

### Connection Issues (Claude Code CLI)

```bash
# Check current configuration
claude mcp list

# Use absolute paths (remove and re-add)
claude mcp remove mcp-task-server
claude mcp add mcp-task-server $(pwd)/start-mcp-server.sh

# Verify connection
claude mcp list

# Check if server is running
ps aux | grep mcp-task-server

# Test server manually
/absolute/path/to/start-mcp-server.sh
# Should start server and wait for JSON-RPC input
```

**Common Issues:**
- **"Not Connected"**: Server script not executable (`chmod +x start-mcp-server.sh`)
- **"Command not found"**: Use absolute path, not relative
- **"Database connection failed"**: Check PostgreSQL is running and `.env` is configured
- **"JAR not found"**: Run `./mvnw clean package` first

---

### Connection Issues (Gemini)

```bash
# Use absolute paths
gemini mcp remove mcp-task-server
gemini mcp add mcp-task-server $(pwd)/start-mcp-server.sh

# Verify
gemini mcp list
```

### Database Connection Errors

Check `.env` file configuration:
```bash
DB_HOST=localhost
DB_PORT=5432
DB_NAME=taskdb
DB_USER=taskuser
DB_PASSWORD=<your-secure-password-here>   # Change this!
```

Verify PostgreSQL is accessible:
```bash
podman exec -it mcp-task-postgres psql -U taskuser -d taskdb
```

---

## TECHNICAL DETAILS

### Database Schema

```sql
CREATE TABLE tasks (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL,
    due_date DATE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_due_date ON tasks(due_date);
```

### Transport Protocol

- **Protocol:** JSON-RPC 2.0
- **STDIO transport:** stdin/stdout, line-delimited JSON, UTF-8 (default, trusted local channel — no auth)
- **HTTP transport:** Streamable HTTP/SSE on `/mcp` (POST = JSON-RPC, GET = SSE stream, DELETE = close session); API key auth via `X-API-Key` header
- **Health endpoint:** `GET /mcp/health` — unauthenticated, active in both `http` and `both` modes

### MCP Initialization Sequence

```
1. Client sends:  initialize request
2. Server responds: capabilities (tools, resources, prompts)
3. Client calls:  tools/list      → discover 7 available tools
4. Client calls:  resources/list  → discover task resources
5. Client calls:  prompts/list    → discover 3 prompt templates
6. Client calls:  tools/call / resources/read / prompts/get  → execute
```

---

## QUICK REFERENCE

### Tool Selection Matrix

```
NEED                           | USE TOOL              | NOTES
-------------------------------|-----------------------|---------------------------
Understand capabilities        | mcp-help              | First call
Get task schema                | mcp-schema-tasks      | Before generation
Insert < 5000 tasks           | mcp-tasks             | Async, check with job-status
Insert > 5000 tasks           | mcp-tasks-from-file   | Required for large batches
Check async job               | mcp-job-status        | After tasks/tasks-from-file
Browse / filter tasks          | mcp-tasks-list        | Pagination: page, pageSize, status
Verify insertion              | mcp-tasks-summary     | Final validation
```

### Status Values

```
TODO         - Task not started
IN_PROGRESS  - Task being worked on
DONE         - Task completed
```

### Date Format

```
FORMAT: YYYY-MM-DD
REGEX: ^\d{4}-\d{2}-\d{2}$

Valid:   2025-03-15, 2025-12-31
Invalid: 03/15/2025, 2025-3-15, 15-03-2025
```

---

## SUMMARY

This MCP server provides optimized bulk task operations for AI agents across multiple MCP clients.

**Key Points:**
1. **Choose your client** - Claude Code CLI, Google Gemini, Claude Desktop, or custom
2. **Follow client-specific setup** - Use absolute paths, verify prerequisites
3. **Always start with discovery** - Call `mcp-help` and `mcp-schema-tasks`
4. **Use appropriate tool for batch size** - <5000: direct, ≥5000: file
5. **Validate data client-side** - Check schema compliance before submission
6. **Monitor async operations** - Use `mcp-job-status` for progress tracking
7. **Verify results** - Call `mcp-tasks-summary` after operations

**Optimal Workflow (All Clients):**
```
Setup Client → mcp-help → mcp-schema-tasks → [generate data] → mcp-tasks → mcp-job-status → mcp-tasks-summary
```

**Client Selection Guide:**
- **Development/Testing**: Claude Code CLI (workspace-level config)
- **Google AI Studio**: Google Gemini CLI
- **Desktop App**: Claude Desktop (manual JSON config)
- **CI/CD/Automation**: Custom MCP client via STDIO

**For detailed schemas and examples:** Call `mcp-help` from your MCP client.

---

## CLIENT-SPECIFIC VERIFICATION

### Verifying Claude Code CLI Setup

After running `claude mcp add`, test with:

```bash
# Start Claude Code session
claude

# In the session, ask Claude to call MCP tools:
# "Can you call mcp-help and show me the documentation?"
# "Call mcp-tasks-summary to show current database stats"
```

Claude will automatically use the configured MCP tools when you reference them.

### Verifying Google Gemini Setup

After running `gemini mcp add`, test with:

```bash
# Start Gemini session
gemini

# Ask Gemini to use MCP tools:
# "Use mcp-help to get the server documentation"
# "Call mcp-schema-tasks to show the task schema"
```

### Verifying Claude Desktop Setup

After editing the config file and restarting:

1. Open Claude Desktop
2. Look for MCP tools in the tool picker/menu
3. Ask Claude: "What MCP tools are available?"
4. Request: "Call mcp-help to show server documentation"

### Manual Testing (All Clients)

Test the server directly:

```bash
# Start server manually
/absolute/path/to/start-mcp-server.sh

# Server should start and wait for JSON-RPC input
# Press Ctrl+C to stop

# Check logs if issues occur
tail -f logs/mcp-server.log  # if logging is configured
```

---

## MCP PROMPTS

The server implements the full MCP Prompts primitive (`prompts/list` + `prompts/get`).

### Available Prompts

| Prompt Name | Description | Arguments |
|---|---|---|
| `create-tasks-from-description` | Generates a structured task list from a natural-language description | `description` (required) |
| `summarize-tasks-by-status` | Returns a live summary with DB stats, grouped by status | `status` (optional: `TODO`, `IN_PROGRESS`, `DONE`) |
| `task-report-template` | Produces a formatted task report template | `format` (optional: `brief` \| `detailed`) |

### Usage Example

```
STEP 1: Discover prompts
  → Call prompts/list
  → Returns all 3 prompt names with argument descriptors

STEP 2: Retrieve a prompt
  → Call prompts/get with name + arguments
  → Returns ready-to-use message content (role: user or assistant)

STEP 3: Use the returned message
  → Inject into your LLM conversation as-is
```

### Prompt vs. Tool

```
USE PROMPT when...              USE TOOL when...
--------------------------------|--------------------------------
You need a reusable template    | You want to write/read DB data
You want live stats embedded    | You want raw results
You need role-annotated content | You need structured JSON output
```

---

## SUPPORT

- **Documentation:** Call `mcp-help` tool for comprehensive, up-to-date information
- **Schema Details:** Call `mcp-schema-tasks` for current validation rules
- **Tool Definitions:** Use MCP `tools/list` to get all available tools with schemas

**Troubleshooting:**
- **Connection Issues:** See [Troubleshooting](#troubleshooting) section for your client
- **Performance Issues:** Review [Performance Optimization](#performance-optimization)
- **Validation Errors:** Check [Error Handling](#error-handling)

**Note:** This document provides conceptual guidance. For exact API specifications, tool schemas, and current examples, always query the server directly using the tools mentioned above.
