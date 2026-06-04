package dev.dokimos.examples.springai.agent;

/**
 * A whisky in the catalog.
 *
 * @param id     stable catalog id
 * @param name   the bottling name
 * @param region the producing region (for example {@code "Islay"})
 * @param age    the age statement in years
 * @param peaty  whether the whisky is peated
 */
public record Whisky(String id, String name, String region, int age, boolean peaty) {}
