package dev.dokimos.kotlin.core

import com.fasterxml.jackson.databind.JavaType
import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.Example
import dev.dokimos.core.OutputType
import dev.dokimos.core.agents.ToolCall
import dev.dokimos.core.exceptions.DokimosTypeConversionException
import dev.dokimos.kotlin.core.internal.KotlinJson

/**
 * Captures a full generic type [T] (including its type arguments) into an [OutputType] super-type
 * token so it survives Kotlin's reified erasure.
 *
 * The reified read extensions below use the [Type][OutputType.getType] this token records to resolve a
 * Jackson `JavaType`, so generic targets such as `List<Whisky>` or `Map<String, Int>` round-trip
 * correctly. The anonymous subclass `object : OutputType<T>() {}` records [T] in its generic supertype
 * exactly as the Java idiom `new OutputType<List<Whisky>>() {}` does.
 *
 * @param T the captured target type
 * @return an [OutputType] token carrying the full generic type of [T]
 */
inline fun <reified T> outputType(): OutputType<T> = object : OutputType<T>() {}

/**
 * Resolves the Jackson [JavaType] for a reified target [T] via an [outputType] token, so generic
 * targets survive erasure. Published so the inline extensions below can reference it; not part of the
 * supported API surface.
 *
 * @param T the captured target type
 * @return the resolved Jackson type
 */
@PublishedApi
internal inline fun <reified T> javaTypeOf(): JavaType = KotlinJson.resolveType(outputType<T>().type)

/**
 * Converts an already-deserialized [value] into [type] through the Kotlin-aware mapper, wrapping any
 * conversion failure in [DokimosTypeConversionException]. An absent or `null` [value] yields `null`.
 *
 * dokimos-core stays Java-only, so this routes through [KotlinJson] (which registers the Kotlin
 * module) rather than the core accessor, letting a plain unannotated Kotlin data class read back.
 */
@PublishedApi
internal fun <T> convertValue(value: Any?, type: JavaType): T? {
    if (value == null) {
        return null
    }
    return try {
        KotlinJson.convert(value, type)
    } catch (e: IllegalArgumentException) {
        throw DokimosTypeConversionException("Failed to convert value to $type", e)
    }
}

/**
 * Parses [json] into [type] through the Kotlin-aware mapper, wrapping any failure in
 * [DokimosTypeConversionException]. A `null` or blank [json] yields `null`.
 */
@PublishedApi
internal fun <T> readJson(json: String?, type: JavaType): T? {
    if (json.isNullOrBlank()) {
        return null
    }
    return try {
        KotlinJson.read(json, type)
    } catch (e: Exception) {
        throw DokimosTypeConversionException("Failed to parse JSON as $type", e)
    }
}

/**
 * Reads the primary actual output (`"output"`) converted to the reified type [T].
 *
 * Converts the raw value through the Kotlin-aware mapper, so generic targets like `List<Whisky>`
 * survive erasure and a plain unannotated Kotlin data class reads back.
 *
 * @param T the target type
 * @return the converted value, or `null` if absent
 */
inline fun <reified T> EvalTestCase.actualOutputAs(): T? = actualOutputAs("output")

/**
 * Reads the actual output under [key] converted to the reified type [T].
 *
 * @param key the actual-output key
 * @param T the target type
 * @return the converted value, or `null` if [key] is absent
 */
inline fun <reified T> EvalTestCase.actualOutputAs(key: String): T? =
    convertValue(actualOutputs()[key], javaTypeOf<T>())

/**
 * Reads the primary expected output (`"output"`) converted to the reified type [T].
 *
 * @param T the target type
 * @return the converted value, or `null` if absent
 */
inline fun <reified T> EvalTestCase.expectedOutputAs(): T? = expectedOutputAs("output")

/**
 * Reads the expected output under [key] converted to the reified type [T].
 *
 * @param key the expected-output key
 * @param T the target type
 * @return the converted value, or `null` if [key] is absent
 */
inline fun <reified T> EvalTestCase.expectedOutputAs(key: String): T? =
    convertValue(expectedOutputs()[key], javaTypeOf<T>())

/**
 * Reads the primary input (`"input"`) converted to the reified type [T].
 *
 * @param T the target type
 * @return the converted value, or `null` if absent
 */
inline fun <reified T> EvalTestCase.inputAs(): T? = inputAs("input")

/**
 * Reads the input under [key] converted to the reified type [T].
 *
 * @param key the input key
 * @param T the target type
 * @return the converted value, or `null` if [key] is absent
 */
inline fun <reified T> EvalTestCase.inputAs(key: String): T? = convertValue(inputs()[key], javaTypeOf<T>())

/**
 * Reads the metadata under [key] converted to the reified type [T].
 *
 * @param key the metadata key
 * @param T the target type
 * @return the converted value, or `null` if [key] is absent
 */
inline fun <reified T> EvalTestCase.metadataAs(key: String): T? = convertValue(metadata()[key], javaTypeOf<T>())

/**
 * Reads the primary expected output (`"output"`) converted to the reified type [T].
 *
 * @param T the target type
 * @return the converted value, or `null` if absent
 */
inline fun <reified T> Example.expectedOutputAs(): T? = expectedOutputAs("output")

/**
 * Reads the expected output under [key] converted to the reified type [T].
 *
 * @param key the expected-output key
 * @param T the target type
 * @return the converted value, or `null` if [key] is absent
 */
inline fun <reified T> Example.expectedOutputAs(key: String): T? = convertValue(expectedOutputs()[key], javaTypeOf<T>())

/**
 * Reads the primary input (`"input"`) converted to the reified type [T].
 *
 * @param T the target type
 * @return the converted value, or `null` if absent
 */
inline fun <reified T> Example.inputAs(): T? = inputAs("input")

/**
 * Reads the input under [key] converted to the reified type [T].
 *
 * @param key the input key
 * @param T the target type
 * @return the converted value, or `null` if [key] is absent
 */
inline fun <reified T> Example.inputAs(key: String): T? = convertValue(inputs()[key], javaTypeOf<T>())

/**
 * Reads the metadata under [key] converted to the reified type [T].
 *
 * @param key the metadata key
 * @param T the target type
 * @return the converted value, or `null` if [key] is absent
 */
inline fun <reified T> Example.metadataAs(key: String): T? = convertValue(metadata()[key], javaTypeOf<T>())

/**
 * Deserializes this tool call's `result` string into the reified type [T].
 *
 * Parses the result JSON through the Kotlin-aware mapper, so generic targets like `List<Order>`
 * survive erasure and a plain unannotated Kotlin data class reads back. Like the Java method, a
 * `null`/blank/JSON-null result yields `null`.
 *
 * @param T the target type
 * @return the deserialized result, or `null` if the result is `null`/blank/JSON null
 */
inline fun <reified T> ToolCall.resultAs(): T? = readJson(result(), javaTypeOf<T>())

/**
 * Converts this tool call's `arguments` map into the reified type [T].
 *
 * @param T the target type
 * @return the converted arguments
 */
inline fun <reified T> ToolCall.argumentsAs(): T = convertValue<T>(arguments(), javaTypeOf<T>()) as T

/**
 * Reads the metadata under [key] converted to the reified type [T].
 *
 * @param key the metadata key
 * @param T the target type
 * @return the converted value, or `null` if [key] is absent
 */
inline fun <reified T> ToolCall.metadataAs(key: String): T? = convertValue(metadata()[key], javaTypeOf<T>())
