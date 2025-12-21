package io.dokimos.langchain4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.Result;
import io.dokimos.core.Example;
import io.dokimos.core.JudgeLM;
import io.dokimos.core.Task;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * This class tests the {@link LangChain4jSupport} class, which contains support utilities for integrating with LangChain4j.
 */
class LangChain4jSupportTest {

    @Test
    void asJudge_shouldDelegateToChatModel() {
        ChatModel mockModel = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest chatRequest) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("Judge response"))
                        .build();
            }
        };

        JudgeLM judge = LangChain4jSupport.asJudge(mockModel);

        String response = judge.generate("Test prompt");
        assertThat(response).isEqualTo("Judge response");
    }


}
