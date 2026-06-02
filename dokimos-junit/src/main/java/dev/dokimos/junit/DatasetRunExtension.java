package dev.dokimos.junit;

import dev.dokimos.core.Dataset;
import dev.dokimos.core.EvalResult;
import dev.dokimos.core.Example;
import dev.dokimos.core.ItemResult;
import dev.dokimos.core.NoOpReporter;
import dev.dokimos.core.Reporter;
import dev.dokimos.core.RunHandle;
import dev.dokimos.core.RunStatus;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;

/**
 * Opens and reports a {@link Reporter} run for each {@link DatasetSource} test method.
 *
 * <p>
 * Reporting is opt-in. The extension looks for a static field of type {@link Reporter} annotated
 * with {@link DatasetReporter} on the test class. When none is found, {@link NoOpReporter} is used
 * and the test runs exactly as a plain parameterized test would.
 *
 * <p>
 * When a reporter is present, one run is opened per {@code @DatasetSource} method: the run starts
 * before the first invocation, each parameterized invocation is reported as an {@link ItemResult},
 * and the run is completed after the last invocation with {@link RunStatus#SUCCESS} when every
 * invocation passed or {@link RunStatus#FAILED} when at least one threw. The reporter is always
 * {@link Reporter#flush() flushed} after completion. The extension never closes a reporter supplied
 * via {@link DatasetReporter}; that lifecycle belongs to the test class.
 */
