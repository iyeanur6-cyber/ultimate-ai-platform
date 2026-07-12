package ai.ultimate.memory;

/**
 * Types of memories Jarvis stores about users.
 *
 * FACT:       True information about the user
 *             "User's name is Dravin"
 *             "User has 16GB RAM"
 *
 * GOAL:       What the user is working toward
 *             "Building Jarvis AI Platform"
 *             "Learning Spring AI"
 *
 * PREFERENCE: How the user likes things done
 *             "Prefers detailed explanations"
 *             "Uses dark mode"
 *
 * CONTEXT:    Current project or situation
 *             "Working on Phase 2 memory system"
 *             "Using Java 21 + Spring Boot 4"
 *
 * EVENT:      Something that happened
 *             "Published first article on Dev.to"
 *             "Released v0.1.0"
 */
public enum MemoryType {
    FACT,
    GOAL,
    PREFERENCE,
    CONTEXT,
    EVENT
}