package io.github.sob1234509876.oc.rts.content;

import io.github.sob1234509876.oc.googology.Natural;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The items a new player is given when a world is created, keyed by item path.
 *
 * <p>Amounts are {@link Natural} values, like every other quantity in the engine, so a game that hands out
 * an absurd starting fortune can say so exactly. Paths are item paths inside the content's own namespace
 * (for example {@code gold}), not full {@code namespace:path} keys: a content catalogue only ever refers to
 * its own items this way, and resolving the path against the catalogue at trade time is what keeps a typo
 * a loud failure instead of a silently missing stock.
 *
 * <p>Instances are immutable and ordered by insertion, so a host deposits the same items in the same order
 * on every run.
 */
public final class StartingResources {

    /** No starting stock at all; what a content without an explicit economy starts from. */
    public static final StartingResources EMPTY = new StartingResources(Map.of());

    private final Map<String, Natural> amounts;

    private StartingResources(Map<String, Natural> amounts) {
        this.amounts = Collections.unmodifiableMap(amounts);
    }

    /** Builds a stock from item path to amount, in the iteration order of the given map. */
    public static StartingResources of(Map<String, ?> amounts) {
        Objects.requireNonNull(amounts, "amounts");
        Builder builder = builder();
        amounts.forEach((path, amount) -> builder.add(path, amountOf(path, amount)));
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private static Natural amountOf(String path, Object value) {
        if (value instanceof Natural natural) {
            return natural;
        }
        if (value instanceof Number number) {
            return Natural.of(number.longValue());
        }
        throw new IllegalArgumentException("starting amount of '" + path + "' must be a Natural or a number, was "
                + value);
    }

    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    /** Number of distinct item kinds. */
    public int size() {
        return amounts.size();
    }

    /** The stock by item path, in insertion order. */
    public Map<String, Natural> amounts() {
        return amounts;
    }

    /** How much of one item path the player starts with; zero when the path is not part of the stock. */
    public Natural amount(String itemPath) {
        return amounts.getOrDefault(Objects.requireNonNull(itemPath, "itemPath"), Natural.ZERO);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StartingResources resources && amounts.equals(resources.amounts);
    }

    @Override
    public int hashCode() {
        return amounts.hashCode();
    }

    @Override
    public String toString() {
        if (amounts.isEmpty()) {
            return "nothing";
        }
        StringBuilder text = new StringBuilder();
        amounts.forEach((path, amount) -> {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(amount).append("x ").append(path);
        });
        return text.toString();
    }

    /** Mutable helper that sums amounts per item path. */
    public static final class Builder {

        private final Map<String, Natural> amounts = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Adds one item to the stock.
         *
         * @throws IllegalArgumentException when the path is blank, the amount is negative or it is
         *     {@link Natural#BEYOND}
         */
        public Builder add(String itemPath, Natural amount) {
            Objects.requireNonNull(itemPath, "itemPath");
            Objects.requireNonNull(amount, "amount");
            if (itemPath.isBlank()) {
                throw new IllegalArgumentException("a starting resource needs an item path");
            }
            if (amount.isBeyond()) {
                throw new IllegalArgumentException("a starting stock cannot hold BEYOND of " + itemPath);
            }
            if (!amount.isZero()) {
                amounts.merge(itemPath, amount, Natural::add);
            }
            return this;
        }

        /** Convenience for {@link #add(String, Natural)} with a small literal. */
        public Builder add(String itemPath, long amount) {
            return add(itemPath, Natural.of(amount));
        }

        public boolean isEmpty() {
            return amounts.isEmpty();
        }

        public StartingResources build() {
            return amounts.isEmpty() ? EMPTY : new StartingResources(new LinkedHashMap<>(amounts));
        }
    }
}
