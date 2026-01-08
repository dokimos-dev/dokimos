package dev.dokimos.junit;

import dev.dokimos.core.Dataset;
import dev.dokimos.core.DatasetResolutionException;
import dev.dokimos.core.DatasetResolverRegistry;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.AnnotationConsumer;

import java.util.stream.Stream;

/**
 * JUnit ArgumentsProvider that loads {@code Example}s from a {@code Dataset}.
 */
public class DatasetArgumentsProvider implements ArgumentsProvider, AnnotationConsumer<DatasetSource> {

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
        return dataset.examples().stream()
                .map(Arguments::of);
    }

    private Dataset loadDataset() {
        if (!inlineJson.isBlank()) {
            return Dataset.fromJson(inlineJson);
        }

        if (!inlineJsonl.isBlank()) {
            return Dataset.fromJsonl(inlineJsonl);
        }

        if (!uri.isBlank()) {
            return DatasetResolverRegistry.getInstance().resolve(uri);
        }

        throw new DatasetResolutionException("Either `value()`, `json()`, or `jsonl()` must be specified in @DatasetSource");
    }
}
