package io.github.sob1234509876.oc.googology;

import java.util.List;
import java.util.Objects;

/**
 * The default ordinal notation of this library: Cantor normal form, whose values are ordinals below
 * {@code ε₀}, the supremum of {@code 1, ω, ω^ω, ω^ω^ω, ...}.
 *
 * <p>Every ordinal below {@code ε₀} has exactly one normal form
 * {@code ω^α₁·c₁ + ... + ω^αₖ·cₖ} with decreasing exponents, which is what {@link CantorOrdinal}
 * represents, so this notation is a genuine system of unique ordinal names rather than a set of
 * expressions that happen to look different. Its ceiling {@link #limit()} is {@code ε₀} itself, modelled
 * by {@link EpsilonZero}: the smallest ordinal this notation can name but cannot write in normal form.
 *
 * <p>{@link #standard()} is the instance {@link Natural} uses for the finite levels of its structural
 * values. A caller who wants a different set of names implements {@link OrdinalNotation} directly; the
 * {@link CantorOrdinal} value type can be reused by such a notation, which is what keeps ordinals of
 * different notations comparable.
 *
 * @see CantorOrdinal
 * @see EpsilonZero
 */
public final class CantorNormalForm implements OrdinalNotation {

    /** Stable name of this notation. */
    public static final String NAME = "cantor-normal-form";

    private static final CantorNormalForm STANDARD = new CantorNormalForm();

    private final CantorOrdinal zero;
    private final CantorOrdinal omega;
    private final CantorOrdinal one;
    private final EpsilonZero limit;

    private CantorNormalForm() {
        this.zero = CantorOrdinal.canonical(this, List.of());
        this.one = CantorOrdinal.canonical(this, List.of(new CantorOrdinal.Term(zero, Natural.ONE)));
        this.omega = CantorOrdinal.canonical(this, List.of(new CantorOrdinal.Term(one, Natural.ONE)));
        this.limit = new EpsilonZero(this);
    }

    /** The shared instance of this notation. */
    public static CantorNormalForm standard() {
        return STANDARD;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Ordinal zero() {
        return zero;
    }

    @Override
    public Ordinal one() {
        return one;
    }

    @Override
    public Ordinal finite(Natural value) {
        Objects.requireNonNull(value, "value");
        if (value.isZero()) {
            return zero;
        }
        return CantorOrdinal.canonical(this, List.of(new CantorOrdinal.Term(zero, value)));
    }

    @Override
    public Ordinal omega() {
        return omega;
    }

    /**
     * {@code ω^exponent}. An exponent equal to {@link #limit()} folds back into that limit, because
     * {@code ω^ε₀ = ε₀}; any other exponent must be expressible in this notation.
     */
    @Override
    public Ordinal omegaPower(Ordinal exponent) {
        Objects.requireNonNull(exponent, "exponent");
        if (exponent instanceof EpsilonZero) {
            return limit;
        }
        if (!contains(exponent)) {
            throw new IllegalArgumentException("the exponent " + exponent + " (" + exponent.notation().name()
                    + ") is not a " + NAME + " ordinal");
        }
        if (exponent.isZero()) {
            return one;
        }
        return CantorOrdinal.canonical(this, List.of(new CantorOrdinal.Term(exponent, Natural.ONE)));
    }

    /**
     * The ordinal sum of the given terms, read from the largest to the smallest. Additions are folded from
     * the left, which is the associative way to build {@code α + β + γ}; a term that is larger than
     * everything before it simply absorbs it, exactly as {@code ω + ω^2 = ω^2}.
     */
    @Override
    public Ordinal sum(List<Ordinal> terms) {
        Objects.requireNonNull(terms, "terms");
        Ordinal result = zero;
        for (Ordinal term : terms) {
            result = result.add(Objects.requireNonNull(term, "term"));
        }
        return result;
    }

    /** Whether the ordinal is a {@link CantorOrdinal}; {@link #limit()} itself is not. */
    @Override
    public boolean contains(Ordinal ordinal) {
        return ordinal instanceof CantorOrdinal;
    }

    /** {@code ε₀}, the least ordinal this notation cannot write in normal form. */
    @Override
    public Ordinal limit() {
        return limit;
    }

    /** {@code ε₀}, spelled out for readability at call sites. */
    public Ordinal epsilonZero() {
        return limit;
    }

    /**
     * The tower {@code ω^ω^...^ω} with the given number of omegas, i.e. the ordinal whose fundamental
     * sequence defines {@code ω} levels of the notation. A height of zero is {@code 1}.
     */
    public Ordinal omegaTower(int height) {
        if (height < 0) {
            throw new IllegalArgumentException("a tower height must not be negative, was " + height);
        }
        Ordinal tower = one;
        for (int level = 0; level < height; level++) {
            tower = omegaPower(tower);
        }
        return tower;
    }

    @Override
    public String toString() {
        return NAME;
    }
}
