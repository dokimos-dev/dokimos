---
sidebar_position: 8
title: Regression gate (server-free)
---

# Regression gate (server-free)

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';
import ThemedImage from '@theme/ThemedImage';

Run your evals as a test and fail the build when quality drops. You commit a baseline next to your test, and on every run the gate compares the fresh result against it and throws on a real regression. No server, no account, no API key for the gate itself. The failing test is the gate, and it fires the same way locally and in CI.

This is eval-driven development: a quality change shows up as a red build on the PR that caused it, the same place a broken unit test does.

![The eval gate as a JUnit test: a clean run passes, a quality drop fails with the regressed cases, then re-running with the update flag re-baselines](/img/regression-gate-terminal.svg)

## Quickstart

Build an experiment, run it, and assert it has not regressed against a named baseline.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.*;
import dev.dokimos.core.evaluators.ExactMatchEvaluator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegressionGateTest {

    @Test
    void noRegression() {
        Dataset dataset = Dataset.builder()
            .name("QA")
            .addExample(Example.of("What is 2+2?", "4"))
            .addExample(Example.of("Capital of France?", "Paris"))
            .build();

        Task task = example -> Map.of("output", myBot.answer(example.input()));

        Evaluator exactMatch = ExactMatchEvaluator.builder()
            .name("Exact Match")
            .threshold(1.0)
            .build();

        ExperimentResult result = Experiment.builder()
            .name("rag")              // resolves the baseline file name
            .dataset(dataset)
            .task(task)
            .evaluators(List.of(exactMatch))
            .build()
            .run();

        // The gate. Throws on a regression; the baseline is src/test/resources/dokimos/baselines/rag.json
        Assertions.assertNoRegression(result, "rag");
    }
}
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.kotlin.core.assertNoRegression
import dev.dokimos.kotlin.dsl.dataset
import dev.dokimos.kotlin.dsl.evaluators
import dev.dokimos.kotlin.dsl.exactMatch
import dev.dokimos.kotlin.dsl.experiment
import dev.dokimos.kotlin.dsl.task
import org.junit.jupiter.api.Test

class RegressionGateTest {

