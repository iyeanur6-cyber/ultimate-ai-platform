# ADR-002: Spring WebFlux Over Spring MVC

**Date:** 2026-05-22\
**Status:** Accepted

## Context

Jarvis streams AI tokens in real-time.
We needed a web framework for this requirement.

## Decision

Spring WebFlux (reactive, non-blocking).

## Reasons

- AI token streaming = Flux of tokens = natural fit
- Non-blocking = handles concurrent AI sessions efficiently
- SSE + WebFlux = designed for each other
- R2DBC integration = end-to-end reactive stack
- Future-proof for Angular frontend in Phase 7

## Alternatives Considered

- **Spring MVC:** Rejected — thread-blocking under long AI
  response times defeats concurrency model

## Consequences

- Higher initial learning curve for contributors
- R2DBC instead of JPA
- All repository methods return Mono/Flux
- Better long-term scalability under concurrent load