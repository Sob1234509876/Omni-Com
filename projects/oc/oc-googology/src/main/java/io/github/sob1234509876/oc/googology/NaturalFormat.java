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
        if (value.arrows() == 1) {
            return value.towerBase().orElse(Natural.TEN) + "^" + value.towerHeight().orElse(Natural.ONE);
        }
        return arrow(value);
    }

    /** Knuth up arrow notation for a structural value; exact values print as decimal digits. */
    public static String arrow(Natural value) {
        Objects.requireNonNull(value, "value");
        if (value.isExact() || value.isBeyond()) {
            return value.toString();
        }
        return value.towerBase().orElse(Natural.TEN)
                + Natural.arrows(value.arrows())
                + value.towerHeight().orElse(Natural.ONE);
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
        text.append(value.arrows() == 1 ? "exponentiation" : value.arrows() == 2 ? "tetration"
                : value.arrows() + " arrows");
        Optional<BigInteger> digits = value.decimalDigits();
        if (digits.isPresent()) {
            text.append(", ").append(digits.get()).append(" decimal digits");
        } else {
            text.append(", digit count itself out of range");
        }
        text.append(", magnitude ").append(scientific(value));
        return text.append(")").toString();
    }

    /** The hyperoperation whose level best describes the value, if it is a structural one. */
    public static Optional<HyperOperation> operationOf(Natural value) {
        Objects.requireNonNull(value, "value");
        if (!value.isStructural()) {
            return Optional.empty();
        }
        return HyperOperation.find(value.arrows() + 2);
    }
}
