package dev.dokimos.server.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit coverage for the span matching logic that drives online eval enqueueing. */
class TraceEvalRuleMatchTest {

    private TraceSpan span(String name, Map<String, Object> attributes) {
        TraceSpan span = new TraceSpan("trace-1", "span-1", name, Instant.now());
        span.setAttributes(attributes);
        return span;
    }

    private TraceEvalRule rule(TraceMatchType type, String key, String value) {
        TraceEvalRule rule = new TraceEvalRule(null, "r", type, value, null, "judge", "is good");
        rule.setMatchKey(key);
        return rule;
    }

    @Test
    void spanNameMatchesExactName() {
        TraceEvalRule rule = rule(TraceMatchType.SPAN_NAME, null, "llm.generate");
        assertThat(rule.matches(span("llm.generate", Map.of()))).isTrue();
        assertThat(rule.matches(span("other", Map.of()))).isFalse();
    }

    @Test
    void attributeMatchesKeyAndValue() {
        TraceEvalRule rule = rule(TraceMatchType.ATTRIBUTE, "gen_ai.system", "openai");
        assertThat(rule.matches(span("s", Map.of("gen_ai.system", "openai")))).isTrue();
        assertThat(rule.matches(span("s", Map.of("gen_ai.system", "anthropic"))))
                .isFalse();
    }

    @Test
    void attributeMatchComparesNonStringValuesByStringForm() {
        TraceEvalRule rule = rule(TraceMatchType.ATTRIBUTE, "http.status", "200");
        assertThat(rule.matches(span("s", Map.of("http.status", 200L)))).isTrue();
    }

    @Test
    void attributeMatchFailsWhenKeyAbsentOrAttributesNull() {
        TraceEvalRule rule = rule(TraceMatchType.ATTRIBUTE, "missing", "x");
        assertThat(rule.matches(span("s", Map.of("present", "x")))).isFalse();
        assertThat(rule.matches(span("s", null))).isFalse();
    }
}
