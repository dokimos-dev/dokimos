package dev.dokimos.core.evaluators;

import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.Evaluator;
import dev.dokimos.core.OutputType;
import dev.dokimos.core.exceptions.DokimosTypeConversionException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Wraps a delegate evaluator so it sees selected fields of a structured task output instead of the
 * whole output.
 *
 * <p>Extractions fill the conventional slots of a derived {@link EvalTestCase} handed to the
 * delegate: {@link Builder#extracting(Function)} fills the {@code "output"} slot, the keyed
 * overload fills a named slot such as {@code "retrievalContext"}. The original inputs and metadata
 * pass through untouched, so delegates that require the input keep working. The delegate's
 * actual-output map contains only the declared extractions.
 *
 * <p>When the {@code "output"} expected slot has no {@code expecting(...)} projection, the
 * {@code "output"} extractor is also applied to the expected side, read as the actual output type.
 * This covers goldens that share the task output's shape, whether they arrive as the record itself
 * or as a map loaded from JSON or CSV. Declare {@code expecting(...)} when the golden has a
 * different shape. A golden that is entirely absent leaves the slot empty, so delegates that do
 * not read the expected side still run. A golden that is present but cannot be converted, or
 * whose conversion returns {@code null}, fails the evaluation result.
 *
 * <p>A missing actual output, a conversion failure, or an extractor that throws a
 * {@link RuntimeException} or returns {@code null} produces a failed {@link EvalResult} carrying the reason;
 * {@link #evaluate(EvalTestCase)} never throws for those projection failures. Exceptions thrown
 * by the delegate, including required-parameter validation failures, propagate unchanged.
 *
 * <p>Pair scalar fields (String, enums, numbers) with evaluators that compare string forms, such
 * as {@link ExactMatchEvaluator}. Pair a nested record or list field with
 * {@link StructuralMatchEvaluator} instead: string-form comparison of a structured value depends
 * on its {@code toString()} formatting and is not stable.
 *
 * <p>By default, emitted results keep the delegate's name. Call {@link Builder#name(String)} to
 * give projected fields distinct report names.
 *
 * <p>Example:
 *
 * <pre>{@code
 * record Output(String title, String summary, Category category) {}
 *
 * Evaluator category = TypedEvaluator.of(Output.class)
 *         .name("Category")
 *         .extracting(Output::category)
 *         .evaluateWith(ExactMatchEvaluator.builder().build());
 * }</pre>
 */
public final class TypedEvaluator implements Evaluator {

    private static final String DEFAULT_KEY = "output";

    private final String name;
    private final Evaluator delegate;
    private final Function<EvalTestCase, Object> actualReader;
    private final Function<EvalTestCase, Object> expectedAsActualReader;
    private final String actualTypeName;
    private final Map<String, Function<Object, Object>> extractions;
    private final ExpectedProjection expectedProjection;

    private TypedEvaluator(Builder<?> builder, Evaluator delegate) {
        this.name = builder.name != null ? builder.name : delegate.name();
        this.delegate = delegate;
        this.actualReader = builder.actualReader;
        this.expectedAsActualReader = builder.expectedAsActualReader;
        this.actualTypeName = builder.actualTypeName;
        this.extractions = Map.copyOf(builder.extractions);
        this.expectedProjection = builder.expectedProjection;
    }

    /**
     * Starts a builder for task outputs convertible to the given class. Conversion uses
     * {@link EvalTestCase#actualOutputAs(Class)}, so it works whether the runtime value is the
     * record itself or a map loaded from JSON or CSV.
     *
     * @param actualType the type the actual output is converted to before extraction
     * @param <A> the actual output type
     * @return a new builder
     * @throws NullPointerException if actualType is null
     */
    public static <A> Builder<A> of(Class<A> actualType) {
        Objects.requireNonNull(actualType, "actualType must not be null");
        return new Builder<>(
                testCase -> testCase.actualOutputAs(actualType),
                testCase -> testCase.expectedOutputAs(actualType),
                actualType.getName());
    }

    /**
     * Starts a builder for task outputs of a generic type such as {@code List<Movie>}, which a
     * plain {@code Class} cannot express.
     *
     * @param actualType the generic type token for the actual output
     * @param <A> the actual output type
     * @return a new builder
     * @throws NullPointerException if actualType is null
     */
    public static <A> Builder<A> of(OutputType<A> actualType) {
        Objects.requireNonNull(actualType, "actualType must not be null");
        return new Builder<>(
                testCase -> testCase.actualOutputAs(actualType),
                testCase -> testCase.expectedOutputAs(actualType),
                actualType.toString());
    }

    @Override
    public EvalResult evaluate(EvalTestCase testCase) {
        Object actual;
        try {
            actual = actualReader.apply(testCase);
        } catch (DokimosTypeConversionException e) {
            return failure(
                    "The actual output could not be converted to %s: %s".formatted(actualTypeName, causeMessage(e)));
        }
        if (actual == null) {
            return failure("The actual outputs contain no '%s' entry to extract from.".formatted(DEFAULT_KEY));
        }

        EvalTestCase.Builder projected =
                EvalTestCase.builder().inputs(testCase.inputs()).metadata(testCase.metadata());

        for (Map.Entry<String, Function<Object, Object>> extraction : extractions.entrySet()) {
            String key = extraction.getKey();
            Object value;
            try {
                value = extraction.getValue().apply(actual);
            } catch (RuntimeException e) {
                return failure("The extractor for actual slot '%s' threw: %s".formatted(key, e));
            }
            if (value == null) {
                return failure("The extractor for actual slot '%s' returned null.".formatted(key));
            }
            projected.actualOutput(key, value);
        }

        if (expectedProjection != null) {
            Object converted;
            try {
                converted = expectedProjection.reader().apply(testCase);
            } catch (DokimosTypeConversionException e) {
                return failure("The expected output for slot '%s' could not be converted to %s: %s"
                        .formatted(DEFAULT_KEY, expectedProjection.typeName(), causeMessage(e)));
            }
            if (converted == null && testCase.expectedOutputs().containsKey(DEFAULT_KEY)) {
                return failure("The expected output for slot '%s' converted to null as %s."
                        .formatted(DEFAULT_KEY, expectedProjection.typeName()));
            }
            if (converted != null) {
                EvalResult failed = fillExpectedSlot(DEFAULT_KEY, expectedProjection.getter(), converted, projected);
                if (failed != null) {
                    return failed;
                }
            }
        }

        if (expectedProjection == null && extractions.containsKey(DEFAULT_KEY)) {
            Object converted = null;
            try {
                converted = expectedAsActualReader.apply(testCase);
            } catch (DokimosTypeConversionException e) {
                if (testCase.expectedOutputs().containsKey(DEFAULT_KEY)) {
                    return failure(
                            "The expected output could not be converted to %s: %s. Declare expecting(...) if the golden has a different shape."
                                    .formatted(actualTypeName, causeMessage(e)));
                }
            }
            if (converted == null && testCase.expectedOutputs().containsKey(DEFAULT_KEY)) {
                return failure(
                        "The expected output converted to null as %s. Declare expecting(...) if the golden has a different shape."
                                .formatted(actualTypeName));
            }
            if (converted != null) {
                EvalResult failed = fillExpectedSlot(DEFAULT_KEY, extractions.get(DEFAULT_KEY), converted, projected);
                if (failed != null) {
                    return failed;
                }
            }
        }

        EvalResult result = delegate.evaluate(projected.build());
        return new EvalResult(
                name(), result.score(), result.threshold(), result.success(), result.reason(), result.metadata());
    }

    private EvalResult fillExpectedSlot(
            String key, Function<Object, Object> getter, Object converted, EvalTestCase.Builder projected) {
        Object value;
        try {
            value = getter.apply(converted);
        } catch (RuntimeException e) {
            return failure("The extractor for expected slot '%s' threw: %s".formatted(key, e));
        }
        if (value == null) {
            return failure("The extractor for expected slot '%s' returned null.".formatted(key));
        }
        projected.expectedOutput(key, value);
        return null;
    }

    private EvalResult failure(String reason) {
        return new EvalResult(name(), 0.0, delegate.threshold(), false, reason, Map.of());
    }

    private static String causeMessage(DokimosTypeConversionException e) {
        Throwable cause = e.getCause();
        return cause != null && cause.getMessage() != null ? cause.getMessage() : e.getMessage();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public double threshold() {
        return delegate.threshold();
    }

    private record ExpectedProjection(
            Function<EvalTestCase, Object> reader, Function<Object, Object> getter, String typeName) {}

    /**
     * Builder for {@link TypedEvaluator}. Methods may be called in any order;
     * {@link #evaluateWith(Evaluator)} finishes the builder and returns the evaluator.
     *
     * @param <A> the actual output type
     */
    public static final class Builder<A> {

        private final Function<EvalTestCase, Object> actualReader;
        private final Function<EvalTestCase, Object> expectedAsActualReader;
        private final String actualTypeName;
        private String name;
        private final Map<String, Function<Object, Object>> extractions = new LinkedHashMap<>();
        private ExpectedProjection expectedProjection;

        private Builder(
                Function<EvalTestCase, Object> actualReader,
                Function<EvalTestCase, Object> expectedAsActualReader,
                String actualTypeName) {
            this.actualReader = actualReader;
            this.expectedAsActualReader = expectedAsActualReader;
            this.actualTypeName = actualTypeName;
        }

        /**
         * Sets the name stamped on emitted results. If omitted, the delegate evaluator's name is
         * used.
         *
         * @param name the result name
         * @return this builder
         * @throws NullPointerException if name is null
         */
        public Builder<A> name(String name) {
            this.name = Objects.requireNonNull(name, "name must not be null");
            return this;
        }

        /**
         * Extracts the value the delegate sees as the primary actual output ({@code "output"}).
         * Unless an {@code expecting(...)} projection for the {@code "output"} slot is declared,
         * the same getter is applied to the expected side read as the actual type.
         *
         * @param getter reads the field from the converted actual output
         * @return this builder
         * @throws NullPointerException if getter is null
         * @throws IllegalStateException if the {@code "output"} slot is already configured
         */
        public Builder<A> extracting(Function<? super A, ?> getter) {
            return extracting(DEFAULT_KEY, getter);
        }

        /**
         * Extracts a value into the named actual-output slot, for delegates that read a custom
         * key such as {@code ContextualRelevanceEvaluator}'s {@code "retrievalContext"}. Slots
         * other than {@code "output"} are not mirrored to the expected side. Passing
         * {@code "output"} here is equivalent to {@link #extracting(Function)} and participates
         * in mirroring.
         *
         * @param key the actual-output slot to fill
         * @param getter reads the field from the converted actual output
         * @return this builder
         * @throws NullPointerException if key or getter is null
         * @throws IllegalStateException if the slot is already configured
         */
        @SuppressWarnings("unchecked")
        public Builder<A> extracting(String key, Function<? super A, ?> getter) {
            Objects.requireNonNull(key, "key must not be null");
            Objects.requireNonNull(getter, "getter must not be null");
            if (extractions.containsKey(key)) {
                throw new IllegalStateException("Actual slot '%s' is already configured.".formatted(key));
            }
            extractions.put(key, (Function<Object, Object>) getter);
            return this;
        }

        /**
         * Overrides how the expected {@code "output"} slot is read, for goldens whose shape
         * differs from the actual output type. The expected side is read with
         * {@link EvalTestCase#expectedOutputAs(Class)}.
         *
         * @param expectedType the type the stored expected output is converted to
         * @param getter reads the comparison value from the converted expected output
         * @param <E> the expected output type
         * @return this builder
         * @throws NullPointerException if expectedType or getter is null
         * @throws IllegalStateException if the expected {@code "output"} slot is already configured
         */
        public <E> Builder<A> expecting(Class<E> expectedType, Function<? super E, ?> getter) {
            Objects.requireNonNull(expectedType, "expectedType must not be null");
            return expecting(testCase -> testCase.expectedOutputAs(expectedType), expectedType.getName(), getter);
        }

        /**
         * Generic-type variant of {@link #expecting(Class, Function)}.
         *
         * @param expectedType the generic type token for the expected output
         * @param getter reads the comparison value from the converted expected output
         * @param <E> the expected output type
         * @return this builder
         * @throws NullPointerException if expectedType or getter is null
         * @throws IllegalStateException if the expected {@code "output"} slot is already configured
         */
        public <E> Builder<A> expecting(OutputType<E> expectedType, Function<? super E, ?> getter) {
            Objects.requireNonNull(expectedType, "expectedType must not be null");
            return expecting(testCase -> testCase.expectedOutputAs(expectedType), expectedType.toString(), getter);
        }

        @SuppressWarnings("unchecked")
        private <E> Builder<A> expecting(
                Function<EvalTestCase, Object> reader, String typeName, Function<? super E, ?> getter) {
            Objects.requireNonNull(getter, "getter must not be null");
            if (expectedProjection != null) {
                throw new IllegalStateException("Expected slot '%s' is already configured.".formatted(DEFAULT_KEY));
            }
            expectedProjection = new ExpectedProjection(reader, (Function<Object, Object>) getter, typeName);
            return this;
        }

        /**
         * Finishes the builder and returns the wrapping evaluator.
         *
         * @param delegate the evaluator that scores the extracted values
         * @return the wrapping evaluator
         * @throws NullPointerException if delegate is null
         * @throws IllegalStateException if no extracting call was made
         */
        public TypedEvaluator evaluateWith(Evaluator delegate) {
            Objects.requireNonNull(delegate, "delegate must not be null");
            if (extractions.isEmpty()) {
                throw new IllegalStateException(
                        "No extraction was declared. Call extracting(...) before evaluateWith(...).");
            }
            return new TypedEvaluator(this, delegate);
        }
    }
}
