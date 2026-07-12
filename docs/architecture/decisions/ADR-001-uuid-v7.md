# ADR-001: UUID v7 as Primary Key Strategy

**Date:** 2026-05-22\
**Status:** Accepted\
**Decided by:** Dravin (project founder)

## Context

We needed an ID strategy for an open-source
project where users run their own instances
and data may eventually be merged or distributed.

## Decision

Use UUID v7 for all primary keys.

## Reasons

- **Distributed-safe:** No conflicts between instances
- **Time-ordered:** Good index performance (unlike UUID v4)
- **Non-guessable:** Security benefit over BIGINT
- **Future-proof:** Works across microservices if needed

## Alternatives Considered

- **BIGINT:** Rejected — conflicts across distributed instances
- **UUID v4:** Rejected — random = index fragmentation
- **UUID v7:** Accepted — time-ordered + universally unique

## Consequences

- IDs are 16 bytes (larger than BIGINT 8 bytes)
- Less human-readable than BIGINT
- No migration needed when/if project scales