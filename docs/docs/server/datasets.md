---
sidebar_position: 11
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Server datasets

Store your test data on the server once, version it, and point your tests at a specific version by URI. No more copying the same examples into every test.

Each run records the exact dataset version it used. That is what lets a regression gate compare like for like.

## How it works

A dataset is a named container. The data lives in **versions**.

- Versions are numbered from 1.
- A version is immutable once written.
- Adding examples never edits an existing version. It creates the next one.
- The alias `latest` always resolves to the highest version number.

Browse your datasets under **Datasets** in the web UI. The list shows each dataset's latest version and item count. Open one to see its versions and page through the items in a version.

## Create a dataset and add a version

Create an empty dataset, then add a version with its items.

```bash
# 1. Create an empty dataset
curl -X POST http://localhost:8080/api/v1/datasets \
  -H 'Content-Type: application/json' \
  -d '{ "name": "qa-regression", "description": "Customer support QA set" }'

# 2. Add version 1 with its items
curl -X POST http://localhost:8080/api/v1/datasets/qa-regression/versions \
  -H 'Content-Type: application/json' \
  -d '{
    "description": "Initial import",
    "items": [
      {
        "inputs":          { "question": "What is the capital of France?" },
        "expectedOutputs": { "answer": "Paris" },
        "metadata":        { "category": "geography" }
      }
    ]
  }'
```

Each item needs `inputs`. The `expectedOutputs` and `metadata` fields are optional.

## All dataset endpoints

| Method | Path | What it does |
|--------|------|--------------|
| `POST` | `/api/v1/datasets` | Create an empty dataset |
| `POST` | `/api/v1/datasets/{name}/versions` | Add a new version with its items |
| `GET` | `/api/v1/datasets` | List datasets with their latest version |
| `GET` | `/api/v1/datasets/{name}` | One dataset with all its versions |
| `GET` | `/api/v1/datasets/{name}/versions/{version}` | One version (`latest` or a number) |
| `GET` | `/api/v1/datasets/{name}/versions/{version}/items` | Page through a version's items |
| `DELETE` | `/api/v1/datasets/{name}` | Delete a dataset and all its versions |

Write operations need an EDITOR role when authentication is on. See [Authentication](./authentication).

To grow a dataset from real run results instead of hand-writing items, see [Review and curation](./curation).

## Point your tests at a server dataset

Add the `dokimos-server-client` dependency to your test classpath.

```xml
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-server-client</artifactId>
    <version>${dokimos.version}</version>
    <scope>test</scope>
</dependency>
```

The dependency registers a resolver for `dataset://` URIs. Anywhere Dokimos resolves a dataset (the registry, or the JUnit `@DatasetSource` annotation) can now point at the server.

### Resolve a dataset in code

Call the registry with a `dataset://` URI.

<Tabs groupId="language">
  <TabItem value="java" label="Java" default>

```java
import dev.dokimos.core.Dataset;
import dev.dokimos.core.DatasetResolverRegistry;

Dataset dataset = DatasetResolverRegistry.getInstance()
    .resolve("dataset://qa-regression@3");
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.core.Dataset
import dev.dokimos.core.DatasetResolverRegistry

val dataset: Dataset = DatasetResolverRegistry.getInstance()
    .resolve("dataset://qa-regression@3")
```

  </TabItem>
</Tabs>

### Resolve a dataset in a JUnit test

Use `@DatasetSource` on a parameterized test. Pin to `@latest` to always pull the newest version.

```java
@ParameterizedTest
@DatasetSource("dataset://qa-regression@latest")
void evaluatesAnswers(Example example) {
    String answer = aiService.generate(example.input());
    Assertions.assertEval(example.toTestCase(answer), evaluators);
}
```

### URI format

The URI is `dataset://<name>@<version>`. The version is a positive integer or `latest`.

The version is required. A pinned test always states the exact data it ran against.

## Configure the server connection

The resolver reads two environment variables.

| Variable | What it is |
|----------|------------|
| `DOKIMOS_SERVER_URL` | Base URL of the server to fetch from |
| `DOKIMOS_API_KEY` | Bearer key, when the server requires one |

When `DOKIMOS_SERVER_URL` is unset, the resolver stays inert and resolves nothing. The same test then runs offline against file-based datasets. You do not need to configure the server to run your tests locally.

## Offline cache

Resolved datasets are cached at `~/.dokimos/datasets-cache/<name>@<version>/items.json`.

- A **pinned** version is fetched network-first and falls back to its cached copy when the server is briefly unreachable. A transient outage does not break a CI run that already pulled that version once.
- The **`latest`** alias is always fetched fresh. Once it resolves to a concrete version, that version is cached too.
- A 4xx response or a parse error is surfaced directly, not masked by the cache. Those are not transient.

## Next steps

- [Review and curation](./curation): turn real run failures into new dataset versions
- [CI regression gate](./ci-gate): fail a build when a run regresses against a dataset version
