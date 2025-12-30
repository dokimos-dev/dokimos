package dev.dokimos.examples.springai.tutorial.evaluation;

import dev.dokimos.core.EvalTestCaseParam;
import dev.dokimos.core.Evaluator;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.core.evaluators.ContextualRelevanceEvaluator;
import dev.dokimos.core.evaluators.FaithfulnessEvaluator;
import dev.dokimos.core.evaluators.HallucinationEvaluator;
import dev.dokimos.core.evaluators.LLMJudgeEvaluator;

import java.util.List;

/**
 * Factory for creating evaluators used to assess the Knowledge Assistant.
 *
 * <p>
 * This class provides a standard set of evaluators for RAG-based Q&A systems:
 * <ul>
 * <li>Faithfulness - checks if responses are grounded in retrieved context</li>
 * <li>Hallucination - detects fabricated information</li>
 * <li>Answer Quality - assesses helpfulness and clarity</li>
 * <li>Contextual Relevance - evaluates retrieval quality</li>
 * </ul>
 */
public final class QAEvaluators {

        public static final String CONTEXT_KEY = "context";

        private QAEvaluators() {
        }

        /**
         * Creates the standard set of evaluators for knowledge assistant evaluation.
         *
         * @param judge the LLM judge to use for semantic evaluation
         * @return list of configured evaluators
         */
        public static List<Evaluator> standard(JudgeLM judge) {
                return List.of(
                                faithfulness(judge),
                                hallucination(judge),
                                answerQuality(judge),
                                contextualRelevance(judge));
        }

        /**
         * Creates a faithfulness evaluator that checks if responses are grounded
         * in the retrieved context.
         */
        public static Evaluator faithfulness(JudgeLM judge) {
                return FaithfulnessEvaluator.builder()
                                .threshold(0.8)
                                .judge(judge)
                                .contextKey(CONTEXT_KEY)
                                .includeReason(true)
                                .build();
        }

        /**
         * Creates a hallucination evaluator that detects fabricated content.
         * Lower scores are better (0.0 = no hallucinations).
         */
        public static Evaluator hallucination(JudgeLM judge) {
                return HallucinationEvaluator.builder()
                                .threshold(0.2)
                                .judge(judge)
                                .contextKey(CONTEXT_KEY)
                                .includeReason(true)
                                .build();
        }

        /**
         * Creates an answer quality evaluator that assesses helpfulness and clarity.
         */
        public static Evaluator answerQuality(JudgeLM judge) {
                return LLMJudgeEvaluator.builder()
                                .name("Answer Quality")
                                .criteria("""
                                                Evaluate the answer based on:
                                                1. Does it directly address the user's question?
                                                2. Is it clear and easy to understand?
                                                3. Does it provide specific, actionable information?
                                                4. Is it appropriately concise?
                                                """)
                                .evaluationParams(List.of(
                                                EvalTestCaseParam.INPUT,
                                                EvalTestCaseParam.ACTUAL_OUTPUT))
                                .threshold(0.7)
                                .judge(judge)
                                .build();
        }

        /**
         * Creates a contextual relevance evaluator that checks retrieval quality.
         */
        public static Evaluator contextualRelevance(JudgeLM judge) {
                return ContextualRelevanceEvaluator.builder()
                                .threshold(0.6)
                                .judge(judge)
                                .retrievalContextKey(CONTEXT_KEY)
                                .includeReason(true)
                                .build();
        }
}
