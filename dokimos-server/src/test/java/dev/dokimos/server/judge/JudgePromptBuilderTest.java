package dev.dokimos.server.judge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dokimos.core.EvalTestCaseParam;
import java.util.List;
import org.junit.jupiter.api.Test;

class JudgePromptBuilderTest {

    @Test
    void includesCriteriaSelectedParamsAndJsonInstruction() {
        String prompt = JudgePromptBuilder.build(
                "answer is correct",
                List.of(EvalTestCaseParam.INPUT, EvalTestCaseParam.ACTUAL_OUTPUT),
                0.0,
                1.0,
                "what is 2+2",
                "4",
                "four");

        assertThat(prompt).contains("answer is correct");
        assertThat(prompt).contains("Input: what is 2+2");
        assertThat(prompt).contains("Actual Output: four");
        assertThat(prompt).contains("{\"score\": <number>, \"reason\": \"<explanation>\"}");
    }

    @Test
    void omitsParamsNotRequested() {
        String prompt = JudgePromptBuilder.build(
                "criteria", List.of(EvalTestCaseParam.ACTUAL_OUTPUT), 0.0, 1.0, "in", "exp", "act");

        assertThat(prompt).doesNotContain("Input:");
        assertThat(prompt).doesNotContain("Expected Output:");
        assertThat(prompt).contains("Actual Output: act");
    }
}
