![UltimateAI Platform Logo](https://github.com/iyeanur6-cyber/ultimate-ai-platform/blob/main/docs/UltimateAI.png)
# 🤝 Contributing to UltimateAI Platform

First off — thank you for considering contributing to UltimateAI Platform.

UltimateAI is an open-source, privacy-first AI platform built for developers, researchers, and AI enthusiasts who believe personal AI should run locally and remain fully under user control.

Every contribution matters — whether it is code, documentation, bug reports, testing, ideas, or feedback.

---


## ✅ Correct Pattern

```java
package ai.ultimate.tools.builtin;

import ai.ultimate.tools.UltimateTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class MyTool implements UltimateTool {

    @Tool(
        description =
            "What this tool does. "
            + "When the AI should call it. "
            + "What it returns."
    )
    public String doSomething(
        @ToolParam(
            description =
                "What this parameter expects"
        )
        String input
    ) {

        try {
            return "result: " + input;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
```

## ❌ Bad Tool Example

```java
@Tool(description = "My tool")
public String myTool(String input) {
    throw new RuntimeException("fail");
}
```

## Tool Description Rules

```text
✅ Explain WHAT it does

✅ Explain WHEN the AI should use it

✅ List example parameter values

✅ Explain what it RETURNS

❌ Never throw exceptions to the AI model

❌ Never return null — always return a String
```

---

# 🌱 Good First Issues

| Label            | Issues Available        |
| ---------------- | ----------------------- |
| good first issue | #3, #4, Phase 4 CLI     |
| phase-2          | #34 CLI memory commands |
| phase-3          | Document REST API, CLI  |
| phase-4          | CLI tool commands       |
| testing          | All phases              |
| documentation    | Always open             |

---

# 📝 Commit Message Convention

Ultimate follows **Conventional Commits**.

## Examples

```text
feat: add weather tool with OpenWeatherMap integration

fix: resolve streaming timeout on long responses

docs: update architecture diagrams

test: add integration tests for AiOrchestrator

refactor: extract provider routing logic

chore: upgrade Spring AI dependencies
```

## Allowed Types

| Type     | Purpose            |
| -------- | ------------------ |
| feat     | New feature        |
| fix      | Bug fix            |
| docs     | Documentation      |
| test     | Tests              |
| refactor | Code restructuring |
| chore    | Maintenance        |

---

# 🔄 Submitting Pull Requests

## Before Opening a PR

```bash
git fetch upstream

git rebase upstream/main

./mvnw test

./mvnw compile
```

## Pull Request Rules

### Required

* One feature/fix per PR
* Tests must pass
* Follow coding standards
* Update documentation if needed
* Use conventional commits

### Not Allowed

* Breaking changes without discussion
* Committing secrets or API keys
* Large unrelated refactors

---

# 💬 Community & Support

Need help?

* Open a GitHub Discussion
* Ask architecture questions
* Share ideas and feedback
* Suggest improvements

---

# ❤️ Thank You

Thank you for helping build an open, privacy-first AI platform for everyone.

Together, we are building:

* Local-first AI
* Open AI infrastructure
* Community-driven tooling
* A developer-focused AI ecosystem

🚀 Welcome to the UltimateAI contributor community.

### Owner can reject any PR without any explanation.
