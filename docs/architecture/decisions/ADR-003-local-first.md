# ADR-003: Local-First AI Architecture

**Date:** 2026-05-22\
**Status:** Accepted

## Context

Most AI assistants send all data to cloud servers.
This creates privacy concerns and cost issues.

## Decision

Ollama (local) as primary AI provider.
Cloud providers (Gemini) as fallback only.

## Reasons

- **Privacy:** Conversations never leave user's machine
- **Cost:** Ollama is completely free
- **Offline:** Works without internet
- **Performance:** No network latency for AI calls

## Consequences

- Users need sufficient RAM (8GB+ recommended)
- Initial setup requires model download (~5GB)
- Response speed depends on user's hardware
- Provider abstraction makes switching transparent