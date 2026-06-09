package dev.dokimos.core.gate;

/**
 * Configuration for the server-free regression gate.
 *
 * <p>The defaults suit an LLM-judge gate: it flags only statistically significant aggregate and
 * per-evaluator drops, so a noisy judge does not flake the build, while {@code severityMargin} still
 * fails the gate on a single item that breaks hard. Pair the gate with a temperature-0 judge for
 * stable scores.
 *
 * <p>Accessors follow the framework convention (no {@code get} prefix). Construct via {@link
 * #defaults()} or {@link #builder()}.
 */
public final class GateConfig {

    /** How baseline and candidate items are paired. */
    public enum Pairing {
        /** Pair by {@code datasetItemId} when every item carries one, else positionally. */
        AUTO,
        /** Always pair by position ({@code "item-<index>"} keys). */
        POSITIONAL,
        /** Always pair by {@code datasetItemId} (fails if any item lacks one). */
        DATASET_ITEM_ID
    }

    /** What to do when an evaluator present in the baseline is missing from the candidate. */
    public enum RemovedEvaluatorPolicy {
        /** Fail the gate: a dropped evaluator is indistinguishable from hiding a regression. */
        FAIL,
        /** Warn only. */
        WARN
    }

    private final double alpha;
    private final long seed;
    private final int permutationIterations;
    private final int bootstrapIterations;
    private final double severityMargin;
    private final Pairing pairing;
    private final boolean failOnRegression;
    private final boolean failOnRemovedItems;
    private final RemovedEvaluatorPolicy onRemovedEvaluator;
    private final boolean bootstrapPasses;
    private final boolean updateBaseline;

    private GateConfig(Builder b) {
        this.alpha = b.alpha;
        this.seed = b.seed;
        this.permutationIterations = b.permutationIterations;
        this.bootstrapIterations = b.bootstrapIterations;
        this.severityMargin = b.severityMargin;
        this.pairing = b.pairing;
        this.failOnRegression = b.failOnRegression;
        this.failOnRemovedItems = b.failOnRemovedItems;
        this.onRemovedEvaluator = b.onRemovedEvaluator;
        this.bootstrapPasses = b.bootstrapPasses;
        this.updateBaseline = b.updateBaseline;
    }

    /**
     * Returns a configuration with all defaults.
     *
     * @return the default gate configuration
     */
    public static GateConfig defaults() {
        return builder().build();
    }

    /**
     * Creates a new builder.
     *
     * @return a new builder pre-populated with defaults
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Whether an explicit baseline update was requested via {@code DOKIMOS_UPDATE_BASELINE} (env, the
     * primary control because {@code -D} does not reach the test JVM under default Gradle or the
     * IntelliJ runner) or the {@code dokimos.updateBaseline} system property.
     *
     * @return true if a baseline overwrite was requested out of band
     */
    public static boolean updateBaselineRequested() {
        return truthy(System.getenv("DOKIMOS_UPDATE_BASELINE")) || truthy(System.getProperty("dokimos.updateBaseline"));
    }

