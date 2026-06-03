package dev.dokimos.kotlin.core

import dev.dokimos.core.EvalResult
import dev.dokimos.core.EvalTestCase

/**
 * Builds an [EvalTestCase]
 */
fun EvalTestCase(
    input: String,
    actualOutput: String,
    outputContext: List<String> = emptyList(),
    expectedOutput: String? = null,
    metadata: Map<String, Any> = emptyMap(),
): EvalTestCase {
    val actualOutputs = buildMap {
        put("output", actualOutput)
        if (outputContext.isNotEmpty()) put("context", outputContext)
    }
    return EvalTestCase(
        mapOf("input" to input),
        actualOutputs,
        expectedOutput?.let { mapOf("output" to it) } ?: emptyMap(),
        metadata,
    )
}

/**
 * Builds an [EvalTestCase]
 */
fun EvalTestCase(
    input: String,
    actualOutputs: Map<String, Any>,
    expectedOutputs: Map<String, Any> = emptyMap(),
    metadata: Map<String, Any> = emptyMap(),
): EvalTestCase = EvalTestCase(
    mapOf("input" to input),
    actualOutputs,
    expectedOutputs,
    metadata,
)

/**
 * Builds an [EvalTestCase]
 */
fun EvalTestCase(
    input: String,
    actualOutput: String,
    actualOutputs: Map<String, Any> = emptyMap(),
    expectedOutputs: Map<String, Any> = emptyMap(),
    metadata: Map<String, Any> = emptyMap(),
): EvalTestCase = EvalTestCase(
    mapOf("input" to input),
    actualOutputs + mapOf("output" to actualOutput),
    expectedOutputs,
    metadata,
)

/**
 * Builds an [EvalTestCase]
 */
fun EvalTestCase(actualOutputs: Map<String, Any>): EvalTestCase =
    EvalTestCase.builder().actualOutputs(actualOutputs).build()

/**
 * Builds an [EvalTestCase] via the Java builder.
 *
 * An unambiguous 2-arg factory for the common case, avoiding the overload
 * ambiguity of calling [EvalTestCase] with two positional [String] arguments.
 */
fun evalCase(input: String, actualOutput: String, expectedOutput: String? = null): EvalTestCase {
    val builder = EvalTestCase.builder()
        .input(input)
        .actualOutput(actualOutput)
    expectedOutput?.let { builder.expectedOutput(it) }
    return builder.build()
}

/**
 * Builds an [EvalResult]
 */
fun EvalResult(name: String, score: Double, threshold: Double, reason: String) =
    EvalResult.of(name, score, threshold, reason)

/**
 * Builds an [EvalResult]
 */
fun EvalResult(name: String, score: Double, success: Boolean, reason: String) =
    if (success) EvalResult.success(name, score, reason) else EvalResult.failure(name, score, reason)
