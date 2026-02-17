#!/bin/bash
set -e

# Automatische Erkennung des Podman API-Socket für macOS
# Findet den von gvproxy bereitgestellten Docker-kompatiblen Socket

echo "🔍 Suche Podman API-Socket..."

# Socket-Pfad aus laufendem gvproxy-Prozess ermitteln
SOCKET_PATH=$(ps aux | grep gvproxy | grep -o '\-forward-sock [^ ]*' | awk '{print $2}' | head -1)

if [ -z "$SOCKET_PATH" ]; then
    echo "❌ Podman API-Socket nicht gefunden."
    echo "   Stelle sicher, dass Podman Machine läuft:"
    echo "   podman machine start"
    exit 1
fi

if [ ! -S "$SOCKET_PATH" ]; then
    echo "❌ Socket existiert nicht: $SOCKET_PATH"
    exit 1
fi

echo "✅ Podman API-Socket gefunden: $SOCKET_PATH"
echo ""
echo "🧪 Führe Tests aus..."
echo ""

# Tests mit docker.host Maven-Property ausführen
exec ./mvnw -Ddocker.host="unix://$SOCKET_PATH" "$@"
