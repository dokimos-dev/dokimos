# Create Evaluator Plugin for Claude Code

Scaffolds a new Evaluator implementation for the Dokimos LLM evaluation framework.

## Features

This plugin provides a skill that:

- Creates an evaluator class extending `BaseEvaluator` with the builder pattern
- Creates corresponding unit tests with JUnit 6 and AssertJ
- Follows all Dokimos conventions (Javadoc, immutability, package structure)
- Supports both simple evaluators and LLM-judged evaluators using `JudgeLM`

## Installation

### Step 1: Add the Dokimos Marketplace

```
/plugin marketplace add dokimos-dev/dokimos
```

### Step 2: Install the Plugin

```
/plugin install create-evaluator@dokimos
```

Or install via CLI:

```bash
claude plugin install create-evaluator@dokimos
```

### Step 3 (Optional): Enable for Your Team

Add to your project's `.claude/settings.json`:

```json
{
  "enabledPlugins": {
    "create-evaluator@dokimos": true
  }
}
```

## What's Included

| File | Description |
|------|-------------|
| `SKILL.md` | Evaluator template, builder pattern, test structure, and conventions checklist |

## Usage

Once installed, the skill activates when you ask to create, add, or implement a new evaluator, metric, or scoring function.

### Example Triggers

- "Create an evaluator that checks if the response contains keywords"
- "Add a SentimentEvaluator that scores positive sentiment"
- "Implement a custom metric for response length"

## Contributing

This plugin lives in the Dokimos repository at `.claude-plugin/plugins/create-evaluator/`.

## Version History

### 0.1.0

- Initial release
- Evaluator template with builder pattern
- Unit test scaffolding with JUnit 6 and AssertJ
