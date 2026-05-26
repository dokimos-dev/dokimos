# AGENTS Instructions

## Project Overview

Dokimos is an LLM evaluation framework for Java and Kotlin. It provides tools for evaluating LLM application responses, tracking quality over time, catching regressions, and running evaluations as part of test suites and CI/CD pipelines.

Current version: **0.13.0** | License: MIT | Published to Maven Central under `dev.dokimos`.

## Project Structure

This is a **Maven multi-module** project:

```
dokimos/
├── dokimos-core/           # Core framework (evaluators, experiments, datasets)
├── dokimos-junit/          # JUnit 5/6 integration (@DatasetSource annotation)
├── dokimos-langchain4j/    # LangChain4j integration (RAG evaluators)
├── dokimos-spring-ai/      # Spring AI integration
├── dokimos-koog/           # Koog AI agent integration (Kotlin)
├── dokimos-kotlin/         # Kotlin DSL for experiment builders
├── dokimos-server-client/  # HTTP client for the experiment server
├── dokimos-mcp-server/     # MCP server exposing evaluation tools over stdio (Claude Desktop, etc.)
├── dokimos-server/         # REST API + React web UI (Spring Boot + PostgreSQL)
│   └── frontend/           # React + Vite + Tailwind CSS frontend
├── dokimos-examples/       # Runnable examples for all frameworks
└── docs/                   # Docusaurus documentation site
```

## Tech Stack

- **Java 17+** (minimum, tested on 17, 21, and 25)
- **Kotlin 2.3.10** (for `dokimos-kotlin` and `dokimos-koog`)
- **Maven 3.6+** (build tool)
- **Spring Boot 3.5.10** (server module)
- **JUnit 6** (testing framework, compatible with JUnit 5.10.3+)
- **Jackson** (JSON/CSV serialization)
- **PostgreSQL + Flyway** (server database and migrations)
- **React 19 + Vite + Tailwind CSS** (server frontend)

## Build & Test Commands

Use Make targets (preferred) or Maven directly:

```bash
# Build
make build              # Clean install, skip tests
make compile            # Compile all modules
mvn clean install       # Full build with tests

# Test
make test               # Unit tests only
make test-all           # Unit + integration tests
make verify             # Full clean verification
make test-module MODULE=dokimos-core  # Test a single module

# Integration tests (require OPENAI_API_KEY)
export OPENAI_API_KEY='your-key'
mvn verify -Dgroups=integration
```

## Coding Conventions

### Java

- Target **Java 17** — do not use features from later versions.
- Public APIs must have **Javadoc** comments explaining purpose, parameters, return values, and exceptions.
- Use the **builder pattern** for object construction (see `Experiment.builder()`, `EvalTestCase.builder()`).
- Use Java **records** for value types and prefer immutability.
- Use `List.copyOf()` / `Map.copyOf()` for defensive copying.
- Package structure: `dev.dokimos.<module>.<feature>` (e.g., `dev.dokimos.core.evaluators`).

### Kotlin

- Kotlin modules use **DSL-style lambdas** for builders (e.g., `experiment { ... }`).
- JVM target matches the Java version (17).
- Use **Dokka** for Kotlin documentation.
- Use **MockK** (not Mockito) for mocking in Kotlin tests.

### Testing

- **Framework**: JUnit 6 (Jupiter) with **AssertJ** for fluent assertions and **Mockito** for mocking.
- **Test file naming**: `*Test.java` / `*Test.kt`, mirroring the source structure.
- **Integration tests**: Must be annotated with `@Tag("integration")`. These are excluded from regular `mvn test` and require `mvn verify -Dgroups=integration`.
- Integration tests that call external APIs (e.g., OpenAI) require the `OPENAI_API_KEY` environment variable.
- Do not add integration test tags to tests that don't require external services.

## CI/CD

CI runs on GitHub Actions (`.github/workflows/ci.yml`):