    @Test
    fun noRegression() {
        experiment {
            name = "rag"
            dataset {
                name = "QA"
                example { input = "What is 2+2?"; expected = "4" }
                example { input = "Capital of France?"; expected = "Paris" }
            }
            task { example -> mapOf("output" to myBot.answer(example.input())) }
            evaluators { exactMatch { name = "Exact Match"; threshold = 1.0 } }
        }.run().assertNoRegression("rag")
    }
}
```

  </TabItem>
</Tabs>

`assertNoRegression(result)` (no name) resolves the baseline from the experiment name; the explicit name above is the same thing spelled out. Both throw `IllegalArgumentException` if the experiment is unnamed, because two unnamed experiments would collide on one baseline file. To put the baseline somewhere else, pass a `Path` instead of a name.

:::note Working directory

The logical-name overload resolves `src/test/resources/dokimos/baselines/<name>.json` relative to the test JVM's working directory. Under Maven Surefire that is the module directory, so the path resolves correctly. If your runner starts the test JVM somewhere else, pass the `Path` overload (`assertNoRegression(result, Path)`) to make the location explicit.

:::

### First run scaffolds the baseline

There is no baseline yet, so the first **local** run writes one and passes:

```
Baseline created at .../src/test/resources/dokimos/baselines/rag.json. Commit it so the gate compares against it from now on.
```

The new file shows up in your `git status` and your PR diff. Review it and commit it like any other test fixture. From the next run on, the gate compares against it and stays green until quality actually changes.

A **CI** run with no committed baseline does not write one (the checkout is ephemeral, so the write would be lost); it reports `NO_BASELINE` and passes with a warning, measuring nothing. Create and commit the baseline locally first.

Prefer a red build until the baseline is reviewed? Set `bootstrapPasses(false)` and the first run still writes the file but fails once (`Review and commit it, then re-run.`), the strict approval-test stance where an unreviewed baseline never quietly becomes the source of truth. See [Configuration](#configuration).

## The baseline file

The baseline lives at `src/test/resources/dokimos/baselines/<name>.json`, committed to git alongside the test.

It is a stable projection of a run, not a dump of one. It records exactly what the comparison reads (a per-item key plus each evaluator's score, threshold, and pass/fail) and excludes model outputs, judge prose, and call metrics. The file changes only when measured quality changes, so a git diff shows the regression and nothing else.

```json
{
  "formatVersion" : 1,
  "experiment" : "rag",
  "dataset" : {
    "itemCount" : 2
  },
  "pairing" : "positional",
  "runsPerItem" : 1,
  "items" : [ {
    "key" : "item-0",
    "input" : "What is 2+2?",
    "evaluators" : [ {
      "name" : "Exact Match",
      "score" : 1.0,
      "threshold" : 1.0,
      "pass" : true
    } ]
  } ],
  "provenance" : { }
}
```

The `dataset` summary and the `provenance` block (Dokimos version and judge model/temperature, when known) are advisory; the comparison reads neither. They round out what a real committed file looks like.

### Re-baseline an intended change

When a change moves scores on purpose, accept it by regenerating the file. Re-run with the environment variable set, then commit the updated baseline:

```bash
DOKIMOS_UPDATE_BASELINE=true mvn test
```

The `-Ddokimos.updateBaseline=true` system property does the same thing, but the env var is the one to reach for. `-D` does not always reach the test JVM under Gradle or the IntelliJ runner. The FAIL message prints this exact command, so you never have to remember it.

## How the gate decides

The gate fails when either of two independent guards fires:

1. **Broad regression.** A significance test (McNemar for pass/fail, a paired permutation test with a bootstrap interval otherwise) flags a real aggregate pass-rate drop or a significantly regressed evaluator. This is what keeps a noisy judge from flaking your build: random per-item flapping does not clear the test.
2. **Localized-severe regression.** Any single item whose worst per-evaluator score drop exceeds `severityMargin` (default 0.15) fails the gate, even on a dataset too small for the significance test to react. This catches the one case that broke hard.

### Pin your judge

The gate is only as stable as the scores it compares. Deterministic evaluators like `ExactMatchEvaluator` are stable by construction, so they need no special care. For an LLM judge, pin two things so the baseline does not drift:

- **`temperature = 0`**: at temperature 0 a modern judge's per-item verdict is effectively fixed run to run, so an unchanged candidate reproduces the baseline.
- **A dated model snapshot** (e.g. a `-2025-..` id), not a floating alias. A floating alias silently swaps the model under you and moves the baseline for reasons that have nothing to do with your code.
- **A fixed evaluator set**: adding or removing an evaluator changes the population the significance test runs over, which shifts the other evaluators' p-values. Re-baseline after any evaluator-set change.

## Configuration

The defaults are tuned for an LLM-judge gate and need no configuration to start. To change them, build a `GateConfig` and pass it as the last argument to `assertNoRegression`.

<Tabs groupId="lang" defaultValue="java">
  <TabItem value="java" label="Java">

```java
import dev.dokimos.core.gate.GateConfig;

GateConfig config = GateConfig.builder()
    .severityMargin(0.10)                              // stricter single-item drop guard
    .pairing(GateConfig.Pairing.DATASET_ITEM_ID)       // pair strictly by id
    .bootstrapPasses(false)                            // fail once until the baseline is reviewed
    .build();

Assertions.assertNoRegression(result, "rag", config);
```

  </TabItem>
  <TabItem value="kotlin" label="Kotlin">

```kotlin
import dev.dokimos.core.gate.GateConfig

val config = GateConfig.builder()
    .severityMargin(0.10)
    .build()

