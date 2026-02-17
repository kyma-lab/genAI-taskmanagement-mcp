#!/bin/bash
# Start MCP Task Server in STDIO or HTTP/SSE mode
#
# Usage:
#   ./start-mcp-server.sh          # STDIO mode (default, for Claude Desktop)
#   ./start-mcp-server.sh --http   # HTTP/SSE mode (REST + SSE on port 8070)

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Parse arguments
MODE="stdio"
if [[ "$1" == "--http" ]]; then
    MODE="http"
fi

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Check if JAR exists
JAR_FILE="target/mcp-task-server-1.0.0-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}ERROR: JAR file not found: $JAR_FILE${NC}"
    echo -e "${YELLOW}Run: ./mvnw clean package${NC}"
    exit 1
fi

# Check if PostgreSQL is running
if ! podman ps --format "{{.Names}}" | grep -q "^mcp-task-postgres$"; then
    echo -e "${RED}ERROR: PostgreSQL container 'mcp-task-postgres' is not running${NC}"
    echo -e "${YELLOW}Run: podman compose up postgres -d${NC}"
    exit 1
fi

# Load environment variables from .env if it exists
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
fi

# Set transport mode (application.yml reads MCP_TRANSPORT)
export MCP_TRANSPORT="$MODE"

# Set Java Home if needed
if [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME=/Users/m.berger/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home
fi

if [[ "$MODE" == "http" ]]; then
    echo -e "${GREEN}Starting MCP Task Server in HTTP/SSE mode...${NC}" >&2
    echo -e "${YELLOW}Database : PostgreSQL on localhost:5432${NC}" >&2
    echo -e "${YELLOW}RPC      : POST http://localhost:8070/mcp/rpc${NC}" >&2
    echo -e "${YELLOW}SSE      : GET  http://localhost:8070/mcp/events${NC}" >&2
    echo -e "${YELLOW}Health   : GET  http://localhost:8070/mcp/health${NC}" >&2
    if [ -n "$API_KEY" ]; then
        echo -e "${YELLOW}API-Key  : set (X-API-Key header required)${NC}" >&2
    else
        echo -e "${YELLOW}API-Key  : not set (no auth)${NC}" >&2
    fi
else
    echo -e "${GREEN}Starting MCP Task Server in STDIO mode...${NC}" >&2
    echo -e "${YELLOW}Database : PostgreSQL on localhost:5432${NC}" >&2
    echo -e "${YELLOW}Mode     : STDIO (stdin/stdout for Claude Desktop)${NC}" >&2
fi

# Run the server
exec "$JAVA_HOME/bin/java" \
    -Dspring.profiles.active=dev \
    -jar "$JAR_FILE"
