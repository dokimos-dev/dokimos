package dev.dokimos.server.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dokimos.server.dto.v1.otlp.OtlpExportTraceServiceRequest;
import dev.dokimos.server.service.OtlpTraceParser.ParsedSpan;
import dev.dokimos.server.service.OtlpTraceParser.Result;
import org.junit.jupiter.api.Test;

/** Unit coverage for parsing the OTLP/HTTP JSON encoding into the server span model. */
class OtlpTraceParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OtlpTraceParser parser = new OtlpTraceParser();

    private OtlpExportTraceServiceRequest decode(String json) throws Exception {
        return objectMapper.readValue(json, OtlpExportTraceServiceRequest.class);
    }

    @Test
    void parsesSpanWithAttributesAndDerivedText() throws Exception {
        String json = """
                {
                  "resourceSpans": [{
                    "resource": {"attributes": [{"key": "service.name", "value": {"stringValue": "rag"}}]},
                    "scopeSpans": [{
                      "spans": [{
                        "traceId": "abc123",
                        "spanId": "span1",
                        "name": "llm.generate",
                        "startTimeUnixNano": "1700000000000000000",
                        "endTimeUnixNano": "1700000001000000000",
                        "status": {"code": "STATUS_CODE_OK"},
                        "attributes": [
                          {"key": "input", "value": {"stringValue": "what is 2+2"}},
                          {"key": "output", "value": {"stringValue": "4"}},
                          {"key": "tokens", "value": {"intValue": "12"}}
                        ]
                      }]
                    }]
                  }]
                }
                """;

        Result result = parser.parse(decode(json));

        assertThat(result.rejected()).isZero();
        assertThat(result.spans()).hasSize(1);
        ParsedSpan span = result.spans().get(0);
        assertThat(span.traceId()).isEqualTo("abc123");
        assertThat(span.name()).isEqualTo("llm.generate");
        assertThat(span.inputText()).isEqualTo("what is 2+2");
        assertThat(span.outputText()).isEqualTo("4");
        assertThat(span.statusCode()).isEqualTo("STATUS_CODE_OK");
        assertThat(span.startTimeUnixNano()).isEqualTo(1700000000000000000L);
        assertThat(span.attributes()).containsEntry("service.name", "rag").containsEntry("tokens", 12L);
    }

    @Test
    void skipsMalformedSpanNonFatally() throws Exception {
        String json = """
                {
                  "resourceSpans": [{
                    "scopeSpans": [{
                      "spans": [
                        {"spanId": "s", "name": "no-trace-id"},
                        {"traceId": "t1", "spanId": "s1", "name": "good"}
                      ]
                    }]
                  }]
                }
                """;

        Result result = parser.parse(decode(json));

        assertThat(result.rejected()).isEqualTo(1);
        assertThat(result.spans()).hasSize(1);
        assertThat(result.spans().get(0).name()).isEqualTo("good");
    }

    @Test
    void resolvesProjectNameFromResourceAttribute() throws Exception {
        String json = """
                {
                  "resourceSpans": [{
                    "resource": {"attributes": [{"key": "dokimos.project", "value": {"stringValue": "checkout"}}]},
                    "scopeSpans": [{
                      "spans": [{"traceId": "t1", "spanId": "s1", "name": "n"}]
                    }]
                  }]
                }
                """;

        Result result = parser.parse(decode(json));

        assertThat(result.spans().get(0).projectName()).isEqualTo("checkout");
    }

    @Test
    void emptyRequestYieldsNoSpans() throws Exception {
        Result result = parser.parse(decode("{}"));
        assertThat(result.spans()).isEmpty();
        assertThat(result.rejected()).isZero();
    }
}
