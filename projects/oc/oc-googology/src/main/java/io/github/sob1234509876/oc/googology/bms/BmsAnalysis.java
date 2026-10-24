package io.github.sob1234509876.oc.googology.bms;

import java.util.ArrayList;
import java.util.List;

/**
 * The reference's analysis of one matrix: the parent of every element, the lowermost non-zero element
 * (LNZ) of the last column, the bad root, the ascension offset vector {@code Δ}, and the ascension
 * matrix that says which elements of the bad part move.
 *
 * <p>This is the expansion machinery behind {@link BmsOrdinal#fundamentalSequence(int)} and {@link
 * NonRecursiveBms}, kept in one place so both types read the same definitions. It is package private
 * because it is an implementation detail: callers should use the ordinal types.
 *
 * <p>The definitions are those of the reference (:file:`documents/AI/bms/bms-zh.raw.wiki`):
 *
 * <ul>
 *   <li>the parent of a first-row element is the rightmost smaller element to its left;</li>
 *   <li>the parent of an element in a lower row is the rightmost smaller element to its left whose
 *       element directly above is an ancestor of the element directly above this one;</li>
 *   <li>the ancestors of an element are the element itself and its parent chain;</li>
 *   <li>the bad root is the column of the parent of the LNZ; a last column of all zeros has no LNZ and
 *       therefore no bad root;</li>
 *   <li>{@code Δ[i] = last[i] - badRoot[i]} when the entry below row {@code i} is non-zero, and zero
 *       otherwise (the article's {@code α_{i+1} != 0} rule). For a non-increasing column this is the
 *       same as the article's mathematical {@code i < t} form;</li>
 *   <li>the {@code a}-th bad part copy adds {@code a·Δ[i]} to the elements whose row-{@code i} ancestor
 *       chain passes through the bad root, and leaves every other element unchanged (the article's
 *       "ancestors that do not pass through the bad root do not get Δ" exception).</li>
 * </ul>
 *
 * <p>The matrix is read with columns padded by zeros to {@link BmsMatrix#rows()} rows, which is how the
 * reference's fixed-height matrices and this library's short columns meet.
 */
final class BmsAnalysis {

    private final List<List<Integer>> columns;
    private final int rows;
    private final int lastColumn;
    private final int lnzRow;
    private final int badRoot;
    private final int[] delta;
    private final boolean[][] badRootAncestor;

    private BmsAnalysis(List<List<Integer>> columns, int rows, int lastColumn, int lnzRow, int badRoot,
            int[] delta, boolean[][] badRootAncestor) {
        this.columns = columns;
        this.rows = rows;
        this.lastColumn = lastColumn;
        this.lnzRow = lnzRow;
        this.badRoot = badRoot;
        this.delta = delta;
        this.badRootAncestor = badRootAncestor;
    }

