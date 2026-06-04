package dev.dokimos.core.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Shared, framework-internal JSON utilities backed by a single, immutable Jackson {@link
 * ObjectMapper}.
 *
 * <p>The mapper is constructed once in a static initializer and is never mutated afterwards. Only
 * the thread-safe {@link ObjectReader} and {@link ObjectWriter} views are exposed (directly or via
 * convenience helpers), which keeps all usage safe under the parallel experiment runs that
 * dokimos-core performs.
 *
 * <p>Configuration of note:
 *
 * <ul>
 *   <li>{@link StreamReadFeature#STRICT_DUPLICATE_DETECTION} is enabled on the comparison reader so
 *       duplicate object keys (a real signal in an evaluation) fail loudly rather than being
 *       silently collapsed.
 *   <li>{@link DeserializationFeature#FAIL_ON_TRAILING_TOKENS} is enabled so trailing garbage after
 *       a value is rejected.
 *   <li>Non-finite floating point values (NaN, Infinity) are rejected when parsing (the
 *       {@link JsonReadFeature#ALLOW_NON_NUMERIC_NUMBERS} read feature is disabled). Jackson has no
 *       equivalent throw-on-write for non-finite doubles, so the structural comparator additionally
 *       rejects in-memory non-finite values before comparison; this keeps Jackson's surprising
 *       {@code NaN == NaN} out of eval results.
 * </ul>
 *
 * <p>This class is internal API. It is not part of the public, supported surface of dokimos-core
 * and may change without notice.
 */
public final class Json {

    private static final ObjectMapper MAPPER;
    private static final ObjectReader READER;
    private static final ObjectReader COMPARISON_READER;
    private static final ObjectWriter COMPACT_WRITER;
    private static final ObjectWriter PRETTY_WRITER;

    static {
        MAPPER = JsonMapper.builder()
                // Reject NaN / Infinity tokens when parsing: Jackson's NaN == NaN is the
                // opposite of Java, which would silently corrupt structural comparison.
                .disable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();

        READER = MAPPER.reader();
        COMPARISON_READER = MAPPER.reader().with(StreamReadFeature.STRICT_DUPLICATE_DETECTION);
        COMPACT_WRITER = MAPPER.writer();
        PRETTY_WRITER = MAPPER.writerWithDefaultPrettyPrinter();
    }

    private Json() {}

    /**
     * A thread-safe reader over the shared, immutable mapper. Use for general value conversion.
     *
     * @return the shared {@link ObjectReader}
     */
    public static ObjectReader reader() {
        return READER;
    }

    /**
     * A thread-safe reader configured for structural comparison: strict duplicate-key detection is
     * enabled so {@code {"x":1,"x":2}} fails rather than silently collapsing.
     *
     * @return the shared comparison {@link ObjectReader}
     */
    public static ObjectReader comparisonReader() {
        return COMPARISON_READER;
    }

    /**
     * A thread-safe writer producing compact (single-line) JSON.
     *
     * @return the shared compact {@link ObjectWriter}
     */
    public static ObjectWriter compactWriter() {
        return COMPACT_WRITER;
    }

    /**
     * A thread-safe writer producing pretty-printed JSON.
     *
     * @return the shared pretty-printing {@link ObjectWriter}
     */
    public static ObjectWriter prettyWriter() {
        return PRETTY_WRITER;
    }

    /**
     * Converts a value into an instance of {@code type} without an intermediate textual round-trip.
     *
     * @param value the source value (may be {@code null})
     * @param type the target class
     * @param <T> the target type
     * @return the converted value, or {@code null} if {@code value} is {@code null}
     * @throws IllegalArgumentException if the value cannot be converted to {@code type}
     */
    public static <T> T convert(Object value, Class<T> type) {
        if (value == null) {
            return null;
        }
        return MAPPER.convertValue(value, type);
    }

    /**
     * Converts a value into an instance described by a Jackson {@link JavaType}, supporting generic
     * targets such as {@code List<Foo>}.
     *
     * @param value the source value (may be {@code null})
     * @param type the target Jackson type
     * @param <T> the target type
     * @return the converted value, or {@code null} if {@code value} is {@code null}
     * @throws IllegalArgumentException if the value cannot be converted to {@code type}
     */
    public static <T> T convert(Object value, JavaType type) {
        if (value == null) {
            return null;
        }
        return MAPPER.convertValue(value, type);
    }

    /**
     * Converts an arbitrary value to a {@link JsonNode} tree (the in-memory model the structural
     * comparator works against).
     *
     * @param value the source value (may be {@code null})
     * @return the value as a {@link JsonNode}; a {@code null} value yields a Jackson null node
     */
    public static JsonNode toNode(Object value) {
        return MAPPER.valueToTree(value);
    }

    /**
     * Serializes a value to compact (single-line) JSON.
     *
     * @param value the value to serialize
     * @return the compact JSON string
     * @throws IllegalArgumentException if the value cannot be serialized
     */
    public static String writeCompact(Object value) {
        try {
            return COMPACT_WRITER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize value to JSON", e);
        }
    }

    /**
     * Serializes a value to pretty-printed JSON.
     *
     * @param value the value to serialize
     * @return the pretty-printed JSON string
     * @throws IllegalArgumentException if the value cannot be serialized
     */
    public static String writePretty(Object value) {
        try {
            return PRETTY_WRITER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize value to JSON", e);
        }
    }

    /**
     * Parses a JSON string into an instance of {@code type}.
     *
     * <p>Backed by {@code reader().forType(type)}, which returns a fresh, type-bound view of the
     * shared immutable mapper without mutating it — so this is safe under the parallel experiment
     * runs dokimos-core performs.
     *
     * @param json the JSON text to parse
     * @param type the target class
     * @param <T> the target type
     * @return the parsed value (the JSON literal {@code null} parses to {@code null})
     * @throws IllegalArgumentException if the text is not valid JSON or does not match {@code type}
     */
    public static <T> T read(String json, Class<T> type) {
        return readInternal(json, READER.forType(type));
    }

    /**
     * Parses a JSON string into an instance described by a Jackson {@link JavaType}, supporting
     * generic targets such as {@code List<Foo>}.
     *
     * <p>Backed by {@code reader().forType(type)}, which returns a fresh, type-bound view of the
     * shared immutable mapper without mutating it — so this is safe under the parallel experiment
     * runs dokimos-core performs.
     *
     * @param json the JSON text to parse
     * @param type the target Jackson type
     * @param <T> the target type
     * @return the parsed value (the JSON literal {@code null} parses to {@code null})
     * @throws IllegalArgumentException if the text is not valid JSON or does not match {@code type}
     */
    public static <T> T read(String json, JavaType type) {
        return readInternal(json, READER.forType(type));
    }

    /**
     * Parses a JSON string into the generic target captured by a Jackson {@link TypeReference}, using
     * the strict {@linkplain #comparisonReader() comparison reader} so duplicate object keys are
     * rejected alongside the NaN/Infinity and trailing-token tightening that applies to every reader.
     *
     * <p>This is the load-path entry point for dataset and server JSON: malformed structured input
     * (a duplicate key, a {@code NaN} token, trailing garbage) fails here at parse time rather than
     * surfacing confusingly later during evaluation.
     *
     * @param json the JSON text to parse
     * @param type the captured generic target type
     * @param <T> the target type
     * @return the parsed value (the JSON literal {@code null} parses to {@code null})
     * @throws IllegalArgumentException if the text is not valid JSON or does not match {@code type}
     */
    public static <T> T read(String json, TypeReference<T> type) {
        return readInternal(json, COMPARISON_READER.forType(type));
    }

    private static <T> T readInternal(String json, ObjectReader reader) {
        try {
            return reader.readValue(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse JSON", e);
        }
    }

    /**
     * Resolves a Jackson {@link JavaType} from a generic {@link java.lang.reflect.Type}. Used to
     * bridge an {@code OutputType<T>}'s captured type into the conversion machinery.
     *
     * @param type the generic type to resolve
     * @return the corresponding Jackson {@link JavaType}
     */
    public static JavaType resolveType(java.lang.reflect.Type type) {
        return MAPPER.getTypeFactory().constructType(type);
    }
}
