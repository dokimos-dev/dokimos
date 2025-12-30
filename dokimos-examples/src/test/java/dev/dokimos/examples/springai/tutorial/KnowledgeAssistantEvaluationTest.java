package dev.dokimos.examples.springai.tutorial;

import dev.dokimos.core.Assertions;
import dev.dokimos.core.EvalTestCase;
import dev.dokimos.core.Evaluator;
import dev.dokimos.core.Example;
import dev.dokimos.core.JudgeLM;
import dev.dokimos.examples.springai.tutorial.evaluation.QAEvaluators;
import dev.dokimos.junit5.DatasetSource;
import dev.dokimos.springai.SpringAiSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * Evaluation tests for the Knowledge Assistant using Dokimos.
 *
 * <p>This test class demonstrates how to evaluate a RAG-based assistant
 * using the JUnit 5 integration. Each test case from the dataset is
 * evaluated against multiple quality dimensions.
 */
@SpringBootTest
class KnowledgeAssistantEvaluationTest {

    @Autowired
    private KnowledgeAssistant assistant;

    @Autowired
    private ChatModel chatModel;

    private List<Evaluator> evaluators;

    @BeforeEach
    void setup() {
        JudgeLM judge = SpringAiSupport.asJudge(chatModel);
        evaluators = QAEvaluators.standard(judge);
    }

    @ParameterizedTest
    @DatasetSource("classpath:datasets/qa-dataset.json")
    void shouldProvideQualityAnswers(Example example) {
        var response = assistant.answer(example.input());

        List<String> contextTexts = response.retrievedDocuments().stream()
                .map(Document::getText)
                .toList();

        EvalTestCase testCase = EvalTestCase.builder()
                .input(example.input())
                .actualOutput(response.answer())
                .actualOutput(QAEvaluators.CONTEXT_KEY, contextTexts)
                .expectedOutput(example.expectedOutput())
                .build();

        Assertions.assertEval(testCase, evaluators);
    }
}
