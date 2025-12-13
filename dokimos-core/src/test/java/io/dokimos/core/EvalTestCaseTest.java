package io.dokimos.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class EvalTestCaseTest {

    @Test
    void shouldBuildMinimalTestCase() {
        var testCase = EvalTestCase.builder()
                .input("What is the refund policy?")
                .actualOutput("30 days full refund")
                .build();

        assertThat(testCase.input()).isEqualTo("What is the refund policy?");
        assertThat(testCase.actualOutput()).isEqualTo("30 days full refund");
        assertThat(testCase.expectedOutput()).isNull();
        assertThat(testCase.retrievalContext()).isEmpty();
        assertThat(testCase.metadata()).isEmpty();
    }

    @Test
    void shouldBuildFullTestCase() {
        var testCase = EvalTestCase.builder()
                .input("What is the refund policy?")
                .actualOutput("30 days full refund")
                .expectedOutput("Full refund within 30 days")
                .retrievalContext(List.of("Policy: 30 day refund for all customers"))
                .metadata(Map.of("source", "faq"))
                .build();

        assertThat(testCase.retrievalContext()).containsExactly("Policy: 30 day refund for all customers");
        assertThat(testCase.metadata()).containsEntry("source", "faq");
    }

    @Test
    void shouldMakeDefensiveCopiesOfCollections() {
        var context = new java.util.ArrayList<>(List.of("doc1"));
        var testCase = EvalTestCase.builder()
                .input("test")
                .actualOutput("test")
                .retrievalContext(context)
                .build();

        context.add("doc2");

        assertThat(testCase.retrievalContext()).containsExactly("doc1");
    }
}
