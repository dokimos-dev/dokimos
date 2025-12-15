package io.dokimos.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class DatasetParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DatasetParser() {}

    public static Dataset parseJson(String json) {
        try {
            Map<String, Object> root = MAPPER.readValue(json, new TypeReference<>() {});

            String name = (String) root.getOrDefault("name", "unnamed");
            String description = (String) root.getOrDefault("description", "");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawExamples = (List<Map<String, Object>>) root.get("examples");

            if (rawExamples == null) {
                throw new IllegalArgumentException("JSON dataset must contain 'examples' array!");
            }

            List<Example> examples = rawExamples.stream()
                    .map(DatasetParser::parseExample)
                    .toList();

            return Dataset.builder()
                    .name(name)
                    .description(description)
                    .addExamples(examples)
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse JSON content of dataset", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Example parseExample(Map<String, Object> raw) {
        Map<String, Object> inputs = (Map<String, Object>) raw.getOrDefault("inputs", Map.of());
        Map<String, Object> expectedOutputs = (Map<String, Object>) raw.getOrDefault("expectedOutputs", Map.of());
        Map<String, Object> metadata = (Map<String, Object>) raw.getOrDefault("metadata", Map.of());

        // Support "input" and "expectedOutput" as top-level strings
        if (inputs.isEmpty() && raw.containsKey("input")) {
            inputs = Map.of("input", raw.get("input"));
        }
        if (expectedOutputs.isEmpty() && raw.containsKey("expectedOutput")) {
            expectedOutputs = Map.of("output", raw.get("expectedOutput"));
        }

        return new Example(inputs, expectedOutputs, metadata);
    }

    public static Dataset parseCsv(String csv, String name) {
        return parseCsv(csv, name, ',');
    }

    public static Dataset parseCsv(String csv, String name, char delimiter) {
        try (BufferedReader reader = new BufferedReader(new StringReader(csv))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("The provided CSV is empty");
            }

            String[] headers = headerLine.split(Pattern.quote(String.valueOf(delimiter)));
            int inputIdx = findColumnIndex(headers, "input");
            int outputIdx = findColumnIndex(headers, "expectedOutput", "expected_output", "output");

            if (inputIdx == -1) {
                throw new IllegalArgumentException("CSV must have an 'input' column");
            }

            List<Example> examples = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                String[] values = parseCsvLine(line, delimiter);

                Map<String, Object> inputs = new HashMap<>();
                Map<String, Object> expectedOutputs = new HashMap<>();
                Map<String, Object> metadata = new HashMap<>();

                for (int i = 0; i < headers.length && i < values.length; i++) {
                    String header = headers[i].trim();
                    String value = values[i].trim();

                    if (i == inputIdx) {
                        inputs.put("input", value);
                    } else if (i == outputIdx) {
                        expectedOutputs.put("output", value);
                    } else {
                        metadata.put(header, value);
                    }
                }

                examples.add(new Example(inputs, expectedOutputs, metadata));
            }

            return Dataset.builder()
                    .name(name)
                    .addExamples(examples)
                    .build();

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse CSV dataset", e);
        }
    }

    private static int findColumnIndex(String[] headers, String... candidates) {
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].trim().toLowerCase();
            for (String candidate : candidates) {
                if (header.equals(candidate.toLowerCase())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String[] parseCsvLine(String line, char delimiter) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == delimiter && !inQuotes) {
                values.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());

        return values.toArray(new String[0]);
    }
}