package dev.dokimos.kotlin.core

import dev.dokimos.core.EvalTestCase
import dev.dokimos.core.Example
import dev.dokimos.core.OutputType
import dev.dokimos.core.agents.ToolCall

/**
 * Captures a full generic type [T] (including its type arguments) into an [OutputType] super-type
 * token so it survives Kotlin's reified erasure.
 *
 * The reified read extensions below delegate to this rather than to `T::class.java` so that generic
 * targets such as `List<Whisky>` or `Map<String, Int>` round-trip correctly. The anonymous subclass
 * `object : OutputType<T>() {}` records [T] in its generic supertype exactly as the Java idiom
 * `new OutputType<List<Whisky>>() {}` does.
 *
 * @param T the captured target type
 * @return an [OutputType] token carrying the full generic type of [T]
 */
inline fun <reified T> outputType(): OutputType<T> = object : OutputType<T>() {}

/**
 * Reads the primary actual output (`"output"`) converted to the reified type [T].
 *
 * Delegates to [EvalTestCase.actualOutputAs] with an [outputType] token, so generic targets like
 * `List<Whisky>` survive erasure.
 *
 * @param T the target type
 * @return the converted value, or `null` if absent
 */
inline fun <reified T> EvalTestCase.actualOutputAs(): T? = actualOutputAs(outputType<T>())

/**
 * Reads the actual output under [key] converted to the reified type [T].
 *
 * @param key the actual-output key
 * @param T the target type
 * @return the converted value, or `null` if [key] is absent
 */
inline fun <reified T> EvalTestCase.actualOutputAs(key: String): T? = actualOutputAs(key, outputType<T>())

/**
 * Reads the primary expected output (`"output"`) converted to the reified type [T].
 *
 * @param T the target type
 * @return the converted value, or `null` if absent
 */
inline fun <reified T> EvalTestCase.expectedOutputAs(): T? = expectedOutputAs(outputType<T>())

/**
 * Reads the expected output under [key] converted to the reified type [T].
 *
 * @param key the expected-output key
 * @param T the target type
 * @return the converted value, or `null` if [key] is absent
 */
inline fun <reified T> EvalTestCase.expectedOutputAs(key: String): T? = expectedOutputAs(key, outputType<T>())

/**
 * Reads the primary input (`"input"`) converted to the reified type [T].
 *
 * @param T the target type
 * @return the converted value, or `null` if absent
 */
inline fun <reified T> EvalTestCase.inputAs(): T? = inputAs(outputType<T>())

/**
 * Reads the input under [key] converted to the reified type [T].
 *
 * @param key the input key
 * @param T the target type
 * @return the converted value, or `null` if [key] is absent
 */
inline fun <reified T> EvalTestCase.inputAs(key: String): T? = inputAs(key, outputType<T>())

/**
 * Reads the metadata under [key] converted to the reified type [T].
 *
 * @param key the metadata key
 * @param T the target type
 * @return the converted value, or `null` if [key] is absent
 */
inline fun <reified T> EvalTestCase.metadataAs(key: String): T? = metadataAs(key, outputType<T>())

/**
 * Reads the primary expected output (`"output"`) converted to the reified type [T].
 *
 * @param T the target type
 * @return the converted value, or `null` if absent
 */
inline fun <reified T> Example.expectedOutputAs(): T? = expectedOutputAs(outputType<T>())

/**
 * Reads the expected output under [key] converted to the reified type [T].
 *
 * @param key the expected-output key
 * @param T the target type
 * @return the converted value, or `null` if [key] is absent
 */
inline fun <reified T> Example.expectedOutputAs(key: String): T? = expectedOutputAs(key, outputType<T>())

/**
 * Reads the primary input (`"input"`) converted to the reified type [T].
 *
 * @param T the target type
 * @return the converted value, or `null` if absent
 */
inline fun <reified T> Example.inputAs(): T? = inputAs(outputType<T>())

/**
 * Reads the input under [key] converted to the reified type [T].
 *
 * @param key the input key
 * @param T the target type
 * @return the converted value, or `null` if [key] is absent
 */
inline fun <reified T> Example.inputAs(key: String): T? = inputAs(key, outputType<T>())

/**
 * Reads the metadata under [key] converted to the reified type [T].
 *
 * @param key the metadata key
 * @param T the target type
 * @return the converted value, or `null` if [key] is absent
 */
inline fun <reified T> Example.metadataAs(key: String): T? = metadataAs(key, outputType<T>())

/**
 * Deserializes this tool call's `result` string into the reified type [T].
 *
 * Delegates to [ToolCall.resultAs] with an [outputType] token, so generic targets like
 * `List<Order>` survive erasure. Like the Java method, a `null`/blank/JSON-null result yields `null`.
 *
 * @param T the target type
 * @return the deserialized result, or `null` if the result is `null`/blank/JSON null
 */
inline fun <reified T> ToolCall.resultAs(): T? = resultAs(outputType<T>())

/**
 * Converts this tool call's `arguments` map into the reified type [T].
 *
 * @param T the target type
 * @return the converted arguments
 */
inline fun <reified T> ToolCall.argumentsAs(): T = argumentsAs(outputType<T>())

/**
 * Reads the metadata under [key] converted to the reified type [T].
 *
 * @param key the metadata key
 * @param T the target type
 * @return the converted value, or `null` if [key] is absent
 */
inline fun <reified T> ToolCall.metadataAs(key: String): T? = metadataAs(key, outputType<T>())
