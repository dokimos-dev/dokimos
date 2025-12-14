package io.dokimos.core;

import java.util.Map;

public record EvalResult(
        String name,
        double score,
        boolean success,
        String reason,
        Map<String, Object> metadata
) {
    public EvalResult {
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("Score must be between 0.0 and 1.0");
        }
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public static EvalResult of(String name, double score, String reason) {
        // TODO: handle success correctly
        return new EvalResult(name, score, true, reason, Map.of());
    }

    public static EvalResult success(String name, double score, String reason) {
        return new EvalResult(name, score, true, reason, Map.of());
    }

    public static EvalResult failure(String name, double score, String reason) {
        return new EvalResult(name, score, false, reason, Map.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private double score;
        private Double threshold;
        private String reason;
        private Map<String, Object> metadata;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder score(double score) {
            this.score = score;
            return this;
        }

        public Builder threshold(double threshold) {
            this.threshold = threshold;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public EvalResult build() {
            boolean success = threshold != null ? score >= threshold : score >= 0.5;
            return new EvalResult(name, score, success, reason, metadata);
        }
    }
}