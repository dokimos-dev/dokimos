package dev.dokimos.examples.conversation.goldens;

import dev.dokimos.core.conversation.ConversationTrajectory;
import dev.dokimos.core.conversation.ConversationalApplication;
import dev.dokimos.core.conversation.Message;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.ArrayList;
import java.util.List;

/**
 * The application under test: a customer support desk for a fictional kitchen appliance store,
 * backed by a LangChain4j chat model.
 *
 * <p>It is a {@link ConversationalApplication}, so {@code GoldenGenerator} and
 * {@code ConversationSimulator} can drive it turn by turn. Every call replays the whole trajectory
 * to the model, which keeps the desk stateless and lets the same instance answer several
 * conversations.
 */
public class SupportDesk implements ConversationalApplication {

    /**
     * The OpenAI model that answers, and the model the generator and the replay test use as a judge.
     */
    public static final String MODEL_ID = "gpt-4o-mini";

    private static final String SYSTEM_PROMPT = """
            You are the support agent for Kettleworks, an online kitchen appliance store.

            Policies:
            - Returns are accepted within 30 days of delivery.
            - A refund can only be issued once you have the order number.
            - Delivery takes 5 to 7 business days.
            - Shipping is free on orders above 50 USD.
            - An order that has already shipped cannot be changed, but it can be returned.

            Answer in at most three sentences. Ask for the order number when you need it, and
            name the relevant policy when it applies.
            """;

    private final ChatModel model;

    /**
     * Creates a support desk backed by the given chat model.
     *
     * @param model the chat model that produces the assistant turns
     */
    public SupportDesk(ChatModel model) {
        this.model = model;
    }

    /**
     * Creates a support desk backed by OpenAI's {@value #MODEL_ID}.
     *
     * @param apiKey the OpenAI API key
     * @return a new support desk
     */
    public static SupportDesk withOpenAi(String apiKey) {
        return new SupportDesk(
                OpenAiChatModel.builder().apiKey(apiKey).modelName(MODEL_ID).build());
    }

    /**
     * Answers the last user turn, given the conversation so far.
     *
     * @param trajectory the conversation up to and including the user turn to answer
     * @return the assistant's reply
     */
    @Override
    public Message respond(ConversationTrajectory trajectory) {
        List<ChatMessage> history = new ArrayList<>();
        history.add(SystemMessage.from(SYSTEM_PROMPT));
        for (Message message : trajectory.messages()) {
            history.add(toChatMessage(message));
        }
        return Message.assistant(model.chat(history).aiMessage().text());
    }

    private static ChatMessage toChatMessage(Message message) {
        return switch (message.role()) {
            case USER -> UserMessage.from(message.content());
            case ASSISTANT -> AiMessage.from(message.content());
            case SYSTEM -> SystemMessage.from(message.content());
        };
    }
}
