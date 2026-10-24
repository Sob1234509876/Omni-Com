package io.github.sob1234509876.oc.googology.bms;

import io.github.sob1234509876.oc.googology.ComparisonResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A non-recursive Bashicu matrix (non-recursive BMS, NRBMS): the matrix shape of {@link BmsMatrix} read
 * with the extra rules of the Chinese Googology Wiki article
 * {@code documents/AI/bms/non-recursive-bms-zh.raw.wiki}.
 *
 * <p>The article's rules are:
 *
 * <ul>
 *   <li><b>first column.</b> The first column is a run of ones, for example {@code (1)}, {@code (1,1)} or
 *       {@code (1,1,1)}. This replaces BMS's all-zero first column: non-recursive BMS is a notation of
 *       non-recursive ordinals, which start at one rather than at zero.</li>
 *   <li><b>trailing zeros.</b> A zero at the end of a column is the same as nothing there, and zeros may be
 *       appended freely: {@code (1,1,1,1)(2,2,1)(3,3,2) = (1,1,1,1,0)(2,2,1,0,0)(3,3,2,0,0)}. This is the
 *       same canonical short form {@link BmsMatrix} already enforces.</li>
 *   <li><b>successor.</b> A last column of a single {@code (1)} is the successor: {@code
 *       (1,1,1)(2,2)(1) = (1,1,1)(2,2) + 1}. {@link #isSuccessor()}, {@link #successor()} and
 *       {@link #predecessor()} implement this rule. Note the difference from plain BMS, where the last
 *       column {@code (1)} is the {@code ω} limit: in non-recursive BMS the base value is one, so appending
 *       {@code (1)} is the next value.</li>
 *   <li><b>limit.</b> Otherwise, if the LNZ (lowermost non-zero) element of the last column finds a parent
 *       by the ordinary BMS rules, the expression is a limit and the ordinary BMS bad-root expansion is
 *       used: {@code (1,1,1)(2,2)(3,3,1,1)} expands to {@code (1,1,1)(2,2)(3,3,1)(4,4,2)(5,5,3)...}
 *       ({@link #fundamentalSequence(int)}).</li>
 *   <li><b>non-recursive expansion.</b> If the LNZ has no parent, there is no bad root, and the article
 *       removes the LNZ, adds one to the remaining rows of the last column and appends an infinite tail of
 *       ones. {@code (1,1,1)(2,2,1)} becomes {@code (1,1,1)(2,2)(3,3,1,1,1,1,...)}. That infinite column
 *       cannot be a finite matrix, so it is reported as a {@link NonRecursiveExpansion} description by
 *       {@link #nonRecursiveExpansion()} and {@link #fundamentalSequence(int)} has no finite element to
 *       return for it.</li>
 * </ul>
 *
 * <p><b>Order.</b> On finite expressions this type reuses the notation's lexicographic order through
 * {@link BmsOrder}: columns from the left, rows from the top, a prefix column smaller, the longer matrix
 * larger when the common columns agree. The same caveat as for {@link BmsOrdinal} applies: the article's
 * rules are not a proof that the lexicographic order coincides with the ordinal order for every form, and
 * this layer does not attempt the deep standard-form decision. The self check only expands and compares the
 * article's own examples.
 *
 * <p><b>No {@code fromBms}/{@code toBms} bridge.</b> The two reference articles define different
 * notations and neither gives a translation between them. There is a purely syntactic correspondence -
 * add one to every entry of a BMS matrix and its first column becomes a run of ones - and that map does
 * preserve the lexicographic order, but neither article claims that it preserves the ordinals the matrices
 * denote (BMS is zero-based, NRBMS one-based, and NRBMS is about non-recursive ordinals). A bridge would
 * therefore be a guess, so this class does not offer one. Converting the <em>shape</em> is just
 * {@code new int[][]} plus one to every entry; converting the <em>value</em> has no source.
 *
 * @see BmsOrdinal
 * @see NonRecursiveExpansion
 */
public final class NonRecursiveBms implements Comparable<NonRecursiveBms> {

    private final BmsMatrix matrix;

    private NonRecursiveBms(BmsMatrix matrix) {
        this.matrix = matrix;
    }

    /**
     * Reads a matrix in the article's notation, normalising the trailing zeros first. The first column must
     * be a run of ones and the last column must have a non-zero entry (the empty matrix is allowed and is
     * zero; a last column of all zeros is not a non-recursive BMS standard form).
     *
     * @throws IllegalArgumentException if the first column is not a run of ones, or the last column is all
     *         zeros
     * @throws NullPointerException if {@code columns} or one of its rows is {@code null}
     */
    public static NonRecursiveBms of(int[][] columns) {
        return of(BmsMatrix.canonical(columns));
    }

    /**
     * Reads an already canonical matrix in the article's notation. The matrix is normalised again (a no-op
     * for a canonical one, and a convenience for a builder result), and the same shape rules as
     * {@link #of(int[][])} are checked.
     *
     * @throws IllegalArgumentException if the first column is not a run of ones, or the last column is all
     *         zeros
     * @throws NullPointerException if {@code matrix} is {@code null}
     */
    public static NonRecursiveBms of(BmsMatrix matrix) {
        Objects.requireNonNull(matrix, "matrix");
        BmsMatrix canonical = BmsMatrix.canonical(columnsToArray(matrix));
        requireNonRecursiveForm(canonical);
        return new NonRecursiveBms(canonical);
    }

    /**
     * Parses the textual matrix notation, for example {@code "(1,1,1)(2,2)(1)"}, and applies the shape rules
     * of {@link #of(int[][])}.
     *
     * @throws IllegalArgumentException if the text is not a matrix, or the result is not a non-recursive BMS
     *         form
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public static NonRecursiveBms parse(String text) {
        return of(BmsMatrix.parse(text));
    }

    /** The canonical matrix behind this expression. */
    public BmsMatrix matrix() {
        return matrix;
    }

    /** The number of rows: the height of the tallest column. */
    public int rows() {
        return matrix.rows();
    }

    /** The number of columns. */
    public int length() {
        return matrix.length();
    }

    /** Whether this is the empty matrix, read as zero. */
    public boolean isEmpty() {
        return matrix.isEmpty();
    }

    /** Whether this is the empty matrix, read as zero. */
    public boolean isZero() {
        return matrix.isEmpty();
    }

    /**
     * Whether the last column is the single {@code (1)}, which the article defines as the successor case.
     * The empty matrix (zero) is not a successor.
     */
    public boolean isSuccessor() {
        return !matrix.isEmpty()
                && matrix.columns().get(matrix.length() - 1).equals(List.of(1));
    }

    /** Whether this is a limit, i.e. neither zero nor the successor case. */
    public boolean isLimit() {
        return !matrix.isEmpty() && !isSuccessor();
    }

    /**
     * The successor {@code S ~ (1)}: the article's successor step. The result is again a non-recursive BMS
     * form (its first column is unchanged and its last column is the single {@code (1)}).
     */
    public NonRecursiveBms successor() {
        return of(matrix.appendColumn(1));
    }

    /**
     * The {@code β} with {@code β + 1 = this}: the matrix without its last {@code (1)} column. Empty for
     * zero and for limit expressions.
     */
    public Optional<NonRecursiveBms> predecessor() {
        if (!isSuccessor()) {
            return Optional.empty();
        }
        return Optional.of(of(matrix.removeLastColumn()));
    }

    /** Whether the LNZ of the last column has a parent, i.e. whether the BMS bad root is defined. */
    public boolean hasBadRoot() {
        return !matrix.isEmpty() && BmsAnalysis.of(matrix).hasBadRoot();
    }

    /**
     * The {@code index}-th element of the fundamental sequence when the ordinary BMS bad-root expansion
     * applies, or empty when the expression is zero, is the successor {@code (1)}, or falls into the
     * article's non-recursive case (no bad root, so no finite fundamental sequence element exists; see
     * {@link #nonRecursiveExpansion()}).
     *
     * <p>The expansion is the same one {@link BmsOrdinal#fundamentalSequence(int)} implements, with the
     * article's indexing: the index counts the copies of the bad part, so {@code S[0]} is the good part
     * {@code G}. The article's example {@code (1,1,1)(2,2)(3,3,1,1)} is the matrix
     * {@code (1,1,1)(2,2)(3,3,1)} and its expansion is {@code (1,1,1)(2,2)(3,3,1)(4,4,2)(5,5,3)...}; the
     * finite elements are the prefixes of that sequence.
     *
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public Optional<NonRecursiveBms> fundamentalSequence(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("the fundamental sequence index must not be negative, but it is "
                    + index);
        }
        if (matrix.isEmpty() || isSuccessor()) {
            return Optional.empty();
        }
        BmsAnalysis analysis = BmsAnalysis.of(matrix);
        if (!analysis.hasBadRoot()) {
            return Optional.empty();
        }
        return Optional.of(of(analysis.expand(index)));
    }

    /**
     * The {@code index}-th fundamental sequence element for a limit expression, with the same rules as
     * {@link #fundamentalSequence(int)}.
     *
     * @throws IllegalStateException if this expression is zero, the successor {@code (1)}, or the
     *         non-recursive case with no finite fundamental sequence element
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public NonRecursiveBms expand(int index) {
        return fundamentalSequence(index).orElseThrow(() -> new IllegalStateException(
                this + " has no finite fundamental sequence element"));
    }

    /**
     * The article's non-recursive expansion, present exactly when the last column has a lowermost non-zero
     * element without a parent: the LNZ and the zero rows below it are removed from the last column, one is
     * added to the rows that remain, and the new column is completed with an infinite tail of ones. The
     * truncated last column stays in place, so {@code (1,1,1)(2,2,1)} becomes
     * {@code (1,1,1)(2,2)(3,3,1,1,1,1,...)}. The result is a {@link NonRecursiveExpansion} description
     * because an infinite column is not a finite matrix; empty for zero, for the successor {@code (1)} and
     * for expressions where the ordinary bad-root expansion applies.
     */
    public Optional<NonRecursiveExpansion> nonRecursiveExpansion() {
        if (matrix.isEmpty() || isSuccessor()) {
            return Optional.empty();
        }
        BmsAnalysis analysis = BmsAnalysis.of(matrix);
        if (analysis.hasBadRoot()) {
            return Optional.empty();
        }
        int lnzRow = analysis.lowermostNonZeroRow();
        if (lnzRow < 0) {
            return Optional.empty();
        }
        List<Integer> last = matrix.columns().get(matrix.length() - 1);
        List<Integer> truncated = new ArrayList<>(lnzRow);
        List<Integer> column = new ArrayList<>(lnzRow);
        for (int row = 0; row < lnzRow; row++) {
            truncated.add(last.get(row));
            column.add(last.get(row) + 1);
        }
        BmsMatrix fixed = matrix.removeLastColumn();
        if (!truncated.isEmpty()) {
            fixed = fixed.appendColumn(truncated.stream().mapToInt(Integer::intValue).toArray());
        }
        return Optional.of(new NonRecursiveExpansion(fixed, column));
    }

    /**
     * The shared lexicographic order: columns from the left, rows from the top, a prefix column smaller, the
     * longer matrix larger when every common column is equal. The same rule and caveats as
     * {@link BmsOrdinal#compareTo(BmsOrdinal)}.
     */
    @Override
    public int compareTo(NonRecursiveBms other) {
        Objects.requireNonNull(other, "other");
        return BmsOrder.compare(matrix, other.matrix);
    }

    /**
     * The comparison reported through the module's comparison API; always one of the three proofs
     * {@link ComparisonResult#LESS}, {@link ComparisonResult#EQUAL} or {@link ComparisonResult#GREATER}.
     */
    public ComparisonResult compareMagnitude(NonRecursiveBms other) {
        return ComparisonResult.of(compareTo(other));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NonRecursiveBms value && matrix.equals(value.matrix);
    }

    @Override
    public int hashCode() {
        return matrix.hashCode();
    }

    /** The matrix's rendering, for example {@code (1,1,1)(2,2)(1)}; {@code ()} for zero. */
    @Override
    public String toString() {
        return matrix.toString();
    }

    private static void requireNonRecursiveForm(BmsMatrix matrix) {
        if (matrix.isEmpty()) {
            return;
        }
        List<Integer> first = matrix.columns().get(0);
        for (int row = 0; row < first.size(); row++) {
            if (first.get(row) != 1) {
                throw new IllegalArgumentException("the first column of a non-recursive BMS form must be a run of"
                        + " ones, but it is " + first + "; " + matrix + " is not a non-recursive BMS form");
            }
        }
        List<Integer> last = matrix.columns().get(matrix.length() - 1);
        boolean anyNonZero = false;
        for (int value : last) {
            if (value != 0) {
                anyNonZero = true;
                break;
            }
        }
        if (!anyNonZero) {
            throw new IllegalArgumentException("the last column of a non-recursive BMS form must have a non-zero"
                    + " entry, but it is " + last + "; " + matrix + " is not a non-recursive BMS form");
        }
    }

    private static int[][] columnsToArray(BmsMatrix matrix) {
        int[][] raw = new int[matrix.length()][];
        for (int column = 0; column < raw.length; column++) {
            List<Integer> values = matrix.columns().get(column);
            raw[column] = new int[values.size()];
            for (int row = 0; row < values.size(); row++) {
                raw[column][row] = values.get(row);
            }
        }
        return raw;
    }
}
