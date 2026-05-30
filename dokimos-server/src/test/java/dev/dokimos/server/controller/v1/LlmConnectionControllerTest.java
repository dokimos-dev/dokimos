package dev.dokimos.server.controller.v1;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.dokimos.server.controller.GlobalExceptionHandler;
import dev.dokimos.server.dto.v1.CreateLlmConnectionRequest;
import dev.dokimos.server.dto.v1.LlmConnectionView;
import dev.dokimos.server.service.LlmConnectionService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class LlmConnectionControllerTest extends AbstractControllerTest {

    @Mock
    private LlmConnectionService connectionService;

    @BeforeEach
    void setUp() {
        LlmConnectionController controller = new LlmConnectionController(connectionService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private LlmConnectionView view(UUID id) {
        return new LlmConnectionView(
                id, "conn", "https://api.example.com", "gpt-4", "OPENAI_KEY", false, Instant.now());
    }

    @Test
    void create_shouldReturn201AndNoKeyMaterial() throws Exception {
        UUID id = UUID.randomUUID();
        when(connectionService.create(any())).thenReturn(view(id));
        CreateLlmConnectionRequest request =
                new CreateLlmConnectionRequest("conn", "https://api.example.com", "gpt-4", null, "OPENAI_KEY");

        mockMvc.perform(post("/api/v1/llm-connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.hasInlineKey").value(false))
                .andExpect(jsonPath("$.encryptedApiKey").doesNotExist())
                .andExpect(jsonPath("$.apiKey").doesNotExist());
    }

    @Test
    void create_shouldReturn400WhenBothCredentialsSupplied() throws Exception {
        CreateLlmConnectionRequest request =
                new CreateLlmConnectionRequest("conn", "https://api.example.com", "gpt-4", "sk-inline", "OPENAI_KEY");

        mockMvc.perform(post("/api/v1/llm-connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_shouldReturn409OnDuplicateName() throws Exception {
        doThrow(new IllegalStateException("Connection already exists: conn"))
                .when(connectionService)
                .create(any());
        CreateLlmConnectionRequest request =
                new CreateLlmConnectionRequest("conn", "https://api.example.com", "gpt-4", null, "OPENAI_KEY");

        mockMvc.perform(post("/api/v1/llm-connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void get_shouldReturn404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(connectionService.get(eq(id))).thenThrow(new IllegalArgumentException("Connection not found: " + id));

        mockMvc.perform(get("/api/v1/llm-connections/" + id)).andExpect(status().isNotFound());
    }
}