- **Build matrix**: JDK 17, 21, 25 on Ubuntu
- **JUnit compatibility matrix**: Tests `dokimos-junit` against JUnit 5.10.3, 5.11.4, 5.14.1 (default build uses JUnit 6.0.1)
- **Integration tests**: Run after build, require `OPENAI_API_KEY` secret
- CI is triggered on pushes to `master` and pull requests targeting `master`
- Markdown files, docs, LICENSE, and .gitignore changes do not trigger CI

## Key Interfaces

When working on the core framework, understand these central types in `dokimos-core`:

- **`Evaluator`** — Evaluates a test case and produces an `EvalResult` (score + pass/fail).
- **`EvalTestCase`** — Input/expected/actual output for a single evaluation.
- **`Dataset`** — Collection of examples loaded from JSON or CSV.
- **`Experiment`** — Orchestrates task execution across a dataset with evaluators and reporters.
- **`Task`** — Functional interface for executing a single example (the LLM call under test).
- **`Reporter`** — Reports experiment results (local logging or server-based).
- **`JudgeLM`** — Functional interface for an LLM used as a judge in evaluations.

- **`ToolCall`** — Record representing a single tool invocation (name, arguments, result, metadata). In `dev.dokimos.core.agents`.
- **`ToolDefinition`** — Record describing a tool's contract (name, description, JSON schema). In `dev.dokimos.core.agents`.
- **`AgentTrace`** — Wraps a complete agent execution trace. Use `toOutputMap()` to produce the map format evaluators expect. In `dev.dokimos.core.agents`.
- **Agent evaluators** — Six evaluators in `dev.dokimos.core.evaluators.agents`: `ToolCallValidityEvaluator`, `ToolCorrectnessEvaluator`, `TaskCompletionEvaluator`, `ToolArgumentHallucinationEvaluator`, `ToolNameReliabilityEvaluator`, `ToolDescriptionReliabilityEvaluator`. Agent evaluators use custom `EvalTestCase` map keys (`"toolCalls"`, `"tools"`, `"tasks"`) and set `evaluationParams = List.of()` to skip standard key validation.

## Module-Specific Notes

### dokimos-server

- Requires PostgreSQL — use `docker compose up` in `dokimos-server/` for local dev.
- Database migrations managed by **Flyway** (see `src/main/resources/db/migration/`).
- The React frontend is built as part of the Maven build and served as static resources.
- Optional API key auth via `DOKIMOS_API_KEY` env var (read operations are always open).

### dokimos-server-client

- Uses async batching: background thread batches HTTP calls every 500ms or 10 items.
- Implements exponential backoff retries (3 attempts).

### dokimos-mcp-server

- Exposes four tools over MCP stdio: `run_evaluation`, `list_experiments`, `compare_runs`, `get_failing_queries`.
- Not published to Maven Central. Packaged as a shaded executable JAR via the shade plugin.
- **Stdout is reserved for the JSON-RPC stream.** All logging goes to stderr, and `logback.xml` registers a `NopStatusListener` so logback's own status output never reaches stdout (the shaded JAR loses logback's version metadata, which would otherwise trigger a status dump to stdout).
- `run_evaluation` requires `OPENAI_API_KEY`. Runs are persisted to `~/.dokimos/mcp-results.json` via `JsonResultStore`.

### dokimos-examples

- This module is not published to Maven Central.
- Contains runnable examples for all supported frameworks.
- Examples serve as living documentation and should be kept up to date.

### docs

- Uses **Docusaurus** (Node.js 20+).
- Preview locally: `cd docs && npm install && npm start` (runs on `http://localhost:3000`).

## Dependencies

- LangChain4j, Spring AI, and Koog are **provided-scope** dependencies — users bring their own version.
- All dependency versions are managed in the **parent POM** (`pom.xml` at repo root).
- Do not add dependencies to individual module POMs without declaring them in the parent's `<dependencyManagement>`.

## Security

- Never commit API keys or secrets. Use environment variables.
- The server supports optional API key authentication. Do not weaken or remove this without discussion.
- When adding new server endpoints, write operations (POST/PUT/PATCH/DELETE) must respect the existing auth filter.