result.assertNoRegression("rag", config)
```

  </TabItem>
</Tabs>

| Option | Default | What it controls |
| --- | --- | --- |
| `bootstrapPasses` | `true` | First local run with no baseline writes the file and passes. Set `false` to write it but fail once until you review and commit it (the strict approval-test stance). |
| `severityMargin` | `0.15` | Guard 2. Any single item whose worst per-evaluator score drops by more than this fails the gate, even on a dataset too small for the significance test to react. |
| `pairing` | `AUTO` | How baseline and candidate items are matched. `AUTO` pairs by `id` when every item carries one, else by position; `POSITIONAL` always pairs by position; `DATASET_ITEM_ID` always pairs by id and fails if any item lacks one. |
| `failOnRegression` | `true` | Whether a significant regression fails the gate. Set `false` to record the verdict without failing the build. |
| `failOnRemovedItems` | `false` | Whether an item present in the baseline but absent from the candidate fails the gate. |
| `onRemovedEvaluator` | `FAIL` | What happens when an evaluator in the baseline is missing from the candidate. `FAIL`, because a dropped evaluator is indistinguishable from hiding a regression; `WARN` to allow it. |
| `alpha` | `0.05` | Significance level for the McNemar and permutation tests. Lower is more conservative, so fewer changes are called regressions. |
| `seed` | `42` | RNG seed for the permutation and bootstrap tests, pinned so a verdict is reproducible run to run. |
| `permutationIterations` | `10000` | Permutation-test iteration count (guard 1, non-binary scores). |
| `bootstrapIterations` | `10000` | Bootstrap confidence-interval iteration count (guard 1). |
| `updateBaseline` | `false` | Overwrite the baseline from this run and pass. Usually set out of band with `DOKIMOS_UPDATE_BASELINE=true` (see [Re-baseline an intended change](#re-baseline-an-intended-change)) rather than in code. |

## Stable ids for evolving datasets

Without ids, the gate pairs baseline and candidate items by position, so inserting or reordering a row shifts every later item and blows up the diff.

Give each example a stable `id` and the gate pairs by id instead. Inserting, reordering, or removing rows keeps the diff scoped to the item that actually changed. A JSON or JSONL example carries a top-level `"id"`; a CSV adds an `id` column.

```json
{ "id": "qa-001", "input": "What is 2+2?", "expectedOutput": "4" }
```

```csv
id,input,expectedOutput
qa-001,What is 2+2?,4
```

## In CI

The loop is: run the gate test (it throws on a regression), then report the verdict, even when the build failed. Drop this PR-triggered job into your workflow:

```yaml
eval-gate:
  name: Eval Gate (server-free)
  runs-on: ubuntu-latest
  if: github.event_name == 'pull_request'
  permissions:
    contents: read
    pull-requests: write

  steps:
    - uses: actions/checkout@v4

    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: 'maven'

    # A real regression fails this step. The report step still runs (if: always()),
    # and the gate writes a per-baseline verdict file before throwing, so the verdict is always available.
    - name: Run eval gate
      run: mvn -B test -Dtest=RegressionGateTest

    - name: Report gate verdict
      if: always()
      uses: dokimos-dev/dokimos/.github/actions/eval-gate-report@v0
      with:
        verdict-dir: target/dokimos
```

`RegressionGateTest` and the single-module `mvn test` are placeholders. Point `-Dtest` at your own gate test and adjust the build for your module layout.

The `if: always()` on the report step is the load-bearing part. The gate writes a per-baseline verdict JSON under `target/dokimos` *before* it throws, so the report step posts the sticky PR comment after a failing build. Without `always()`, the one run you most want explained would post nothing. The action renders every verdict file in the directory, so one job can gate several baselines. The comment shows the pass-rate move and the regressed cases, and updates in place on each push instead of stacking up.

<div style={{maxWidth: '560px'}}>
  <ThemedImage
    alt="The eval gate's comment on a pull request: a failing run posts the pass-rate move, the significance flag, and the regressed cases"
    sources={{
      light: '/img/eval-gate-pr-comment-light.png',
      dark: '/img/eval-gate-pr-comment-dark.png',
    }}
  />
</div>

**Not on GitHub?** A failing `mvn test` is the gate on every runner: GitLab, Jenkins, Gradle, local. The verdict JSON lands under `target/dokimos`, one file per baseline (named for the baseline stem), if you want to render it yourself.

**Cost.** The candidate re-runs the eval on every push, so an LLM-judge gate costs tokens each time. Path-filter the workflow to PRs that touch datasets, prompts, model config, or the code under test. There is nothing to regress when only docs changed. Deterministic evaluators are free, so a gate built on those can run on every push.

## Server-based gate

Already running the Dokimos server? It offers the same gate as an HTTP endpoint that picks the baseline run for you and branches CI on a single `passed` boolean, with no committed baseline file to maintain. See [CI regression gate](../server/ci-gate.md). The server-free gate on this page is the right fit when you want the baseline in git and the gate to run as an ordinary test with no extra infrastructure.
