package dev.dokimos.examples.springai.agent;

import java.util.List;
import java.util.Locale;

/**
 * An in-memory whisky catalog with a tiny keyword search. Backs the Spring AI tool the agent calls.
 */
public class WhiskyCatalog {

    private final List<Whisky> whiskies = List.of(
            new Whisky("lp10", "Laphroaig 10", "Islay", 10, true),
            new Whisky("ard12", "Ardbeg An Oa", "Islay", 12, true),
            new Whisky("lag16", "Lagavulin 16", "Islay", 16, true),
            new Whisky("glf12", "Glenfiddich 12", "Speyside", 12, false),
            new Whisky("mac12", "Macallan 12", "Speyside", 12, false));

    /**
     * Returns whiskies whose name or region contains any whitespace-separated term of {@code query},
     * case-insensitively. A blank query returns the whole catalog.
     *
     * @param query free-text search such as {@code "peaty Islay 12"}
     * @return the matching whiskies, never null
     */
    public List<Whisky> search(String query) {
        if (query == null || query.isBlank()) {
            return whiskies;
        }
        String[] terms = query.toLowerCase(Locale.ROOT).split("\\s+");
        return whiskies.stream()
                .filter(w -> {
                    String haystack = (w.name() + " " + w.region()).toLowerCase(Locale.ROOT);
                    for (String term : terms) {
                        if (haystack.contains(term)) {
                            return true;
                        }
                    }
                    return false;
                })
                .toList();
    }
}
