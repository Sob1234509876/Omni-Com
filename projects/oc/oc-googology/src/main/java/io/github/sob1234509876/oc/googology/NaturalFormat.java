package io.github.sob1234509876.oc.googology;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns a {@link Natural} into text for humans: exact decimal digits, a scientific magnitude, arrow
 * notation, or a sentence describing what the value is and how it is stored.
 *
 * <p>{@link Natural#toString()} already picks a reasonable form; this class exists for the cases where a
 * caller wants a specific one, for example a log line that must never print 1233 digits.
 */
public final class NaturalFormat {

    private NaturalFormat() {
    }

    /** Exact decimal digits, or the arrow form when the value is not stored exactly. */
    public static String decimal(Natural value) {
        Objects.requireNonNull(value, "value");
        return value.isExact() ? value.toBigInteger().toString() : arrow(value);
    }

    /**
     * Magnitude in scientific notation: {@code 1234} for a small exact value, {@code ~1.23e1233} for a
     * large one, {@code 10^1234} for a power of ten, {@code 10↑↑10} for a power tower.
     */
    public static String scientific(Natural value) {
        Objects.requireNonNull(value, "value");
        if (value.isBeyond()) {
            return "beyond";
        }
        if (value.isExact()) {
            BigInteger exact = value.toBigInteger();
            String digits = exact.toString();
            if (digits.length() <= 6) {
                return digits;
            }
            String mantissa = digits.charAt(0) + "." + digits.substring(1, 3);
            return "~" + mantissa + "e" + (digits.length() - 1);
        }
        if (value.finiteLevel().filter(Natural.ONE::equals).isPresent()) {
            return value.towerBase().orElse(Natural.TEN) + "^" + value.towerHeight().orElse(Natural.ONE);
        }
        return arrow(value);
    }

    /**
     * Knuth up arrow notation for a structural value: {@code 10^100}, {@code 10↑↑10}, and for a level that
     * is a transfinite ordinal the hyperoperation form {@code 10{ω^ω}10}. Exact values print as decimal
     * digits; a very long run of arrows is also written in hyperoperation form, since a wall of arrows
     * helps nobody.
     */
    public static String arrow(Natural value) {
        Objects.requireNonNull(value, "value");
        return value.toString();
    }

    /**
     * A one line description: the value, the representation behind it and, when it can be computed, how
     * many decimal digits it has.
     */
    public static String describe(Natural value) {
        Objects.requireNonNull(value, "value");
        if (value.isBeyond()) {
            return "BEYOND (larger than any value this notation can name)";
        }
        if (value.isExact()) {
            BigInteger exact = value.toBigInteger();
            return exact + " (" + exact.bitLength() + " bits, " + exact.toString().length() + " decimal digits)";
        }
        StringBuilder text = new StringBuilder();
        text.append(arrow(value)).append(" (structural, ");
        text.append(value.level().map(level -> "level " + level)
                .orElseGet(() -> value.finiteLevel().filter(Natural.ONE::equals).isPresent() ? "exponentiation"
                        : value.finiteLevel().filter(Natural.TWO::equals).isPresent() ? "tetration"
                                : value.finiteLevel().map(level -> level + " arrows").orElse("unknown level")));
        Optional<BigInteger> digits = value.decimalDigits();
        if (digits.isPresent()) {
            text.append(", ").append(digits.get()).append(" decimal digits");
        } else {
            text.append(", digit count itself out of range");
        }
        text.append(", magnitude ").append(scientific(value));
        return text.append(")").toString();
    }

    /**
     * The classic hyperoperation whose level describes the value, when the value is a structural one with a
     * finite level in the range the {@link HyperOperation} enum names. A transfinite level has no classic
     * hyperoperation, so the result is empty and callers should look at {@link Natural#level()} instead.
     */
    public static Optional<HyperOperation> operationOf(Natural value) {
        Objects.requireNonNull(value, "value");
        if (!value.isStructural()) {
            return Optional.empty();
        }
        return value.rank()
                .flatMap(Ordinal::finiteValue)
                .flatMap(rank -> HyperOperation.find((int) rank.toLongSaturated()));
    }
}
