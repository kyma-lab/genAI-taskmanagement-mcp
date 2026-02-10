#!/bin/bash

# MCP Task Server - Podman PostgreSQL Setup Script
# This script sets up PostgreSQL using Podman for the MCP Task Server

set -e

echo "🚀 MCP Task Server - PostgreSQL Setup"
echo "======================================"

# Check if Podman is installed
if ! command -v podman &> /dev/null; then
    echo "❌ Error: Podman is not installed"
    echo "Please install Podman: https://podman.io/getting-started/installation"
    exit 1
fi

echo "✓ Podman is installed"

# Check if port 5432 is available
if lsof -Pi :5432 -sTCP:LISTEN -t >/dev/null 2>&1 ; then
    echo "⚠️  Port 5432 is already in use"
    echo "Checking if it's our container..."
    if podman ps --filter "name=mcp-task-postgres" --format "{{.Names}}" | grep -q "mcp-task-postgres"; then
        echo "✓ MCP PostgreSQL container is already running"
        exit 0
    else
        echo "❌ Port 5432 is used by another process"
        echo "Please stop the existing PostgreSQL or use a different port"
        exit 1
    fi
fi

# Start PostgreSQL using Podman Compose
echo "📦 Starting PostgreSQL container..."
podman compose up -d

# Wait for PostgreSQL to be ready
echo "⏳ Waiting for PostgreSQL to be ready..."
sleep 3

# Check container status
if podman ps --filter "name=mcp-task-postgres" --format "{{.Names}}" | grep -q "mcp-task-postgres"; then
    echo "✅ PostgreSQL container is running!"
    echo ""
    echo "📊 Connection Details:"
    echo "  Host: localhost"
    echo "  Port: 5432"
    echo "  Database: mcptasks"
    echo "  Username: postgres"
    echo "  Password: postgres"
    echo ""
    echo "🔗 JDBC URL: jdbc:postgresql://localhost:5432/mcptasks"
    echo ""
    echo "📝 Useful commands:"
    echo "  Stop:    podman compose down"
    echo "  Logs:    podman compose logs postgres"
    echo "  Status:  podman compose ps"
    echo "  Shell:   podman exec -it mcp-task-postgres psql -U postgres -d mcptasks"
else
    echo "❌ Failed to start PostgreSQL container"
    echo "Check logs: podman compose logs"
    exit 1
fi
