package dev.dokimos.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DatasetParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final char BOM = '﻿';

    private DatasetParser() {}

    public static Dataset parseJson(String json) {
        json = stripBom(json);
        try {
            Map<String, Object> root = MAPPER.readValue(json, new TypeReference<>() {});

            String name = (String) root.getOrDefault("name", "unnamed");
            String description = (String) root.getOrDefault("description", "");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawExamples = (List<Map<String, Object>>) root.get("examples");

            if (rawExamples == null) {
                throw new IllegalArgumentException("JSON dataset must contain 'examples' array!");
            }

            List<Example> examples =
                    rawExamples.stream().map(DatasetParser::parseExample).toList();

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

    /**
     * Parses a dataset from a JSONL file, streaming line-by-line from disk.
     *
     * @param path the file path
     * @param name the dataset name
     * @return the parsed dataset
     * @throws IOException if reading the file fails
     */
    public static Dataset parseJsonl(Path path, String name) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return parseJsonl(reader, name);
        }
    }

    /**
     * Parses a dataset from a JSONL string.
     * Each line is a separate JSON object representing an example.
     *
     * @param jsonl the JSONL content
     * @param name  the dataset name
     * @return the parsed dataset
     */
    public static Dataset parseJsonl(String jsonl, String name) {
        try (BufferedReader reader = new BufferedReader(new StringReader(stripBom(jsonl)))) {
            return parseJsonl(reader, name);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse JSONL dataset", e);
        }
    }

    private static Dataset parseJsonl(BufferedReader reader, String name) throws IOException {
        List<Example> examples = new ArrayList<>();
        String line;
        int lineNumber = 0;

        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (lineNumber == 1) {
                line = stripBom(line);
            }
            if (line.isBlank()) continue;

            try {
                Map<String, Object> raw = MAPPER.readValue(line, new TypeReference<>() {});
                examples.add(parseExample(raw));
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        "Failed to parse JSONL at line " + lineNumber + ": " + e.getMessage(), e);
            }
        }

        if (examples.isEmpty()) {
            throw new IllegalArgumentException("JSONL dataset contains no examples");
        }

        return Dataset.builder().name(name).addExamples(examples).build();
    }

    public static Dataset parseCsv(String csv, String name) {
        return parseCsv(csv, name, ',');
    }

    public static Dataset parseCsv(String csv, String name, char delimiter) {
        List<List<String>> rows = parseCsvRecords(stripBom(csv), delimiter);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("The provided CSV is empty");
        }

        List<String> headerRow = rows.get(0);
        String[] headers = headerRow.toArray(new String[0]);
        // Headers are unquoted identifiers, so trim them for matching.
        for (int i = 0; i < headers.length; i++) {
            headers[i] = headers[i].trim();
        }

        int inputIdx = findColumnIndex(headers, "input");
        int outputIdx = findColumnIndex(headers, "expectedOutput", "expected_output", "output");

        if (inputIdx == -1) {
            throw new IllegalArgumentException(
                    "CSV must have an 'input' column. Found columns: " + String.join(", ", headers));
        }

        List<Example> examples = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) {
            List<String> values = rows.get(r);

            Map<String, Object> inputs = new HashMap<>();
            Map<String, Object> expectedOutputs = new HashMap<>();
            Map<String, Object> metadata = new HashMap<>();

            for (int i = 0; i < headers.length && i < values.size(); i++) {
                String header = headers[i];
                String value = values.get(i);

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

        return Dataset.builder().name(name).addExamples(examples).build();
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

    /**
     * Parses CSV text into rows of fields using a single stateful scanner over the
     * entire text (RFC 4180). A quoted field may contain the delimiter, a newline,
     * and doubled quotes (two double-quote chars collapse to one literal quote).
     * Whitespace inside quoted fields is preserved; unquoted fields are trimmed.
     */
    private static List<List<String>> parseCsvRecords(String csv, char delimiter) {
        List<List<String>> rows = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean quotedField = false;
        boolean rowHasContent = false;

        int len = csv.length();
        for (int i = 0; i < len; i++) {
            char c = csv.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < len && csv.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
                continue;
            }

            if (c == '"') {
                inQuotes = true;
                quotedField = true;
                rowHasContent = true;
            } else if (c == delimiter) {
                current.add(finishField(field, quotedField));
                field.setLength(0);
                quotedField = false;
                rowHasContent = true;
            } else if (c == '\n' || c == '\r') {
                // Swallow a CRLF pair as a single line terminator.
                if (c == '\r' && i + 1 < len && csv.charAt(i + 1) == '\n') {
                    i++;
                }
                if (rowHasContent || !current.isEmpty() || field.length() > 0) {
                    current.add(finishField(field, quotedField));
                    rows.add(current);
                    current = new ArrayList<>();
                    field.setLength(0);
                    quotedField = false;
                    rowHasContent = false;
                }
            } else {
                field.append(c);
                rowHasContent = true;
            }
        }

        if (rowHasContent || !current.isEmpty() || field.length() > 0) {
            current.add(finishField(field, quotedField));
            rows.add(current);
        }

        return rows;
    }

    private static String finishField(StringBuilder field, boolean quoted) {
        String value = field.toString();
        return quoted ? value : value.trim();
    }

    private static String stripBom(String text) {
        if (text != null && !text.isEmpty() && text.charAt(0) == BOM) {
            return text.substring(1);
        }
        return text;
    }
}
