package dev.dokimos.examples.springai.tutorial;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration for the vector store with company documentation.
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();

        // Load our company documents
        List<Document> documents = List.of(
            new Document(
                "Our return policy allows customers to return any product within 30 days " +
                "of purchase for a full refund. Items must be in original condition with " +
                "tags attached. Refunds are processed within 5 business days."
            ),
            new Document(
                "Premium members receive free shipping on all orders, 20% discount on " +
                "all products, early access to new releases, and priority customer support. " +
                "Premium membership costs $99 per year."
            ),
            new Document(
                "Our customer support team is available Monday through Friday from 9 AM " +
                "to 6 PM Eastern Time. You can reach us by email at support@example.com " +
                "or by phone at 1-800-EXAMPLE."
            ),
            new Document(
                "We offer three shipping options: Standard (5-7 business days, $5.99), " +
                "Express (2-3 business days, $12.99), and Next Day ($24.99). " +
                "Orders over $50 qualify for free standard shipping."
            ),
            new Document(
                "Gift cards are available in denominations of $25, $50, $100, and $200. " +
                "Gift cards never expire and can be used for any purchase on our website. " +
                "They cannot be redeemed for cash."
            )
        );

        store.add(documents);
        return store;
    }
}
