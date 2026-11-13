package io.github.sob1234509876.oc.googology.bms;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.googology.Ordinal;
import io.github.sob1234509876.oc.googology.OrdinalNotation;

import java.util.Objects;
import java.util.Optional;

/**
 * A finite ordinal of the Bashicu matrix notation too large to materialise as zero columns: above
 * {@link BmsOrdinalNotation#MAX_FINITE_COLUMNS} the notation's {@code finite(n)} - which writes the finite
 * ordinal {@code n} as {@code n} columns of {@code (0)} - would need more columns than this implementation
 * is willing to build, so the value is kept as a {@link Natural} instead.
 *
 * <p>The value is a genuine finite ordinal and answers everything a finite ordinal needs: it compares
 * correctly against materialised matrices (above every finite matrix, below every transfinite one and the
 * {@link BmsEpsilonZero ε₀} boundary), it has a successor and a predecessor, it adds and multiplies with
 * other finite values through {@link Natural} arithmetic, and its {@link #finiteValue()} is the value
 * itself. What it does not offer is the column-level writing: {@code ω^this} and a fundamental sequence
 * would need the value written out as columns, so they are refused with a precise message. A transfinite
 * matrix plus this value is refused too, because the sum would need the value materialised as columns to
 * be represented.
 *
 * <p>The class is deliberately package private: values of it flow through the public signatures as plain
 * {@link Ordinal}, produced only by {@link BmsOrdinalNotation#finite(Natural)}.
 *
 * @see BmsOrdinalNotation#finite(Natural)
 * @since 2.0a
 */
final class BmsLargeFinite implements Ordinal {

    private final BmsOrdinalNotation notation;
    private final Natural value;

    BmsLargeFinite(BmsOrdinalNotation notation, Natural value) {
        this.notation = notation;
        this.value = value;
    }

    /** The finite value this ordinal represents, which is also its {@link #finiteValue()}. */
    Natural value() {
        return value;
    }

    @Override
    public OrdinalNotation notation() {
        return notation;
    }

    @Override
    public boolean isZero() {
        return value.isZero();
    }

    @Override
    public boolean isLimit() {
        return false;
    }

    @Override
    public Ordinal successor() {
        return notation.finite(value.add(Natural.ONE));
    }

    @Override
    public Optional<Ordinal> predecessor() {
        if (value.isZero()) {
            return Optional.empty();
        }
        return Optional.of(notation.finite(value.subtractClamped(Natural.ONE)));
    }

    /** A finite ordinal is not a limit, so it has no fundamental sequence. */
    @Override
    public Optional<Ordinal> fundamentalSequence(Natural index) {
        Objects.requireNonNull(index, "index");
        return Optional.empty();
    }

    @Override
    public Optional<Natural> finiteValue() {
        return Optional.of(value);
    }

    /**
     * Ordinal addition of two finite values: the sum as a {@link Natural}, materialised or not.
     *
     * @throws UnsupportedOperationException when the other ordinal is transfinite, because neither the
     *         absorption nor the sum of a transfinite matrix with an unmaterialisable finite value is
     *         writable in this notation
     */
    @Override
    public Ordinal add(Ordinal other) {
        Objects.requireNonNull(other, "other");
        if (other instanceof BmsLargeFinite finite) {
            return notation.finite(value.add(finite.value));
        }
        if (other instanceof BmsOrdinal matrix) {
            Optional<Natural> matrixValue = matrix.finiteValue();
            if (matrixValue.isPresent()) {
                return notation.finite(value.add(matrixValue.get()));
            }
            throw new UnsupportedOperationException("the sum " + matrix + " + " + value
                    + " needs the finite addend written as columns, which this notation does not materialise");
        }
        if (other instanceof BmsEpsilonZero) {
            return other;   // ε₀ absorbs every ordinal below it, finite values included
        }
        throw new IllegalArgumentException("cannot combine " + other + " (" + other.notation().name()
                + ") with a " + BmsOrdinalNotation.NAME + " ordinal");
    }

    /**
     * The product of two finite values: the product as a {@link Natural}, materialised or not.
     *
     * @throws IllegalArgumentException when the factor is zero, which is the one factor no ordinal
     *         multiplication can absorb into a finite value
     */
    @Override
    public Ordinal times(Natural factor) {
        Objects.requireNonNull(factor, "factor");
        if (factor.isZero()) {
            return notation.zero();
        }
        return notation.finite(value.multiply(factor));
    }

    /**
     * @throws UnsupportedOperationException always, because {@code ω^n} needs {@code n} columns of
     *         {@code (1)} and this value is above the materialisation bound by construction
     */
    @Override
    public Ordinal omegaPower() {
        throw new UnsupportedOperationException("ω^" + value + " needs the exponent written as columns, "
                + "which this notation does not materialise above " + BmsOrdinalNotation.MAX_FINITE_COLUMNS);
    }

    /** Above every materialised finite matrix, below every transfinite matrix and the ε₀ boundary. */
    @Override
    public int compareTo(Ordinal other) {
        Objects.requireNonNull(other, "other");
        if (other instanceof BmsLargeFinite finite) {
            return value.compareTo(finite.value);
        }
        if (other instanceof BmsOrdinal matrix) {
            Optional<Natural> matrixValue = matrix.finiteValue();
            if (matrixValue.isPresent()) {
                return value.compareTo(matrixValue.get());
            }
            return -1;
        }
        if (other instanceof BmsEpsilonZero) {
            return -1;
        }
        return notation.name().compareTo(other.notation().name());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BmsLargeFinite finite && value.equals(finite.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
