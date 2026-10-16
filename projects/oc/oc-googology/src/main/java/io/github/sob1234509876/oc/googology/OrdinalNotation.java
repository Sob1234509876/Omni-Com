package io.github.sob1234509876.oc.googology;

import java.util.List;
import java.util.Objects;

/**
 * A factory for the ordinals of one notation: the seam that lets a caller plug in their own ordinal
 * notation and use it as a {@link TransfiniteHyperoperation} level.
 *
 * <p>A notation provides its vocabulary ({@link #zero()}, {@link #finite(Natural)}, {@link #omega()},
 * {@link #omegaPower(Ordinal)}, {@link #sum(List)}) and describes its own reach:
 *
 * <ul>
 *   <li>{@link #contains(Ordinal)} says whether an ordinal can be written in this notation, and</li>
 *   <li>{@link #limit()} is the least ordinal the notation cannot name, which is the ordinal whose
 *       fundamental sequence is the notation's own increasing approximations. The default notation's
 *       limit is {@code ε₀}, the supremum of {@code 1, ω, ω^ω, ω^ω^ω, ...}.</li>
 * </ul>
 *
 * <p>Two obligations come with the interface. First, the ordinals a notation produces must be closed
 * under fundamental sequences (see {@link Ordinal#fundamentalSequence(Natural)}): a notation that cannot
 * express {@code ω·(n+1)} cannot name {@code ω^2} either, because {@code (ω^2)[n] = ω·(n+1)}. Second, a
 * notation's ordinals must be immutable values with a working {@code equals}, {@code hashCode} and
 * {@code compareTo}, since they are used as tower levels inside {@link Natural}.
 *
 * <p>The library ships one implementation, {@code io.github.sob1234509876.oc.googology.ordinal
 * .CantorNormalForm}, and that is also the notation {@link Natural} uses for the finite levels of its
 * structural values.
 *
 * @see Ordinal
 */
public interface OrdinalNotation {

    /** Stable name of this notation, used in rendering and to order ordinals of different notations. */
    String name();

    /** The ordinal {@code 0}. */
    Ordinal zero();

    /** The ordinal {@code 1}, the successor of zero. */
    default Ordinal one() {
        return finite(Natural.ONE);
    }

    /** The finite ordinal {@code value}. */
    Ordinal finite(Natural value);

    /** The first infinite ordinal {@code ω}. */
    Ordinal omega();

    /**
     * {@code ω^exponent}. Implementations should keep their canonical form: an exponent equal to the
     * notation's own {@link #limit()} has to fold back into that limit, because {@code ω^ε₀ = ε₀}.
     */
    Ordinal omegaPower(Ordinal exponent);

    /**
     * The ordinal sum of the given terms, canonicalised by the notation: zero terms are dropped, equal
     * exponents are merged into coefficients and the terms end up in decreasing exponent order. The list
     * is read from the largest term to the smallest, so {@code sum(List.of(omega(), one()))} is
     * {@code ω + 1}.
     */
    Ordinal sum(List<Ordinal> terms);

    /** Whether the ordinal can be written in this notation. */
    boolean contains(Ordinal ordinal);

    /**
     * The least ordinal this notation cannot name, i.e. its ceiling. The ordinal returned is itself a
     * valid {@link Ordinal} with a fundamental sequence, but {@link #contains(Ordinal)} reports
     * {@code false} for it.
     */
    Ordinal limit();

    /** Convenience for {@link #finite(Natural)} with a small literal. */
    default Ordinal of(long value) {
        return finite(Natural.of(value));
    }

    /** {@code ω^exponent · coefficient}, the shape of one normal form term. */
    default Ordinal term(Ordinal exponent, Natural coefficient) {
        Objects.requireNonNull(coefficient, "coefficient");
        return omegaPower(exponent).times(coefficient);
    }

    /** The sum of two ordinals, in this notation's canonical form. */
    default Ordinal sum(Ordinal first, Ordinal second) {
        return sum(List.of(first, second));
    }

    /** The sum of three ordinals, in this notation's canonical form. */
    default Ordinal sum(Ordinal first, Ordinal second, Ordinal third) {
        return sum(List.of(first, second, third));
    }

    /** Whether this notation and the other are the same notation. */
    default boolean sameAs(OrdinalNotation other) {
        return other != null && name().equals(other.name());
    }
}
