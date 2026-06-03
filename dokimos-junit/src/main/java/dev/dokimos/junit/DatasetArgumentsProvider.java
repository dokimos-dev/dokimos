package dev.dokimos.junit;

import dev.dokimos.core.Dataset;
import dev.dokimos.core.DatasetResolutionException;
import dev.dokimos.core.DatasetResolverRegistry;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.AnnotationConsumer;

/**
 * JUnit ArgumentsProvider that loads {@code Example}s from a {@code Dataset}.
 */
public class DatasetArgumentsProvider implements ArgumentsProvider, AnnotationConsumer<DatasetSource> {

    /**
     * Namespace under which the resolved dataset is stashed for {@link DatasetRunExtension}.
     */
    static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(DatasetArgumentsProvider.class);

    /**
     * Store key for the resolved {@link Dataset}.
     */
    static final String DATASET_KEY = "dataset";

    private String uri;
    private String inlineJson;
    private String inlineJsonl;

    @Override
    public void accept(DatasetSource annotation) {
        this.uri = annotation.value();
        this.inlineJson = annotation.json();
        this.inlineJsonl = annotation.jsonl();
    }

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
        Dataset dataset = loadDataset();
        context.getStore(NAMESPACE).put(DATASET_KEY, dataset);
        return dataset.examples().stream().map(Arguments::of);
    }

    private Dataset loadDataset() {
        if (!inlineJson.isBlank()) {
            return load("json()", "inline JSON", () -> Dataset.fromJson(inlineJson));
        }

        if (!inlineJsonl.isBlank()) {
            return load("jsonl()", "inline JSONL", () -> Dataset.fromJsonl(inlineJsonl));
        }

        if (!uri.isBlank()) {
            return load(
                    "value()", uri, () -> DatasetResolverRegistry.getInstance().resolve(uri));
        }

        throw new DatasetResolutionException(
                "Either `value()`, `json()`, or `jsonl()` must be specified in @DatasetSource");
    }

    /**
     * Loads a dataset, wrapping any failure in a message that names {@code @DatasetSource}, the
     * offending source, and the underlying cause so the JUnit user sees the real reason instead of an
     * opaque arguments-provider error.
     */
    private Dataset load(String attribute, String source, Supplier<Dataset> loader) {
        try {
            return loader.get();
        } catch (RuntimeException e) {
            throw new DatasetResolutionException(
                    "Failed to load dataset for @DatasetSource " + attribute + " '" + source + "': " + e.getMessage(),
                    e);
        }
    }
}
