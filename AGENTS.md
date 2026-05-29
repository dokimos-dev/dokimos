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
├── dokimos-mcp-server/     # MCP server exposing evaluation tools over stdio to any MCP client
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


<claude-mem-context>
# Memory Context

# [dokimos] recent context, 2026-05-29 10:07am GMT+2

Legend: 🎯session 🔴bugfix 🟣feature 🔄refactor ✅change 🔵discovery ⚖️decision 🚨security_alert 🔐security_note
Format: ID TIME TYPE TITLE
Fetch details: get_observations([IDs]) | Search: mem-search skill

Stats: 50 obs (17,721t read) | 781,821t work | 98% savings

### May 27, 2026
3996 8:06a 🔵 plan-design-review design binary requires OpenAI API key — not configured for fkapsahili-dokimos
4010 9:49a 🔵 Velm now has an official React component: @velmhq/embed-react
4011 " ✅ Installed @velmhq/embed-react package in Dokimos docs project
4012 9:50a ✅ Removed Velm script-tag from docusaurus.config.ts in favor of React component approach
4014 " 🟣 Velm React component integration fully built and verified — migration from script tag complete
4013 " 🟣 Created Docusaurus swizzled Root.tsx mounting Velm React component globally
S1083 Configure Velm widget optional props for Dokimos brand — color, theme, title, greeting, and suggested prompts (May 27 at 9:51 AM)
4016 9:57a 🔵 Dokimos docs brand uses monochrome black/white color scheme
4017 " ✅ Velm widget configured with Dokimos-specific branding and starter prompts in Root.tsx
4018 " 🟣 Velm widget fully configured and production build verified for Dokimos docs
S1090 Dokimos eval-loop plan: full plan review cycle completed (eng review + design review) for closing the end-to-end eval-driven development loop (May 27 at 9:57 AM)
4047 10:02a 🔵 gstack design-shotgun skill works without OpenAI API key using HTML/CSS mode
S1087 Set Velm widget mode to "editorial" for the Dokimos docs site (May 27 at 10:02 AM)
4019 " ✅ Velm widget mode set to "editorial" in Dokimos docs Root.tsx
S1097 Shorten the Velm widget greeting text — user found the original greeting too long (May 27 at 10:02 AM)
4022 10:03a 🔵 Velm widget not present in server-rendered HTML — confirmed client-side only rendering
S1150 User approved shortened greeting and requested PR be opened for the Velm widget integration (May 27 at 10:03 AM)
S1089 Start dev server for local visual verification of the Velm widget integration (May 27 at 10:03 AM)
S1101 Dokimos eval-loop plan: generate rendered HTML/CSS design mockups for diff-view and dataset browser using /design-html without OpenAI API key, then approve a skin (May 27 at 10:04 AM)
4110 10:13a 🔵 Dokimos git repo has fork setup — origin is fkapsahili/dokimos, upstream is ghtjr410/dokimos
4050 10:36a 🟣 HTML/CSS design mockups generated for dokimos eval-loop UI surfaces
4051 " ⚖️ Three diff-view skin options produced for implementer selection
S1151 Opening PR for Velm widget integration — investigating git/repo setup before branching and committing (May 27 at 12:38 PM)
4119 4:33p ⚖️ T11 React diff UI implementation kicked off with subagent parallelism
S1152 Open PR for Velm chat widget integration — PR #77 successfully opened at dokimos-dev/dokimos (May 27 at 4:33 PM)
4111 4:34p 🔵 Upstream repo is dokimos-dev/dokimos — fkapsahili/dokimos is not a GitHub fork
4112 " ✅ Created branch docs-velm-widget and staged all Velm integration files
4113 " 🟣 Velm widget integration committed and pushed to origin as docs-velm-widget branch
4114 " 🔵 Cross-repo PR creation failed — fkapsahili/dokimos is not recognized as a fork of dokimos-dev/dokimos by GitHub
4115 " 🟣 PR #77 opened at dokimos-dev/dokimos for Velm chat widget integration
4122 4:37p 🔵 Code Review: RunComparison Engine Has Two P0 Bugs and Multiple P1 Issues
4123 4:56p 🔵 Second Review Subagent Run Returned Identical Cached Findings — Two P0 Bugs Confirmed
4131 5:40p 🔴 P0-B: McNemar continuity correction fixed for balanced discordant pairs
4132 " 🔴 P0-A: Per-item ComparisonStatus now significance-gated via two-pass approach
4133 " 🟣 New multi-run multi-item permutation integration tests added to RunComparisonTest
4134 " ✅ Java comparison backend fully verified: 432 tests green, Spotless clean, files untracked
S1160 Continue T11 implementation: Java comparison engine fixes complete, Codex review launched, T11 React UI pending (May 27 at 6:01 PM)
4135 6:01p 🔵 Codex review of RunComparison engine: four concrete findings
4136 " 🔵 RunComparison.java full source reviewed: aggregate() divides by observed items not run count
4137 6:03p 🔵 ItemResult.success() requires ALL evaluators to pass for an item to count as passing
4138 " 🔴 aggregate() now rejects duplicate item keys within a single run
4139 " 🔵 dokimos feat/eval-loop-run-comparison branch — core tests passing
4141 6:06p 🔴 F1: Pass-rate regression now a first-class gate signal in RunComparison
4142 " 🔴 F2: Aggregation denominator fixed and duplicate itemKey within a run now rejected
4143 " 🔴 F3 + F4: Builder validation added and pass-rate metrics made consistent across paired/unpaired items
4144 " 🟣 F5: New regression, aggregation, validation, and gate tests added to RunComparisonTest
4146 6:08p ✅ Run comparison engine committed locally on feat/eval-loop-run-comparison
4148 " 🔵 MCP server compare_runs tool uses naive delta comparison without statistical significance
4149 " 🔵 RunRecord.ItemDetail lacks itemKey field needed to wire RunComparison engine into MCP server
4150 6:12p 🔵 Conversion path from RunRecord to RunResult established for MCP-engine wiring
4152 " 🟣 handleCompareRuns upgraded to use RunComparison engine with statistical significance
4151 6:13p 🔵 ToolHandlersTest uses empty items lists in sampleRecord — tests will need refactoring for RunComparison wiring
4155 6:14p 🟣 MCP compare_runs tool wired to RunComparison statistical engine
4156 " 🔄 ToolHandlersTest.java: stub compare tests replaced with data-driven statistical tests
4157 " 🔵 mvn -pl MODULE test fails with NoClassDefFoundError when core is not rebuilt
4158 6:24p 🔵 Codex review of compare_runs delegation found three correctness gaps
4159 " 🔵 ToolHandlers.java final structure confirmed: 593 lines, 4 public handlers
### May 29, 2026
4180 10:07a 🟣 Server-Authoritative Dataset Backend (V5 Migration)
4181 " 🟣 ServerDatasetResolver with Offline Cache in dokimos-server-client
4182 " ⚖️ Phase 1a Review Scope: Concurrency, Cascade, and Test Quality

Access 782k tokens of past work via get_observations([IDs]) or mem-search skill.
</claude-mem-context>