package dev.dokimos.kotlin.core

import dev.dokimos.core.EvalTestCase
import kotlin.Any
import kotlin.String

/**
 * Builds an [EvalTestCase]
 */
fun EvalTestCase(
    input: String,
    actualOutput: String,
    expectedOutput: String? = null,
    metadata: Map<String, Any> = emptyMap()
): EvalTestCase {
    return EvalTestCase(
        mapOf("input" to input),
        mapOf("output" to actualOutput),
        expectedOutput?.let { mapOf("output" to it) } ?: emptyMap(),
        metadata
    )
}
