package dev.dokimos.core.gate;

import dev.dokimos.core.EvalResult;
import dev.dokimos.core.Example;
import dev.dokimos.core.ExperimentResult;
import dev.dokimos.core.ItemResult;
import dev.dokimos.core.RunResult;
import dev.dokimos.core.gate.BaselineFile.BaselineItem;
import dev.dokimos.core.gate.BaselineFile.DatasetInfo;
import dev.dokimos.core.gate.BaselineFile.EvaluatorEntry;
import dev.dokimos.core.gate.BaselineFile.Provenance;
import dev.dokimos.core.internal.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reads and writes the regression-gate baseline file, and reconstructs comparison-ready {@link
 * RunResult}s from it.
 *
 * <p>The baseline is a committed source artifact: it is read AND written through the module-relative
 * filesystem path, never the classpath, so a surefire/Gradle copy of {@code src/test/resources} to
 * {@code target/test-classes} can never split the read from the write.
 */
public final class BaselineStore {

    private static final double PRECISION_SCALE = 1_000_000.0; // 6 decimals, matching the engine
    private static final int ANCHOR_MAX = 80;
    private static final String POSITIONAL_PREFIX = "item-";

    private BaselineStore() {}

    /**
     * Projects an experiment result into the v1 baseline format (no I/O).
     *
     * @param result the experiment result to project (must have at least one run)
     * @param config the gate configuration (controls pairing)
     * @return the baseline projection
     * @throws IllegalArgumentException if the result has no runs
     * @throws IllegalStateException if an item has no evaluator results
     */
    public static BaselineFile project(ExperimentResult result, GateConfig config) {
        List<RunResult> runs = result.runResults();
        if (runs.isEmpty()) {
            throw new IllegalArgumentException("Cannot baseline an experiment with no runs");
        }
        boolean byId = resolvePairById(runs, config.pairing());

        // Group items across runs by their pairing key, preserving first-seen order.
        LinkedHashMap<String, ItemAgg> byKey = new LinkedHashMap<>();
        for (RunResult run : runs) {
            List<ItemResult> items = run.itemResults();
            for (int i = 0; i < items.size(); i++) {
                ItemResult item = items.get(i);
                String key = byId ? item.example().datasetItemId() : POSITIONAL_PREFIX + i;
                byKey.computeIfAbsent(key, k -> new ItemAgg(item.example())).add(item);
            }
        }

        List<BaselineItem> baselineItems = new ArrayList<>(byKey.size());
        for (Map.Entry<String, ItemAgg> e : byKey.entrySet()) {
            ItemAgg agg = e.getValue();
            baselineItems.add(new BaselineItem(
                    e.getKey(), fingerprint(agg.example), anchor(agg.example), agg.toEntries(e.getKey())));
        }
        baselineItems.sort(byId ? Comparator.comparing(BaselineItem::key) : positionalKeyComparator());

        DatasetInfo dataset = new DatasetInfo(datasetName(result), baselineItems.size());
        Provenance provenance = new Provenance(BaselineStore.class.getPackage().getImplementationVersion(), null);
        return new BaselineFile(
                BaselineFile.CURRENT_VERSION,
                result.name(),
                dataset,
                byId ? "dataset_item_id" : "positional",
                1,
                baselineItems,
                provenance);
    }

    /**
     * Writes a baseline atomically to the given source-tree path (creating parent directories).
     *
     * @param path the baseline file path
     * @param result the experiment result to project
     * @param config the gate configuration
     * @throws IOException if the file cannot be written
     */
    public static void write(Path path, ExperimentResult result, GateConfig config) throws IOException {
        writeFile(path, Json.writePretty(project(result, config)));
    }

    /**
     * Reads and validates a baseline file.
     *
     * @param path the baseline file path
     * @return the parsed and validated baseline
     * @throws IOException if the file cannot be read
     * @throws IllegalStateException if the format version is unsupported or the content is invalid
     * @throws IllegalArgumentException if the file is not valid JSON
     */
    public static BaselineFile read(Path path) throws IOException {
        BaselineFile baseline = Json.read(Files.readString(path), BaselineFile.class);
        validate(baseline);
        return baseline;
    }

    /**
     * Reconstructs comparison-ready runs from a baseline. The reconstructed run is faithful for what
     * the comparison engine reads (item key + per-evaluator score and verdict); the model outputs,
     * reasons, and metrics the projection dropped are never consulted by the engine.
     *
     * @param baseline the baseline to reconstruct
     * @return a single-element list holding the reconstructed run
     */
    public static List<RunResult> toRunResults(BaselineFile baseline) {
        boolean byId = "dataset_item_id".equals(baseline.pairing());
        List<BaselineItem> items = new ArrayList<>(baseline.items());
        // Restore positional order so positions align with a candidate run built in dataset order.
        items.sort(byId ? Comparator.comparing(BaselineItem::key) : positionalKeyComparator());

        List<ItemResult> itemResults = new ArrayList<>(items.size());
        for (BaselineItem item : items) {
            List<EvalResult> evals = new ArrayList<>(item.evaluators().size());
            for (EvaluatorEntry ev : item.evaluators()) {
                evals.add(new EvalResult(ev.name(), ev.score(), ev.threshold(), ev.pass(), "", Map.of()));
            }
            String anchor = item.input() != null ? item.input() : "";
            Example example = new Example(Map.of("input", anchor), Map.of(), Map.of(), byId ? item.key() : null);
            itemResults.add(new ItemResult(example, Map.of(), evals));
        }
        return List.of(new RunResult(0, itemResults));
    }

