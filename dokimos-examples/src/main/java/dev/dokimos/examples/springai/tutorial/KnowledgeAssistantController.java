package dev.dokimos.examples.springai.tutorial;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class KnowledgeAssistantController {

    private final KnowledgeAssistant assistant;

    public KnowledgeAssistantController(KnowledgeAssistant assistant) {
        this.assistant = assistant;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        var response = assistant.answer(request.question());

        List<String> sources = response.retrievedDocuments().stream()
                .map(doc -> doc.getText())
                .toList();

        return ResponseEntity.ok(new ChatResponse(response.answer(), sources));
    }

    public record ChatRequest(String question) {}

    public record ChatResponse(String answer, List<String> sources) {}
}
