---
name: create-evaluator
description: Scaffolds a new Evaluator for the Dokimos LLM evaluation framework. Use this skill whenever the user wants to create, add, or implement a new evaluator, metric, or scoring function for evaluating LLM outputs. Also use when the user mentions adding evaluation criteria, custom metrics, or grading logic.
---

# Create Evaluator

Scaffold a new Evaluator implementation for Dokimos following project conventions.

The user will describe what the evaluator should do via `$ARGUMENTS`. Use that description to determine the evaluator's name, scoring logic, which test case parameters it requires, and what constitutes a passing score.

## Where things live

- **Evaluator source**: `dokimos-core/src/main/java/dev/dokimos/core/evaluators/`
- **Tests**: `dokimos-core/src/test/java/dev/dokimos/core/`
- **Base class**: `dev.dokimos.core.BaseEvaluator`
- **Key types**: `EvalResult`, `EvalTestCase`, `EvalTestCaseParam`, `JudgeLM`

Before writing code, read these files to understand the current conventions:
- `dokimos-core/src/main/java/dev/dokimos/core/BaseEvaluator.java`
- `dokimos-core/src/main/java/dev/dokimos/core/evaluators/ExactMatchEvaluator.java` (simple evaluator reference)
- `dokimos-core/src/main/java/dev/dokimos/core/evaluators/LLMJudgeEvaluator.java` (LLM-based evaluator reference, if the new evaluator needs a JudgeLM)

## Evaluator structure

Every evaluator follows this pattern:

1. **Extends `BaseEvaluator`** with a private constructor that takes a `Builder`
2. **Implements `runEvaluation(EvalTestCase testCase)`** — this is where the scoring logic goes
3. **Has a static `Builder` class** with sensible defaults for name, threshold, and evaluationParams
4. **Returns an `EvalResult`** via `EvalResult.builder().name(name).score(score).threshold(threshold).reason(reason).build()`

The `evaluationParams` field declares which `EvalTestCaseParam` values the evaluator requires (INPUT, ACTUAL_OUTPUT, EXPECTED_OUTPUT). `BaseEvaluator` validates these are non-null before calling `runEvaluation`.

If the evaluator needs an LLM to judge (semantic similarity, faithfulness, etc.), accept a `JudgeLM` in the builder. `JudgeLM` is a functional interface: `String generate(String prompt)`.

### Template

```java
package dev.dokimos.core.evaluators;

import dev.dokimos.core.BaseEvaluator;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.EvalTestCaseParam;

import java.util.List;

/**
 * [One-line description of what this evaluator checks.]
 */
public class NameEvaluator extends BaseEvaluator {

    // Add any evaluator-specific fields here (e.g., JudgeLM judge)

    private NameEvaluator(Builder builder) {
        super(builder.name, builder.threshold, builder.evaluationParams);
        // Initialize evaluator-specific fields from builder
    }

    /**
     * Creates a new builder for constructing [Name] evaluators.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected EvalResult runEvaluation(EvalTestCase testCase) {
        double score = 0.0;
        String reason = "...";

        return EvalResult.builder()
                .name(name)
                .score(score)
                .threshold(threshold)
                .reason(reason)
                .build();
    }

    public static class Builder {
        private String name = "Name";
        private double threshold = 1.0;
        private List<EvalTestCaseParam> evaluationParams = List.of(
                EvalTestCaseParam.ACTUAL_OUTPUT,
                EvalTestCaseParam.EXPECTED_OUTPUT
        );

        public Builder name(String name) { this.name = name; return this; }
        public Builder threshold(double threshold) { this.threshold = threshold; return this; }
        public Builder evaluationParams(List<EvalTestCaseParam> params) {
            this.evaluationParams = List.copyOf(params);
            return this;
        }
        // Add evaluator-specific builder methods

        public NameEvaluator build() { return new NameEvaluator(this); }
    }
}
```

## Test structure

Create a test class in `dokimos-core/src/test/java/dev/dokimos/core/`. Tests use JUnit 6 (Jupiter) and AssertJ. Name it `<EvaluatorName>Test.java` in the `dev.dokimos.core` package (not the `evaluators` subpackage — this matches existing convention).

Cover at minimum:
- A case where the evaluator gives a high/passing score
- A case where the evaluator gives a low/failing score
- A case with a missing required parameter (should throw `IllegalArgumentException`)
- A case verifying custom threshold behavior

If the evaluator uses a `JudgeLM`, mock it with Mockito in tests.

## Conventions checklist

- [ ] Class is in `dev.dokimos.core.evaluators` package
- [ ] Extends `BaseEvaluator`, not `Evaluator` directly
- [ ] Constructor is private, takes `Builder`
- [ ] Has `public static Builder builder()` method
- [ ] Builder uses `List.copyOf()` for defensive copying of evaluationParams
- [ ] All public methods and the class have Javadoc
- [ ] Score is between 0.0 and 1.0
- [ ] Test class is in `dev.dokimos.core` package (not `evaluators`)
- [ ] Tests use AssertJ assertions (`assertThat(...)`)
- [ ] No Java features beyond Java 17
