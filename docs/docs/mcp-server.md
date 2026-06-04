---
sidebar_position: 7
---

# MCP Server

Run Dokimos evaluations straight from a chat with your AI agent, no code and no build.

The Dokimos MCP server exposes the evaluation framework as tools for LLM agents. Connect it to any [Model Context Protocol](https://modelcontextprotocol.io) client (Claude Desktop, Claude Code, Cursor, and others). Then you can run evaluations, list past runs, compare runs, and inspect failures by asking in plain language.

## Run with Docker

The published image ships everything the server needs. You do not need a JDK or a local build. Add this block to your MCP client config:

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

Replace two values:

- `OPENAI_API_KEY`: your OpenAI key. The `run_evaluation` tool calls OpenAI and needs it.
- `/absolute/path/to/datasets`: the folder on your machine that holds your dataset files.

Three flags do the work:

- `-i` keeps stdin open. The server speaks JSON-RPC over stdin and stdout, so this flag is required.
- `-v dokimos-mcp:/home/dokimos/.dokimos` mounts a named volume that persists run results across restarts. This keeps `list_experiments` and `compare_runs` working.
- `-v /absolute/path/to/datasets:/data:ro` makes your dataset files visible inside the container, read-only. Inside the container they live under `/data`, so pass `dataset_path` as an in-container path, for example `/data/qa-pairs.json`.

Restart your MCP client after editing the config. The four Dokimos tools then show up in the client.

:::tip No Docker?
Build a self-contained JAR from source and run it with `java -jar`. See the [module README](https://github.com/dokimos-dev/dokimos/tree/master/dokimos-mcp-server).
:::

## Tools

The server provides four tools. Each one maps to one thing you ask for in chat.

### run_evaluation

Loads a dataset, calls the model for each example, evaluates the outputs, and returns summary metrics plus a run ID. Save the run ID. You pass it to the other tools.

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `dataset_path` | string | yes | | Path to the dataset file (`.json`, `.csv`, or `.jsonl`) |
| `model` | string | no | `gpt-5.5` | OpenAI model name |
| `temperature` | number | no | model default | Sampling temperature, 0.0 to 2.0. Omitted when unset, so the model uses its own default |
| `evaluator` | string | no | `exact_match` | `exact_match` or `llm_judge` |
| `criteria` | string | no | | Evaluation criteria. Used by the `llm_judge` evaluator |
| `threshold` | number | no | `0.7` | Score threshold for pass/fail |
| `experiment_name` | string | no | `mcp-evaluation` | Name for this experiment |

### list_experiments

Lists past evaluation runs with their run IDs, timestamps, dataset names, and summary metrics. Filter by dataset name when you only want one dataset's history.

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `limit` | integer | no | `20` | Maximum number of runs to return |
| `dataset_name` | string | no | | Filter to runs that used this dataset name |

### compare_runs

Compares two runs side by side. Reports metric deltas and flags regressions. Treat `run_id_a` as the baseline and `run_id_b` as the new run.

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `run_id_a` | string | yes | | First run ID (baseline) |
| `run_id_b` | string | yes | | Second run ID (comparison) |

### get_failing_queries

Returns the examples from a run whose evaluator scores fell below a threshold. Each result includes the input, expected output, actual output, and per-evaluator detail.

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `run_id` | string | yes | | Run ID to inspect |
| `threshold` | number | no | `0.5` | Score below which a query counts as failing |

## Storage

Runs persist to `~/.dokimos/mcp-results.json`. Inside the container that path is `/home/dokimos/.dokimos`. The named volume in the Docker config mounts there, so history survives restarts.

## Example session

Once connected, drive evaluations by asking. A typical flow:

```
> Run an evaluation on /data/qa-pairs.json using gpt-5.5 with the llm_judge evaluator
> Show me the failing queries from that run
> Now compare it with run abc123
```

The first message runs `run_evaluation` and returns a run ID. The second runs `get_failing_queries` on that run. The third runs `compare_runs` against an earlier run.
