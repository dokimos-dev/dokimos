package io.dokimos.core;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public record Dataset(String name, String description, List<Example> examples) implements Iterable<Example> {

    public Dataset(String name, String description, List<Example> examples) {
        this.name = name;
        this.description = description;
        this.examples = List.copyOf(examples);
    }

    @NotNull
    @Override
    public Iterator<Example> iterator() {
        return examples.iterator();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Load the dataset from a JSON file.
     */
    public static Dataset fromJson(Path path) throws IOException {
        String content = Files.readString(path);
        return fromJson(content);
    }

    /**
     * Load the dataset from a JSON string.
     */
    public static Dataset fromJson(String json) {
        return DatasetParser.parseJson(json);
    }

    /**
     * Load dataset from a CSV file.
     * Expects headers: input,expectedOutput, plus optional metadata columns.
     */
    public static Dataset fromCsv(Path path) throws IOException {
        String content = Files.readString(path);
        return fromCsv(content, path.getFileName().toString().replace(".csv", ""));
    }

    /**
     * Load dataset from a CSV string.
     */
    public static Dataset fromCsv(String csv, String name) {
        return DatasetParser.parseCsv(csv, name);
    }

    public static class Builder {
        private String name = "unnamed";
        private String description = "";
        private final List<Example> examples = new ArrayList<>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder addExample(Example example) {
            this.examples.add(example);
            return this;
        }

        public Builder addExamples(List<Example> examples) {
            this.examples.addAll(examples);
            return this;
        }

        public Dataset build() {
            return new Dataset(name, description, examples);
        }
    }

}
