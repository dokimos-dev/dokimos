# dokimos-mcp-server

MCP server that exposes the dokimos evaluation framework as tools for LLM agents. Connect it to Claude Desktop (or any MCP client) and run evaluations, compare runs, and inspect failures from a conversation.

## Prerequisites

- Java 17+
- `OPENAI_API_KEY` environment variable (required for `run_evaluation`)

## Build

From the repository root:

```bash
mvn package -pl dokimos-mcp-server -am -DskipTests
```

This produces a self-contained JAR at:

```
dokimos-mcp-server/target/dokimos-mcp-server-0.15.0-SNAPSHOT.jar
```

## Usage

### Claude Desktop

Add this to your Claude Desktop config (`~/Library/Application Support/Claude/claude_desktop_config.json` on macOS):

```json
{
  "mcpServers": {
    "dokimos": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/dokimos-mcp-server-0.15.0-SNAPSHOT.jar"],
      "env": {
        "OPENAI_API_KEY": "sk-..."
      }
    }
  }
}
```

### Claude Code

Add to your project's `.mcp.json`:

```json
{
  "mcpServers": {
    "dokimos": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/dokimos-mcp-server-0.15.0-SNAPSHOT.jar"],
      "env": {
        "OPENAI_API_KEY": "sk-..."
      }
    }
  }
}
```

### Standalone

```bash
export OPENAI_API_KEY='sk-...'
java -jar dokimos-mcp-server/target/dokimos-mcp-server-0.15.0-SNAPSHOT.jar
```

The server communicates over stdio (JSON-RPC). It is not meant to be run interactively.

## Tools

### run_evaluation

Run an evaluation against a dataset.

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `dataset_path` | string | yes | | Path to dataset file (.json, .csv, .jsonl) |
| `model` | string | no | gpt-4o-mini | OpenAI model name |
| `temperature` | number | no | 0.0 | Sampling temperature |
| `evaluator` | string | no | exact_match | `exact_match` or `llm_judge` |
| `criteria` | string | no | | Evaluation criteria (for `llm_judge`) |
| `threshold` | number | no | 0.7 | Pass/fail score threshold |
| `experiment_name` | string | no | mcp-evaluation | Name for the experiment |

Returns a run ID, pass rate, and per-evaluator average scores.

### list_experiments

List past evaluation runs.

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `limit` | integer | no | 20 | Max runs to return |
| `dataset_name` | string | no | | Filter by dataset name |

### compare_runs

Compare two runs side by side with metric deltas.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `run_id_a` | string | yes | Baseline run ID |
| `run_id_b` | string | yes | Comparison run ID |

Flags regressions when metrics decrease between runs.

### get_failing_queries

Inspect failing examples from a run.

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `run_id` | string | yes | | Run ID to inspect |
| `threshold` | number | no | 0.5 | Score threshold for failures |

Returns each failing query with its input, expected/actual output, and evaluator scores.

## Storage

Run results are persisted to `~/.dokimos/mcp-results.json`. This file is created automatically on first use.

## Dataset Format

Datasets follow the standard dokimos format. Minimal JSON example:

```json
{
  "name": "my-dataset",
  "description": "QA pairs for testing",
  "examples": [
    {"input": "What is 2+2?", "expectedOutput": "4"},
    {"input": "Capital of France?", "expectedOutput": "Paris"}
  ]
}
```

CSV and JSONL formats are also supported. See the [dokimos documentation](https://dokimos.dev) for details.

## Example Session

Once connected to an MCP client, you can run evaluations conversationally:

```
> Run an evaluation on /data/qa-pairs.json using gpt-4o with the llm_judge evaluator

[calls run_evaluation with dataset_path=/data/qa-pairs.json, model=gpt-4o, evaluator=llm_judge]

> Show me the failing queries from that run

[calls get_failing_queries with the run ID from the previous result]

> Now compare it with run abc123

[calls compare_runs with the two run IDs]
```