    private static boolean truthy(String v) {
        return v != null && (v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes"));
    }

    /** @return the significance level for the statistical tests */
    public double alpha() {
        return alpha;
    }

    /** @return the RNG seed for the permutation and bootstrap tests (pinned for reproducibility) */
    public long seed() {
        return seed;
    }

    /** @return the permutation-test iteration count */
    public int permutationIterations() {
        return permutationIterations;
    }

    /** @return the bootstrap iteration count */
    public int bootstrapIterations() {
        return bootstrapIterations;
    }

    /** @return the per-item worst-evaluator score-drop threshold that fails the gate (guard 2) */
    public double severityMargin() {
        return severityMargin;
    }

    /** @return the item pairing strategy */
    public Pairing pairing() {
        return pairing;
    }

    /** @return whether a detected regression fails the gate */
    public boolean failOnRegression() {
        return failOnRegression;
    }

    /** @return whether a removed item (present in baseline, absent in candidate) fails the gate */
    public boolean failOnRemovedItems() {
        return failOnRemovedItems;
    }

    /** @return the policy for an evaluator present in the baseline but missing from the candidate */
    public RemovedEvaluatorPolicy onRemovedEvaluator() {
        return onRemovedEvaluator;
    }

    /**
     * Whether a missing-baseline bootstrap writes the file and passes (the default), versus the
     * strict approval-test stance that writes it but fails once so the run is red until the new
     * baseline is reviewed and committed. The CI no-baseline branch is unaffected either way: it
     * never writes (the checkout is ephemeral) and always passes with a warning.
     *
     * @return true if a local missing-baseline bootstrap passes after writing
     */
    public boolean bootstrapPasses() {
        return bootstrapPasses;
    }

    /** @return whether the baseline should be overwritten from the candidate and the gate pass */
    public boolean updateBaseline() {
        return updateBaseline;
    }

    /** Builder for {@link GateConfig}. */
    public static final class Builder {
        private double alpha = 0.05;
        private long seed = 42L;
        private int permutationIterations = 10_000;
        private int bootstrapIterations = 10_000;
        private double severityMargin = 0.15;
        private Pairing pairing = Pairing.AUTO;
        private boolean failOnRegression = true;
        private boolean failOnRemovedItems = false;
        private RemovedEvaluatorPolicy onRemovedEvaluator = RemovedEvaluatorPolicy.FAIL;
        // Default: a first local run writes the baseline and passes, so the new file lands in the PR
        // diff for review without a red build on run one. Set false for the strict approval-test
        // stance (write it, fail once until reviewed and committed).
        private boolean bootstrapPasses = true;
        private boolean updateBaseline = false;

        /** @param alpha the significance level @return this builder */
        public Builder alpha(double alpha) {
            this.alpha = alpha;
            return this;
        }

        /** @param seed the RNG seed @return this builder */
        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }

        /** @param iterations the permutation-test iteration count @return this builder */
        public Builder permutationIterations(int iterations) {
            this.permutationIterations = iterations;
            return this;
        }

        /** @param iterations the bootstrap iteration count @return this builder */
        public Builder bootstrapIterations(int iterations) {
            this.bootstrapIterations = iterations;
            return this;
        }

        /** @param margin the guard-2 localized-severity FAIL threshold @return this builder */
        public Builder severityMargin(double margin) {
            this.severityMargin = margin;
            return this;
        }

        /** @param pairing the pairing strategy @return this builder */
        public Builder pairing(Pairing pairing) {
            this.pairing = pairing;
            return this;
        }

        /** @param failOnRegression whether a regression fails the gate @return this builder */
        public Builder failOnRegression(boolean failOnRegression) {
            this.failOnRegression = failOnRegression;
            return this;
        }

        /** @param failOnRemovedItems whether a removed item fails the gate @return this builder */
        public Builder failOnRemovedItems(boolean failOnRemovedItems) {
            this.failOnRemovedItems = failOnRemovedItems;
            return this;
        }

        /** @param policy the removed-evaluator policy @return this builder */
        public Builder onRemovedEvaluator(RemovedEvaluatorPolicy policy) {
            this.onRemovedEvaluator = policy;
            return this;
        }

        /**
         * @param bootstrapPasses whether a missing-baseline bootstrap passes after writing (default
         *     {@code true}); {@code false} fails once until the new baseline is reviewed and committed
         * @return this builder
         */
        public Builder bootstrapPasses(boolean bootstrapPasses) {
            this.bootstrapPasses = bootstrapPasses;
            return this;
        }

        /** @param updateBaseline whether to overwrite the baseline and pass @return this builder */
        public Builder updateBaseline(boolean updateBaseline) {
            this.updateBaseline = updateBaseline;
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return a new {@link GateConfig}
         * @throws IllegalArgumentException if any value is out of range
         */
        public GateConfig build() {
            if (alpha <= 0.0 || alpha >= 1.0) {
                throw new IllegalArgumentException("alpha must be in (0, 1): " + alpha);
            }
            if (severityMargin < 0.0) {
                throw new IllegalArgumentException("severityMargin must be >= 0: " + severityMargin);
            }
            return new GateConfig(this);
        }
    }
}
