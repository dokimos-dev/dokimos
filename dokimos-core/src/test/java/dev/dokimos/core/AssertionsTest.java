package dev.dokimos.core;

import static org.assertj.core.api.Assertions.*;

import dev.dokimos.core.gate.BaselineStore;
import dev.dokimos.core.gate.GateConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssertionsTest {

    @Test
    void shouldPassWhenEvaluatorSucceeds() {
        var testCase = EvalTestCase.builder()
                .input("What is 5+3?")
                .actualOutput("5+3 is equal to 8.")
                .build();

        Evaluator passingEvaluator = new Evaluator() {
            @Override
            public EvalResult evaluate(EvalTestCase tc) {
                return EvalResult.success("fake", 1.0, "The answer is mathematically correct.");
            }

            @Override
            public String name() {
                return "fake-success";
            }

            @Override
            public double threshold() {
                return 0.9;
            }
        };

        // Should not throw
        assertThatNoException().isThrownBy(() -> Assertions.assertEval(testCase, passingEvaluator));
    }

    @Test
    void shouldFailWithEvaluator() {
        var testCase = EvalTestCase.builder()
                .input("test input")
                .actualOutput("test output")
                .build();

        Evaluator failingEvaluator = new Evaluator() {
            @Override
            public EvalResult evaluate(EvalTestCase tc) {
                return EvalResult.failure("correctness", 0.3, "The output doesn't match");
            }

            @Override
            public String name() {
                return "correctness";
            }

            @Override
            public double threshold() {
                return 0.5;
            }
        };

        assertThatThrownBy(() -> Assertions.assertEval(testCase, failingEvaluator))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("correctness")
                .hasMessageMatching(".*0[\\.,]30.*")
                .hasMessageContaining("The output doesn't match");
    }

    @Test
    void shouldRunMultipleEvaluators() {
        var testCase =
                EvalTestCase.builder().input("test").actualOutput("output").build();

        var counter = new AtomicInteger(0);

        Evaluator first = new Evaluator() {
            @Override
            public EvalResult evaluate(EvalTestCase tc) {
                counter.incrementAndGet();
                return EvalResult.success("first", 0.8, "ok");
            }

            @Override
            public String name() {
                return "first";
            }

            @Override
            public double threshold() {
                return 0.5;
            }
        };

        Evaluator second = new Evaluator() {
            @Override
            public EvalResult evaluate(EvalTestCase tc) {
                counter.incrementAndGet();
                return EvalResult.success("second", 0.9, "ok");
            }

            @Override
            public String name() {
                return "second";
            }

            @Override
            public double threshold() {
                return 0.5;
            }
        };

        Assertions.assertEval(testCase, first, second);

        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    void shouldStopAtFirstFailure() {
        var testCase =
                EvalTestCase.builder().input("test").actualOutput("output").build();

        var secondCalled = new AtomicBoolean(false);

        Evaluator failing = new Evaluator() {
            @Override
            public EvalResult evaluate(EvalTestCase tc) {
                return EvalResult.failure("first", 0.2, "bad");
            }

            @Override
            public String name() {
                return "first";
            }

            @Override
            public double threshold() {
                return 0.5;
            }
        };

        Evaluator second = new Evaluator() {
            @Override
            public EvalResult evaluate(EvalTestCase tc) {
                secondCalled.set(true);
                return EvalResult.success("second", 0.9, "ok");
            }

            @Override
            public String name() {
                return "second";
            }

            @Override
            public double threshold() {
                return 0.5;
            }
        };

        assertThatThrownBy(() -> Assertions.assertEval(testCase, failing, second))
                .isInstanceOf(AssertionError.class);

        assertThat(secondCalled.get()).isFalse();
    }

    // The default-name guard fires before any filesystem or environment access, so calling the public
    // wrapper directly is hermetic here (unlike the path overloads, which reach the runner).

    @Test
    void assertNoRegressionRejectsTheDefaultUnnamedExperiment() {
        ExperimentResult unnamed = new ExperimentResult("unnamed", "", Map.of(), List.of());

        assertThatThrownBy(() -> Assertions.assertNoRegression(unnamed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unnamed")
                .hasMessageContaining("explicit");
    }

    @Test
    void assertNoRegressionWithConfigRejectsTheDefaultUnnamedExperiment() {
        ExperimentResult unnamed = new ExperimentResult("unnamed", "", Map.of(), List.of());

        assertThatThrownBy(() -> Assertions.assertNoRegression(unnamed, "unnamed", GateConfig.defaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unnamed");
    }

    @Test
    void assertNoRegressionRejectsABlankBaselineName() {
        ExperimentResult result = new ExperimentResult("rag-eval", "", Map.of(), List.of());

        assertThatThrownBy(() -> Assertions.assertNoRegression(result, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assertNoRegressionRejectsANameThatIsNotASingleSegment() {
        // A separator-bearing or absolute logical name would escape the baselines directory; it must be
        // rejected before any I/O (offer the Path overload instead). The guard fires regardless of the
        // candidate, so an empty result is enough.
        ExperimentResult result = new ExperimentResult("rag-eval", "", Map.of(), List.of());

        assertThatThrownBy(() -> Assertions.assertNoRegression(result, "../escape"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single filename segment");
        assertThatThrownBy(() -> Assertions.assertNoRegression(result, "a/b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single filename segment");
        assertThatThrownBy(() -> Assertions.assertNoRegression(result, "/tmp/x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single filename segment");
    }

    @Test
    void assertNoRegressionPathOverloadBypassesTheUnnamedGuard(@TempDir Path dir) throws Exception {
        // The candidate is "unnamed", which the logical-name overload rejects. Routed through the Path
        // overload the name guard must NOT apply — it reaches the gate. With a matching baseline already
        // committed, the gate compares and passes (no exception), proving the name guard was bypassed.
        // The test stays on the env-independent compare path; the bootstrap path's behavior differs in CI.
        EvalResult eval = new EvalResult("correctness", 1.0, 0.7, true, "", Map.of());
        ItemResult item = new ItemResult(Example.of("q", "a"), Map.of("output", "x"), List.of(eval));
        ExperimentResult unnamed =
                new ExperimentResult("unnamed", "", Map.of(), List.of(new RunResult(0, List.of(item))));
        Path baseline = dir.resolve("b.json");
        BaselineStore.write(baseline, unnamed, GateConfig.defaults());

        assertThatNoException().isThrownBy(() -> Assertions.assertNoRegression(unnamed, baseline));
    }
}
