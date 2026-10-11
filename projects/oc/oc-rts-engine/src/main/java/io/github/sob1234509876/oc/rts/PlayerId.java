package io.github.sob1234509876.oc.rts;

/**
 * Identity of a player, stable across the whole match.
 */
public record PlayerId(int value) implements Comparable<PlayerId> {

    public static PlayerId of(int value) {
        return new PlayerId(value);
    }

    @Override
    public int compareTo(PlayerId other) {
        return Integer.compare(value, other.value);
    }

    @Override
    public String toString() {
        return "player" + value;
    }
}
