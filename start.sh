#!/bin/bash

echo "🚀 Starting Job Autofill Assistant"
echo "=================================="

# Check if Ollama is running
if ! curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo "❌ Ollama not running. Starting Ollama..."
    ollama serve &
    sleep 3
fi

echo "✓ Ollama is running"

# Start backend
echo "Starting Spring Boot backend..."
cd backend
mvn spring-boot:run