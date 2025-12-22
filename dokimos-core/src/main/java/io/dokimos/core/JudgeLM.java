package io.dokimos.core;

/**
 * A language model used for evaluation.
 * <p>
 * Implementations generate text responses from prompts, typically by calling
 * an LLM to perform judgments on test outputs.
 */
@FunctionalInterface
public interface JudgeLM {
    String generate(String prompt);
}
