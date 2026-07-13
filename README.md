![UltimateAI Platform Logo](https://github.com/iyeanur6-cyber/ultimate-ai-platform/blob/main/docs/UltimateAI.png)
# 🌌 Ultimate AI Platform
A Modular, Local-First, Open-Source AI Assistant & Automation Platform Built with Java 21, Spring Boot 4, and Spring AI 2.0.

---

## 🚀 What Is UltimateAI?
UltimateAI is an enterprise-grade modular AI orchestration platform that runs entirely on your own machine with complete privacy. It bypasses traditional chat constraints to provide localized automation, secure sandboxed execution, and context-aware tools integration.

### ✨ Key Differences from Standard AI Implementations
* **Total Local Sovereignty:** Your conversations, embeddings, and telemetry data never leave your hardware.
* **Fully Cost-Optimized:** Runs on Ollama natively, stripping out platform compute taxes.
* **Ecosystem Actions Expansion:** Seamlessly hooks into IDE automation (VSCodium), secure document pipelines (Headless LibreOffice), and sandboxed browser nodes.
* **Supply-Chain Immutability:** Execution environments are containerized via strict SHA-256 pinned whitelisted images.

---

## 📈 Platform Lifecycle Status
| Phase | Version | Core Capability | Status |
| :--- | :--- | :--- | :--- |
| **Phase 1** | v0.1.0 | AI Chat + Core Engine Framework + JWT Security | ✅ Released |
| **Phase 2** | v0.2.0 | Memory System + pgvector Retrieval Mappings | ✅ Released |
| **Phase 3** | v0.3.0 | RAG Subsystem & Secure Context Processing | ✅ Released |
| **Phase 4** | v0.4.0 | Tool Integration Engine & Custom MCP Layer | ✅ Released |
| **Phase 5** | v0.5.0 | Voice Translation Subsystem (Whisper/TTS) | ✅ Released |
| **Phase 6** | v0.6.0 | ReACT Multi-Step Autonomous Agent Systems | ✅ Released |
| **Phase 7** | v1.0.0 | High-Availability Web UI Infrastructure | 🔨 In Progress |

---

## ⚡ Quick Start (Native Host)

### Prerequisites
| Infrastructure Subsystem | Target Version | Operational Purpose |
| :--- | :--- | :--- |
| **Java Development Kit** | 21+ (LTS) | Central Application Runtime Environment |
| **Docker Engine SUB** | Latest | PostgreSQL Virtualization & Redis Volatile Cache Layer |
| **Ollama Core** | Latest | Local Weights Processing & Embeddings Compute Execution |

### Step 1 — Clone Repository
```bash
git clone [https://github.com/iyeanur6-cyber/ultimate-ai-platform.git](https://github.com/iyeanur6-cyber/ultimate-ai-platform.git)
cd ultimate-ai-platform

```
Step 2 — Pull Weights
```
# Core Chat Architecture Model
ollama pull llama3.1:8b

# High-Density Text Embedding Vectors Model
ollama pull nomic-embed-text

```
Step 3 — Configure Environment Boundaries
```
cp .env.example .env


```
Open and customize .env boundaries:
```
ULTIMATE_JWT_SECRET=your-super-secret-key-at-least-32-characters
OPENWEATHER_API_KEY=your_openweather_key
GEMINI_API_KEY=your_gemini_fallback_key


```
Step 4 — Spin Infrastructure Nodes
```
docker compose up -d


```
Step 5 — Boot Engine Subsystem
```
cd server
./mvnw spring-boot:run

```
💻 CLI Commands Layout
Upon booting the boot-subsystem, enter setup directives to claim authority:
```
ultimate:> setup
ultimate:> login
ultimate:> chat

```
Enforced Console Subsystem Maps:
Security & Auth: login | logout │ whoami | setup
Autonomous Chat: chat | chat --new │ ask -m "Prompt Input"
Vector Memory Management: memory list | memory add │ memory delete --number 1 | memory clear
Active Working Sessions: session | new-session │ switch-session -n 2
Sandbox Tooling Arrays: tools | tool-test --tool datetime
Diagnostic Monitors: status | doctor │ Ultimate-version | benchmark-latency --provider ollama --runs 10
🏗️ Architecture Mapping
```
      Spring Shell Console Interface (ultimate:> prompt)
                           │
             Spring Boot 4 AI Coordination Engine
                           │
         ┌─────────────────┴───────────────────┐
         │                                     │
   AiOrchestrator                        Memory System
         │                                     │
   PromptAssembler                       pgvector Storage
         │
         ├── Volatile Session Cache Nodes (Redis 7 Layer)
         ├── RAG Document Matrix (Headless Parsers)
         └── Long-Term Context Tokens (PostgreSQL 16 Layer)
                           │
                     ProviderRouter
                     ├── OllamaProvider (Primary Safe Compute)
                     └── GeminiProvider (Hard Encrypted Fallback Gateway)
                           │
             Enterprise Tools Virtualization Core
             ├── VSCodium Workspace Sync Core
             ├── Containerized Build Automator (Android 15 / Java 21)
             ├── Headless LibreOffice Document Pipeline ($150 Bounty Open)
             ├── Sandboxed Chromium Safe Browser Engine
             └── Managed ImageMagick Processing Tool

```
📄 Licensing & Copyright
Licensed under the Apache License 2.0. See the LICENSE matrix layout descriptor block for operational details.
Copyright [2026] UltimateAI Platform Ecosystem - Yeanur  (@iyeanur6-cyber). All Rights Reserved.