    // --- internals ---

    private static void validate(BaselineFile baseline) {
        if (baseline.formatVersion() > BaselineFile.CURRENT_VERSION) {
            throw new IllegalStateException("Baseline formatVersion " + baseline.formatVersion()
                    + " is newer than this Dokimos supports (" + BaselineFile.CURRENT_VERSION
                    + "). Upgrade Dokimos.");
        }
        if (baseline.runsPerItem() != 1) {
            throw new IllegalStateException("v1 baselines support runsPerItem=1; got " + baseline.runsPerItem());
        }
        List<BaselineItem> items = baseline.items();
        if (items == null || items.isEmpty()) {
            throw new IllegalStateException("Baseline has no items");
        }
        Set<String> seen = new HashSet<>();
        for (BaselineItem item : items) {
            if (item.key() == null || item.key().isBlank()) {
                throw new IllegalStateException("Baseline item has a blank key");
            }
            if (!seen.add(item.key())) {
                throw new IllegalStateException("Baseline has a duplicate item key: " + item.key());
            }
            if (item.evaluators() == null || item.evaluators().isEmpty()) {
                throw new IllegalStateException("Baseline item '" + item.key() + "' has no evaluators");
            }
            for (EvaluatorEntry ev : item.evaluators()) {
                if (ev.name() == null || ev.name().isBlank()) {
                    throw new IllegalStateException(
                            "Baseline item '" + item.key() + "' has an evaluator with a blank name");
                }
            }
        }
    }

    private static void writeFile(Path path, String content) throws IOException {
        Path target = path.toAbsolutePath();
        Path dir = target.getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        // Temp file in the TARGET's own directory so the rename stays on one filesystem.
        Path tmp = Files.createTempFile(dir, ".baseline-", ".tmp");
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static boolean resolvePairById(List<RunResult> runs, GateConfig.Pairing pairing) {
        if (pairing == GateConfig.Pairing.POSITIONAL) {
            return false;
        }
        boolean allLinked = true;
        for (RunResult run : runs) {
            for (ItemResult item : run.itemResults()) {
                if (item.example().datasetItemId() == null) {
                    allLinked = false;
                    break;
                }
            }
            if (!allLinked) {
                break;
            }
        }
        if (pairing == GateConfig.Pairing.DATASET_ITEM_ID && !allLinked) {
            throw new IllegalStateException("pairing=DATASET_ITEM_ID requires every item to carry a datasetItemId");
        }
        return allLinked;
    }

    private static Comparator<BaselineItem> positionalKeyComparator() {
        return Comparator.comparingInt(item -> positionalIndex(item.key()));
    }

    private static int positionalIndex(String key) {
        return Integer.parseInt(key.substring(POSITIONAL_PREFIX.length()));
    }

    private static double round6(double value) {
        return Math.round(value * PRECISION_SCALE) / PRECISION_SCALE;
    }

    private static String anchor(Example example) {
        String raw = example.input();
        if (raw == null) {
            raw = Json.writeCompact(canonicalize(example.inputs()));
        }
        String normalized = raw.trim().replaceAll("\\s+", " ");
        return normalized.length() > ANCHOR_MAX ? normalized.substring(0, ANCHOR_MAX) : normalized;
    }

    private static String fingerprint(Example example) {
        String canonical = Json.writeCompact(canonicalize(example.inputs()))
                + "|"
                + Json.writeCompact(canonicalize(example.expectedOutputs()));
        return "sha256:" + sha256Hex(canonical);
    }

    /** Recursively sorts map keys so the serialized form (and thus the fingerprint) is deterministic. */
    private static Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                sorted.put(String.valueOf(e.getKey()), canonicalize(e.getValue()));
            }
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object o : list) {
                out.add(canonicalize(o));
            }
            return out;
        }
        return value;
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 unavailable", e);
        }
    }

    private static String datasetName(ExperimentResult result) {
        Object name = result.metadata().get("dataset");
        if (name == null) {
            name = result.metadata().get("datasetName");
        }
        return name != null ? name.toString() : null;
    }

    /** Aggregates one item's observations across runs into a single projected entry. */
    private static final class ItemAgg {
        private final Example example;
        private final LinkedHashMap<String, EvalAgg> evaluators = new LinkedHashMap<>();

        ItemAgg(Example example) {
            this.example = example;
        }

        void add(ItemResult item) {
            for (EvalResult er : item.evalResults()) {
                EvalAgg ev = evaluators.computeIfAbsent(er.name(), n -> new EvalAgg());
                ev.scoreSum += er.score();
                ev.count++;
                if (er.success()) {
                    ev.successCount++;
                }
                if (ev.threshold == null) {
                    ev.threshold = er.threshold();
                }
            }
        }

        List<EvaluatorEntry> toEntries(String key) {
            if (evaluators.isEmpty()) {
                throw new IllegalStateException("Baseline item '" + key + "' has no evaluator results");
            }
            List<EvaluatorEntry> out = new ArrayList<>(evaluators.size());
            for (Map.Entry<String, EvalAgg> e : evaluators.entrySet()) {
                EvalAgg ev = e.getValue();
                double mean = round6(ev.scoreSum / ev.count);
                boolean pass = ev.successCount * 2 >= ev.count; // majority across runs; ties -> pass
                out.add(new EvaluatorEntry(e.getKey(), mean, ev.threshold, pass));
            }
            out.sort(Comparator.comparing(EvaluatorEntry::name));
            return out;
        }
    }

    private static final class EvalAgg {
        private double scoreSum;
        private int count;
        private int successCount;
        private Double threshold;
    }
}
