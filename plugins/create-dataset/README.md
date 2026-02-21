# Create Dataset Plugin for Claude Code

Creates evaluation datasets for Dokimos in JSON, CSV, or JSONL format.

## Features

This plugin provides a skill that:

- Creates datasets in JSON, CSV, or JSONL format following Dokimos conventions
- Supports both simple (`input`/`expectedOutput`) and structured (`inputs`/`expectedOutputs`/`metadata`) examples
- Generates realistic, diverse examples based on the user's description
- Shows how to load datasets in code and with `@DatasetSource` in tests

## Installation

### Step 1: Add the Dokimos Marketplace

```
/plugin marketplace add dokimos-dev/dokimos
```

### Step 2: Install the Plugin

```
/plugin install create-dataset@dokimos
```

Or install via CLI:

```bash
claude plugin install create-dataset@dokimos
```

### Step 3 (Optional): Enable for Your Team

Add to your project's `.claude/settings.json`:

```json
{
  "enabledPlugins": {
    "create-dataset@dokimos": true
  }
}
```

## What's Included

| File | Description |
|------|-------------|
| `SKILL.md` | Supported formats, dataset structure, loading examples, and guidelines |

## Usage

Once installed, the skill activates when you ask to create, generate, or build a dataset for LLM evaluation.

### Example Triggers

- "Create a QA dataset for customer support chatbot evaluation with 10 examples"
- "Generate a RAG evaluation dataset with context fields"
- "Build a CSV dataset for testing my translation model"

## Contributing

This plugin lives in the Dokimos repository at `.claude-plugin/plugins/create-dataset/`.

## Version History

### 0.1.0

- Initial release
- JSON, CSV, and JSONL format support
- Simple and structured example formats
