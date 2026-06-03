package dev.dokimos.examples.springai.agent;

import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * The agent's tool surface: a single {@code searchWhiskies} tool over the {@link WhiskyCatalog}.
 *
 * <p>Spring AI turns the {@link Tool @Tool} method into a callable tool the model can invoke; its
 * name, description, and JSON schema come straight from the annotation and parameter metadata.
 */
public class WhiskyTools {

    private final WhiskyCatalog catalog;

    /**
     * Creates the tool surface over the given catalog.
     *
     * @param catalog the catalog to search
     */
    public WhiskyTools(WhiskyCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Searches the whisky catalog. Exposed to the agent as the {@code searchWhiskies} tool.
     *
     * @param query free-text search terms such as {@code "peaty Islay"}
     * @return whiskies matching the query
     */
    @Tool(name = "searchWhiskies", description = "Search the whisky catalog by name, region, or flavor terms.")
    public List<Whisky> searchWhiskies(
            @ToolParam(description = "Free-text search terms, e.g. 'peaty Islay'") String query) {
        return catalog.search(query);
    }
}
