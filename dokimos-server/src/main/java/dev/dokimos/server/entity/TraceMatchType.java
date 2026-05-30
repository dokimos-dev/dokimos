package dev.dokimos.server.entity;

/**
 * How a {@link TraceEvalRule} decides whether a span matches. {@code SPAN_NAME} tests the rule's match
 * value against the span name; {@code ATTRIBUTE} tests it against the span attribute named by the rule's
 * match key. Stored as a string in the {@code trace_eval_rules.match_type} column.
 */
public enum TraceMatchType {
    SPAN_NAME,
    ATTRIBUTE
}
