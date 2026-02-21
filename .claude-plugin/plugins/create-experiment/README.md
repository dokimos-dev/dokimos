# Create Experiment Plugin for Claude Code

Scaffolds a Dokimos Experiment that wires together a dataset, task, evaluators, and optional reporter.

## Features

This plugin provides a skill that:

- Creates an evaluation experiment using `Experiment.builder()`
- Wires datasets, tasks, evaluators, and optional reporters into a complete pipeline
- Supports plain Java, JUnit integration, and server reporting patterns
- Configures parallelism and multiple runs for variance reduction

## Installation

### Step 1: Add the Dokimos Marketplace

```
/plugin marketplace add dokimos-dev/dokimos
```

### Step 2: Install the Plugin

```
/plugin install create-experiment@dokimos
```

Or install via CLI:

```bash
claude plugin install create-experiment@dokimos
```

### Step 3 (Optional): Enable for Your Team

Add to your project's `.claude/settings.json`:

```json
{
  "enabledPlugins": {
    "create-experiment@dokimos": true
  }
}
```

## What's Included

| File | Description |
|------|-------------|
| `SKILL.md` | Experiment anatomy, builder options, JUnit integration, and server reporting |

## Usage

Once installed, the skill activates when you ask to create an evaluation experiment, set up an eval pipeline, or build an end-to-end evaluation workflow.

### Example Triggers

- "Create an experiment to evaluate my customer support chatbot"
- "Set up an eval pipeline for my RAG application"
- "Evaluate my LLM on a QA dataset with faithfulness and relevance metrics"
- "Build an experiment that reports results to the Dokimos server"

## Contributing

This plugin lives in the Dokimos repository at `.claude-plugin/plugins/create-experiment/`.

## Version History

### 0.1.0

- Initial release
- Basic, JUnit, and server reporting templates
- Builder options reference table
