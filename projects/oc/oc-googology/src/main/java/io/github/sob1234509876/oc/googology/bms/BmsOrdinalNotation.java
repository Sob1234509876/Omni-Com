package io.github.sob1234509876.oc.googology.bms;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.googology.Ordinal;
import io.github.sob1234509876.oc.googology.OrdinalNotation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The Bashicu matrix notation plugged into the module's {@link OrdinalNotation} seam: the factory behind
 * the {@link Ordinal} implementation of {@link BmsOrdinal}, so BMS values can be used as
 * {@link io.github.sob1234509876.oc.googology.TransfiniteHyperoperation} levels like the CNF values before
 * them.
 *
 * <p><b>The vocabulary, and where it comes from.</b> Every operation here is derived from the module's own
 * golden data - the reference nodes {@code (0)=1}, {@code (0)(0)=2}, {@code (0)(1)=ω}, {@code (0)(1)(1)=ω²},
 * {@code (0)(1)(2)=ω^ω} and {@code (0)(1,1)=ε₀}, plus the golden expansions:
 *
 * <ul>
 *   <li>{@link #finite(Natural)} writes the finite ordinal {@code n} as {@code n} zero columns; a value
 *       above {@link #MAX_FINITE_COLUMNS} cannot be materialised as columns and is returned as a
 *       comparison-capable structural value ({@link BmsLargeFinite}) instead;</li>
 *   <li>{@link #omega()} is {@code (0)(1)};</li>
 *   <li>{@link #omegaPower(Ordinal)} accepts the exponents the golden data pins: zero (giving one), a
 *       finite {@code n} (giving {@code (0)} followed by {@code n} columns of {@code (1)}, the pattern the
 *       golden {@code ω²=(0)(1)(1)} starts), and a pure tower {@code (0)(1)...(k)} (giving the tower one
 *       level taller, the pattern of the golden ε₀ sequence {@code ω, ω^ω, ω^ω^ω, ...}); any other
 *       exponent throws {@link UnsupportedOperationException}, because the ordinal of a general BMS matrix
 *       is not computed by this module;</li>
 *   <li>{@link #sum(List)} folds {@link BmsOrdinal#add(Ordinal)}, which concatenates the matrices - the
 *       ordinal sum, since the lexicographic order absorbs a smaller summand into a larger one exactly as
 *       ordinal addition does ({@code (0)(1)~(0)(1) = ω+1+ω = ω·2});</li>
 *   <li>{@link #limit()} is {@link BmsEpsilonZero}, the {@code ε₀} boundary the golden ε₀ expansions climb
 *       towards.</li>
 * </ul>
 *
 * <p><b>Not a canonical system.</b> Unlike the CNF world, two different matrices can denote the same
 * ordinal ({@code (0)(0)(1)} and {@code (0)(1)} are both {@code ω} under the reference's identifications),
 * and equality of {@link BmsOrdinal} stays matrix equality. The lexicographic order is what this module
 * promises, not ordinal canonicity; see {@link BmsOrdinal} for the exact scope of that claim.
 *
 * <p><b>The fundamental sequence convention.</b> The raw reference expansion counts the bad-part copies,
 * so {@code ω = (0)(1)} expands as {@code S[0]=0, S[1]=1, S[2]=2}: the sequence starts at zero. The
 * {@link Ordinal} interface's convention - the one {@code TransfiniteHyperoperation} documents and its
 * section checks pin - is {@code ω[m] = m+1}. {@link BmsOrdinal#fundamentalSequence(Natural)} therefore
 * reports the raw expansion at index {@code m+1}, which reproduces the CNF outcomes element by element
 * ({@code ω[m] = m+1}, {@code (ω²)[m] = ω·(m+1)}, {@code (ω^ω)[m] = ω^(m+1)}); the self check cross-checks
 * this agreement against the CNF chain. For the ε₀ boundary the two conventions differ by one leading
 * element: the raw sequence spends {@code S[1] = 1} before reaching {@code ω}, and the wrapper reports it
 * as {@code ε₀[0] = 1} where the old CNF notation started at {@code ε₀[0] = ω}. That is a documented
 * convention choice, not a mathematical disagreement - both are genuine fundamental sequences of ε₀.
 *
 * @see BmsOrdinal
 * @see BmsEpsilonZero
 * @since 2.0a
 */
public final class BmsOrdinalNotation implements OrdinalNotation {

    /** Stable name of this notation, used in rendering and to order ordinals of different notations. */
    public static final String NAME = "bashicu-matrix";

    /**
     * Largest finite ordinal materialised as zero columns. {@code (0)} repeated {@code n} times is the
     * BMS writing of the finite ordinal {@code n}, so a coefficient-sized value would need that many
     * columns; above this bound {@link #finite(Natural)} switches to {@link BmsLargeFinite}, which keeps
     * the value as a {@link Natural} and supports comparison, but not column-level operations.
     */
    public static final int MAX_FINITE_COLUMNS = 1024;

    /**
     * Largest fundamental sequence index the {@link Ordinal}-convention wrapper materialises. The raw
     * expansion at index {@code n} builds {@code n} copies of the bad part, so an astronomical index is
     * refused with an empty result - a documented limit of this implementation, not a mathematical claim.
     */
    public static final int MAX_FUNDAMENTAL_INDEX = 4096;

    /**
     * Largest number of columns a fundamental sequence element may materialise. A multi-block matrix such
     * as {@code ω^10·11} expands by repeating its bad part, so every step of a walk from it grows the
     * matrix; without a bound, a comparison budget of a few thousand steps would build matrices of
     * hundreds of thousands of columns and spend quadratic work analysing each. When the element would
     * exceed the bound the sequence reports empty - the walk stops and keeps its entry level, which is
     * the same "documented limit of the implementation" the index bound already establishes.
     */
    public static final int MAX_EXPANSION_COLUMNS = 2048;

    private static final BmsOrdinalNotation STANDARD = new BmsOrdinalNotation();

    private final BmsOrdinal zero;
    private final BmsOrdinal one;
    private final BmsOrdinal omega;
    private final BmsEpsilonZero limit;

    private BmsOrdinalNotation() {
        this.zero = BmsOrdinal.of(new int[0][]);
        this.one = BmsOrdinal.of(new int[][]{{0}});
        this.omega = BmsOrdinal.of(new int[][]{{0}, {1}});
        this.limit = new BmsEpsilonZero(this);
    }

    /** The shared instance of this notation. */
    public static BmsOrdinalNotation standard() {
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

    /**
     * The finite ordinal {@code value}: {@code value} zero columns when it fits {@link #MAX_FINITE_COLUMNS},
     * otherwise a {@link BmsLargeFinite} that keeps the value exactly and compares correctly but does not
     * offer the column-level operations.
     *
     * @throws NullPointerException if {@code value} is {@code null}
     */
    @Override
    public Ordinal finite(Natural value) {
        Objects.requireNonNull(value, "value");
        if (!value.isExact()) {
            return new BmsLargeFinite(this, value);
        }
        long columns = value.toLongSaturated();
        if (columns > MAX_FINITE_COLUMNS) {
            return new BmsLargeFinite(this, value);
        }
        return finiteOrdinal((int) columns);
    }

    /** The finite ordinal {@code n} as {@code n} zero columns, for a checked small {@code n}. */
    private BmsOrdinal finiteOrdinal(int columns) {
        int[][] raw = new int[columns][];
        for (int index = 0; index < columns; index++) {
            raw[index] = new int[]{0};
        }
        return BmsOrdinal.of(raw);
    }

    @Override
    public Ordinal omega() {
        return omega;
    }

    /**
     * {@code ω^exponent}, for the exponents the golden data pins: zero gives one, a finite {@code n} gives
     * {@code (0)} followed by {@code n} columns of {@code (1)}, and a pure tower {@code (0)(1)...(k)} gives
     * the tower one level taller. An exponent equal to {@link #limit()} folds back into that limit, because
     * {@code ω^ε₀ = ε₀}.
     *
     * @throws IllegalArgumentException when the exponent is an ordinal of another notation
     * @throws UnsupportedOperationException when the exponent is a general BMS matrix whose power the
     *         module cannot derive, or when the result would not fit the column bound
     */
    @Override
    public Ordinal omegaPower(Ordinal exponent) {
        Objects.requireNonNull(exponent, "exponent");
        if (exponent instanceof BmsEpsilonZero) {
            return limit;
        }
        if (!(exponent instanceof BmsOrdinal matrix)) {
            throw new IllegalArgumentException("the exponent " + exponent + " (" + exponent.notation().name()
                    + ") is not a " + NAME + " ordinal");
        }
        Optional<Natural> finite = matrix.finiteValue();
        if (finite.isPresent()) {
            long columns = finite.get().toLongSaturated();
            if (columns > MAX_FINITE_COLUMNS) {
                throw new UnsupportedOperationException("ω^" + finite.get() + " needs " + columns
                        + " columns of (1), above this notation's materialisation bound " + MAX_FINITE_COLUMNS);
            }
            return finitePower((int) columns);
        }
        int height = towerHeight(matrix);
        if (height >= 0) {
            if (height + 1 > MAX_FINITE_COLUMNS) {
                throw new UnsupportedOperationException("ω^(the tower of " + height + " omegas) needs "
                        + (height + 2) + " columns, above this notation's materialisation bound "
                        + MAX_FINITE_COLUMNS);
            }
            return tower(height + 1);
        }
        throw new UnsupportedOperationException("ω^" + matrix + " is not derivable for a general "
                + NAME + " matrix: this module computes the power only for finite and pure-tower exponents");
    }

    /** {@code ω^n} as {@code (0)} followed by {@code n} columns of {@code (1)}. */
    private BmsOrdinal finitePower(int exponent) {
        if (exponent == 0) {
            return one;
        }
        int[][] raw = new int[exponent + 1][];
        raw[0] = new int[]{0};
        for (int index = 1; index <= exponent; index++) {
            raw[index] = new int[]{1};
        }
        return BmsOrdinal.of(raw);
    }

    /**
     * The height of a pure tower {@code (0)(1)(2)...(k)}, or {@code -1} when the matrix is anything else.
     * The towers are the matrices the golden ε₀ sequence names: {@code (0)(1)} is {@code ω},
     * {@code (0)(1)(2)} is {@code ω^ω}, {@code (0)(1)(2)(3)} is {@code ω^ω^ω}.
     */
    private static int towerHeight(BmsOrdinal matrix) {
        List<List<Integer>> columns = matrix.matrix().columns();
        for (int column = 0; column < columns.size(); column++) {
            List<Integer> values = columns.get(column);
            if (values.size() != 1 || values.get(0) != column) {
                return -1;
            }
        }
        return columns.size() - 1;
    }

    /**
     * The tower {@code ω^ω^...^ω} with the given number of omegas, written {@code (0)(1)...(height)}: the
     * matrices the golden ε₀ sequence climbs through. A height of zero is {@code 1}.
     *
     * @throws IllegalArgumentException when the height is negative or above {@link #MAX_FINITE_COLUMNS}
     */
    public Ordinal omegaTower(int height) {
        if (height < 0 || height > MAX_FINITE_COLUMNS) {
            throw new IllegalArgumentException("a tower height must be between 0 and " + MAX_FINITE_COLUMNS
                    + ", was " + height);
        }
        return tower(height);
    }

    /** The tower of the given height: {@code height} columns after the leading zero column. */
    private BmsOrdinal tower(int height) {
        if (height == 0) {
            return one;
        }
        int[][] raw = new int[height + 1][];
        for (int column = 0; column <= height; column++) {
            raw[column] = new int[]{column};
        }
        return BmsOrdinal.of(raw);
    }

    /**
     * The ordinal sum of the given terms, read from the largest to the smallest, folded through
     * {@link BmsOrdinal#add(Ordinal)}: a term that is larger than everything before it absorbs it, exactly
     * as {@code ω + ω² = ω²}.
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

    /** Whether the ordinal is a {@link BmsOrdinal}; {@link #limit()} itself is not. */
    @Override
    public boolean contains(Ordinal ordinal) {
        return ordinal instanceof BmsOrdinal;
    }

    /** {@code ε₀}, the boundary the golden ε₀ expansions climb towards, as a {@link BmsEpsilonZero}. */
    @Override
    public Ordinal limit() {
        return limit;
    }

    /** {@code ε₀}, spelled out for readability at call sites. */
    public Ordinal epsilonZero() {
        return limit;
    }

    @Override
    public String toString() {
        return NAME;
    }
}
