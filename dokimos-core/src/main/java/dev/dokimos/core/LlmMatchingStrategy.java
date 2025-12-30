package dev.dokimos.core;

/**
 * A matching strategy that uses an LLM to determine semantic equivalence.
 * <p>
 * This is the most flexible matching approach but also the most expensive,
 * as it requires an LLM call for each comparison.
 */
class LlmMatchingStrategy implements MatchingStrategy {

    private final JudgeLM judge;

    LlmMatchingStrategy(JudgeLM judge) {
        this.judge = judge;
    }

    @Override
    public boolean matches(Object retrieved, Object expected) {
        String prompt = """
                Determine if the following two items represent the same information or are semantically equivalent.

                RETRIEVED ITEM:
                %s

                EXPECTED ITEM:
                %s

                Consider them a match if:
                - They refer to the same document, entity, or concept
                - One is a paraphrase or reformatting of the other
                - They contain the same essential information

                Respond with ONLY "yes" or "no".
                """.formatted(retrieved, expected);

        String response = LlmResponseUtils.stripMarkdown(judge.generate(prompt));
        return response != null && response.trim().toLowerCase().startsWith("yes");
    }
}
