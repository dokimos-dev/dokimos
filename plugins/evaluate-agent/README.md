# Evaluate Agent Plugin for Claude Code

Sets up evaluation of AI agents using Dokimos.

## Features

This plugin provides a skill that:

- Creates evaluation pipelines for AI agents that use tools
- Uses the `ToolCall`, `ToolDefinition`, and `AgentTrace` data model for portable agent traces
- Supports rule-based evaluators: tool call validity, tool correctness, tool name and description reliability
- Supports LLM-based evaluators: task completion, tool argument hallucination detection
- Works with both Java and Kotlin (DSL included)

## Installation

### Step 1: Add the Dokimos Marketplace

```
/plugin marketplace add dokimos-dev/dokimos
```

### Step 2: Install the Plugin

```
/plugin install evaluate-agent@dokimos
```

Or install via CLI:

```bash
claude plugin install evaluate-agent@dokimos
```

### Step 3 (Optional): Enable for Your Team

Add to your project's `.claude/settings.json`:

```json
{
  "enabledPlugins": {
    "evaluate-agent@dokimos": true
  }
}
```

## What's Included

| File | Description |
|------|-------------|
| `SKILL.md` | Data model (ToolCall, ToolDefinition, AgentTrace), all 6 evaluators, Java and Kotlin patterns, EvalTestCase conventions |

## Usage

Once installed, the skill activates when you ask to evaluate, test, or benchmark an AI agent, or check tool call correctness, task completion, or tool definition quality.

### Example Triggers

- "Evaluate my AI agent's tool usage"
- "Set up tool call validation for my agent"
- "Test if my agent completes tasks correctly"
- "Check my agent for argument hallucinations"
- "Assess the quality of my tool definitions"
- "Benchmark my travel booking agent with Dokimos"

## Evaluators

| Evaluator | Category | LLM Required |
|-----------|----------|-------------|
| `ToolCallValidityEvaluator` | Tool proficiency | No |
| `ToolCorrectnessEvaluator` | Tool proficiency | No |
| `TaskCompletionEvaluator` | Task completion | Yes |
| `ToolArgumentHallucinationEvaluator` | Tool proficiency | Yes |
| `ToolNameReliabilityEvaluator` | Tool reliability | Optional |
| `ToolDescriptionReliabilityEvaluator` | Tool reliability | Optional |

## Dependencies

Agent evaluation is built into `dokimos-core` — no additional modules needed:

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-core</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

## Contributing

This plugin lives in the Dokimos repository at `plugins/evaluate-agent/`.

## Version History

### 0.1.0

- Initial release
- Data model: ToolCall, ToolDefinition, AgentTrace
- 6 agent evaluators: validity, correctness, task completion, hallucination, name reliability, description reliability
- Java and Kotlin DSL support
