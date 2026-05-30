---
sidebar_position: 11
---

# Server datasets

The server can hold your datasets as the source of truth, versioned and shared, instead of every test embedding its own copy. You create a dataset once, add immutable versions to it over time, and reference a specific version from code by URI. This is what lets a regression gate compare like for like: a run records which dataset version it ran against.

## Datasets and versions

A dataset is a named container. Its data lives in **versions**, which are numbered from 1 and immutable once written. Adding examples never edits a version in place; it creates the next one. The alias `latest` always resolves to the highest version.

Browse datasets under **Datasets** in the web UI. The list shows each dataset's latest version and item count; opening one shows its versions and lets you page through the items in a version.

## Managing datasets through the API

```bash
# Create an empty dataset
curl -X POST http://localhost:8080/api/v1/datasets \
  -H 'Content-Type: application/json' \
  -d '{ "name": "qa-regression", "description": "Customer support QA set" }'

# Add a version with its items
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

`inputs` is required on each item; `expectedOutputs` and `metadata` are optional. Other endpoints:

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/v1/datasets` | List datasets with their latest version |
| `GET` | `/api/v1/datasets/{name}` | One dataset with all its versions |
| `GET` | `/api/v1/datasets/{name}/versions/{version}` | A version (`latest` or a number) |
| `GET` | `/api/v1/datasets/{name}/versions/{version}/items` | Page through a version's items |
| `DELETE` | `/api/v1/datasets/{name}` | Delete a dataset and its versions |

Writes require an EDITOR role when authentication is enabled; see [Authentication](./authentication). To grow a dataset from real run results instead of hand-writing items, see [Review and curation](./curation).

## Referencing a dataset from code

Add the `dokimos-server-client` dependency to your test classpath. It registers a resolver that handles `dataset://` URIs, so anywhere Dokimos resolves a dataset (the registry, or the JUnit `@DatasetSource` annotation) can point at the server:

```java
import dev.dokimos.core.DatasetResolverRegistry;

Dataset dataset = DatasetResolverRegistry.getInstance()
    .resolve("dataset://qa-regression@3");
```

```java
@DatasetSource("dataset://qa-regression@latest")
void evaluatesAnswers(Example example) { ... }
```

The URI is `dataset://<name>@<version>`, where the version is a positive integer or `latest`. The version is required, so a pinned test always states exactly which data it ran against.

The resolver reads two environment variables and stays inert (resolving nothing) when the server URL is unset, so the same test runs offline against file based datasets when the server is not configured:

| Variable | Purpose |
|----------|---------|
| `DOKIMOS_SERVER_URL` | Base URL of the server to fetch from |
| `DOKIMOS_API_KEY` | Bearer key, when the server requires one |

## Offline cache

Resolved datasets are cached under `~/.dokimos/datasets-cache/<name>@<version>/items.json`. A pinned version is fetched network first and falls back to its cached copy when the server is briefly unreachable, so a transient outage does not break a CI run that already pulled that version once. The `latest` alias is always fetched fresh, and once it resolves to a concrete version that version is cached too. A 4xx response or a parse error is surfaced directly rather than masked by the cache, since those are not transient.

## Next steps

- [Review and curation](./curation): turn real run failures into new dataset versions
- [CI regression gate](./ci-gate): fail a build when a run regresses against a dataset version
