# Create Tests Plugin for Claude Code

Scaffolds eval-driven tests using dokimos-junit that run Dokimos evaluations as JUnit parameterized tests.

## Features

This plugin provides a skill that:

- Creates JUnit parameterized tests using `@DatasetSource` to feed dataset examples as test parameters
- Uses `Assertions.assertEval()` to run evaluators as test assertions
- Supports Q&A, LLM-judged, and RAG evaluation patterns
- Enables eval-driven development where evaluations run in CI like unit tests

## Installation

### Step 1: Add the Dokimos Marketplace

```
/plugin marketplace add dokimos-dev/dokimos
```

### Step 2: Install the Plugin

```
/plugin install create-tests@dokimos
```

Or install via CLI:

```bash
claude plugin install create-tests@dokimos
```

### Step 3 (Optional): Enable for Your Team

Add to your project's `.claude/settings.json`:

```json
{
  "enabledPlugins": {
    "create-tests@dokimos": true
  }
}
```

## What's Included

| File | Description |
|------|-------------|
| `SKILL.md` | Core patterns, dataset source options, RAG evaluation, integration test setup, and checklist |

## Usage

Once installed, the skill activates when you ask to create evaluation tests, set up eval-driven development, or write parameterized evaluation tests.

### Example Triggers

- "Create evaluation tests for my RAG chatbot"
- "Set up eval-driven tests for my Q&A bot using dokimos-junit"
- "Write parameterized tests that evaluate my LLM responses against a dataset"
- "Add CI evaluation tests using @DatasetSource"

## Contributing

This plugin lives in the Dokimos repository at `.claude-plugin/plugins/create-tests/`.

## Version History

### 0.1.0

- Initial release
- `@DatasetSource` and `@ParameterizedTest` patterns
- Q&A, LLM-judged, and RAG evaluation support
- Integration test setup with `@Tag("integration")`
