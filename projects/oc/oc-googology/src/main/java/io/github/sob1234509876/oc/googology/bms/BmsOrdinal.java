package io.github.sob1234509876.oc.googology.bms;

import io.github.sob1234509876.oc.googology.ComparisonResult;

import java.util.List;
import java.util.Objects;

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
 * <em>not</em> decide the deep standard-form conditions, because that needs the expansion rules (this
 * package deliberately has none yet - the reference's parent, bad-root, ascension-offset and
 * fundamental-sequence rules are left to the later TODO items, which build on this ordering). Passing a
 * matrix that is not a genuine standard form is the caller's mistake; the order is only promised for the
 * standard ones.
 *
 * <p><b>Exactness.</b> The comparison is a pure lexicographic comparison of two canonical integer
 * sequences, so it is total, transitive and antisymmetric, and {@link #compareMagnitude(BmsOrdinal)}
 * always returns one of the three proofs {@link ComparisonResult#LESS}, {@link ComparisonResult#EQUAL} or
 * {@link ComparisonResult#GREATER}, never {@link ComparisonResult#UNKNOWN} and never a lean. Unlike
 * {@code Natural}, no budget is involved and no approximation is made.
 *
 * <p><b>Not yet an {@code Ordinal}.</b> This type deliberately implements only
 * {@link Comparable}, not {@link io.github.sob1234509876.oc.googology.Ordinal}: it has no successor, no
 * addition and no fundamental sequence, because the expansion rules are not implemented in this task.
 * It is the ordering half of a BMS notation, not a full ordinal notation.
 *
 * @see BmsMatrix
 * @see io.github.sob1234509876.oc.googology.ComparisonResult#of(int)
 */
public final class BmsOrdinal implements Comparable<BmsOrdinal> {

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

    /**
     * The lexicographic order: columns from the left, each column row by row from the top, a shorter
     * column smaller when it is a prefix, and the longer matrix larger when every common column is equal.
     *
     * <p>For standard forms this is the order of the ordinals the matrices denote (see the class
     * documentation for the claim and its source). The comparison is exact, total and transitive, and it
     * is <em>not</em> valid for non-standard matrices - construction normalises the trailing-zero writing
     * but cannot decide deep standardness without the expansion rules.
     */
    @Override
    public int compareTo(BmsOrdinal other) {
        Objects.requireNonNull(other, "other");
        List<List<Integer>> left = matrix.columns();
        List<List<Integer>> right = other.matrix.columns();
        int shared = Math.min(left.size(), right.size());
        for (int column = 0; column < shared; column++) {
            int comparison = compareColumns(left.get(column), right.get(column));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(left.size(), right.size());
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

    private static int compareColumns(List<Integer> left, List<Integer> right) {
        int shared = Math.min(left.size(), right.size());
        for (int row = 0; row < shared; row++) {
            int comparison = Integer.compare(left.get(row), right.get(row));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(left.size(), right.size());
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
}
