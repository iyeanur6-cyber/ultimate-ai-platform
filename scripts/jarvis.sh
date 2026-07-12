#!/bin/bash
# Jarvis AI Platform Launcher for Linux/Mac

JARVIS_DIR="$(dirname "$0")"
JAR="$JARVIS_DIR/jarvis-server-0.1.0.jar"

if [ ! -f "$JAR" ]; then
    echo ""
    echo "Jarvis JAR not found."
    echo "Download from:"
    echo "https://github.com/sujankim/jarvis-ai-platform/releases"
    exit 1
fi

echo "Starting Jarvis AI Platform..."

# -Djline.terminal=unix ensures proper terminal on Mac/Linux
java \
  -Djline.terminal=unix \
  -jar "$JAR" "$@"