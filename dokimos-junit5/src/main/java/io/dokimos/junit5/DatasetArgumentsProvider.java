package io.dokimos.junit5;

import io.dokimos.core.Dataset;
import io.dokimos.core.DatasetResolutionException;
import io.dokimos.core.DatasetResolverRegistry;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.AnnotationConsumer;

import java.util.stream.Stream;

/**
 * JUnit 5 ArgumentsProvider that loads {@code Example}s from a {@code Dataset}.
 */
public class DatasetArgumentsProvider implements ArgumentsProvider, AnnotationConsumer<DatasetSource> {

    private String uri;
    private String inlineJson;

    @Override
    public void accept(DatasetSource annotation) {
        this.uri = annotation.value();
        this.inlineJson = annotation.json();
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

        if (!uri.isBlank()) {
            return DatasetResolverRegistry.getInstance().resolve(uri);
        }

        throw new DatasetResolutionException("Either `value()` or `json()` must be specified in @DatasetSource");
    }
}
