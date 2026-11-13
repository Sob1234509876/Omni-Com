package io.github.sob1234509876.oc.googology.bms;

import io.github.sob1234509876.oc.googology.ComparisonResult;
import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.googology.Ordinal;
import io.github.sob1234509876.oc.googology.OrdinalNotation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A Bashicu matrix in standard form, read as an ordinal and ordered by the lexicographic rule of the
 * notation: compare the columns from the left, compare each column row by row from the top, and if every
 * compared column is equal, the longer matrix is the larger one.
 *
 * <p>Two worked examples from the task definition fix the rule:
 *
 * <ul>
 *   <li>{@code A = BMS(0)(1,1)} and {@code B = BMS(0)(1)(2)}: the second columns are {@code (1,1)} and
 *       {@code (1)}, and {@code (1,1) > (1)}, so {@code A > B}. The reference identifies these matrices
 *       as {@code ε₀} and {@code ω^ω}, and indeed {@code ε₀ > ω^ω}.</li>
 *   <li>{@code C = BMS(0)(1,0)} and the same {@code B}: the raw second column {@code (1,0)} is longer than
 *       {@code (1)} and so compares greater, but {@code BMS(0)(1,0)} is only a padded way of writing
 *       {@code BMS(0)(1)}. Once the trailing zero is gone the two matrices share their first two columns
 *       exactly, and the equal prefix runs out: {@code B} has a third column, {@code C} does not, so
 *       {@code B > C}. This is why construction normalises instead of trusting the raw writing.</li>
 * </ul>
 *
 * <p><b>What the order is claimed to be.</b> The task states, and the reference's analysis section is
 * consistent with, the claim that on <em>standard forms</em> this lexicographic order coincides with the
 * order of the ordinals the matrices denote: {@code (0)(1)} is {@code ω}, {@code (0)(1)(2)} is
 * {@code ω^ω}, {@code (0,0)(1,1)} is {@code ε₀}, {@code (0,0)(1,1)(1,1)} is {@code ε₁},
 * {@code (0,0)(1,1)(2,0)} is {@code ε_ω} and {@code (0,0)(1,1)(2,1)} is {@code ζ₀}. Those identifications
 * come from the reference, not from this class: this class does not compute the ordinal of a matrix. The
 * self check cross checks the six nodes up to {@code ε₀} - {@code (0)}, {@code (0)(0)}, {@code (0)(1)},
 * {@code (0)(1)(1)}, {@code (0)(1)(2)} and {@code (0,0)(1,1)} - against the corresponding ordinals of
 * {@link io.github.sob1234509876.oc.googology.CantorNormalForm} and {@code ε₀}, and the higher nodes only
 * against each other's order. The claim is not proved here, and it is not a definition of the ordinal
 * order.
 *
 * <p><b>What the order is not valid for.</b> For a matrix that is not a standard form the lexicographic
 * order and the ordinal order come apart: the raw column {@code (1,0)} is lexicographically greater than
 * {@code (1)} although both denote the same ordinal once the trailing zero is dropped; a matrix whose first
 * column is not all zeros has no zero base at all; and the reference excludes further shapes (a column that
 * is not non-increasing, or a row entry more than one above everything to its left). Construction therefore
 * normalises the trailing-zero representation and rejects a first column that is not all zeros. It does
 * <em>not</em> decide the deep standard-form conditions (the closure from the reference's limit sequence
 * under predecessor and expansion), because that needs a much stronger analysis than this layer offers.
 * Passing a matrix that is not a genuine standard form is the caller's mistake; the order is only promised
 * for the standard ones.
 *
 * <p><b>Expansion.</b> {@link #fundamentalSequence(int)} implements the reference's expansion rules:
 * the empty matrix is zero; a matrix whose last column is all zeros has no bad root and is the successor
 * of the matrix without that column; otherwise the LNZ (lowermost non-zero) element of the last column
 * determines the parent chain, the bad root, the good part {@code G}, the bad part {@code B}, the
 * ascension offset vector {@code Δ} and the copies {@code B_m}, and {@code S[n] = G ~ B_0 ~ ... ~ B_{n-1}}
 * (with {@code B_0 = B}). The copies add {@code m·Δ} only at elements whose ancestor chain passes through
 * the bad root, which is the reference's ancestor exception. The self check asserts, for every standard
 * matrix it expands, that {@code S > S[n]} and that {@code S[0] < S[1] < ...}: those are the properties a
 * usable fundamental sequence needs, and the class documentation records what is argued and what is only
 * tested.
 *
 * <p><b>Exactness.</b> The comparison is a pure lexicographic comparison of two canonical integer
 * sequences, so it is total, transitive and antisymmetric, and {@link #compareMagnitude(BmsOrdinal)}
 * always returns one of the three proofs {@link ComparisonResult#LESS}, {@link ComparisonResult#EQUAL} or
 * {@link ComparisonResult#GREATER}, never {@link ComparisonResult#UNKNOWN} and never a lean. Unlike
 * {@code Natural}, no budget is involved and no approximation is made. Expansion is exact as well: it is a
 * finite rearrangement of the columns under the reference's rules, not a search or an approximation.
 *
 * <p><b>An {@link io.github.sob1234509876.oc.googology.Ordinal} as well.</b> This type implements the
 * module's {@code Ordinal} interface through the {@link BmsOrdinalNotation} factory: the vocabulary the
 * interface needs is mapped onto BMS semantics where the golden data pins it - addition as matrix
 * concatenation (the ordinal sum under the lexicographic absorption), multiplication as repeated
 * concatenation, {@code ω^} for finite and pure-tower exponents, and {@code ε₀} as the
 * {@link BmsEpsilonZero} boundary. Operations the module cannot derive for a general matrix
 * ({@code ω^(ω·2)} and friends) throw {@code UnsupportedOperationException} with a precise message, which
 * the {@code Ordinal} javadoc marks as implementation-optional. The fundamental sequence of the interface,
 * {@link #fundamentalSequence(Natural)}, reports the raw expansion one index further along, which is what
 * aligns it with the {@code ω[m] = m+1} convention {@code TransfiniteHyperoperation} documents; see the
 * method javadoc.
 *
 * @see BmsMatrix
 * @see NonRecursiveBms
 * @see io.github.sob1234509876.oc.googology.ComparisonResult#of(int)
 */
public final class BmsOrdinal implements Ordinal {

    private final BmsMatrix matrix;

    private BmsOrdinal(BmsMatrix matrix) {
        this.matrix = matrix;
    }

    /**
     * Reads a matrix as an ordinal, normalising it first: trailing zeros are dropped and the empty matrix
     * is the ordinal zero. The first column must be all zeros (after normalisation, {@code (0)}), because
     * that is where a BMS form starts.
     *
     * @throws IllegalArgumentException if a column is empty, an entry is negative, or the first column is
     *         not all zeros
     * @throws NullPointerException if {@code columns} or one of its rows is {@code null}
     */
    public static BmsOrdinal of(int[][] columns) {
        return of(BmsMatrix.canonical(columns));
    }

    /**
     * Reads an already canonical matrix as an ordinal. The matrix is not re-normalised (it cannot need it),
     * but its first column must be all zeros. The empty matrix is the ordinal zero.
     *
     * @throws IllegalArgumentException if the first column is not all zeros
     * @throws NullPointerException if {@code matrix} is {@code null}
     */
    public static BmsOrdinal of(BmsMatrix matrix) {
        Objects.requireNonNull(matrix, "matrix");
        requireZeroBase(matrix);
        return new BmsOrdinal(matrix);
    }

    /** The canonical matrix behind this ordinal. */
    public BmsMatrix matrix() {
        return matrix;
    }

    /** The number of rows of the matrix. */
    public int rows() {
        return matrix.rows();
    }

    /** The number of columns of the matrix. */
    public int length() {
        return matrix.length();
    }

    /** Whether this is the empty matrix, the ordinal zero. */
    public boolean isEmpty() {
        return matrix.isEmpty();
    }

    /** Whether this is the empty matrix, the ordinal zero. */
    public boolean isZero() {
        return matrix.isEmpty();
    }

    /**
     * Whether the last column is all zeros, which is the reference's successor case: such a matrix is the
     * successor of the matrix with that column removed. The empty matrix (zero) is not a successor.
     *
     * <p>Note that a last column of a single {@code (1)} is <em>not</em> a successor in plain BMS: it is
     * the {@code ω} limit, handled by the bad-root expansion. The single-{@code (1)} successor rule is the
     * non-recursive variant's; see {@link NonRecursiveBms#isSuccessor()}.
     */
    public boolean isSuccessor() {
        if (matrix.isEmpty()) {
            return false;
        }
        for (int value : matrix.columns().get(matrix.length() - 1)) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    /** Whether this ordinal is a limit, i.e. neither zero nor a successor. */
    public boolean isLimit() {
        return !matrix.isEmpty() && !isSuccessor();
    }

    /**
     * The successor {@code S ~ (0)}: appending the zero column is the reference's successor step. The
     * result is again a zero-based canonical matrix.
     */
    public BmsOrdinal successor() {
        return of(matrix.appendColumn(0));
    }

    /**
     * The {@code β} with {@code β + 1 = this}: the matrix without its last column when the last column is
     * all zeros. Empty for zero and for limit ordinals.
     */
    public Optional<Ordinal> predecessor() {
        if (!isSuccessor()) {
            return Optional.empty();
        }
        return Optional.<Ordinal>of(of(matrix.removeLastColumn()));
    }

    /**
     * The {@code index}-th element of the fundamental sequence, or empty when this ordinal has no
     * fundamental sequence: the empty matrix (zero) and the successor case have none. A matrix whose last
     * column has a non-zero entry always has a fundamental sequence (its limit case).
     *
     * <p>The expansion is the reference's:
     *
     * <ol>
     *   <li>the empty matrix is {@code 0};</li>
     *   <li>if the last column is all zeros there is no bad root, and the matrix is the successor of the
     *       matrix without that column (see {@link #predecessor()});</li>
     *   <li>otherwise the LNZ (lowermost non-zero) element of the last column is found, its parent gives
     *       the bad root, the columns before the bad root are {@code G}, the columns from the bad root to
     *       the second-to-last are {@code B}, the ascension offset vector {@code Δ} is computed by the
     *       reference's rule, and {@code S[index] = G ~ B_0 ~ B_1 ~ ... ~ B_{index-1}} with
     *       {@code B_0 = B}. Each {@code B_m} adds {@code m·Δ} only where the row-wise ancestor chain of
     *       the element passes through the bad root; elements whose ancestors avoid the bad root keep their
     *       value (the reference's exception).</li>
     * </ol>
     *
     * <p>The index counts the copies of the bad part, so {@code S[0] = G}. This is the reading of the
     * reference's mathematical form {@code sup{G, G B^(0), G B^(0) B^(1), ...}}: the prose's
     * {@code S[n] = G ~ B ~ B_1 ~ ... ~ B_{n-1}} is the same formula with {@code B = B_0}, where the
     * displayed list has {@code n} entries.
     *
     * @throws IllegalArgumentException if {@code index} is negative, or if a last column with a non-zero
     *         entry has no LNZ parent (which means the matrix is not a standard form)
     */
    public Optional<BmsOrdinal> fundamentalSequence(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("the fundamental sequence index must not be negative, but it is "
                    + index);
        }
        if (matrix.isEmpty()) {
            return Optional.empty();
        }
        BmsAnalysis analysis = BmsAnalysis.of(matrix);
        if (!analysis.hasLowermostNonZero()) {
            return Optional.empty();
        }
        if (!analysis.hasBadRoot()) {
            throw new IllegalArgumentException("the lowermost non-zero element of " + matrix
                    + " has no parent, so the matrix has no bad root and is not a standard form");
        }
        return Optional.of(of(analysis.expand(index)));
    }

    /**
     * The {@code index}-th fundamental sequence element for a limit ordinal, with the same rules as
     * {@link #fundamentalSequence(int)}.
     *
     * @throws IllegalStateException if this ordinal is zero or a successor, so it has no fundamental
     *         sequence
     * @throws IllegalArgumentException if {@code index} is negative, or if the matrix is not a standard
     *         form
     */
    public BmsOrdinal expand(int index) {
        return fundamentalSequence(index).orElseThrow(() -> new IllegalStateException(
                this + " has no fundamental sequence: it is zero or a successor"));
    }

    // ------------------------------------------------------------------ the Ordinal interface

    /** The Bashicu matrix notation, the factory these values come from. */
    @Override
    public BmsOrdinalNotation notation() {
        return BmsOrdinalNotation.standard();
    }

    /**
     * The finite value when every column is the single zero column, i.e. when the matrix is the BMS
     * writing of a finite ordinal: {@code n} columns of {@code (0)} is {@code n}. Empty for every
     * transfinite matrix.
     */
    @Override
    public Optional<Natural> finiteValue() {
        for (List<Integer> column : matrix.columns()) {
            if (column.size() != 1 || column.get(0) != 0) {
                return Optional.empty();
            }
        }
        return Optional.of(Natural.of(matrix.length()));
    }

    /**
     * The {@code index}-th element of the fundamental sequence in the {@code Ordinal} interface's
     * {@code ω[m] = m+1} convention: the raw expansion {@link #fundamentalSequence(int)} at index
     * {@code m+1}, skipping the raw sequence's leading {@code S[0] = G} element.
     *
     * <p>The shift is what aligns this sequence with the CNF outcomes the transfinite hyperoperation pins:
     * the raw expansion of {@code ω = (0)(1)} reads {@code S[n] = n} (starting at zero), so the shifted
     * sequence reads {@code ω[m] = m+1}; the same one-step shift carries over to {@code (ω²)[m] = ω·(m+1)}
     * and {@code (ω^ω)[m] = ω^(m+1)}. For the ε₀ boundary the shift makes {@code ε₀[0] = 1} where the old
     * CNF notation started at {@code ε₀[0] = ω} - a documented convention difference, since both are
     * genuine fundamental sequences.
     *
     * <p>Empty when this ordinal is zero or a successor, and also when the shifted index is structural or
     * above {@link BmsOrdinalNotation#MAX_FUNDAMENTAL_INDEX}: the expansion would need that many copies of
     * the bad part materialised, which is a documented limit of this implementation rather than a
     * mathematical claim.
     */
    @Override
    public Optional<Ordinal> fundamentalSequence(Natural index) {
        Objects.requireNonNull(index, "index");
        if (!isLimit()) {
            return Optional.empty();
        }
        Natural shifted = index.add(Natural.ONE);
        if (!shifted.isExact() || shifted.toLongSaturated() > BmsOrdinalNotation.MAX_FUNDAMENTAL_INDEX) {
            return Optional.empty();
        }
        return fundamentalSequence((int) shifted.toLongSaturated()).map(ordinal -> ordinal);
    }

    /**
     * Ordinal addition as matrix concatenation, with one normalisation: a transfinite right operand
     * absorbs a finite left tail ({@code (ω+1) + ω = ω·2}, {@code 2 + ω = ω}), so the left operand's
     * trailing zero columns are dropped before the matrices are written one after the other. Without that
     * strip the concatenation would be a non-standard writing whose lexicographic position sits below the
     * summand it should exceed. A finite right operand is the successor step repeated, which concatenates
     * safely: {@code (0)(1)~(0)(0) = ω + 2}.
     *
     * <p>Adding the {@link BmsEpsilonZero ε₀} boundary returns the boundary, because {@code ε₀} absorbs
     * every ordinal below it. Two finite values add through the {@link Natural} arithmetic, which covers
     * sums above the materialisation bound as {@link BmsLargeFinite} values.
     *
     * @throws IllegalArgumentException when the other ordinal belongs to a foreign notation
     * @throws UnsupportedOperationException when a transfinite matrix is added to a finite value above the
     *         materialisation bound, whose sum would need that value written as columns
     */
    @Override
    public Ordinal add(Ordinal other) {
        Objects.requireNonNull(other, "other");
        if (other instanceof BmsEpsilonZero) {
            return other;
        }
        if (other instanceof BmsLargeFinite finite) {
            Optional<Natural> mine = finiteValue();
            if (mine.isPresent()) {
                return notation().finite(mine.get().add(finite.value()));
            }
            throw new UnsupportedOperationException("the sum " + this + " + " + finite.value()
                    + " needs the finite addend written as columns, which this notation does not materialise");
        }
        if (!(other instanceof BmsOrdinal right)) {
            throw new IllegalArgumentException("cannot combine " + other + " (" + other.notation().name()
                    + ") with a " + notation().name() + " ordinal");
        }
        if (right.finiteValue().isPresent()) {
            return of(concat(matrix, right.matrix));
        }
        return of(concat(withoutFiniteTail(matrix), right.matrix));
    }

    /**
     * Multiplication by a finite coefficient as repeated addition: {@code α·n} is {@code α} added to
     * itself {@code n} times, which for a transfinite matrix scales its leading part exactly as the CNF
     * rule does ({@code (ω+1)·2 = ω·2+1}). Two finite values multiply through the {@link Natural}
     * arithmetic, which covers products above the materialisation bound.
     *
     * @throws UnsupportedOperationException when the product of a transfinite matrix with the factor would
     *         exceed the column bound, so the copies cannot be materialised
     */
    @Override
    public Ordinal times(Natural factor) {
        Objects.requireNonNull(factor, "factor");
        if (factor.isZero()) {
            return notation().zero();
        }
        if (factor.isOne()) {
            return this;
        }
        Optional<Natural> finite = finiteValue();
        if (finite.isPresent()) {
            return notation().finite(finite.get().multiply(factor));
        }
        if (!factor.isExact() || factor.toLongSaturated() > BmsOrdinalNotation.MAX_FINITE_COLUMNS
                || (long) matrix.length() * factor.toLongSaturated() > BmsOrdinalNotation.MAX_FINITE_COLUMNS) {
            throw new UnsupportedOperationException(this + " · " + factor + " needs " + factor
                    + " concatenated copies, above this notation's materialisation bound "
                    + BmsOrdinalNotation.MAX_FINITE_COLUMNS + " columns");
        }
        int copies = (int) factor.toLongSaturated();
        Ordinal result = this;
        for (int copy = 1; copy < copies; copy++) {
            result = result.add(this);
        }
        return result;
    }

    /**
     * {@code ω^this}, answered by {@link BmsOrdinalNotation#omegaPower(Ordinal)}: derivable for finite and
     * pure-tower exponents, refused with a precise message for a general matrix.
     */
    @Override
    public Ordinal omegaPower() {
        return notation().omegaPower(this);
    }

    /**
     * The lexicographic order: columns from the left, each column row by row from the top, a shorter
     * column smaller when it is a prefix, and the longer matrix larger when every common column is equal.
     *
     * <p>For standard forms this is the order of the ordinals the matrices denote (see the class
     * documentation for the claim and its source). The comparison is exact, total and transitive, and it
     * is <em>not</em> valid for non-standard matrices - construction normalises the trailing-zero writing
     * but cannot decide deep standardness. Against the {@link BmsEpsilonZero ε₀} boundary the wrapped
     * matrix decides; against an ordinal of a foreign notation the notation names order the values, as the
     * {@code Ordinal} interface documents.
     */
    @Override
    public int compareTo(Ordinal other) {
        Objects.requireNonNull(other, "other");
        if (other instanceof BmsOrdinal ordinal) {
            return BmsOrder.compare(matrix, ordinal.matrix);
        }
        if (other instanceof BmsEpsilonZero boundary) {
            return BmsOrder.compare(matrix, boundary.comparisonMatrix().matrix);
        }
        if (other instanceof BmsLargeFinite finite) {
            Optional<Natural> mine = finiteValue();
            return mine.isPresent() ? mine.get().compareTo(finite.value()) : 1;
        }
        return notation().name().compareTo(other.notation().name());
    }

    /**
     * The same comparison as {@link #compareTo(BmsOrdinal)}, reported through the module's comparison API.
     * Because the rule is exact, the result is always a proof - {@link ComparisonResult#LESS},
     * {@link ComparisonResult#EQUAL} or {@link ComparisonResult#GREATER} - and never
     * {@link ComparisonResult#UNKNOWN} or a probable lean.
     */
    public ComparisonResult compareMagnitude(BmsOrdinal other) {
        return ComparisonResult.of(compareTo(other));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BmsOrdinal ordinal && matrix.equals(ordinal.matrix);
    }

    @Override
    public int hashCode() {
        return matrix.hashCode();
    }

    /** The matrix's rendering, for example {@code (0)(1,1)(2)}; {@code ()} for zero. */
    @Override
    public String toString() {
        return matrix.toString();
    }

    private static void requireZeroBase(BmsMatrix matrix) {
        if (matrix.columns().isEmpty()) {
            return;
        }
        List<Integer> first = matrix.columns().get(0);
        for (int row = 0; row < first.size(); row++) {
            if (first.get(row) != 0) {
                throw new IllegalArgumentException("the first column of a BMS ordinal must be all zeros, but it is "
                        + first + "; " + matrix + " is not a standard form");
            }
        }
    }

    /** The two matrices written one after the other, canonicalised; the ordinal sum under the lex order. */
    private static BmsMatrix concat(BmsMatrix left, BmsMatrix right) {
        int[][] raw = new int[left.length() + right.length()][];
        int index = 0;
        for (List<Integer> column : left.columns()) {
            raw[index++] = toArray(column);
        }
        for (List<Integer> column : right.columns()) {
            raw[index++] = toArray(column);
        }
        return BmsMatrix.canonical(raw);
    }

    /**
     * The matrix without its trailing single-zero columns: the finite tail a transfinite right operand
     * absorbs in an addition. A finite matrix strips to the empty matrix, which is exactly the reading
     * {@code n + λ = λ}.
     */
    private static BmsMatrix withoutFiniteTail(BmsMatrix matrix) {
        List<List<Integer>> columns = matrix.columns();
        int end = columns.size();
        while (end > 0) {
            List<Integer> column = columns.get(end - 1);
            if (column.size() != 1 || column.get(0) != 0) {
                break;
            }
            end--;
        }
        if (end == columns.size()) {
            return matrix;
        }
        int[][] raw = new int[end][];
        for (int index = 0; index < end; index++) {
            raw[index] = toArray(columns.get(index));
        }
        return BmsMatrix.canonical(raw);
    }

    private static int[] toArray(List<Integer> column) {
        int[] values = new int[column.size()];
        for (int row = 0; row < values.length; row++) {
            values[row] = column.get(row);
        }
        return values;
    }
}
