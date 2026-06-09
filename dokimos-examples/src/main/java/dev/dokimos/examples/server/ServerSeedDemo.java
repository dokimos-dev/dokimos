package dev.dokimos.examples.server;

import dev.dokimos.core.*;
import dev.dokimos.core.evaluators.ExactMatchEvaluator;
import dev.dokimos.server.client.DokimosServerReporter;
import java.util.List;
import java.util.Map;

/**
 * Seeds a local Dokimos server with realistic, deterministic demo data, with no LLM calls. It
 * populates one project with two experiments: a RAG answer-quality experiment run several times so
 * the run list shows a trend and the last run carries a regression for the diff view, and a smaller
 * summarization experiment. Each item carries synthetic token, cost, and latency metrics so the
 * run-detail cards light up.
 *
 * <p>Prerequisites: a running server at {@link #SERVER_URL} (see {@code
 * dokimos-server/docker-compose.dev.yml} for PostgreSQL). Run with:
 *
 * <pre>{@code
 * mvn exec:java -pl dokimos-examples -Dexec.mainClass="dev.dokimos.examples.server.ServerSeedDemo"
 * }</pre>
 */
public final class ServerSeedDemo {

    private static final String SERVER_URL = "http://localhost:8080";
    private static final String PROJECT = "support-assistant";
    private static final String MODEL_ID = "gpt-5-nano";

    /** Illustrative per-million-token rates (USD): {@code {inputPerMillion, outputPerMillion}}. */
    private static final Map<String, double[]> RATES = Map.of("gpt-5-nano", new double[] {0.05, 0.40});

    private static final PriceTable PRICES = (model, tokensIn, tokensOut) -> {
        double[] rate = model == null ? null : RATES.get(model);
        if (rate == null || tokensIn == null || tokensOut == null) {
            return null;
        }
        double usd = ((long) tokensIn * rate[0] + (long) tokensOut * rate[1]) / 1_000_000d;
        return Math.round(usd * 1_000_000d) / 1_000_000d;
    };

    private ServerSeedDemo() {}

    public static void main(String[] args) {
        DokimosServerReporter reporter = DokimosServerReporter.builder()
                .serverUrl(SERVER_URL)
                .projectName(PROJECT)
                .build();
        try {
            seedRagQuality(reporter);
            seedSummarization(reporter);
            System.out.println("Seeded project '" + PROJECT + "' at " + SERVER_URL);
        } finally {
            reporter.close();
        }
    }

    /** RAG answer quality: four runs, a downward pass-rate trend, the last run a regression. */
    private static void seedRagQuality(DokimosServerReporter reporter) {
        String[][] qa = {
            {"How do I reset my password?", "Open Settings, choose Security, then Reset password."},
            {"What is your refund window?", "You can get a full refund within 30 days of purchase."},
            {"Do you offer a free trial?", "Yes, a 14-day free trial with no credit card required."},
            {"How do I export my data?", "Settings, then Data, then Export to download a JSON archive."},
            {"Which payment methods do you accept?", "All major credit cards, PayPal, and bank transfer."},
            {"How do I contact support?", "Email support@example.com or use the in-app chat."},
            {"Can I change my plan later?", "Yes, upgrade or downgrade anytime from the Billing page."},
            {"Where are servers hosted?", "In the EU (Frankfurt) and the US (Virginia)."},
        };

        Dataset.Builder dataset =
                Dataset.builder().name("Support RAG QA").description("Grounded answers from the help center");
        for (String[] row : qa) {
            dataset.addExample(Example.of(row[0], row[1]));
        }
        Dataset ds = dataset.build();

        List<Evaluator> evaluators = List.of(
                ExactMatchEvaluator.builder()
                        .name("answer-match")
                        .threshold(1.0)
                        .build(),
                cannedScore("faithfulness", 0.7));

        // Which item indices answer wrongly in each run: a clean start, then a regression.
        int[][] wrongPerRun = {{}, {}, {4}, {4, 6}};
        for (int run = 0; run < wrongPerRun.length; run++) {
            int[] wrong = wrongPerRun[run];
            final int r = run;
            MeasuredTask task = example -> {
                int i = indexOf(qa, example.input());
                boolean broken = contains(wrong, i);
                String answer = broken ? "I'm not sure, please check the help center." : qa[i][1];
                int tokensIn = 180 + i * 24;
                int tokensOut = 26 + i * 6;
                long latency = 520 + (long) i * 35 + r * 12L;
                Double cost = PRICES.costUsd(MODEL_ID, tokensIn, tokensOut);
                return new TaskResult(Map.of("output", answer), new CallMetrics(tokensIn, tokensOut, cost, latency));
            };
            runExperiment(reporter, "rag-answer-quality", ds, task, evaluators);
        }
    }

    /** A second experiment so the project page is not a single row. */
    private static void seedSummarization(DokimosServerReporter reporter) {
        Dataset ds = Dataset.builder()
                .name("Summarization")
                .description("One-line summaries of support tickets")
                .addExample(Example.of("Ticket: login loops after SSO redirect", "SSO redirect causes a login loop."))
                .addExample(Example.of("Ticket: invoice PDF is blank", "Invoice PDF renders blank."))
                .addExample(Example.of("Ticket: webhook retries stop after 3 tries", "Webhook retries cap at three."))
                .build();

        List<Evaluator> evaluators = List.of(cannedScore("conciseness", 0.6), cannedScore("coverage", 0.6));

        for (int run = 0; run < 2; run++) {
            MeasuredTask task = example -> {
                int tokensIn = 320;
                int tokensOut = 18;
                Double cost = PRICES.costUsd(MODEL_ID, tokensIn, tokensOut);
                return new TaskResult(
                        Map.of("output", example.expectedOutput()), new CallMetrics(tokensIn, tokensOut, cost, 410L));
            };
            runExperiment(reporter, "summarization-quality", ds, task, evaluators);
        }
    }

    private static void runExperiment(
            DokimosServerReporter reporter, String name, Dataset ds, MeasuredTask task, List<Evaluator> evaluators) {
        Experiment.builder()
                .name(name)
                .dataset(ds)
                .measuredTask(task)
                .evaluators(evaluators)
                .reporter(reporter)
                .metadata(Map.of("model", MODEL_ID, "temperature", 0.0))
                .build()
                .run();
    }

    /** An evaluator that returns a fixed, item-derived score so runs show a non-binary distribution. */
    private static Evaluator cannedScore(String name, double threshold) {
        return new Evaluator() {
            @Override
            public EvalResult evaluate(EvalTestCase testCase) {
                String input = testCase.input() == null ? "" : testCase.input();
                double score = 0.55 + (Math.abs(input.hashCode()) % 45) / 100.0; // 0.55..0.99, stable per input
                score = Math.round(score * 100.0) / 100.0;
                String reason = "Scored " + score + " for " + name + ".";
                return score >= threshold
                        ? EvalResult.success(name, score, reason)
                        : EvalResult.failure(name, score, reason);
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public double threshold() {
                return threshold;
            }
        };
    }

    private static int indexOf(String[][] qa, String input) {
        for (int i = 0; i < qa.length; i++) {
            if (qa[i][0].equals(input)) {
                return i;
            }
        }
        return 0;
    }

    private static boolean contains(int[] arr, int v) {
        for (int x : arr) {
            if (x == v) {
                return true;
            }
        }
        return false;
    }
}
