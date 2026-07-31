# Generate Goldens Plugin for Claude Code

Generates multi-turn conversation goldens for Dokimos from scenario seeds.

## Features

This plugin provides a skill that:

- Turns scenario seeds into a committed conversation dataset with `GoldenGenerator`
- Covers both scripted seeds (fixed user turns, no judge) and persona-driven seeds (`UserPersonas`)
- Explains the generated golden shape, including why persona seeds get no default answer
- Shows how to replay the suite with `@DatasetSource`, and why the transcript must not be fed back as a prompt

## Installation

### Step 1: Add the Dokimos Marketplace

```
/plugin marketplace add dokimos-dev/dokimos
```

### Step 2: Install the Plugin

```
/plugin install generate-goldens@dokimos
```

Or install via CLI:

```bash
claude plugin install generate-goldens@dokimos
```

### Step 3 (Optional): Enable for Your Team

Add to your project's `.claude/settings.json`:

```json
{
  "enabledPlugins": {
    "generate-goldens@dokimos": true
  }
}
```

## What's Included

| File | Description |
|------|-------------|
| `SKILL.md` | Seed types, generator API, golden shape, replay patterns, and guidelines |

## Usage

Once installed, the skill activates when you ask for synthetic conversation test data or a multi-turn regression suite.

### Example Triggers

- "Generate conversation goldens for my support agent"
- "Build a multi-turn regression suite I can replay in CI"
- "Create scenario seeds for an angry customer and a confused user"

## Contributing

This plugin lives in the Dokimos repository at `plugins/generate-goldens/`.

## Version History

### 0.1.0

- Initial release
- Scripted and persona-driven scenario seeds
- Golden shape reference and `@DatasetSource` replay patterns
