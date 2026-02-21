# Evaluate LangChain4j Plugin for Claude Code

Sets up evaluation of LangChain4j applications and RAG pipelines using Dokimos.

## Features

This plugin provides a skill that:

- Creates evaluation pipelines for LangChain4j applications (Q&A and RAG)
- Uses `LangChain4jSupport` utilities for task creation (`simpleTask`, `ragTask`, `customTask`)
- Uses `asJudge(ChatModel)` to wrap LangChain4j models into `JudgeLM` instances
- Supports RAG-specific evaluators: faithfulness, contextual relevance, and hallucination

## Installation

### Step 1: Add the Dokimos Marketplace

```
/plugin marketplace add dokimos-dev/dokimos
```

### Step 2: Install the Plugin

```
/plugin install evaluate-langchain4j@dokimos
```

Or install via CLI:

```bash
claude plugin install evaluate-langchain4j@dokimos
```

### Step 3 (Optional): Enable for Your Team

Add to your project's `.claude/settings.json`:

```json
{
  "enabledPlugins": {
    "evaluate-langchain4j@dokimos": true
  }
}
```

## What's Included

| File | Description |
|------|-------------|
| `SKILL.md` | LangChain4jSupport utilities, Q&A and RAG evaluation patterns, context key configuration |

## Usage

Once installed, the skill activates when you ask to evaluate, test, or benchmark a LangChain4j application or RAG pipeline.

### Example Triggers

- "Evaluate my RAG pipeline for faithfulness and relevance"
- "Set up Dokimos evaluation for my LangChain4j chatbot"
- "Test my retrieval pipeline for hallucination"
- "Benchmark my LangChain4j AiService with Dokimos"

## Dependencies

Requires `dokimos-langchain4j` as a dependency. LangChain4j itself is provided-scope.

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-langchain4j</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

## Contributing

This plugin lives in the Dokimos repository at `.claude-plugin/plugins/evaluate-langchain4j/`.

## Version History

### 0.1.0

- Initial release
- Q&A and RAG evaluation patterns
- `LangChain4jSupport` utilities for task and judge creation
