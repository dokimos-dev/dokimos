package dev.dokimos.server.integration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("null")
class ApiKeyAuthIntegrationTest {

    private static final String TEST_API_KEY = "test-integration-api-key";

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @TestPropertySource(properties = "dokimos.api-key=" + TEST_API_KEY)
    class WithAuthEnabled {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void getRequestsWorkWithoutAuth() throws Exception {
            mockMvc.perform(get("/api/v1/projects"))
                    .andExpect(status().isOk());
        }

        @Test
        void postRequestsFailWithoutAuth() throws Exception {
            mockMvc.perform(post("/api/v1/projects/test-project/runs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                                "experimentName": "test-experiment",
                                "metadata": {}
                            }
                            """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Invalid or missing API key"));
        }

        @Test
        void postRequestsFailWithWrongApiKey() throws Exception {
            mockMvc.perform(post("/api/v1/projects/test-project/runs")
                    .header("Authorization", "Bearer wrong-key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                                "experimentName": "test-experiment",
                                "metadata": {}
                            }
                            """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Invalid or missing API key"));
        }

        @Test
        void postRequestsSucceedWithCorrectApiKey() throws Exception {
            // This should pass auth and hit the controller
            mockMvc.perform(post("/api/v1/projects/test-project/runs")
                    .header("Authorization", "Bearer " + TEST_API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                                "experimentName": "test-experiment",
                                "metadata": {"model": "gpt-4"}
                            }
                            """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.runId").exists());
        }

        @Test
        void patchRequestsFailWithoutAuth() throws Exception {
            mockMvc.perform(patch("/api/v1/runs/00000000-0000-0000-0000-000000000001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                                "status": "SUCCESS"
                            }
                            """))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @TestPropertySource(properties = "dokimos.api-key=")
    class WithAuthDisabled {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void getRequestsWork() throws Exception {
            mockMvc.perform(get("/api/v1/projects"))
                    .andExpect(status().isOk());
        }

        @Test
        void postRequestsWorkWithoutAuth() throws Exception {
            // This should pass through without auth and hit the controller
            mockMvc.perform(post("/api/v1/projects/test-project/runs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                                "experimentName": "test-experiment",
                                "metadata": {}
                            }
                            """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.runId").exists());
        }
    }
}
