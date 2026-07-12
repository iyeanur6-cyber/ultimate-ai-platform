-- ═══════════════════════════════════════════════════
-- V8: Seed Default Data
-- Default AI providers and system prompt.
-- This data is created on EVERY fresh install.
-- ═══════════════════════════════════════════════════

-- ── Default AI Providers ────────────────────────────

-- Ollama (Local AI — Primary)
INSERT INTO ai_providers (
    id,
    name,
    display_name,
    provider_type,
    model_name,
    base_url,
    is_default,
    is_active,
    temperature,
    max_tokens,
    context_window
) VALUES (
             '018e8f7a-0001-7000-8000-000000000001',
             'ollama-llama3',
             'Llama 3.1 8B (Local — Ollama)',
             'OLLAMA',
             'llama3.1:8b',
             'http://localhost:11434',
             TRUE,   -- default provider
             TRUE,
             0.70,
             2048,
             128000
         );

-- Ollama Phi3 Mini (Lightweight option)
INSERT INTO ai_providers (
    id,
    name,
    display_name,
    provider_type,
    model_name,
    base_url,
    is_default,
    is_active,
    temperature,
    max_tokens,
    context_window
) VALUES (
             '018e8f7a-0001-7000-8000-000000000002',
             'ollama-phi3',
             'Phi-3 Mini (Local — Fast & Lightweight)',
             'OLLAMA',
             'phi3:mini',
             'http://localhost:11434',
             FALSE,
             TRUE,
             0.70,
             1024,
             128000
         );

-- Gemini (Cloud Fallback)
INSERT INTO ai_providers (
    id,
    name,
    display_name,
    provider_type,
    model_name,
    base_url,
    is_default,
    is_active,
    temperature,
    max_tokens,
    context_window
) VALUES (
             '018e8f7a-0001-7000-8000-000000000003',
             'gemini-flash',
             'Gemini 1.5 Flash (Cloud — Fallback)',
             'GEMINI',
             'gemini-1.5-flash',
             NULL,   -- no base_url for cloud
             FALSE,
             TRUE,   -- active but not default
             0.70,
             2048,
             1000000
         );

-- ── Default System Prompt ────────────────────────────

INSERT INTO system_prompts (
    id,
    name,
    display_name,
    content,
    description,
    is_default,
    is_active
) VALUES (
             '018e8f7a-0002-7000-8000-000000000001',
             'Ultimate-default',
             'Ultimate — Default Assistant',
             'You are Ultimate, an intelligent personal AI assistant.

         You are running locally on {username}''s machine.
         Current date and time: {datetime}
         Active AI model: {model}
         Session started: {session_age}

         Your personality:
         - Professional yet warm and approachable
         - Concise unless asked for detail
         - Honest about limitations — never fabricate facts
         - Use markdown formatting in responses
         - Reference previous context when relevant

         Your capabilities in this session:
         - Answer questions and have conversations
         - Help with analysis, writing, and problem-solving
         - Assist with coding and technical questions
         - Remember context from this conversation

         Important rules:
         - If you don''t know something, say so clearly
         - Never pretend to have real-time data unless a tool provides it
         - Protect user privacy — do not repeat sensitive information
         - If asked to do something harmful, decline politely',
             'Default Ultimate personality. Professional, helpful, honest.',
             TRUE,
             TRUE
         );

-- Coding-focused system prompt
INSERT INTO system_prompts (
    id,
    name,
    display_name,
    content,
    description,
    is_default,
    is_active
) VALUES (
             '018e8f7a-0002-7000-8000-000000000002',
             'Ultimate-coding',
             'Ultimate — Coding Assistant',
             'You are Ultimate in coding assistant mode.

         User: {username} | Time: {datetime} | Model: {model}

         Focus on:
         - Writing clean, production-quality code
         - Explaining code clearly with comments
         - Following best practices for the language/framework
         - Suggesting improvements and catching bugs
         - Providing working examples

         Code formatting:
         - Always use code blocks with language tags
         - Include imports and dependencies
         - Note any assumptions made

         Current tech stack context:
         - Java 23, Spring Boot 4, Spring AI 2.0
         - WebFlux (reactive), R2DBC, PostgreSQL
         - This is the Ultimate AI Platform project',
             'Optimized for coding assistance and code review.',
             FALSE,
             TRUE
         );

COMMENT ON TABLE ai_providers IS
    'Seeded with Ollama (default), Phi3 (lightweight), Gemini (fallback).';