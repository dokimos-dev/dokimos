package io.dokimos.core;

@FunctionalInterface
public interface JudgeLM {
    String generate(String prompt);
}
