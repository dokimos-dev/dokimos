package io.dokimos.core;

import java.util.List;
import java.util.Map;

public record EvalTestCase(
        String input,
        String actualOutput,
        String expectedOutput,
        List<String> retrievalContext,
        Map<String, Object> metadata
) {
    public EvalTestCase {
        retrievalContext = retrievalContext != null ? List.copyOf(retrievalContext) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String input;
        private String actualOutput;
        private String expectedOutput;
        private List<String> retrievalContext;
        private Map<String, Object> metadata;

        public Builder input(String input) {
            this.input = input;
            return this;
        }

        public Builder actualOutput(String actualOutput) {
            this.actualOutput = actualOutput;
            return this;
        }

        public Builder expectedOutput(String expectedOutput) {
            this.expectedOutput = expectedOutput;
            return this;
        }

        public Builder retrievalContext(List<String> retrievalContext) {
            this.retrievalContext = retrievalContext;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public EvalTestCase build() {
            return new EvalTestCase(input, actualOutput, expectedOutput, retrievalContext, metadata);
        }
    }
}