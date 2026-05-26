---
sidebar_position: 7
---

# MCP Server

The Dokimos MCP server exposes the evaluation framework as tools for LLM agents. Connect it to any [Model Context Protocol](https://modelcontextprotocol.io) client (Claude Desktop, Claude Code, Cursor, and others) to run evaluations, compare runs, and inspect failures from a conversation.

## Run with Docker

The published image needs no JDK and no build. Add this to your MCP client config:

```json
{
  "mcpServers": {
    "dokimos": {
      "command": "docker",
      "args": [
        "run", "-i", "--rm",
        "-e", "OPENAI_API_KEY",
        "-v", "dokimos-mcp:/home/dokimos/.dokimos",
        "-v", "/absolute/path/to/datasets:/data:ro",
        "ghcr.io/dokimos-dev/dokimos-mcp-server:latest"
      ],
      "env": {
        "OPENAI_API_KEY": "sk-..."
      }
    }
  }
}
```

Two mounts matter:

- `dokimos-mcp:/home/dokimos/.dokimos` is a named volume that persists run results across restarts, so `list_experiments` and `compare_runs` keep working.
- `/absolute/path/to/datasets:/data:ro` makes your dataset files visible inside the container. Pass `dataset_path` as the in-container path, for example `/data/qa-pairs.json`.

The `-i` flag is required, since the server speaks JSON-RPC over stdin and stdout.

:::tip No Docker?
You can also build a self-contained JAR from source and run it with `java -jar`. See the [module README](https://github.com/dokimos-dev/dokimos/tree/master/dokimos-mcp-server).
:::

## Tools

### run_evaluation

Runs a dataset through a model and evaluator, then returns summary metrics and a run ID.

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `dataset_path` | string | yes | | Path to dataset file (`.json`, `.csv`, `.jsonl`) |
| `model` | string | no | `gpt-5.5` | OpenAI model name |
| `temperature` | number | no | `0.0` | Sampling temperature |
| `evaluator` | string | no | `exact_match` | `exact_match` or `llm_judge` |
| `criteria` | string | no | | Evaluation criteria (for `llm_judge`) |
| `threshold` | number | no | `0.7` | Pass/fail score threshold |
| `experiment_name` | string | no | `mcp-evaluation` | Name for the experiment |

### list_experiments

Lists past evaluation runs, with optional filtering by dataset name.

### compare_runs

Compares two runs side by side, reporting metric deltas and flagging regressions.

### get_failing_queries

Returns the examples from a run whose evaluator scores fell below a threshold, with input, expected and actual output, and per-evaluator detail.

## Storage

Runs persist to `~/.dokimos/mcp-results.json` (inside the container, `/home/dokimos/.dokimos`). Mount a volume there to keep history between runs.

## Example session

Once connected, you can drive evaluations conversationally:

```
> Run an evaluation on /data/qa-pairs.json using gpt-5.5 with the llm_judge evaluator
> Show me the failing queries from that run
> Now compare it with run abc123
```
