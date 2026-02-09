package dev.dokimos.kotlin.core

import dev.dokimos.core.EvalTestCase

/**
 * Builds an [EvalTestCase]
 */
fun EvalTestCase(
    input: String,
    actualOutput: String,
    outputContext: List<String> = emptyList(),
    expectedOutput: String? = null,
    metadata: Map<String, Any> = emptyMap()
): EvalTestCase {
    val actualOutputs = buildMap {
        put("output", actualOutput)
        if (outputContext.isNotEmpty()) put("context", outputContext)
    }
    return EvalTestCase(
        mapOf("input" to input),
        actualOutputs,
        expectedOutput?.let { mapOf("output" to it) } ?: emptyMap(),
        metadata
    )
}


fun EvalTestCase(
    input: String,
    actualOutputs: Map<String, Any>,
    expectedOutput: Map<String, Any>  = emptyMap(),
    metadata: Map<String, Any> = emptyMap()): EvalTestCase {
    return EvalTestCase(
        mapOf("input" to input),
        actualOutputs,
        expectedOutput,
        metadata
    )
}