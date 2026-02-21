---
name: create-dataset
description: Creates evaluation datasets for Dokimos in JSON, CSV, or JSONL format. Use this skill whenever the user wants to create, generate, or build a dataset for LLM evaluation, test data for experiments, or example files for evaluators. Also triggers when the user asks about dataset formats or wants to convert data into Dokimos-compatible formats.
---

# Create Dataset

Create an evaluation dataset for Dokimos. The user will describe the dataset purpose and content via `$ARGUMENTS`.

## Where things live

- **Dataset class**: `dokimos-core/src/main/java/dev/dokimos/core/Dataset.java`
- **Parser**: `dokimos-core/src/main/java/dev/dokimos/core/DatasetParser.java`
- **Example class**: `dokimos-core/src/main/java/dev/dokimos/core/Example.java`
- **Example datasets**: `dokimos-examples/src/main/resources/datasets/`

Before creating a dataset, read `Dataset.java` and `DatasetParser.java` to understand supported formats.

## Supported formats

### JSON

The standard format. Structure:

```json
{
  "name": "Dataset Name",
  "description": "What this dataset evaluates",
  "examples": [
    {
      "input": "What is 2+2?",
      "expectedOutput": "4"
    },
    {
      "inputs": { "input": "complex query", "context": "additional info" },
      "expectedOutputs": { "output": "expected answer" },
      "metadata": { "category": "math", "difficulty": "easy" }
    }
  ]
}
```

Two forms are supported:
- **Simple**: `input` and `expectedOutput` as top-level strings
- **Structured**: `inputs`, `expectedOutputs`, and `metadata` as maps (for multi-field examples)

### CSV

Headers must include `input`. Optional: `expectedOutput` (or `expected_output` or `output`). Extra columns become metadata.

```csv
input,expectedOutput,category
What is 2+2?,4,math
Capital of France?,Paris,geography
```

### JSONL

One JSON object per line. Same structure as JSON examples but without the wrapper:

```jsonl
{"input": "What is 2+2?", "expectedOutput": "4"}
{"input": "Capital of France?", "expectedOutput": "Paris"}
```

## Steps

1. Ask the user what format they prefer (JSON is the default) if not specified
2. Determine the dataset's purpose, name, and description from `$ARGUMENTS`
3. Create realistic, diverse examples that cover:
   - Happy path cases
   - Edge cases (empty input, long input, special characters)
   - Different categories/difficulty levels if applicable
4. Place the dataset file in the appropriate location:
   - For test resources: `src/test/resources/datasets/`
   - For example resources: `dokimos-examples/src/main/resources/datasets/`
   - Or wherever the user specifies
5. Show the user how to load it:

```java
// From file
Dataset dataset = Dataset.fromJson(Path.of("path/to/dataset.json"));
Dataset dataset = Dataset.fromCsv(Path.of("path/to/dataset.csv"));
Dataset dataset = Dataset.fromJsonl(Path.of("path/to/dataset.jsonl"));

// From classpath (in tests)
// Use @DatasetSource("classpath:datasets/my-dataset.json") with dokimos-junit
```

## Guidelines

- Include at least 5 examples for a useful dataset; 10+ is better for meaningful evaluation
- Mix easy and hard cases — evaluations are only useful if some cases can fail
- Use realistic data that matches what the system under test will actually encounter
- Add metadata fields for slicing results (category, difficulty, source, etc.)
- For RAG datasets, include context fields in the structured format
