package io.dokimos.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class AssertionsTest {

    @Test
    void shouldPassWhenEvaluatorSucceeds() {
        var testCase = EvalTestCase.builder()
                .input("What is 5+3?")
                .actualOutput("5+3 is equal to 8.")
                .build();

        Evaluator passingEvaluator = new Evaluator() {
            @Override
            public EvalResult evaluate(EvalTestCase testCase) {
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
        assertThatNoException().isThrownBy(() ->
                Assertions.assertEval(testCase, passingEvaluator)
        );
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
            public String name() { return "correctness"; }
            @Override
            public double threshold() { return 0.5; }
        };

        assertThatThrownBy(() -> Assertions.assertEval(testCase, failingEvaluator))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("correctness")
                .hasMessageContaining("0.30")
                .hasMessageContaining("The output doesn't match");
    }

    @Test
    void shouldRunMultipleEvaluators() {
        var testCase = EvalTestCase.builder()
                .input("test")
                .actualOutput("output")
                .build();

        var counter = new AtomicInteger(0);

        Evaluator first = new Evaluator() {
            @Override
            public EvalResult evaluate(EvalTestCase tc) {
                counter.incrementAndGet();
                return EvalResult.success("first", 0.8, "ok");
            }
            @Override
            public String name() { return "first"; }
            @Override
            public double threshold() { return 0.5; }
        };

        Evaluator second = new Evaluator() {
            @Override
            public EvalResult evaluate(EvalTestCase tc) {
                counter.incrementAndGet();
                return EvalResult.success("second", 0.9, "ok");
            }
            @Override
            public String name() { return "second"; }
            @Override
            public double threshold() { return 0.5; }
        };

        Assertions.assertEval(testCase, first, second);

        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    void shouldStopAtFirstFailure() {
        var testCase = EvalTestCase.builder()
                .input("test")
                .actualOutput("output")
                .build();

        var secondCalled = new AtomicBoolean(false);

        Evaluator failing = new Evaluator() {
            @Override
            public EvalResult evaluate(EvalTestCase tc) {
                return EvalResult.failure("first", 0.2, "bad");
            }
            @Override
            public String name() { return "first"; }
            @Override
            public double threshold() { return 0.5; }
        };

        Evaluator second = new Evaluator() {
            @Override
            public EvalResult evaluate(EvalTestCase tc) {
                secondCalled.set(true);
                return EvalResult.success("second", 0.9, "ok");
            }
            @Override
            public String name() { return "second"; }
            @Override
            public double threshold() { return 0.5; }
        };

        assertThatThrownBy(() -> Assertions.assertEval(testCase, failing, second))
                .isInstanceOf(AssertionError.class);

        assertThat(secondCalled.get()).isFalse();
    }

}
