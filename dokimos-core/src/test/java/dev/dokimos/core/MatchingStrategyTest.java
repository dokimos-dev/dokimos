package dev.dokimos.core;


import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingStrategyTest {

    @Test
    void byEqualityShouldMatchEqualObjects() {
        var strategy = MatchingStrategy.byEquality();

        assertThat(strategy.matches("doc_1", "doc_1")).isTrue();
        assertThat(strategy.matches("doc_1", "doc_2")).isFalse();
        assertThat(strategy.matches(123, 123)).isTrue();
        assertThat(strategy.matches(null, null)).isTrue();
        assertThat(strategy.matches("doc_1", null)).isFalse();
    }

    @Test
    void caseInsensitiveShouldIgnoreCase() {
        var strategy = MatchingStrategy.caseInsensitive();

        assertThat(strategy.matches("DOC_1", "doc_1")).isTrue();
        assertThat(strategy.matches("Doc_1", "DOC_1")).isTrue();
        assertThat(strategy.matches("doc_1", "doc_2")).isFalse();
        // Non-strings fall back to equality
        assertThat(strategy.matches(123, 123)).isTrue();
    }

    @Test
    void byFieldShouldCompareSpecificField() {
        var strategy = MatchingStrategy.byField("id");

        var item1 = Map.of("id", "doc_1", "score", 0.9);
        var item2 = Map.of("id", "doc_1", "score", 0.5);
        var item3 = Map.of("id", "doc_2", "score", 0.9);

        assertThat(strategy.matches(item1, item2)).isTrue();
        assertThat(strategy.matches(item1, item3)).isFalse();
    }

    @Test
    void byFieldShouldHandleNonMaps() {
        var strategy = MatchingStrategy.byField("id");

        // Non-maps fall back to equality
        assertThat(strategy.matches("doc_1", "doc_1")).isTrue();
        assertThat(strategy.matches("doc_1", "doc_2")).isFalse();
    }

    @Test
    void byFieldsShouldCompareMultipleFields() {
        var strategy = MatchingStrategy.byFields("subject", "predicate", "object");

        var triple1 = Map.of("subject", "A", "predicate", "likes", "object", "B");
        var triple2 = Map.of("subject", "A", "predicate", "likes", "object", "B", "confidence", 0.9);
        var triple3 = Map.of("subject", "A", "predicate", "hates", "object", "B");

        assertThat(strategy.matches(triple1, triple2)).isTrue(); // Extra field ignored
        assertThat(strategy.matches(triple1, triple3)).isFalse(); // Different predicate
    }

    @Test
    void byIdentifierShouldUseExtractor() {
        var strategy = MatchingStrategy.byIdentifier(item -> {
            if (item instanceof String s) {
                return s.toLowerCase();
            }
            if (item instanceof Map<?, ?> m) {
                return m.get("id");
            }
            return item;
        });

        assertThat(strategy.matches("DOC_1", "doc_1")).isTrue();
        assertThat(strategy.matches(Map.of("id", "x"), Map.of("id", "x", "extra", "y"))).isTrue();
    }

    @Test
    void byContainmentShouldMatchSubstrings() {
        var strategy = MatchingStrategy.byContainment(false);

        assertThat(strategy.matches("hello world", "world")).isTrue();
        assertThat(strategy.matches("world", "hello world")).isTrue();
        assertThat(strategy.matches("hello", "world")).isFalse();
    }

    @Test
    void byContainmentWithNormalizeShouldIgnoreCaseAndWhitespace() {
        var strategy = MatchingStrategy.byContainment(true);

        assertThat(strategy.matches("Hello   World", "hello world")).isTrue();
        assertThat(strategy.matches("HELLO WORLD", "world")).isTrue();
    }

    @Test
    void customShouldUseProvidedPredicate() {
        var strategy = MatchingStrategy.custom((a, b) -> a.toString().length() == b.toString().length());

        assertThat(strategy.matches("abc", "xyz")).isTrue();
        assertThat(strategy.matches("ab", "xyz")).isFalse();
    }

    @Test
    void anyOfShouldMatchIfAnyStrategyMatches() {
        var strategy = MatchingStrategy.anyOf(
                MatchingStrategy.byEquality(),
                MatchingStrategy.caseInsensitive());

        assertThat(strategy.matches("doc_1", "doc_1")).isTrue();
        assertThat(strategy.matches("DOC_1", "doc_1")).isTrue();
        assertThat(strategy.matches("doc_1", "doc_2")).isFalse();
    }

    @Test
    void allOfShouldMatchOnlyIfAllStrategiesMatch() {
        var strategy = MatchingStrategy.allOf(
                MatchingStrategy.byField("type"),
                MatchingStrategy.byField("id"));

        var item1 = Map.of("type", "doc", "id", "1");
        var item2 = Map.of("type", "doc", "id", "1");
        var item3 = Map.of("type", "doc", "id", "2");
        var item4 = Map.of("type", "image", "id", "1");

        assertThat(strategy.matches(item1, item2)).isTrue();
        assertThat(strategy.matches(item1, item3)).isFalse();
        assertThat(strategy.matches(item1, item4)).isFalse();
    }

    @Test
    void countMatchesShouldCountCorrectly() {
        var strategy = MatchingStrategy.byEquality();

        var retrieved = List.of("a", "b", "c", "d");
        var expected = List.of("b", "d", "e");

        long matches = strategy.countMatches(retrieved, expected);

        assertThat(matches).isEqualTo(2); // b and d
    }

    @Test
    void countMatchesShouldHandleEmptyCollections() {
        var strategy = MatchingStrategy.byEquality();

        assertThat(strategy.countMatches(List.of(), List.of("a", "b"))).isEqualTo(0);
        assertThat(strategy.countMatches(List.of("a", "b"), List.of())).isEqualTo(0);
        assertThat(strategy.countMatches(List.of(), List.of())).isEqualTo(0);
    }

    @Test
    void llmBasedShouldUseLlmForMatching() {
        // Mock judge that says yes if items contain same digits
        JudgeLM mockJudge = prompt -> {
            if (prompt.contains("doc_1") && prompt.contains("document-1")) {
                return "yes";
            }
            return "no";
        };

        var strategy = MatchingStrategy.llmBased(mockJudge);

        assertThat(strategy.matches("doc_1", "document-1")).isTrue();
        assertThat(strategy.matches("doc_1", "doc_2")).isFalse();
    }
}
