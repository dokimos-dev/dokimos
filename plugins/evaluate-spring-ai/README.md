# Evaluate Spring AI Plugin for Claude Code

Sets up evaluation of Spring AI applications using Dokimos.

## Features

This plugin provides a skill that:

- Creates evaluation pipelines for Spring AI applications (ChatClient, RAG, advisors)
- Uses `SpringAiSupport` utilities for judge creation and type conversion
- Supports `@SpringBootTest` integration for evaluations in CI
- Converts between Spring AI types (`EvaluationRequest`) and Dokimos types (`EvalTestCase`)

## Installation

### Step 1: Add the Dokimos Marketplace

```
/plugin marketplace add dokimos-dev/dokimos
```

### Step 2: Install the Plugin

```
/plugin install evaluate-spring-ai@dokimos
```

Or install via CLI:

```bash
claude plugin install evaluate-spring-ai@dokimos
```

### Step 3 (Optional): Enable for Your Team

Add to your project's `.claude/settings.json`:

```json
{
  "enabledPlugins": {
    "evaluate-spring-ai@dokimos": true
  }
}
```

## What's Included

| File | Description |
|------|-------------|
| `SKILL.md` | SpringAiSupport utilities, ChatClient and RAG evaluation patterns, type conversion |

## Usage

Once installed, the skill activates when you ask to evaluate, test, or benchmark a Spring AI application.

### Example Triggers

- "Evaluate my Spring AI chatbot with RAG advisors"
- "Set up Dokimos evaluation for my Spring Boot LLM application"
- "Test my ChatClient with faithfulness and relevance metrics"
- "Integrate Dokimos with my Spring AI project"

## Dependencies

Requires `dokimos-spring-ai` as a dependency. Spring AI itself is provided-scope.

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-spring-ai</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

## Contributing

This plugin lives in the Dokimos repository at `.claude-plugin/plugins/evaluate-spring-ai/`.

## Version History

### 0.1.0

- Initial release
- ChatClient and RAG evaluation patterns
- `SpringAiSupport` utilities for judge creation and type conversion
