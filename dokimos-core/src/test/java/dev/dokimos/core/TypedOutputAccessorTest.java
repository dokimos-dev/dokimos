package dev.dokimos.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.dokimos.core.exceptions.DokimosTypeConversionException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TypedOutputAccessorTest {

    record Whisky(String name, int age) {}

    @Test
    void readsRecordViaClass() {
        var testCase = EvalTestCase.builder()
                .actualOutput("output", Map.of("name", "Lagavulin", "age", 16))
                .build();

        Whisky whisky = testCase.actualOutputAs(Whisky.class);

        assertThat(whisky).isEqualTo(new Whisky("Lagavulin", 16));
    }

    @Test
    void readsGenericListViaOutputType() {
        var testCase = EvalTestCase.builder()
                .actualOutput(
                        "output",
                        List.of(Map.of("name", "Ardbeg", "age", 10), Map.of("name", "Oban", "age", 14)))
                .build();

        List<Whisky> whiskies = testCase.actualOutputAs(new OutputType<List<Whisky>>() {});

        assertThat(whiskies).containsExactly(new Whisky("Ardbeg", 10), new Whisky("Oban", 14));
    }

    @Test
    void readsKeyedOutput() {
        var testCase = EvalTestCase.builder()
                .actualOutput("dram", Map.of("name", "Talisker", "age", 18))
                .expectedOutput("dram", Map.of("name", "Talisker", "age", 18))
                .build();

        assertThat(testCase.actualOutputAs("dram", Whisky.class)).isEqualTo(new Whisky("Talisker", 18));
        assertThat(testCase.expectedOutputAs("dram", Whisky.class)).isEqualTo(new Whisky("Talisker", 18));
        assertThat(testCase.expectedOutputAs("dram", new OutputType<Whisky>() {}))
                .isEqualTo(new Whisky("Talisker", 18));
    }

    @Test
    void absentKeyReturnsNull() {
        var testCase = EvalTestCase.builder().actualOutput("output", "x").build();

        assertThat(testCase.actualOutputAs("missing", Whisky.class)).isNull();
        assertThat(testCase.actualOutputAs("missing", new OutputType<List<Whisky>>() {}))
                .isNull();
        assertThat(testCase.expectedOutputAs("missing", Whisky.class)).isNull();
        assertThat(testCase.expectedOutputAs(Whisky.class)).isNull();
    }

    @Test
    void unconvertibleValueThrowsTypeConversionException() {
        var testCase = EvalTestCase.builder()
                .actualOutput("output", "not a whisky object")
                .build();

        assertThatThrownBy(() -> testCase.actualOutputAs(Whisky.class))
                .isInstanceOf(DokimosTypeConversionException.class)
                .hasMessageContaining(Whisky.class.getName())
                .hasCauseInstanceOf(Throwable.class);
    }

    @Test
    void unconvertibleValueThrowsForOutputType() {
        var testCase = EvalTestCase.builder()
                .actualOutput("output", "not a list")
                .build();

        assertThatThrownBy(() -> testCase.actualOutputAs(new OutputType<List<Whisky>>() {}))
                .isInstanceOf(DokimosTypeConversionException.class);
    }

    @Test
    void stringReadAsStringPassesThroughWithoutDoubleEncoding() {
        var testCase = EvalTestCase.builder().actualOutput("output", "Bern").build();

        assertThat(testCase.actualOutputAs(String.class)).isEqualTo("Bern");
        assertThat(testCase.actualOutputAs("output", String.class)).isEqualTo("Bern");
    }

    @Test
    void exampleExpectedAccessorsReadStructuredValues() {
        var example = Example.builder()
                .input("question", "best islay")
                .expectedOutput("output", Map.of("name", "Laphroaig", "age", 10))
                .expectedOutput("alt", List.of(Map.of("name", "Bowmore", "age", 12)))
                .build();

        assertThat(example.expectedOutputAs(Whisky.class)).isEqualTo(new Whisky("Laphroaig", 10));
        assertThat(example.expectedOutputAs("alt", new OutputType<List<Whisky>>() {}))
                .containsExactly(new Whisky("Bowmore", 12));
        assertThat(example.expectedOutputAs("missing", Whisky.class)).isNull();
    }

    @Test
    void exampleExpectedStringPassesThrough() {
        var example = Example.of("q", "Bern");

        assertThat(example.expectedOutputAs(String.class)).isEqualTo("Bern");
    }

    @Test
    void exampleUnconvertibleValueThrows() {
        var example = Example.builder()
                .expectedOutput("output", "not a whisky")
                .build();

        assertThatThrownBy(() -> example.expectedOutputAs(Whisky.class))
                .isInstanceOf(DokimosTypeConversionException.class);
    }
}
