package dev.dokimos.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.ParameterizedType;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutputTypeTest {

    record Whisky(String name, int age) {}

    @Test
    void capturesSimpleClassTypeArgument() {
        OutputType<Whisky> type = new OutputType<>() {};

        assertThat(type.getType()).isEqualTo(Whisky.class);
        assertThat(type.toString()).contains("Whisky");
    }

    @Test
    void capturesGenericListTypeArgument() {
        OutputType<List<Whisky>> type = new OutputType<>() {};

        assertThat(type.getType()).isInstanceOf(ParameterizedType.class);
        ParameterizedType parameterized = (ParameterizedType) type.getType();
        assertThat(parameterized.getRawType()).isEqualTo(List.class);
        assertThat(parameterized.getActualTypeArguments()).containsExactly(Whisky.class);
    }

    @Test
    void rawConstructionThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> {
                    @SuppressWarnings({"rawtypes", "unused"})
                    OutputType raw = new OutputType() {};
                })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actual type argument");
    }

    @Test
    void equalsAndHashCodeBasedOnCapturedType() {
        OutputType<List<Whisky>> a = new OutputType<>() {};
        OutputType<List<Whisky>> b = new OutputType<>() {};
        OutputType<Whisky> c = new OutputType<>() {};

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }
}
