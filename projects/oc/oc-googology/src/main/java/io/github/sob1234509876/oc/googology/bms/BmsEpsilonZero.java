package io.github.sob1234509876.oc.googology.bms;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.googology.Ordinal;
import io.github.sob1234509876.oc.googology.OrdinalNotation;

import java.util.Objects;
import java.util.Optional;

/**
 * {@code ε₀} as the ceiling of {@link BmsOrdinalNotation}: the least fixed point of {@code α ↦ ω^α}, which
 * the module's golden expansions identify with the matrix {@code (0)(1,1)} - the value whose fundamental
 * sequence is {@code 0, 1, ω, ω^ω, ω^ω^ω, ...}.
 *
 * <p>It is a limit ordinal, and its fundamental sequence is the tower sequence read through the notation's
 * {@code ω[m] = m+1} convention: {@code ε₀[0] = 1}, {@code ε₀[1] = ω}, {@code ε₀[2] = ω^ω} and so on - the
 * raw expansion {@code S[n] = (0)(1)...(n-1)} at index {@code n+1}. The old CNF notation chose to start its
 * ε₀ sequence at {@code ε₀[0] = ω} instead; both are genuine fundamental sequences, and the difference is
 * the one documented convention change of the BMS-backed ordinal layer.
 *
 * <p>Accordingly some operations are partial here, exactly as they were for the CNF ceiling:
 * {@code ε₀ + 1} and {@code ε₀·2} are not derivable in this notation, so {@link #successor()} and
 * {@link #times(Natural)} with a factor above one fail with a clear message instead of inventing a value.
 * Adding a smaller ordinal is fine, because {@code ε₀} absorbs it.
 *
 * <p>The boundary is a distinct value, not a {@link BmsOrdinal}: {@link BmsOrdinalNotation#contains(Ordinal)}
 * reports {@code false} for it, and {@link #compareTo(Ordinal)} positions it by the matrix it wraps, so it
 * sits above every ω-tower and below a taller matrix that the caller may have built by hand. The
 * identification of the matrix {@code (0)(1,1)} with ε₀ comes from the reference, not from this class.
 *
 * @see BmsOrdinalNotation#limit()
 * @since 2.0a
 */
public final class BmsEpsilonZero implements Ordinal {

    /** The matrix the reference identifies with ε₀; comparisons delegate to it. */
    private static final BmsOrdinal MATRIX = BmsOrdinal.of(new int[][]{{0}, {1, 1}});

    private final BmsOrdinalNotation notation;

    BmsEpsilonZero(BmsOrdinalNotation notation) {
        this.notation = notation;
    }

    /** The matrix the boundary wraps, for the lexicographic comparison in the same package. */
    BmsOrdinal comparisonMatrix() {
        return MATRIX;
    }

    @Override
    public OrdinalNotation notation() {
        return notation;
    }

    @Override
    public boolean isZero() {
        return false;
    }

    @Override
    public boolean isLimit() {
        return true;
    }

    /**
     * @throws UnsupportedOperationException always, because {@code ε₀ + 1} is beyond this notation
     */
    @Override
    public Ordinal successor() {
        throw new UnsupportedOperationException(
                "ε₀ is the limit of the " + BmsOrdinalNotation.NAME + " notation, so ε₀ + 1 cannot be named here");
    }

    @Override
    public Optional<Ordinal> predecessor() {
        return Optional.empty();
    }

    /**
     * The tower sequence through the {@code ω[m] = m+1} convention: {@code ε₀[m]} is the raw expansion of
     * {@code (0)(1,1)} at index {@code m+1}, which is {@code 1, ω, ω^ω, ω^ω^ω, ...}. Empty when the index
     * is so large that the corresponding matrix cannot be written down, which is a documented limit of the
     * implementation rather than a mathematical claim.
     */
    @Override
    public Optional<Ordinal> fundamentalSequence(Natural index) {
        return MATRIX.fundamentalSequence(index);
    }

    @Override
    public Optional<Natural> finiteValue() {
        return Optional.empty();
    }

    /** {@code ε₀ + α} is {@code ε₀} for every {@code α} below {@code ε₀}. */
    @Override
    public Ordinal add(Ordinal other) {
        Objects.requireNonNull(other, "other");
        if (other instanceof BmsEpsilonZero) {
            throw new UnsupportedOperationException("ε₀ + ε₀ is beyond the " + BmsOrdinalNotation.NAME
                    + " notation");
        }
        return this;
    }

    /**
     * {@code ε₀·1} is {@code ε₀} and {@code ε₀·0} is zero.
     *
     * @throws UnsupportedOperationException for a factor above one, which would need {@code ε₀·2}
     */
    @Override
    public Ordinal times(Natural factor) {
        Objects.requireNonNull(factor, "factor");
        if (factor.isZero()) {
            return notation.zero();
        }
        if (factor.isOne()) {
            return this;
        }
        throw new UnsupportedOperationException("ε₀·" + factor + " is beyond the " + BmsOrdinalNotation.NAME
                + " notation");
    }

    /** {@code ω^ε₀ = ε₀}. */
    @Override
    public Ordinal omegaPower() {
        return this;
    }

    /**
     * Positions the boundary by the matrix it wraps: equal to itself, above the ω-towers and below a taller
     * matrix built by hand, and ordered by notation name against ordinals of a foreign notation.
     */
    @Override
    public int compareTo(Ordinal other) {
        Objects.requireNonNull(other, "other");
        if (other instanceof BmsEpsilonZero) {
            return 0;
        }
        if (other instanceof BmsOrdinal) {
            return MATRIX.compareTo(other);
        }
        return notation.name().compareTo(other.notation().name());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BmsEpsilonZero;
    }

    @Override
    public int hashCode() {
        return 0xE0;
    }

    @Override
    public String toString() {
        return "ε₀";
    }
}
