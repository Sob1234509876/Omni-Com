package io.github.sob1234509876.oc.rts.dimension;

import java.util.Objects;

/**
 * Identity of one dimension: the human readable key a scenario, a command or a save file uses to name a map.
 *
 * <p>Keys are lower case and stable, in the same spirit as content keys ({@code overworld}, {@code moon},
 * {@code mars}). They are the sorting order the engine iterates dimensions in, which is what keeps a replay
 * deterministic when several dimensions are simulated.
 */
public record DimensionId(String key) implements Comparable<DimensionId> {

    public DimensionId {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("dimension key must not be blank");
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '_' && c != '-' && c != '/') {
                throw new IllegalArgumentException(
                        "dimension key may only contain a-z, 0-9, '_', '-' and '/', was '" + key + "'");
            }
        }
    }

    public static DimensionId of(String key) {
        return new DimensionId(key);
    }

    /** The dimension every world starts with, and the one the single map API talks to. */
    public static DimensionId overworld() {
        return new DimensionId("overworld");
    }

    @Override
    public int compareTo(DimensionId other) {
        return key.compareTo(other.key);
    }

    @Override
    public String toString() {
        return key;
    }
}
