package dev.dokimos.core;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.node.NullNode;
import dev.dokimos.core.evaluators.ExactMatchEvaluator;
import dev.dokimos.core.evaluators.RegexEvaluator;
import dev.dokimos.core.evaluators.StructuralMatchEvaluator;
import dev.dokimos.core.evaluators.TypedEvaluator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class TypedEvaluatorTest {

    enum Category {
        SPORTS,
        TECH
    }

    record Output(String title, String summary, Category category, List<String> sources) {}

    record Golden(String headline, List<String> relevant) {}

    private static final class CapturingEvaluator implements Evaluator {
        private EvalTestCase captured;
        private final Function<EvalTestCase, EvalResult> evaluation;

        private CapturingEvaluator() {
            this(testCase -> EvalResult.success("Capturing", 1.0, "ok"));
        }

        private CapturingEvaluator(Function<EvalTestCase, EvalResult> evaluation) {
            this.evaluation = evaluation;
        }

        @Override
        public EvalResult evaluate(EvalTestCase testCase) {
            captured = testCase;
            return evaluation.apply(testCase);
        }

        @Override
        public String name() {
            return "Capturing";
        }

        @Override
        public double threshold() {
            return 0.5;
        }
    }

    private static final Output ACTUAL = new Output(
            "Cup final recap", "The home side won the cup final.", Category.SPORTS, List.of("doc_1", "doc_2"));

    private static final Map<String, Object> EXPECTED_AS_MAP = Map.of(
            "title", "Cup final recap",
            "summary", "The home side won the cup final.",
            "category", "SPORTS",
            "sources", List.of("doc_1", "doc_2"));

    @Test
    void shouldMatchScalarFieldWithExactMatch() {
        var evaluator = TypedEvaluator.of(Output.class)
                .name("Category")
                .extracting(Output::category)
                .evaluateWith(ExactMatchEvaluator.builder().build());

        var testCase = EvalTestCase.builder()
                .actualOutput("output", ACTUAL)
                .expectedOutput("output", ACTUAL)
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldConvertExpectedSideArrivingAsRawMap() {
        var evaluator = TypedEvaluator.of(Output.class)
                .name("Category")
                .extracting(Output::category)
                .evaluateWith(ExactMatchEvaluator.builder().build());

        var testCase = EvalTestCase.builder()
                .actualOutput("output", ACTUAL)
                .expectedOutput("output", EXPECTED_AS_MAP)
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldCompareNestedStructureWithStructuralMatch() {
        var evaluator = TypedEvaluator.of(Output.class)
                .name("Sources")
                .extracting(Output::sources)
                .evaluateWith(StructuralMatchEvaluator.builder().build());

        var testCase = EvalTestCase.builder()
                .actualOutput("output", ACTUAL)
                .expectedOutput("output", EXPECTED_AS_MAP)
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldFillNamedActualSlots() {
        var capturing = new CapturingEvaluator();

        var evaluator = TypedEvaluator.of(Output.class)
                .name("Source precision")
                .extracting("retrieved", Output::sources)
                .extracting(Output::title)
                .expecting(Golden.class, Golden::headline)
                .evaluateWith(capturing);

        var testCase = EvalTestCase.builder()
                .input("What happened in the cup final?")
                .actualOutput("output", ACTUAL)
                .expectedOutput("output", Map.of("headline", "Cup final recap", "relevant", List.of("doc_1", "doc_3")))
                .build();

        evaluator.evaluate(testCase);

        assertThat(capturing.captured.actualOutputs())
                .containsEntry("output", "Cup final recap")
                .containsEntry("retrieved", List.of("doc_1", "doc_2"));
        assertThat(capturing.captured.expectedOutputs()).containsEntry("output", "Cup final recap");
    }

    @Test
    void shouldProjectExpectedSideFromDifferentGoldenShape() {
        var evaluator = TypedEvaluator.of(Output.class)
                .name("Title")
                .extracting(Output::title)
                .expecting(Golden.class, Golden::headline)
                .evaluateWith(ExactMatchEvaluator.builder().build());

        var testCase = EvalTestCase.builder()
                .actualOutput("output", ACTUAL)
                .expectedOutput("output", Map.of("headline", "Cup final recap", "relevant", List.of()))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldPassInputAndMetadataThroughToDelegate() {
        var capturing = new CapturingEvaluator();

        var evaluator = TypedEvaluator.of(Output.class)
                .name("Title")
                .extracting(Output::title)
                .evaluateWith(capturing);

        var testCase = EvalTestCase.builder()
                .input("question")
                .metadata("run", "42")
                .actualOutput("output", ACTUAL)
                .build();

        evaluator.evaluate(testCase);

        assertThat(capturing.captured.inputs()).isEqualTo(Map.of("input", "question"));
        assertThat(capturing.captured.metadata()).isEqualTo(Map.of("run", "42"));
        assertThat(capturing.captured.actualOutputs()).isEqualTo(Map.of("output", "Cup final recap"));
    }

    @Test
    void shouldSnapshotConfigurationWhenBuilderIsReused() {
        var builder = TypedEvaluator.of(Output.class).name("Original").extracting(Output::title);
        var originalDelegate = new CapturingEvaluator();
        var original = builder.evaluateWith(originalDelegate);

        var updatedDelegate = new CapturingEvaluator();
        var updated = builder.name("Updated")
                .extracting("sources", Output::sources)
                .expecting(Output.class, Output::summary)
                .evaluateWith(updatedDelegate);
        var testCase = EvalTestCase.builder()
                .actualOutput("output", ACTUAL)
                .expectedOutput("output", EXPECTED_AS_MAP)
                .build();

        var updatedResult = updated.evaluate(testCase);
        var originalResult = original.evaluate(testCase);

        assertThat(originalResult.success()).isTrue();
        assertThat(originalResult.name()).isEqualTo("Original");
        assertThat(original.name()).isEqualTo("Original");
        assertThat(originalDelegate.captured.actualOutputs()).isEqualTo(Map.of("output", ACTUAL.title()));
        assertThat(originalDelegate.captured.expectedOutputs()).isEqualTo(Map.of("output", ACTUAL.title()));
        assertThat(updatedResult.success()).isTrue();
        assertThat(updatedResult.name()).isEqualTo("Updated");
        assertThat(updatedDelegate.captured.actualOutputs())
                .isEqualTo(Map.of("output", ACTUAL.title(), "sources", ACTUAL.sources()));
        assertThat(updatedDelegate.captured.expectedOutputs()).isEqualTo(Map.of("output", ACTUAL.summary()));
    }

    @Test
    void shouldLeaveExpectedSlotEmptyWhenExpectedEntryAbsent() {
        var evaluator = TypedEvaluator.of(Output.class)
                .name("Summary regex")
                .extracting(Output::summary)
                .evaluateWith(RegexEvaluator.builder().pattern("cup final").build());

        var testCase = EvalTestCase.builder().actualOutput("output", ACTUAL).build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldReturnFailedResultWhenExtractionYieldsNull() {
        var evaluator = TypedEvaluator.of(Output.class)
                .name("Title")
                .extracting(Output::title)
                .evaluateWith(ExactMatchEvaluator.builder().build());

        var withNullTitle = new Output(null, "s", Category.TECH, List.of());
        var testCase = EvalTestCase.builder()
                .actualOutput("output", withNullTitle)
                .expectedOutput("output", EXPECTED_AS_MAP)
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.success()).isFalse();
        assertThat(result.name()).isEqualTo("Title");
        assertThat(result.reason()).contains("returned null");
    }

    @Test
    void shouldFailWithoutInvokingDelegateWhenActualGetterThrows() {
        var delegate = new CapturingEvaluator();
        var evaluator = TypedEvaluator.of(Output.class)
                .name("Title")
                .extracting(output -> {
                    throw new IllegalStateException("actual getter failed");
                })
                .evaluateWith(delegate);

        var result = evaluator.evaluate(
                EvalTestCase.builder().actualOutput("output", ACTUAL).build());

        assertThat(result.success()).isFalse();
        assertThat(result.name()).isEqualTo("Title");
        assertThat(result.threshold()).isEqualTo(delegate.threshold());
        assertThat(result.reason())
                .contains("actual slot 'output' threw", "IllegalStateException", "actual getter failed");
        assertThat(delegate.captured).isNull();
    }

    @Test
    void shouldFailWithoutInvokingDelegateWhenMirroredExpectedGetterThrows() {
        var delegate = new CapturingEvaluator();
        var evaluator = TypedEvaluator.of(Output.class)
                .name("Title")
                .extracting(output -> {
                    if (output.category() == Category.TECH) {
                        throw new IllegalStateException("golden getter failed");
                    }
                    return output.title();
                })
                .evaluateWith(delegate);
        var testCase = EvalTestCase.builder()
                .actualOutput("output", ACTUAL)
                .expectedOutput("output", new Output("Golden", "summary", Category.TECH, List.of()))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.success()).isFalse();
        assertThat(result.name()).isEqualTo("Title");
        assertThat(result.threshold()).isEqualTo(delegate.threshold());
        assertThat(result.reason())
                .contains("expected slot 'output' threw", "IllegalStateException", "golden getter failed");
        assertThat(delegate.captured).isNull();
    }

    @Test
    void shouldFailWithoutInvokingDelegateWhenExplicitExpectedGetterThrows() {
        var delegate = new CapturingEvaluator();
        var evaluator = TypedEvaluator.of(Output.class)
                .name("Title")
                .extracting(Output::title)
                .expecting(Golden.class, golden -> {
                    throw new IllegalStateException("explicit getter failed");
                })
                .evaluateWith(delegate);
        var testCase = EvalTestCase.builder()
                .actualOutput("output", ACTUAL)
                .expectedOutput("output", new Golden("Golden", List.of()))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.success()).isFalse();
        assertThat(result.name()).isEqualTo("Title");
        assertThat(result.threshold()).isEqualTo(delegate.threshold());
        assertThat(result.reason())
                .contains("expected slot 'output' threw", "IllegalStateException", "explicit getter failed");
        assertThat(delegate.captured).isNull();
    }

    @Test
    void shouldFailWithoutInvokingDelegateWhenExplicitExpectedConversionThrows() {
        var delegate = new CapturingEvaluator();
        var evaluator = TypedEvaluator.of(Output.class)
                .name("Title")
                .extracting(Output::title)
                .expecting(Golden.class, Golden::headline)
                .evaluateWith(delegate);
        var testCase = EvalTestCase.builder()
                .actualOutput("output", ACTUAL)
                .expectedOutput("not a structured golden")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.success()).isFalse();
        assertThat(result.name()).isEqualTo("Title");
        assertThat(result.threshold()).isEqualTo(delegate.threshold());
        assertThat(result.reason())
                .contains("expected output for slot 'output' could not be converted", Golden.class.getName());
        assertThat(delegate.captured).isNull();
    }

    @Test
    void shouldNotMirrorOrConvertUnrelatedGoldenForNamedOnlyExtraction() {
        var delegate = new CapturingEvaluator();
        var evaluator = TypedEvaluator.of(Output.class)
                .extracting("retrieved", Output::sources)
                .evaluateWith(delegate);
        var testCase = EvalTestCase.builder()
                .actualOutput("output", ACTUAL)
                .expectedOutput("an unrelated scalar golden")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.success()).isTrue();
        assertThat(delegate.captured.actualOutputs()).isEqualTo(Map.of("retrieved", ACTUAL.sources()));
        assertThat(delegate.captured.expectedOutputs()).isEmpty();
    }

    @Test
    void shouldAcceptSupertypeAccessorsForActualAndExpected() {
        Function<CharSequence, Integer> length = CharSequence::length;
        var delegate = new CapturingEvaluator();
        var evaluator = TypedEvaluator.of(String.class)
                .extracting(length)
                .expecting(String.class, length)
                .evaluateWith(delegate);
        var testCase = EvalTestCase.builder()
                .actualOutput("actual")
                .expectedOutput("golden")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.success()).isTrue();
        assertThat(delegate.captured.actualOutputs()).isEqualTo(Map.of("output", 6));
        assertThat(delegate.captured.expectedOutputs()).isEqualTo(Map.of("output", 6));
    }

    @Test
    void shouldReturnFailedResultWhenActualConversionFails() {
        var evaluator = TypedEvaluator.of(Output.class)
                .name("Category")
                .extracting(Output::category)
                .evaluateWith(ExactMatchEvaluator.builder().build());

        var testCase = EvalTestCase.builder()
                .actualOutput("not a structured value")
                .expectedOutput("output", EXPECTED_AS_MAP)
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.success()).isFalse();
        assertThat(result.reason()).contains("actual output could not be converted");
    }

    @Test
    void shouldFailDirectlyWhenMirroredExpectedConversionFails() {
        var evaluator = TypedEvaluator.of(Output.class)
                .name("Category")
                .extracting(Output::category)
                .evaluateWith(ExactMatchEvaluator.builder().build());

        var testCase = EvalTestCase.builder()
                .actualOutput("output", ACTUAL)
                .expectedOutput("SPORTS")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.success()).isFalse();
        assertThat(result.reason())
                .contains("expected output could not be converted")
                .contains("expecting(...)");
    }

    @Test
    void shouldFailExpectedBlindDelegateWhenMirroredExpectedConversionFails() {
        var evaluator = TypedEvaluator.of(Output.class)
                .name("Summary regex")
                .extracting(Output::summary)
                .evaluateWith(RegexEvaluator.builder().pattern("cup final").build());

        var testCase = EvalTestCase.builder()
                .actualOutput("output", ACTUAL)
                .expectedOutput("a plain reference string")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.success()).isFalse();
        assertThat(result.reason()).contains("expected output could not be converted");
    }

    @Test
    void shouldFailWithoutInvokingDelegateWhenMirroredExpectedConversionReturnsNull() {
        var delegate = new CapturingEvaluator();
        var evaluator =
                TypedEvaluator.of(Output.class).extracting(Output::title).evaluateWith(delegate);
        var testCase = EvalTestCase.builder()
                .actualOutput("output", ACTUAL)
                .expectedOutput("output", NullNode.getInstance())
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.success()).isFalse();
        assertThat(result.reason())
                .contains("expected output converted to null")
                .contains(Output.class.getName());
        assertThat(delegate.captured).isNull();
    }

    @Test
    void shouldFailWithoutInvokingDelegateWhenExplicitExpectedConversionReturnsNull() {
        var delegate = new CapturingEvaluator();
        var evaluator = TypedEvaluator.of(Output.class)
                .extracting(Output::title)
                .expecting(Integer.class, Function.identity())
                .evaluateWith(delegate);
        var testCase = EvalTestCase.builder()
                .actualOutput("output", ACTUAL)
                .expectedOutput("output", "")
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.success()).isFalse();
        assertThat(result.reason())
                .contains("expected output for slot 'output' converted to null")
                .contains(Integer.class.getName());
        assertThat(delegate.captured).isNull();
    }

    @Test
    void shouldPropagateValidationFailureWhenGoldenAbsentAndDelegateRequiresIt() {
        var evaluator = TypedEvaluator.of(Output.class)
                .name("Title")
                .extracting(Output::title)
                .evaluateWith(ExactMatchEvaluator.builder().build());

        var testCase = EvalTestCase.builder().actualOutput("output", ACTUAL).build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EXPECTED_OUTPUT");
    }

    @Test
    void shouldPropagateDelegateIllegalArgumentExceptionUnchanged() {
        var failure = new IllegalArgumentException("Unsupported judge configuration");
        var delegate = new CapturingEvaluator(projected -> {
            assertThat(projected.actualOutput()).isEqualTo(ACTUAL.title());
            assertThat(projected.expectedOutput()).isEqualTo(ACTUAL.title());
            throw failure;
        });
        var evaluator = TypedEvaluator.of(Output.class)
                .name("Title")
                .extracting(Output::title)
                .evaluateWith(delegate);
        var testCase = EvalTestCase.builder()
                .actualOutput("output", ACTUAL)
                .expectedOutput("output", EXPECTED_AS_MAP)
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(testCase)).isSameAs(failure);
        assertThat(delegate.captured).isNotNull();
    }

    @Test
    void shouldPreserveDelegateResultFieldsWhenRenaming() {
        var delegateResult = new EvalResult("Delegate", 0.4, 0.7, false, "Partial match", Map.of("matched", 2));
        var delegate = new CapturingEvaluator(testCase -> delegateResult);
        var evaluator = TypedEvaluator.of(Output.class)
                .name("Title")
                .extracting(Output::title)
                .evaluateWith(delegate);

        var result = evaluator.evaluate(
                EvalTestCase.builder().actualOutput("output", ACTUAL).build());

        assertThat(result).isEqualTo(new EvalResult("Title", 0.4, 0.7, false, "Partial match", Map.of("matched", 2)));
    }

    @Test
    void shouldSupportGenericExpectedTypes() {
        var evaluator = TypedEvaluator.of(Output.class)
                .extracting(Output::title)
                .expecting(new OutputType<List<Golden>>() {}, goldens -> goldens.get(0)
                        .headline())
                .evaluateWith(ExactMatchEvaluator.builder().build());
        var testCase = EvalTestCase.builder()
                .actualOutput("output", ACTUAL)
                .expectedOutput("output", List.of(Map.of("headline", ACTUAL.title(), "relevant", List.of())))
                .build();

        assertThat(evaluator.evaluate(testCase).success()).isTrue();
    }

    @Test
    void shouldReturnFailedResultWhenActualOutputAbsent() {
        var evaluator = TypedEvaluator.of(Output.class)
                .name("Title")
                .extracting(Output::title)
                .evaluateWith(ExactMatchEvaluator.builder().build());

        var result = evaluator.evaluate(EvalTestCase.builder().build());

        assertThat(result.success()).isFalse();
        assertThat(result.reason()).contains("no 'output' entry");
    }

    @Test
    void shouldStampWrapperNameOnResult() {
        var evaluator = TypedEvaluator.of(Output.class)
                .name("Category")
                .extracting(Output::category)
                .evaluateWith(ExactMatchEvaluator.builder().build());

        var testCase = EvalTestCase.builder()
                .actualOutput("output", ACTUAL)
                .expectedOutput("output", ACTUAL)
                .build();

        assertThat(evaluator.evaluate(testCase).name()).isEqualTo("Category");
        assertThat(evaluator.name()).isEqualTo("Category");
    }

    @Test
    void shouldUseDelegateThreshold() {
        var evaluator = TypedEvaluator.of(Output.class)
                .name("Category")
                .extracting(Output::category)
                .evaluateWith(ExactMatchEvaluator.builder().threshold(0.7).build());

        assertThat(evaluator.threshold()).isEqualTo(0.7);
    }

    @Test
    void shouldCarryDelegateThresholdOnSyntheticFailures() {
        var evaluator = TypedEvaluator.of(Output.class)
                .name("Title")
                .extracting(Output::title)
                .evaluateWith(ExactMatchEvaluator.builder().threshold(0.7).build());

        var withNullTitle = new Output(null, "s", Category.TECH, List.of());
        var testCase = EvalTestCase.builder()
                .actualOutput("output", withNullTitle)
                .expectedOutput("output", EXPECTED_AS_MAP)
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.success()).isFalse();
        assertThat(result.threshold()).isEqualTo(0.7);
    }

    @Test
    void shouldDefaultToDelegateName() {
        var evaluator = TypedEvaluator.of(Output.class)
                .extracting(Output::category)
                .evaluateWith(ExactMatchEvaluator.builder().name("Category").build());

        assertThat(evaluator.name()).isEqualTo("Category");
    }

    @Test
    void shouldSupportGenericActualTypes() {
        var evaluator = TypedEvaluator.of(new OutputType<List<Output>>() {})
                .name("Titles")
                .extracting(outputs -> outputs.stream().map(Output::title).toList())
                .evaluateWith(StructuralMatchEvaluator.builder().build());

        var testCase = EvalTestCase.builder()
                .actualOutput("output", List.of(ACTUAL))
                .expectedOutput("output", List.of(EXPECTED_AS_MAP))
                .build();

        var result = evaluator.evaluate(testCase);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void shouldRejectEvaluateWithWithoutExtraction() {
        var builder = TypedEvaluator.of(Output.class).name("Category");
        var delegate = ExactMatchEvaluator.builder().build();

        assertThatThrownBy(() -> builder.evaluateWith(delegate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("extracting");
    }

    @Test
    void shouldRejectDuplicateSlots() {
        var builder = TypedEvaluator.of(Output.class).extracting(Output::title);

        assertThatThrownBy(() -> builder.extracting(Output::summary))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("output");

        var withExpecting =
                TypedEvaluator.of(Output.class).extracting(Output::title).expecting(Golden.class, Golden::headline);

        assertThatThrownBy(() -> withExpecting.expecting(Golden.class, Golden::headline))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("output");
    }

    @Test
    void shouldNotThrowWhenDelegateWouldSeeMalformedRowInExperiment() {
        var evaluator = TypedEvaluator.of(Output.class)
                .name("Title")
                .extracting(Output::title)
                .evaluateWith(ExactMatchEvaluator.builder().build());

        var malformed = EvalTestCase.builder()
                .actualOutput("[1, 2, 3]")
                .expectedOutput("output", EXPECTED_AS_MAP)
                .build();

        assertThatCode(() -> evaluator.evaluate(malformed)).doesNotThrowAnyException();
        assertThat(evaluator.evaluate(malformed).success()).isFalse();
    }
}
