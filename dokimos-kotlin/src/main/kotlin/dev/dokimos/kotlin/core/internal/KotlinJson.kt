package dev.dokimos.kotlin.core.internal

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * Framework-internal, Kotlin-aware JSON utilities for the reified typed-read extensions.
 *
 * dokimos-core's `internal.Json` mapper is deliberately Java-only — it registers no Kotlin module, so
 * it cannot construct a plain (unannotated) Kotlin data class, whose constructor parameter names are
 * invisible to Jackson without the Kotlin module. The Kotlin reified extensions therefore convert raw
 * values through this mapper instead of delegating to the core accessor.
 *
 * The [ObjectMapper] is built once via [jacksonObjectMapper] (which registers `KotlinModule`),
 * stored as an immutable singleton, and never mutated afterwards — so it is safe to share across the
 * parallel experiment runs Dokimos performs. Only the thread-safe [convertValue] and reader views are
 * exercised here.
 */
@PublishedApi
internal object KotlinJson {

    private val MAPPER: ObjectMapper = jacksonObjectMapper()

    /**
     * Converts an already-deserialized [value] (typically a `Map` or `List` read from a test case) into
     * an instance described by the Jackson [type], without an intermediate textual round-trip.
     *
     * @param value the source value, or `null`
     * @param type the target Jackson type
     * @return the converted value, or `null` if [value] is `null`
     */
    fun <T> convert(value: Any?, type: JavaType): T? {
        if (value == null) {
            return null
        }
        return MAPPER.convertValue(value, type)
    }

    /**
     * Parses a JSON [json] string into an instance described by the Jackson [type].
     *
     * @param json the JSON text to parse
     * @param type the target Jackson type
     * @return the parsed value (the JSON literal `null` parses to `null`)
     */
    fun <T> read(json: String, type: JavaType): T? = MAPPER.readValue(json, type)

    /**
     * Resolves a reflective [type] (the generic [java.lang.reflect.Type] captured by an `OutputType`
     * token) into a Jackson [JavaType], so generic targets such as `List<Foo>` survive erasure.
     *
     * @param type the generic type to resolve
     * @return the corresponding Jackson type
     */
    @PublishedApi
    internal fun resolveType(type: java.lang.reflect.Type): JavaType = MAPPER.typeFactory.constructType(type)
}
