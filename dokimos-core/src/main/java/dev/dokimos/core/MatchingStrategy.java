package dev.dokimos.core;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Strategy for determining if a retrieved item matches an expected item.
 * <p>
 * This abstraction supports various RAG use cases beyond simple document ID
 * matching,
 * including knowledge graph triples, API responses, and semantic matching.
 */
@FunctionalInterface
public interface MatchingStrategy {

    /**
     * Determines if a retrieved item matches an expected item.
     *
     * @param retrieved the item from the retrieval results
     * @param expected  the item from the ground truth
     * @return true if the items match
     */
    boolean matches(Object retrieved, Object expected);

    /**
     * Counts how many retrieved items match any expected item.
     * <p>
     * Default implementation iterates through all pairs. Implementations may
     * override for better performance with specific data structures.
     *
     * @param retrieved the retrieved items
     * @param expected  the expected (ground truth) items
     * @return the number of retrieved items that match at least one expected item
     */
    default long countMatches(Collection<?> retrieved, Collection<?> expected) {
        return retrieved.stream()
                .filter(r -> expected.stream().anyMatch(e -> matches(r, e)))
                .count();
    }

    // ========== Factory Methods ==========

    /**
     * Matches items using {@link Objects#equals(Object, Object)}.
     * <p>
     * Suitable for simple string IDs or objects with proper equals implementation.
     */
    static MatchingStrategy byEquality() {
        return Objects::equals;
    }

    /**
     * Matches items by extracting an identifier and comparing with equality.
     * <p>
     * Useful when items are complex objects but have a unique identifier.
     *
     * @param identifierExtractor function to extract identifier from an item
     * @return a matching strategy that compares extracted identifiers
     */
    static MatchingStrategy byIdentifier(Function<Object, Object> identifierExtractor) {
        return (retrieved, expected) -> {
            Object retrievedId = identifierExtractor.apply(retrieved);
            Object expectedId = identifierExtractor.apply(expected);
            return Objects.equals(retrievedId, expectedId);
        };
    }

    /**
     * Matches Map items by comparing a specific field.
     * <p>
     * Useful for JSON-like structures where items have an "id" or similar field.
     *
     * @param fieldName the field name to compare
     * @return a matching strategy that compares the specified field
     */
    static MatchingStrategy byField(String fieldName) {
        return byIdentifier(item -> {
            if (item instanceof Map<?, ?> map) {
                return map.get(fieldName);
            }
            return item;
        });
    }

    /**
     * Matches Map items by comparing multiple fields.
     * <p>
     * Useful for composite keys like knowledge graph triples (subject, predicate,
     * object).
     *
     * @param fieldNames the field names that must all match
     * @return a matching strategy that compares all specified fields
     */
    static MatchingStrategy byFields(String... fieldNames) {
        return (retrieved, expected) -> {
            if (!(retrieved instanceof Map<?, ?> rMap) || !(expected instanceof Map<?, ?> eMap)) {
                return Objects.equals(retrieved, expected);
            }
            for (String field : fieldNames) {
                if (!Objects.equals(rMap.get(field), eMap.get(field))) {
                    return false;
                }
            }
            return true;
        };
    }

    /**
     * Matches items using a custom predicate.
     *
     * @param predicate the matching predicate
     * @return a matching strategy using the predicate
     */
    static MatchingStrategy custom(BiPredicate<Object, Object> predicate) {
        return predicate::test;
    }

    /**
     * Matches string items case-insensitively.
     */
    static MatchingStrategy caseInsensitive() {
        return (retrieved, expected) -> {
            if (retrieved instanceof String r && expected instanceof String e) {
                return r.equalsIgnoreCase(e);
            }
            return Objects.equals(retrieved, expected);
        };
    }

    /**
     * Matches string items by checking if one contains the other.
     * <p>
     * Useful for partial text matching where retrieved chunks may be
     * substrings or superstrings of expected content.
     *
     * @param normalize whether to normalize whitespace and case
     * @return a matching strategy for containment matching
     */
    static MatchingStrategy byContainment(boolean normalize) {
        return (retrieved, expected) -> {
            if (!(retrieved instanceof String r) || !(expected instanceof String e)) {
                return Objects.equals(retrieved, expected);
            }
            if (normalize) {
                r = normalizeText(r);
                e = normalizeText(e);
            }
            return r.contains(e) || e.contains(r);
        };
    }

    /**
     * Creates an LLM-based matching strategy.
     * <p>
     * Uses an LLM to determine semantic equivalence between items.
     * This is the most flexible but also most expensive option.
     *
     * @param judge the LLM judge to use for matching
     * @return a matching strategy using LLM-based comparison
     */
    static MatchingStrategy llmBased(JudgeLM judge) {
        return new LlmMatchingStrategy(judge);
    }

    /**
     * Creates a composite strategy that matches if ANY of the strategies match.
     *
     * @param strategies the strategies to combine
     * @return a strategy that returns true if any sub-strategy matches
     */
    static MatchingStrategy anyOf(MatchingStrategy... strategies) {
        return (retrieved, expected) -> {
            for (MatchingStrategy strategy : strategies) {
                if (strategy.matches(retrieved, expected)) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * Creates a composite strategy that matches only if ALL strategies match.
     *
     * @param strategies the strategies to combine
     * @return a strategy that returns true only if all sub-strategies match
     */
    static MatchingStrategy allOf(MatchingStrategy... strategies) {
        return (retrieved, expected) -> {
            for (MatchingStrategy strategy : strategies) {
                if (!strategy.matches(retrieved, expected)) {
                    return false;
                }
            }
            return true;
        };
    }

    private static String normalizeText(String text) {
        return text.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
