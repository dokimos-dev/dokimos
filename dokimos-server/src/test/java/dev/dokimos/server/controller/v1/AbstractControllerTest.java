package dev.dokimos.server.controller.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import org.springframework.lang.NonNull;
import org.springframework.test.web.servlet.MockMvc;

public class AbstractControllerTest {
    protected MockMvc mockMvc;
    protected ObjectMapper objectMapper = new ObjectMapper();

    @NonNull
    protected String toJson(Object obj) throws Exception {
        String json = objectMapper.writeValueAsString(obj);
        return Objects.requireNonNull(json, "JSON serialization returned null");
    }
}
