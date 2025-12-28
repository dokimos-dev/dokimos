package dev.dokimos.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A collection of examples for evaluation.
 *
 * @param name        the dataset name
 * @param description the dataset description
 * @param examples    the examples in the dataset
 */
public record Dataset(String name, String description, List<Example> examples) implements Iterable<Example> {

    public Dataset(String name, String description, List<Example> examples) {
        this.name = name;
        this.description = description;
        this.examples = List.copyOf(examples);
    }

    /**
     * Creates a new builder for constructing datasets.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Loads a dataset from a JSON file.
     *
     * @param path the file path
     * @return the loaded dataset
     * @throws IOException if reading the file fails
     */
    public static Dataset fromJson(Path path) throws IOException {
        String content = Files.readString(path);
        return fromJson(content);
    }

    /**
     * Parses a dataset from a JSON string.
     *
     * @param json the JSON string
     * @return the parsed dataset
     */
    public static Dataset fromJson(String json) {
        return DatasetParser.parseJson(json);
    }

    /**
     * Loads a dataset from a CSV file.
     * Expects headers: input,expectedOutput, plus optional metadata columns.
     *
     * @param path the file path
     * @return the loaded dataset
     * @throws IOException if reading the file fails
     */
    public static Dataset fromCsv(Path path) throws IOException {
        String content = Files.readString(path);
        return fromCsv(content, path.getFileName().toString().replace(".csv", ""));
    }

    /**
     * Parses a dataset from a CSV string.
     *
     * @param csv  the CSV content
     * @param name the dataset name
     * @return the parsed dataset
     */
    public static Dataset fromCsv(String csv, String name) {
        return DatasetParser.parseCsv(csv, name);
    }

    /**
     * Returns the dataset's name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the dataset's description.
     *
     * @return the description
     */
    public String description() {
        return description;
    }

    /**
     * Returns the examples in the dataset.
     *
     * @return the examples list
     */
    public List<Example> examples() {
        return examples;
    }

    /**
     * Returns the number of examples in the dataset.
     *
     * @return the size
     */
    public int size() {
        return examples.size();
    }

    /**
     * Returns the example at the specified index.
     *
     * @param index the example index
     * @return the example
     */
    public Example get(int index) {
        return examples.get(index);
    }

    @Override
    public Iterator<Example> iterator() {
        return examples.iterator();
    }

    /**
     * Builder for constructing datasets.
     */
    public static class Builder {
        private final List<Example> examples = new ArrayList<>();
        private String name = "unnamed";
        private String description = "";

        /**
         * Sets the dataset name.
         *
         * @param name the dataset name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the dataset description.
         *
         * @param description the description
         * @return this builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Adds an example to the dataset.
         *
         * @param example the example to add
         * @return this builder
         */
        public Builder addExample(Example example) {
            this.examples.add(example);
            return this;
        }

        /**
         * Adds multiple examples to the dataset.
         *
         * @param examples the examples to add
         * @return this builder
         */
        public Builder addExamples(List<Example> examples) {
            this.examples.addAll(examples);
            return this;
        }

        /**
         * Builds the dataset.
         *
         * @return a new dataset
         */
        public Dataset build() {
            return new Dataset(name, description, examples);
        }
    }

}
