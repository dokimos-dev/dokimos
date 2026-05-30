package dev.dokimos.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.dokimos.core.Example;
import dev.dokimos.core.ItemResult;
import dev.dokimos.core.Reporter;
import dev.dokimos.core.RunHandle;
import dev.dokimos.core.RunStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class DatasetRunExtensionTest {

    private static Reporter reporter;

    /**
     * Forwards every call to the current {@link #reporter} mock. Fixture classes reference this
     * stable instance from their static {@code @DatasetReporter} field, while each test swaps the
     * underlying mock to keep verifications isolated.
     */
    private static final Reporter FORWARDING = new Reporter() {
        @Override
        public RunHandle startRun(String experimentName, Map<String, Object> metadata) {
            return reporter.startRun(experimentName, metadata);
        }

        @Override
        public void reportItem(RunHandle handle, ItemResult result) {
            reporter.reportItem(handle, result);
        }

        @Override
        public void completeRun(RunHandle handle, RunStatus status) {
            reporter.completeRun(handle, status);
        }

        @Override
        public void flush() {
            reporter.flush();
        }

        @Override
        public void close() {
            reporter.close();
        }
    };

    private static final String THREE_EXAMPLES = """
            {
              "examples": [
                {"input": "Capital of France?", "expectedOutput": "Paris"},
                {"input": "Capital of Germany?", "expectedOutput": "Berlin"},
                {"input": "Capital of Italy?", "expectedOutput": "Rome"}
              ]
            }
            """;

    @BeforeEach
    void setUp() {
        reporter = mock(Reporter.class);
        when(reporter.startRun(any(), anyMap())).thenReturn(new RunHandle("run-1"));
    }

    @AfterEach
    void tearDown() {
        reporter = null;
    }

    private static void run(Class<?> testClass) {
        Launcher launcher = LauncherFactory.create();
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(testClass))
                .build();
        launcher.execute(request);
    }

    @Test
    void startsRunOncePerMethodWithMethodNameByDefault() {
        run(PassingFixture.class);

        verify(reporter).startRun(eq("passes"), anyMap());
        verify(reporter, times(3)).reportItem(any(), any());
        verify(reporter).completeRun(any(), eq(RunStatus.SUCCESS));
    }

    @Test
    void reportsItemsInDatasetOrder() {
        run(PassingFixture.class);

        ArgumentCaptor<ItemResult> captor = ArgumentCaptor.forClass(ItemResult.class);
        verify(reporter, times(3)).reportItem(any(), captor.capture());

        List<String> inputs = new ArrayList<>();
        for (ItemResult result : captor.getAllValues()) {
            inputs.add(result.example().input());
        }
        assertThat(inputs).containsExactly("Capital of France?", "Capital of Germany?", "Capital of Italy?");
    }

    @Test
    void completesWithSuccessWhenAllInvocationsPass() {
        run(PassingFixture.class);

        verify(reporter).completeRun(any(), eq(RunStatus.SUCCESS));
        verify(reporter).flush();
    }

    @Test
    void completesWithFailedWhenAnInvocationThrows() {
        run(FailingFixture.class);

        verify(reporter).completeRun(any(), eq(RunStatus.FAILED));
    }

    @Test
    void flushesAfterCompleteRunEvenWithFailures() {
        run(FailingFixture.class);

        InOrder order = inOrder(reporter);
        order.verify(reporter).completeRun(any(), eq(RunStatus.FAILED));
        order.verify(reporter).flush();
    }

    @Test
    void callsLifecycleInOrder() {
        run(PassingFixture.class);

        InOrder order = inOrder(reporter);
        order.verify(reporter).startRun(any(), anyMap());
        order.verify(reporter, times(3)).reportItem(any(), any());
        order.verify(reporter).completeRun(any(), any());
        order.verify(reporter).flush();
    }

    @Test
    void doesNotReportWhenNoReporterFieldPresent() {
        run(NoReporterFixture.class);

        verify(reporter, never()).startRun(any(), anyMap());
        verify(reporter, never()).reportItem(any(), any());
        verify(reporter, never()).completeRun(any(), any());
    }

    @Test
    void usesCustomExperimentName() {
        run(CustomNameFixture.class);

        verify(reporter).startRun(eq("custom-run"), anyMap());
    }

    @Test
    void forwardsMetadataPairs() {
        run(MetadataFixture.class);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(reporter).startRun(any(), captor.capture());
        assertThat(captor.getValue()).containsEntry("model", "gpt-4").containsEntry("temperature", "0");
    }

    @Test
    void doesNotCloseReporterSuppliedByField() {
        run(PassingFixture.class);

        verify(reporter, never()).close();
    }

    static class PassingFixture {

        @DatasetReporter
        static final Reporter reporter = FORWARDING;

        @ParameterizedTest
        @DatasetSource(json = THREE_EXAMPLES)
        void passes(Example example) {
            assertThat(example.input()).contains("Capital");
        }
    }

    static class FailingFixture {

        @DatasetReporter
        static final Reporter reporter = FORWARDING;

        @ParameterizedTest
        @DatasetSource(json = THREE_EXAMPLES)
        void mixed(Example example) {
            if (example.input().contains("Germany")) {
                Assertions.fail("forced failure");
            }
        }
    }

    static class NoReporterFixture {

        @ParameterizedTest
        @DatasetSource(json = THREE_EXAMPLES)
        void noReporter(Example example) {
            assertThat(example.input()).isNotBlank();
        }
    }

    static class CustomNameFixture {

        @DatasetReporter
        static final Reporter reporter = FORWARDING;

        @ParameterizedTest
        @DatasetSource(json = THREE_EXAMPLES, experimentName = "custom-run")
        void named(Example example) {
            assertThat(example.input()).isNotBlank();
        }
    }

    static class MetadataFixture {

        @DatasetReporter
        static final Reporter reporter = FORWARDING;

        @ParameterizedTest
        @DatasetSource(
                json = THREE_EXAMPLES,
                metadata = {"model", "gpt-4", "temperature", "0"})
        void withMetadata(Example example) {
            assertThat(example.input()).isNotBlank();
        }
    }
}