public class DatasetRunExtension
        implements BeforeEachCallback, AfterEachCallback, TestExecutionExceptionHandler, ParameterResolver {

    private static final Namespace NAMESPACE = Namespace.create(DatasetRunExtension.class);

    private static final String RUN_KEY = "run";
    private static final String EXAMPLE_KEY = "example";
    private static final String RECORDER_KEY = "recorder";

    /**
     * Matches the JUnit platform unique-id segment for a parameterized invocation,
     * {@code [test-template-invocation:#N]}, capturing the one-based invocation number.
     */
    private static final Pattern INVOCATION_SEGMENT = Pattern.compile("test-template-invocation:#(\\d+)");

    @Override
    public void beforeEach(ExtensionContext context) {
        RunState run = runState(context);
        if (run == null) {
            return;
        }

        Dataset dataset = context.getStore(DatasetArgumentsProvider.NAMESPACE)
                .get(DatasetArgumentsProvider.DATASET_KEY, Dataset.class);
        Example example = exampleAt(dataset, invocationIndex(context));
        context.getStore(NAMESPACE).put(EXAMPLE_KEY, example);
    }

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        RunState run = runState(context);
        if (run != null) {
            run.recordFailure();
        }
        throw throwable;
    }

    @Override
    public void afterEach(ExtensionContext context) {
        RunState run = runState(context);
        if (run == null) {
            return;
        }

        Example example = context.getStore(NAMESPACE).get(EXAMPLE_KEY, Example.class);
        if (example == null) {
            return;
        }

        DatasetItemRecorder recorder = context.getStore(NAMESPACE).get(RECORDER_KEY, DatasetItemRecorder.class);
        ItemResult result = recorder != null
                ? new ItemResult(example, recorder.actualOutputs(), recorder.evalResults())
                : new ItemResult(example, Map.of(), List.of());
        run.reporter().reportItem(run.handle(), result);
    }

    /**
     * Supports an optional {@link DatasetItemRecorder} test-method parameter. When the test body
     * declares it, {@link #afterEach} reports the outputs and eval results it captured; otherwise the
     * reported item stays empty.
     */
    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == DatasetItemRecorder.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        DatasetItemRecorder recorder = new DatasetItemRecorder();
        // The invocation-scoped store keeps one recorder per parameterized invocation.
        extensionContext.getStore(NAMESPACE).put(RECORDER_KEY, recorder);
        return recorder;
    }

    /**
     * Returns the run for the current test method, creating it on first access. Returns {@code null}
     * when no {@link DatasetReporter} field is present, signalling the no-op path.
     */
    private RunState runState(ExtensionContext context) {
        Store methodStore = methodStore(context);
        RunState existing = methodStore.get(RUN_KEY, RunState.class);
        if (existing != null) {
            return existing.active() ? existing : null;
        }

        Reporter reporter = findReporter(context);
        if (reporter == null) {
            methodStore.put(RUN_KEY, RunState.inactive());
            return null;
        }

        DatasetSource source = annotation(context);
        String experimentName = resolveExperimentName(source, context);
        Map<String, Object> metadata = resolveMetadata(source);
        RunHandle handle = reporter.startRun(experimentName, metadata);

        RunState run = new RunState(reporter, handle);
        methodStore.put(RUN_KEY, run);
        return run;
    }

    private Store methodStore(ExtensionContext context) {
        ExtensionContext methodContext = context.getParent().orElse(context);
        return methodContext.getStore(NAMESPACE);
    }

    private DatasetSource annotation(ExtensionContext context) {
        return context.getRequiredTestMethod().getAnnotation(DatasetSource.class);
    }

    private String resolveExperimentName(DatasetSource source, ExtensionContext context) {
        if (source != null && !source.experimentName().isBlank()) {
            return source.experimentName();
        }
        return context.getRequiredTestMethod().getName();
    }

    private Map<String, Object> resolveMetadata(DatasetSource source) {
        if (source == null) {
            return Map.of();
        }
        MetadataEntry[] entries = source.entries();
        if (entries.length > 0) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            for (MetadataEntry entry : entries) {
                metadata.put(entry.key(), entry.value());
            }
            return metadata;
        }
        String[] pairs = source.metadata();
        if (pairs.length == 0) {
            return Map.of();
        }
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "@DatasetSource metadata must contain an even number of key-value entries");
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            metadata.put(pairs[i], pairs[i + 1]);
        }
        return metadata;
    }

    /**
     * Returns the zero-based index of the example under test for the current invocation, derived from
     * the parameterized invocation's unique id rather than a separate counter. This keeps the reported
     * example tied to the actual argument even when invocations run out of order or in parallel.
     * Returns {@code -1} when no invocation segment is present (the dataset is then not reported).
     */
    private int invocationIndex(ExtensionContext context) {
        Matcher matcher = INVOCATION_SEGMENT.matcher(context.getUniqueId());
        int oneBased = -1;
        while (matcher.find()) {
            try {
                oneBased = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                // A pathologically large invocation number is not a usable index.
                oneBased = -1;
            }
        }
        // JUnit numbers invocations as #1, #2, ...; the dataset index is one less.
        return oneBased < 1 ? -1 : oneBased - 1;
    }

    private Example exampleAt(Dataset dataset, int index) {
        if (dataset == null) {
            return null;
        }
        List<Example> examples = dataset.examples();
        if (index < 0 || index >= examples.size()) {
            return null;
        }
        return examples.get(index);
    }

    private Reporter findReporter(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        for (Class<?> type = testClass; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!field.isAnnotationPresent(DatasetReporter.class)) {
                    continue;
                }
                if (!Modifier.isStatic(field.getModifiers())) {
                    throw new IllegalStateException("@DatasetReporter field '" + field.getName() + "' must be static");
                }
                if (!Reporter.class.isAssignableFrom(field.getType())) {
                    throw new IllegalStateException(
                            "@DatasetReporter field '" + field.getName() + "' must be of type Reporter");
                }
                field.setAccessible(true);
                try {
                    return (Reporter) field.get(null);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(
                            "Unable to read @DatasetReporter field '" + field.getName() + "'", e);
                }
            }
        }
        return null;
    }

    /**
     * Collects the actual outputs and evaluation results a {@link DatasetSource} test body produced
     * for one parameterized invocation. Declare it as a test-method parameter and populate it; the
     * extension then reports an {@link ItemResult} carrying these values instead of an empty one.
     *
     * <p>
     * A fresh recorder is supplied per invocation, so it need not be reset between examples.
     */
    public static final class DatasetItemRecorder {

        private final Map<String, Object> actualOutputs = new LinkedHashMap<>();
        private final List<EvalResult> evalResults = new ArrayList<>();

        /**
         * Records a single actual output under the given key.
         *
         * @param key the output key
         * @param value the output value
         * @return this recorder
         */
        public DatasetItemRecorder actualOutput(String key, Object value) {
            actualOutputs.put(key, value);
            return this;
        }

        /**
         * Records multiple actual outputs, merging them with any already recorded.
         *
         * @param outputs the outputs to record
         * @return this recorder
         */
        public DatasetItemRecorder actualOutputs(Map<String, Object> outputs) {
            if (outputs != null) {
                actualOutputs.putAll(outputs);
            }
            return this;
        }

        /**
         * Records a single evaluation result.
         *
         * @param result the result to record
         * @return this recorder
         */
        public DatasetItemRecorder evalResult(EvalResult result) {
            if (result != null) {
                evalResults.add(result);
            }
            return this;
        }

        /**
         * Records multiple evaluation results.
         *
         * @param results the results to record
         * @return this recorder
         */
        public DatasetItemRecorder evalResults(List<EvalResult> results) {
            if (results != null) {
                evalResults.addAll(results);
            }
            return this;
        }

        Map<String, Object> actualOutputs() {
            return actualOutputs;
        }

        List<EvalResult> evalResults() {
            return evalResults;
        }
    }

    /**
     * Per-method run state held in the method-scoped store. When stored there it is closed once the
     * test method finishes all parameterized invocations, completing and flushing the run.
     *
     * <p>
     * Implements {@link CloseableResource} rather than {@link AutoCloseable} so the store closes it
     * across the supported JUnit range (5.10.3 through 6.x); 6.x also recognizes
     * {@link AutoCloseable}, but the older platforms only auto-close {@link CloseableResource}.
     */
    private static final class RunState implements CloseableResource {

        private final Reporter reporter;
        private final RunHandle handle;
        private final boolean active;
        private final AtomicInteger failures = new AtomicInteger(0);

        private RunState(Reporter reporter, RunHandle handle) {
            this.reporter = reporter;
            this.handle = handle;
            this.active = true;
        }

        private RunState() {
            this.reporter = null;
            this.handle = null;
            this.active = false;
        }

        static RunState inactive() {
            return new RunState();
        }

        boolean active() {
            return active;
        }

        Reporter reporter() {
            return reporter;
        }

        RunHandle handle() {
            return handle;
        }

        void recordFailure() {
            failures.incrementAndGet();
        }

        @Override
        public void close() {
            if (!active) {
                return;
            }
            RunStatus status = failures.get() == 0 ? RunStatus.SUCCESS : RunStatus.FAILED;
            try {
                reporter.completeRun(handle, status);
            } finally {
                reporter.flush();
            }
        }
    }
}
