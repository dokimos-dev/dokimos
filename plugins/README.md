# Dokimos Claude Code Plugins

Official Claude Code plugins for the Dokimos LLM evaluation framework.

## Available Plugins

### Scaffolding

| Plugin | Description |
|--------|-------------|
| [create-evaluator](./create-evaluator/) | Scaffold a new Evaluator implementation |
| [create-dataset](./create-dataset/) | Create evaluation datasets (JSON/CSV/JSONL) |
| [create-tests](./create-tests/) | Scaffold eval-driven tests using dokimos-junit |
| [create-experiment](./create-experiment/) | Scaffold an Experiment with dataset, task, and evaluators |
| [generate-goldens](./generate-goldens/) | Generate multi-turn conversation goldens from scenario seeds |

### Integration

| Plugin | Description |
|--------|-------------|
| [evaluate-agent](./evaluate-agent/) | Evaluate AI agents with tool call validation, correctness, and task completion |
| [evaluate-koog](./evaluate-koog/) | Evaluate Koog AI agents with Dokimos |
| [evaluate-langchain4j](./evaluate-langchain4j/) | Evaluate LangChain4j apps and RAG pipelines |
| [evaluate-spring-ai](./evaluate-spring-ai/) | Evaluate Spring AI applications |
| [evaluate-spring-ai-alibaba](./evaluate-spring-ai-alibaba/) | Evaluate Spring AI Alibaba graph agents |
| [evaluate-embabel](./evaluate-embabel/) | Evaluate Embabel agents (Java 21+) |

## Installation

### Step 1: Add the Dokimos Marketplace

```
# Add the Dokimos marketplace
/plugin marketplace add dokimos-dev/dokimos

# List available plugins
/plugin list
```

### Step 2: Install a Plugin

```
/plugin install create-evaluator@dokimos
```

Or install via CLI:

```bash
claude plugin install create-evaluator@dokimos
```

### Step 3 (Optional): Enable for Your Team

Add to your project's `.claude/settings.json` to auto-enable for all team members:

```json
{
  "extraKnownMarketplaces": {
    "dokimos": {
      "source": {
        "source": "github",
        "repo": "dokimos-dev/dokimos"
      }
    }
  },
  "enabledPlugins": {
    "create-evaluator@dokimos": true,
    "create-tests@dokimos": true
  }
}
```

## Plugin Structure

```
.claude-plugin/
└── marketplace.json                           ← Marketplace catalog
plugins/
├── README.md                                  ← Plugin directory overview
├── create-evaluator/
│   ├── .claude-plugin/
│   │   └── plugin.json                        ← Plugin manifest
│   ├── skills/
│   │   └── create-evaluator/
│   │       ├── SKILL.md                       ← Main skill file
│   │       ├── REFERENCE.md                   ← API reference
│   │       ├── PATTERNS.md                    ← Usage patterns
│   │       └── TROUBLESHOOTING.md             ← Debugging guide
│   └── README.md                              ← Plugin documentation
├── create-dataset/
│   ├── .claude-plugin/
│   │   └── plugin.json
│   ├── skills/
│   │   └── create-dataset/
│   │       └── SKILL.md
│   └── README.md
```

## Contributing

These plugins live in the Dokimos repository at `.claude-plugin/plugins/`.

To contribute improvements:

1. Edit files in `.claude-plugin/plugins/<plugin-name>/`
2. Test locally with `claude --plugin-dir .claude-plugin/plugins/<plugin-name>`
3. Submit a PR to the Dokimos repository