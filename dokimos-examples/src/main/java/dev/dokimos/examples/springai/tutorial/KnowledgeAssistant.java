package dev.dokimos.examples.springai.tutorial;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Knowledge assistant that combines retrieval with generation.
 * This is the core RAG implementation for the tutorial.
 */
@Service
public class KnowledgeAssistant {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public KnowledgeAssistant(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    public AssistantResponse answer(String question) {
        // Step 1: Retrieve relevant documents
        List<Document> retrievedDocs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(3)
                        .build());

        // Step 2: Build context from retrieved documents
        String context = retrievedDocs.stream()
                .map(Document::getText)
                .reduce("", (a, b) -> a + "\n\n" + b);

        // Step 3: Generate response using context
        String systemPrompt = """
                You are a helpful customer service assistant. Answer the user's question
                based ONLY on the provided context. If the context does not contain
                enough information to answer the question, say so clearly.

                Context:
                %s
                """.formatted(context);

        String response = chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .content();

        // Return both the response and retrieved context for evaluation
        return new AssistantResponse(response, retrievedDocs);
    }

    public record AssistantResponse(
            String answer,
            List<Document> retrievedDocuments) {
    }
}