    /** Analyses {@code matrix}; the empty matrix has neither an LNZ nor a bad root. */
    static BmsAnalysis of(BmsMatrix matrix) {
        List<List<Integer>> columns = matrix.columns();
        int rows = matrix.rows();
        int width = columns.size();
        int lastColumn = width - 1;

        int[][] parent = new int[rows][width];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < width; column++) {
                parent[row][column] = findParent(columns, parent, row, column);
            }
        }

        int lnzRow = -1;
        for (int row = rows - 1; row >= 0; row--) {
            if (value(columns, lastColumn, row) > 0) {
                lnzRow = row;
                break;
            }
        }
        int badRoot = lnzRow < 0 ? -1 : parent[lnzRow][lastColumn];

        int[] delta = new int[rows];
        if (badRoot >= 0) {
            for (int row = 0; row < rows; row++) {
                boolean belowNonZero = row + 1 < rows && value(columns, lastColumn, row + 1) != 0;
                if (belowNonZero) {
                    delta[row] = value(columns, lastColumn, row) - value(columns, badRoot, row);
                }
            }
        }

        boolean[][] badRootAncestor = new boolean[rows][width];
        if (badRoot >= 0) {
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < width; column++) {
                    badRootAncestor[row][column] = isAncestor(parent, row, column, badRoot);
                }
            }
        }

        return new BmsAnalysis(List.copyOf(columns), rows, lastColumn, lnzRow, badRoot, delta,
                badRootAncestor);
    }

    /** Whether the last column has a non-zero entry at all. */
    boolean hasLowermostNonZero() {
        return lnzRow >= 0;
    }

    /** Whether the parent of the LNZ exists, i.e. whether the reference's bad root is defined. */
    boolean hasBadRoot() {
        return badRoot >= 0;
    }

    /** The row of the lowermost non-zero entry of the last column, or {@code -1} if there is none. */
    int lowermostNonZeroRow() {
        return lnzRow;
    }

    /** The column of the bad root, or {@code -1}. */
    int badRoot() {
        return badRoot;
    }

    /** The ascension offset vector, by row; all zeros when there is no bad root. */
    int[] delta() {
        return delta.clone();
    }

    /** The last column with its columns before it, i.e. the predecessor of a successor matrix. */
    BmsMatrix withoutLastColumn() {
        int[][] result = new int[Math.max(lastColumn, 0)][];
        for (int column = 0; column < lastColumn; column++) {
            result[column] = columnArray(column);
        }
        return BmsMatrix.canonical(result);
    }

    /**
     * The {@code index}-th element of the fundamental sequence: {@code G ~ B_0 ~ B_1 ~ ... ~ B_{index-1}},
     * where {@code B_0 = B} and {@code B_m} adds {@code m·Δ} exactly where the reference's ascension matrix
     * is one. The list therefore has {@code index} copies of the bad part, so index {@code 0} is the good
     * part {@code G} alone - the reading of the reference's own {@code sup{G, G B^(0), G B^(0) B^(1), ...}}
     * (the prose's {@code S[n] = G ~ B ~ B_1 ~ ... ~ B_{n-1}} is the same formula with {@code B = B_0}).
     */
    BmsMatrix expand(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("the fundamental sequence index must not be negative, but it is "
                    + index);
        }
        if (!hasBadRoot()) {
            throw new IllegalStateException("this matrix has no bad root, so it has no bad-root expansion");
        }
        List<int[]> result = new ArrayList<>();
        for (int column = 0; column < badRoot; column++) {
            result.add(columnArray(column));
        }
        int badLength = lastColumn - badRoot;
        for (int copy = 0; copy < index; copy++) {
            for (int offset = 0; offset < badLength; offset++) {
                int source = badRoot + offset;
                int[] column = new int[rows];
                for (int row = 0; row < rows; row++) {
                    int value = value(columns, source, row);
                    if (badRootAncestor[row][source]) {
                        value += copy * delta[row];
                    }
                    column[row] = value;
                }
                result.add(column);
            }
        }
        return BmsMatrix.canonical(result.toArray(new int[0][]));
    }

    private int[] columnArray(int column) {
        int[] values = new int[rows];
        for (int row = 0; row < rows; row++) {
            values[row] = value(columns, column, row);
        }
        return values;
    }

    private static int findParent(List<List<Integer>> columns, int[][] parent, int row, int column) {
        int value = value(columns, column, row);
        if (value == 0) {
            return -1;
        }
        for (int candidate = column - 1; candidate >= 0; candidate--) {
            if (value(columns, candidate, row) >= value) {
                continue;
            }
            if (row == 0 || isAncestor(parent, row - 1, column, candidate)) {
                return candidate;
            }
        }
        return -1;
    }

    private static boolean isAncestor(int[][] parent, int row, int column, int target) {
        int current = column;
        while (current >= 0) {
            if (current == target) {
                return true;
            }
            current = parent[row][current];
        }
        return false;
    }

    private static int value(List<List<Integer>> columns, int column, int row) {
        if (column < 0 || column >= columns.size()) {
            return 0;
        }
        List<Integer> values = columns.get(column);
        return row < values.size() ? values.get(row) : 0;
    }
}
