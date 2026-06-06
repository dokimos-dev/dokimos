package dev.dokimos.examples.langchain4j;

import dev.dokimos.core.*;
import dev.dokimos.core.evaluators.ExactMatchEvaluator;
import dev.dokimos.langchain4j.LangChain4jSupport;
import dev.dokimos.server.client.DokimosServerReporter;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.List;
import java.util.Map;

/**
 * Lights up all three run-detail metrics cards (Total Tokens, Total Cost, Avg Latency) end-to-end.
 *
 * <p>The point of this example is the switch from a plain {@code .task(...)} to a measured task:
 * {@link LangChain4jSupport#measuredTask(ChatModel, String, PriceTable)} reads the response token
 * usage, times the call, and composes cost via a supplied {@link PriceTable}, emitting the
 * {@code CallMetrics} that the server stores, aggregates, and the UI renders.
 *
 * <p>The {@link PriceTable} here is a copyable reference map. Dokimos ships no price data: the rates
 * below are ILLUSTRATIVE per-million-token figures you pin and edit yourself. An unknown model or a
 * missing token count yields a null cost (the Cost card stays dark while Tokens and Latency light).
 *
 * <p>Prerequisites:
 * <ul>
 *   <li>{@code export OPENAI_API_KEY='your-key'}</li>
 *   <li>Start the server: {@code mvn spring-boot:run -pl dokimos-server} (needs PostgreSQL; see
 *       {@code dokimos-server/docker-compose.yml})</li>
 * </ul>
 * Run with:
 * {@code mvn exec:java -pl dokimos-examples -Dexec.mainClass="dev.dokimos.examples.langchain4j.CostMetricsExample"}
 */
public class CostMetricsExample {

    private static final String SERVER_URL = "http://localhost:8080";
    private static final String PROJECT_NAME = "cost-metrics-demo";

    /** Drives the chat model AND is the {@link PriceTable} lookup key. */
    private static final String MODEL_ID = "gpt-5-nano";

    /**
     * Copyable reference price map: {@code model -> { inputPerMillion, outputPerMillion }} in USD.
     * ILLUSTRATIVE figures — replace with the current published rates for your model/provider.
     */
    private static final Map<String, double[]> REFERENCE_PRICES = Map.of("gpt-5-nano", new double[] {0.05, 0.40});

    /** A pluggable {@link PriceTable}: per-million rates, 6dp per-item, null on unknown/missing. */
    private static final PriceTable PRICES = (model, tokensIn, tokensOut) -> {
        double[] rate = model == null ? null : REFERENCE_PRICES.get(model);
        if (rate == null || tokensIn == null || tokensOut == null) {
            return null;
        }
        double usd = ((long) tokensIn * rate[0] + (long) tokensOut * rate[1]) / 1_000_000d;
        return Math.round(usd * 1_000_000d) / 1_000_000d;
    };

    public static void main(String[] args) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("ERROR: OPENAI_API_KEY environment variable not set");
            System.err.println("Set it with: export OPENAI_API_KEY='your-api-key'");
            System.exit(1);
        }

        ChatModel model =
                OpenAiChatModel.builder().apiKey(apiKey).modelName(MODEL_ID).build();

        Dataset dataset = Dataset.builder()
                .name("Capital Cities QA")
                .description("Simple questions about world capitals")
                .addExample(Example.of("What is the capital of France?", "Paris"))
                .addExample(Example.of("What is the capital of Japan?", "Tokyo"))
                .build();

        // The switch that lights the cards: .task(...) -> .measuredTask(...).
        MeasuredTask task = LangChain4jSupport.measuredTask(model, MODEL_ID, PRICES);

        DokimosServerReporter reporter = DokimosServerReporter.builder()
                .serverUrl(SERVER_URL)
                .projectName(PROJECT_NAME)
                .build();

        try {
            ExperimentResult result = Experiment.builder()
                    .name("cost-metrics-demo")
                    .dataset(dataset)
                    .measuredTask(task)
                    .evaluators(List.of(ExactMatchEvaluator.builder()
                            .name("exact-match")
                            .threshold(1.0)
                            .build()))
                    .reporter(reporter)
                    .metadata(Map.of("model", MODEL_ID))
                    .build()
                    .run();

            System.out.println("\n=== Captured metrics (these feed the three cards) ===");
            result.itemResults().forEach(item -> {
                CallMetrics m = item.metrics();
                if (m != null) {
                    System.out.printf(
                            "  tokensIn=%s tokensOut=%s costUsd=%s latencyMs=%s%n",
                            m.tokensIn(), m.tokensOut(), m.costUsd(), m.latencyMs());
                }
            });
            System.out.println(
                    "\nOpen " + SERVER_URL + " — the run detail shows Total Tokens, Total Cost, and Avg Latency.");
        } finally {
            reporter.close();
        }
    }
}
