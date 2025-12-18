package io.dokimos.junit5.integration;

import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

public class AIAssistant {

    private final OpenAIClient client;

    public AIAssistant(OpenAIClient client) {
        this.client = client;
    }

    public String answer(String question) {
        var params = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_5_NANO)
                .addSystemMessage("You are a helpful AI Assistant. Answer the user's question in a very concise way.")
                .addUserMessage("The question is: " + question)
                .build();

        return client.chat().completions().create(params)
                .choices().getFirst().message().content().orElse("");
    }

}
