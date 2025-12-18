package io.dokimos.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class EvalResultTest {

    @Test
    void shouldCreateWithStaticFactory() {
        var result = EvalResult.of("faithfulness", 0.85, 0.8, "All claims supported");

        assertThat(result.name()).isEqualTo("faithfulness");
        assertThat(result.score()).isEqualTo(0.85);
        assertThat(result.success()).isTrue();
        assertThat(result.reason()).isEqualTo("All claims supported");
        assertThat(result.metadata()).isEmpty();
    }

    @Test
    void shouldCreateSuccessResult() {
        var result = EvalResult.success("test", 0.3, "Forced success");

        assertThat(result.score()).isEqualTo(0.3);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldCreateFailureResult() {
        var result = EvalResult.failure("test", 0.9, "Forced failure");

        assertThat(result.score()).isEqualTo(0.9);
        assertThat(result.success()).isFalse();
    }

    @Test
    void shouldBuildWithCustomThreshold() {
        var result = EvalResult.builder()
                .name("precision")
                .score(0.7)
                .threshold(0.8)
                .reason("Below threshold")
                .build();

        assertThat(result.success()).isFalse();
    }

    @Test
    void shouldBuildWithMetadata() {
        var result = EvalResult.builder()
                .name("faithfulness")
                .score(0.9)
                .threshold(0.7)
                .reason("Good")
                .metadata(Map.of("claims_verified", 5, "claims_total", 6))
                .build();

        assertThat(result.success()).isTrue();
        assertThat(result.metadata()).containsEntry("claims_verified", 5);
    }

    @Test
    void shouldMakeDefensiveCopyOfMetadata() {
        var metadata = new java.util.HashMap<String, Object>();
        metadata.put("key", "value");

        var result = EvalResult.builder()
                .name("test")
                .score(0.8)
                .metadata(metadata)
                .build();

        metadata.put("another", "value");

        assertThat(result.metadata()).containsOnlyKeys("key");
    }
}