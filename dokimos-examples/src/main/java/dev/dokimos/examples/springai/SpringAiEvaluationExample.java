package dev.dokimos.examples.springai;

import dev.dokimos.core.*;
import dev.dokimos.core.evaluators.ExactMatchEvaluator;
import dev.dokimos.core.evaluators.LLMJudgeEvaluator;
import dev.dokimos.springai.SpringAiSupport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.List;
import java.util.Map;

/**
 * A simple Example demonstrating how to use Spring AI with Dokimos for
 * evaluation.
 * Requires the `OPENAI_API_KEY` environment variable.
 */
public class SpringAiEvaluationExample {

        public static void main(String[] args) {
                if (System.getenv("OPENAI_API_KEY") == null) {
                        System.err.println("OPENAI_API_KEY not set");
                        System.exit(1);
                }

                // 1. Set up Spring AI ChatModel
                OpenAiApi openAiApi = new OpenAiApi(System.getenv("OPENAI_API_KEY"));
                ChatModel chatModel = new OpenAiChatModel(openAiApi,
                                OpenAiChatOptions.builder()
                                                .model("gpt-5-nano")
                                                .build());

                // 2. Create dataset
                Dataset dataset = Dataset.builder()
                                .name("customer-support")
                                .addExample(Example.of(
                                                "What is your return policy?",
                                                "We offer a 30-day money-back guarantee on all purchases."))
                                .addExample(Example.of(
                                                "How long does shipping take?",
                                                "Standard shipping takes 5-7 business days."))
                                .addExample(Example.of(
                                                "Do you offer technical support?",
                                                "Yes, we provide 24/7 technical support via email and chat."))
                                .build();

                // 3. Create task that calls Spring AI ChatModel
                Task task = example -> {
                        String prompt = "Answer the following customer question concisely: " + example.input();
                        String response = chatModel.call(prompt);
                        return Map.of("output", response);
                };

                // 4. Set up evaluators using Spring AI ChatClient as judge
                ChatModel judgeModel = new OpenAiChatModel(openAiApi,
                                OpenAiChatOptions.builder()
                                                .model("gpt-5-nano")
                                                .build());

                ChatClient.Builder judgeBuilder = ChatClient.builder(judgeModel);
                JudgeLM judge = SpringAiSupport.asJudge(judgeBuilder);

                List<Evaluator> evaluators = List.of(
                                ExactMatchEvaluator.builder()
                                                .threshold(0.5)
                                                .build(),
                                LLMJudgeEvaluator.builder()
                                                .name("Answer Quality")
                                                .judge(judge)
                                                .criteria("Is the answer helpful, accurate, and professionally worded?")
                                                .evaluationParams(List.of(
                                                                EvalTestCaseParam.INPUT,
                                                                EvalTestCaseParam.ACTUAL_OUTPUT))
                                                .threshold(0.7)
                                                .build(),
                                LLMJudgeEvaluator.builder()
                                                .name("Conciseness")
                                                .judge(judge)
                                                .criteria("Is the answer concise and to the point?")
                                                .evaluationParams(List.of(
                                                                EvalTestCaseParam.INPUT,
                                                                EvalTestCaseParam.ACTUAL_OUTPUT))
                                                .threshold(0.6)
                                                .build());

                // 5. Run experiment
                ExperimentResult result = Experiment.builder()
                                .name("Spring AI Customer Support Evaluation")
                                .dataset(dataset)
                                .task(task)
                                .evaluators(evaluators)
                                .build()
                                .run();

                // 6. Display results
                System.out.println("=".repeat(60));
                System.out.println("Spring AI Customer Support Evaluation Results");
                System.out.println("=".repeat(60));
                System.out.println("Pass rate: " + String.format("%.0f%%", result.passRate() * 100));
                System.out.println();

                System.out.println("Average Scores:");
                System.out.println("  Answer Quality: " +
                                String.format("%.2f", result.averageScore("Answer Quality")));
                System.out.println("  Conciseness: " +
                                String.format("%.2f", result.averageScore("Conciseness")));
                System.out.println();

                System.out.println("Detailed Results:");
                System.out.println("-".repeat(60));
                result.itemResults().forEach(item -> {
                        System.out.println();
                        System.out.println("Question: " + item.example().input());
                        System.out.println("Response: " + item.actualOutputs().get("output"));
                        System.out.println("Expected: " + item.example().expectedOutput());
                        System.out.println("Status: " + (item.success() ? "✓ PASS" : "✗ FAIL"));
                        System.out.println("Scores:");
                        item.evalResults().forEach(eval -> System.out.println("  • " + eval.name() + ": " +
                                        String.format("%.2f", eval.score()) +
                                        (eval.success() ? " ✓" : " ✗")));
                });
                System.out.println();
                System.out.println("=".repeat(60));
        }
}
