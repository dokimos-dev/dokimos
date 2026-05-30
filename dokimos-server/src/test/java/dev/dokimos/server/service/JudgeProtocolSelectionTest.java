package dev.dokimos.server.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dokimos.server.entity.LlmConnection;
import dev.dokimos.server.entity.LlmConnectionProtocol;
import dev.dokimos.server.judge.OpenAiCompatibleJudge;
import dev.dokimos.server.judge.OpenResponsesJudge;
import org.junit.jupiter.api.Test;

class JudgeProtocolSelectionTest {

    @Test
    void buildsAnOpenResponsesJudgeForTheResponsesProtocol() {
        LlmConnection connection = new LlmConnection("conn", "https://api.example.com/v1", "gpt-4o-mini");
        // RESPONSES is the entity default.
        assertThat(JudgeWorker.judgeFor(connection, "key")).isInstanceOf(OpenResponsesJudge.class);
    }

    @Test
    void buildsAChatCompletionsJudgeForTheChatCompletionsProtocol() {
        LlmConnection connection = new LlmConnection("conn", "https://api.example.com/v1", "gpt-4o-mini");
        connection.setProtocol(LlmConnectionProtocol.CHAT_COMPLETIONS);
        assertThat(JudgeWorker.judgeFor(connection, "key")).isInstanceOf(OpenAiCompatibleJudge.class);
    }
}
