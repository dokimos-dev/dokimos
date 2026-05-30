package dev.dokimos.server.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import dev.dokimos.server.controller.GlobalExceptionHandler;
import dev.dokimos.server.dto.v1.TraceIngestResponse;
import dev.dokimos.server.dto.v1.otlp.OtlpExportTraceServiceRequest;
import dev.dokimos.server.service.OtlpProtobufConverter;
import dev.dokimos.server.service.OtlpTraceParser;
import dev.dokimos.server.service.OtlpTraceParser.ParsedSpan;
import dev.dokimos.server.service.OtlpTraceParser.Result;
import dev.dokimos.server.service.TraceIngestService;
import dev.dokimos.server.service.TraceQueryService;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Web layer coverage for the protobuf ingestion path. Proves a protobuf encoded request maps onto the
 * same internal DTO the JSON docs example produces, so the shared parser derives identical span count,
 * input/output text, and project link. The ingest service is mocked, so no database is required.
 */
@ExtendWith(MockitoExtension.class)
class TraceControllerProtobufTest extends AbstractControllerTest {

    @Mock
    private TraceIngestService ingestService;

    @Mock
    private TraceQueryService queryService;

    private final OtlpTraceParser parser = new OtlpTraceParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * The OTLP/HTTP JSON encoding of the same example, copied from docs/docs/server/traces.md. The JSON
     * endpoint feeds this through Jackson into the same DTO the protobuf converter targets.
     */
    private static final String DOCS_EXAMPLE_JSON = """
            {
              "resourceSpans": [{
                "resource": { "attributes": [
                  { "key": "dokimos.project", "value": { "stringValue": "my-llm-app" } }
                ]},
                "scopeSpans": [{
                  "spans": [{
                    "traceId": "0af7651916cd43dd8448eb211c80319c",
                    "spanId": "b7ad6b7169203331",
                    "name": "llm.generate",
                    "startTimeUnixNano": "1700000000000000000",
                    "endTimeUnixNano": "1700000002000000000",
                    "attributes": [
                      { "key": "input",  "value": { "stringValue": "What is the capital of France?" } },
                      { "key": "output", "value": { "stringValue": "The capital of France is Paris." } }
                    ]
                  }]
                }]
              }]
            }
            """;

    @BeforeEach
    void setUp() {
        TraceController controller = new TraceController(ingestService, queryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /** Mirrors the OTLP span in docs/docs/server/traces.md, encoded as protobuf instead of JSON. */
    private byte[] docsExampleProtobuf() {
        Span span = Span.newBuilder()
                .setTraceId(hexToBytes("0af7651916cd43dd8448eb211c80319c"))
                .setSpanId(hexToBytes("b7ad6b7169203331"))
                .setName("llm.generate")
                .setStartTimeUnixNano(1700000000000000000L)
                .setEndTimeUnixNano(1700000002000000000L)
                .addAttributes(stringAttr("input", "What is the capital of France?"))
                .addAttributes(stringAttr("output", "The capital of France is Paris."))
                .build();
        Resource resource = Resource.newBuilder()
                .addAttributes(stringAttr("dokimos.project", "my-llm-app"))
                .build();
        ResourceSpans resourceSpans = ResourceSpans.newBuilder()
                .setResource(resource)
                .addScopeSpans(ScopeSpans.newBuilder().addSpans(span))
                .build();
        return ExportTraceServiceRequest.newBuilder()
                .addResourceSpans(resourceSpans)
                .build()
                .toByteArray();
    }

    @Test
    void protobufBodyReachesIngestWithSameParsedShapeAsDocsJson() throws Exception {
        when(ingestService.ingest(any(OtlpExportTraceServiceRequest.class)))
                .thenReturn(new TraceIngestResponse(1, 0, 1));

        mockMvc.perform(post("/api/v1/traces")
                        .contentType("application/x-protobuf")
                        .content(docsExampleProtobuf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedSpans").value(1))
                .andExpect(jsonPath("$.rejectedSpans").value(0))
                .andExpect(jsonPath("$.traces").value(1));

        ArgumentCaptor<OtlpExportTraceServiceRequest> captor =
                ArgumentCaptor.forClass(OtlpExportTraceServiceRequest.class);
        org.mockito.Mockito.verify(ingestService).ingest(captor.capture());

        Result parsed = parser.parse(captor.getValue());
        assertThat(parsed.rejected()).isZero();
        assertThat(parsed.spans()).hasSize(1);
        ParsedSpan span = parsed.spans().get(0);
        assertThat(span.traceId()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(span.spanId()).isEqualTo("b7ad6b7169203331");
        assertThat(span.name()).isEqualTo("llm.generate");
        assertThat(span.startTimeUnixNano()).isEqualTo(1700000000000000000L);
        assertThat(span.endTimeUnixNano()).isEqualTo(1700000002000000000L);
        assertThat(span.inputText()).isEqualTo("What is the capital of France?");
        assertThat(span.outputText()).isEqualTo("The capital of France is Paris.");
        assertThat(span.projectName()).isEqualTo("my-llm-app");
    }

    @Test
    void protobufAndJsonEncodingsParseToTheSameSpan() throws Exception {
        OtlpExportTraceServiceRequest fromProtobuf =
                OtlpProtobufConverter.toDto(ExportTraceServiceRequest.parseFrom(docsExampleProtobuf()));
        OtlpExportTraceServiceRequest fromJson =
                objectMapper.readValue(DOCS_EXAMPLE_JSON, OtlpExportTraceServiceRequest.class);

        Result protobufResult = parser.parse(fromProtobuf);
        Result jsonResult = parser.parse(fromJson);

        assertThat(protobufResult.spans()).hasSize(1);
        assertThat(jsonResult.spans()).hasSize(1);
        assertThat(protobufResult.rejected()).isEqualTo(jsonResult.rejected()).isZero();

        ParsedSpan fromProto = protobufResult.spans().get(0);
        ParsedSpan fromJsonSpan = jsonResult.spans().get(0);

        assertThat(fromProto.traceId()).isEqualTo(fromJsonSpan.traceId()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(fromProto.spanId()).isEqualTo(fromJsonSpan.spanId()).isEqualTo("b7ad6b7169203331");
        assertThat(fromProto.startTimeUnixNano())
                .isEqualTo(fromJsonSpan.startTimeUnixNano())
                .isEqualTo(1700000000000000000L);
        assertThat(fromProto.endTimeUnixNano())
                .isEqualTo(fromJsonSpan.endTimeUnixNano())
                .isEqualTo(1700000002000000000L);
        assertThat(fromProto.inputText())
                .isEqualTo(fromJsonSpan.inputText())
                .isEqualTo("What is the capital of France?");
        assertThat(fromProto.outputText())
                .isEqualTo(fromJsonSpan.outputText())
                .isEqualTo("The capital of France is Paris.");
        assertThat(fromProto.projectName())
                .isEqualTo(fromJsonSpan.projectName())
                .isEqualTo("my-llm-app");
    }

    @Test
    void malformedProtobufBodyYields400() throws Exception {
        byte[] notProtobuf = "this is not a valid protobuf message".getBytes();
        mockMvc.perform(post("/api/v1/traces")
                        .contentType("application/x-protobuf")
                        .content(notProtobuf))
                .andExpect(status().isBadRequest());
    }

    private static KeyValue stringAttr(String key, String value) {
        return KeyValue.newBuilder()
                .setKey(key)
                .setValue(AnyValue.newBuilder().setStringValue(value))
                .build();
    }

    private static ByteString hexToBytes(String hex) {
        return ByteString.copyFrom(HexFormat.of().parseHex(hex));
    }
}
