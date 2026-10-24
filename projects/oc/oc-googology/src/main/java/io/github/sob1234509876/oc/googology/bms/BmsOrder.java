package io.github.sob1234509876.oc.googology.bms;

import java.util.List;

/**
 * The notation's lexicographic comparison, shared by {@link BmsOrdinal} and {@link NonRecursiveBms}.
 *
 * <p>Compare the columns from the left; inside a column compare the rows from the top; a column that is
 * a prefix of another is the smaller one; and when every common column is equal, the longer matrix is the
 * larger one. The rule is a pure comparison of two canonical integer sequences, so it is exact, total and
 * transitive, and it never depends on how the matrices were constructed.
 *
 * <p>This class exists only so the two ordinal types cannot drift apart: {@link BmsOrdinal#compareTo} and
 * {@link NonRecursiveBms#compareTo} both delegate here. It is package private.
 */
final class BmsOrder {

    private BmsOrder() {
    }

    /** The lexicographic order on two canonical matrices. */
    static int compare(BmsMatrix left, BmsMatrix right) {
        List<List<Integer>> leftColumns = left.columns();
        List<List<Integer>> rightColumns = right.columns();
        int shared = Math.min(leftColumns.size(), rightColumns.size());
        for (int column = 0; column < shared; column++) {
            int comparison = compareColumns(leftColumns.get(column), rightColumns.get(column));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(leftColumns.size(), rightColumns.size());
    }

    /** The lexicographic order on two columns: rows top to bottom, a prefix is smaller. */
    static int compareColumns(List<Integer> left, List<Integer> right) {
        int shared = Math.min(left.size(), right.size());
        for (int row = 0; row < shared; row++) {
            int comparison = Integer.compare(left.get(row), right.get(row));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(left.size(), right.size());
    }
}
