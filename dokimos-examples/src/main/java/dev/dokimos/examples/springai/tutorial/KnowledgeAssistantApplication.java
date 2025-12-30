package dev.dokimos.examples.springai.tutorial;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application for the Knowledge Assistant.
 *
 * <p>This application exposes a REST API at POST /api/chat for interacting
 * with the RAG-based knowledge assistant.
 *
 * <p>To run:
 * <pre>
 * export OPENAI_API_KEY='your-api-key'
 * mvn spring-boot:run
 * </pre>
 *
 * <p>Example request:
 * <pre>
 * curl -X POST http://localhost:8080/api/chat \
 *   -H "Content-Type: application/json" \
 *   -d '{"question": "What is your return policy?"}'
 * </pre>
 */
@SpringBootApplication
public class KnowledgeAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeAssistantApplication.class, args);
    }
}
