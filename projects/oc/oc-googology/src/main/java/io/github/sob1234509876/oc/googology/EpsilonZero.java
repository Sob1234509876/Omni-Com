package io.github.sob1234509876.oc.googology;

import java.util.Objects;
import java.util.Optional;

/**
 * {@code ε₀}: the least fixed point of {@code α ↦ ω^α}, and the ceiling of
 * {@link CantorNormalForm}: the supremum of {@code 1, ω, ω^ω, ω^ω^ω, ...}.
 *
 * <p>It is a limit ordinal, and its fundamental sequence is exactly the tower sequence
 * {@code ε₀[n] = ω^ω^...^ω} with {@code n+1} omegas. Because the notation cannot write {@code ε₀} in
 * normal form (its normal form would be {@code ω^ε₀}, in which the exponent is the whole ordinal),
 * {@link CantorNormalForm#contains(Ordinal)} reports {@code false} for it: it is the boundary the notation
 * can name but not express.
 *
 * <p>Accordingly some operations are partial here: {@code ε₀ + 1} and {@code ε₀·2} are not expressible in
 * Cantor normal form, so {@link #successor()} and {@link #times(Natural)} with a factor above one fail
 * with a clear message instead of inventing a value. Adding a smaller ordinal is fine, because
 * {@code ε₀} absorbs it.
 *
 * @see CantorNormalForm#limit()
 */
public final class EpsilonZero implements Ordinal {

    /** Towers taller than this are not materialised; a fundamental sequence index above it is refused. */
    static final int MAX_TOWER_HEIGHT = 256;

    private final CantorNormalForm notation;

    EpsilonZero(CantorNormalForm notation) {
        this.notation = notation;
    }

    @Override
    public CantorNormalForm notation() {
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
     * @throws UnsupportedOperationException always, because {@code ε₀ + 1} is beyond Cantor normal form
     */
    @Override
    public Ordinal successor() {
        throw new UnsupportedOperationException(
                "ε₀ is the limit of the Cantor normal form notation, so ε₀ + 1 cannot be named here");
    }

    @Override
    public Optional<Ordinal> predecessor() {
        return Optional.empty();
    }

    /**
     * {@code ε₀[n] = ω^ω^...^ω} with {@code n+1} omegas, which is the tower sequence that defines
     * {@code ε₀}. Empty when the index is so large that the corresponding tower cannot be written down,
     * which is a documented limit of the notation rather than a mathematical claim.
     */
    @Override
    public Optional<Ordinal> fundamentalSequence(Natural index) {
        Objects.requireNonNull(index, "index");
        if (!index.isExact() || index.toLongSaturated() > MAX_TOWER_HEIGHT) {
            return Optional.empty();
        }
        int height = (int) index.toLongSaturated() + 1;
        Ordinal tower = notation.one();
        for (int level = 0; level < height; level++) {
            tower = notation.omegaPower(tower);
        }
        return Optional.of(tower);
    }

    @Override
    public Optional<Natural> finiteValue() {
        return Optional.empty();
    }

    /** {@code ε₀ + α} is {@code ε₀} for every {@code α} below {@code ε₀}. */
    @Override
    public Ordinal add(Ordinal other) {
        Objects.requireNonNull(other, "other");
        if (other instanceof EpsilonZero) {
            throw new UnsupportedOperationException("ε₀ + ε₀ is beyond Cantor normal form");
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
        throw new UnsupportedOperationException("ε₀·" + factor + " is beyond Cantor normal form");
    }

    /** {@code ω^ε₀ = ε₀}. */
    @Override
    public Ordinal omegaPower() {
        return this;
    }

    @Override
    public int compareTo(Ordinal other) {
        Objects.requireNonNull(other, "other");
        if (other instanceof EpsilonZero) {
            return 0;
        }
        if (other instanceof CantorOrdinal) {
            return 1;
        }
        return notation.name().compareTo(other.notation().name());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EpsilonZero;
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
