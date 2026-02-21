# Evaluate Koog Plugin for Claude Code

Sets up evaluation of Koog AI agents using Dokimos.

## Features

This plugin provides a skill that:

- Wires Koog agents as the system under test or as LLM judges via `KoogSupport`
- Uses `asJudge()` to wrap Koog agents into `JudgeLM` instances
- Uses `AIAgent.runBlocking()` for synchronous agent execution
- Supports Kotlin DSL when `dokimos-kotlin` is available

## Installation

### Step 1: Add the Dokimos Marketplace

```
/plugin marketplace add dokimos-dev/dokimos
```

### Step 2: Install the Plugin

```
/plugin install evaluate-koog@dokimos
```

Or install via CLI:

```bash
claude plugin install evaluate-koog@dokimos
```

### Step 3 (Optional): Enable for Your Team

Add to your project's `.claude/settings.json`:

```json
{
  "enabledPlugins": {
    "evaluate-koog@dokimos": true
  }
}
```

## What's Included

| File | Description |
|------|-------------|
| `SKILL.md` | KoogSupport utilities, agent-as-test and agent-as-judge patterns, Kotlin DSL |

## Usage

Once installed, the skill activates when you ask to evaluate, test, or benchmark a Koog agent.

### Example Triggers

- "Evaluate my customer support Koog agent on QA accuracy"
- "Use a Koog agent as a judge for evaluating LLM outputs"
- "Set up Dokimos evaluation for my Koog-based chatbot"

## Dependencies

Requires `dokimos-koog` as a dependency. Koog itself is provided-scope.

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-koog</artifactId>
    <version>${dokimos.version}</version>
</dependency>
```

## Contributing

This plugin lives in the Dokimos repository at `.claude-plugin/plugins/evaluate-koog/`.

## Version History

### 0.1.0

- Initial release
- Agent-as-test and agent-as-judge patterns
- Kotlin DSL support
