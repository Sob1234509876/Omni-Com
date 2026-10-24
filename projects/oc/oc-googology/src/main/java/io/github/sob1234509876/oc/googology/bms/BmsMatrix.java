package io.github.sob1234509876.oc.googology.bms;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An immutable Bashicu matrix (BMS): a finite sequence of columns, each column a non-empty finite
 * sequence of natural numbers, written the way the googology community writes it - {@code (0)(1,1)(2,0)} -
 * with the columns separated by parentheses and the rows by commas.
 *
 * <p><b>The trailing-zero convention.</b> A column with a run of zeros at its bottom is the same column
 * with that run removed: {@code (1,0,0)} is {@code (1)}, and {@code (0,0,0)} is the zero column
 * {@code (0)}. This is the notation's standard short form (the reference writes
 * {@code (0,0,0)(1,1,1)(2,1,0)(1,1,1)} as {@code (0)(1,1,1)(2,1)(1,1,1)}), and it is what makes
 * {@code BMS(0)(1)(2) = BMS(0,0)(1,0)(2,0) = BMS(0,0,0)(1,0,0)(2,0,0)}. Because the columns no longer
 * need to be the same height, {@link #rows()} is the tallest column and a short column is understood to
 * be padded with zeros.
 *
 * <p>A column is never dropped, including a column that is entirely zero: appending {@code (0)} is the
 * successor step, so {@code (0)(0)} is {@code 2} and {@code (0)(1)(0)} is {@code ω + 1}. The only
 * redundancy this representation has is the trailing zero <em>rows</em>; removing the zero columns
 * themselves would change the ordinal.
 *
 * <p><b>Two constructors, two contracts.</b> {@link #of(int[][])} accepts only a matrix that is already
 * in the canonical short form and rejects a trailing zero, which makes it the right choice when a caller
 * wants to state a form exactly. {@link #canonical(int[][])} accepts any matrix, drops the trailing zeros
 * and reports through {@link #isStandard()} whether it had to. Equality and {@link #hashCode()} are
 * defined on the canonical form only, so {@code of(...)} and {@code canonical(...)} of the same form are
 * equal even though {@code isStandard()} may differ.
 *
 * <p><b>Entries are {@code int}.</b> A BMS entry is a row/column index in the notation, not a quantity:
 * the comparison only ever tests two entries for order and equality, and no arithmetic is done on them,
 * so {@code int} is the smallest ladder that works. {@link io.github.sob1234509876.oc.googology.Natural},
 * with its exact/structural machinery, would be pure overhead here; the public API takes {@code int[][]}
 * anyway. Negative entries are rejected, and an entry beyond {@code int} cannot even be passed in.
 *
 * <p><b>What this type does not do.</b> It does not expand a matrix into a fundamental sequence, and it
 * does not decide the deep BMS standard-form conditions of the reference (first column all zeros,
 * non-increasing columns, row increments of at most one). It only normalises the writing. See
 * {@link BmsOrdinal} for the ordering and its scope.
 *
 * @see BmsOrdinal
 */
public final class BmsMatrix {

    /** The empty matrix, {@code ()}, which BMS reads as the ordinal zero. */
    private static final BmsMatrix EMPTY = new BmsMatrix(List.of(), true);

    private final List<List<Integer>> columns;
    private final boolean standard;

    private BmsMatrix(List<List<Integer>> columns, boolean standard) {
        this.columns = columns;
        this.standard = standard;
    }

    /**
     * A matrix whose columns are already written in the canonical short form: every column non-empty,
     * every entry a natural number, and no column ending in a zero unless the column is the single
     * {@code (0)}. The empty matrix is allowed and denotes zero.
     *
     * @throws IllegalArgumentException if a column is empty, if an entry is negative, or if a column ends
     *         in a zero without being {@code (0)}; use {@link #canonical(int[][])} to normalise such input
     * @throws NullPointerException if {@code columns} or one of its rows is {@code null}
     */
    public static BmsMatrix of(int[][] columns) {
        Objects.requireNonNull(columns, "columns");
        List<List<Integer>> raw = copy(columns);
        for (List<Integer> column : raw) {
            if (column.size() > 1 && column.get(column.size() - 1) == 0) {
                throw new IllegalArgumentException("column " + column + " ends in 0, so it is not written in the"
                        + " canonical short form; use BmsMatrix.canonical(...) to drop the trailing zeros");
            }
        }
        return new BmsMatrix(List.copyOf(raw), true);
    }

    /**
     * Normalises any matrix into the canonical short form by dropping the trailing zeros of every column,
     * leaving at least {@code (0)} for an all-zero column. The empty matrix is allowed and denotes zero.
     *
     * <p>No column is removed: see the class documentation for why {@code (0)(0)} is {@code 2}, not
     * {@code 1}. The returned matrix's {@link #isStandard()} is {@code true} exactly when the input was
     * already in the canonical form.
     *
     * @throws IllegalArgumentException if a column is empty or an entry is negative
     * @throws NullPointerException if {@code columns} or one of its rows is {@code null}
     */
    public static BmsMatrix canonical(int[][] columns) {
        Objects.requireNonNull(columns, "columns");
        return canonicalise(copy(columns));
    }

    /**
     * Parses the textual notation, for example {@code "(0)(1,1)(2,0)"}, and normalises the result like
     * {@link #canonical(int[][])}. Whitespace between columns and around numbers is ignored, and
     * {@code "()"} (as well as the empty string) is the empty matrix. The rendering of
     * {@link #toString()} always parses back to an equal matrix.
     *
     * @throws IllegalArgumentException if the text is not a sequence of parenthesised columns
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public static BmsMatrix parse(String text) {
        Objects.requireNonNull(text, "text");
        String source = text.trim();
        if (source.isEmpty() || source.equals("()")) {
            return EMPTY;
        }
        List<List<Integer>> raw = new ArrayList<>();
        int index = 0;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (current != '(') {
                throw new IllegalArgumentException("expected '(' at index " + index + " of \"" + text + "\"");
            }
            int close = source.indexOf(')', index);
            if (close < 0) {
                throw new IllegalArgumentException("column opened at index " + index + " is never closed: \"" + text + "\"");
            }
            String body = source.substring(index + 1, close).trim();
            if (body.isEmpty()) {
                throw new IllegalArgumentException("the empty column () is not allowed; () is the whole empty matrix");
            }
            List<Integer> column = new ArrayList<>();
            for (String part : body.split(",", -1)) {
                String number = part.trim();
                if (number.isEmpty()) {
                    throw new IllegalArgumentException("empty row in column " + body + " of \"" + text + "\"");
                }
                int value;
                try {
                    value = Integer.parseInt(number);
                } catch (NumberFormatException error) {
                    throw new IllegalArgumentException("\"" + number + "\" is not a natural number in \"" + text + "\"",
                            error);
                }
                if (value < 0) {
                    throw new IllegalArgumentException("column " + body + " contains " + value
                            + "; BMS entries are natural numbers");
                }
                column.add(value);
            }
            raw.add(List.copyOf(column));
            index = close + 1;
        }
        return canonicalise(raw);
    }

    /** A builder for matrices; {@link Builder#build()} normalises like {@link #canonical(int[][])}. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Whether the input this matrix was built from was already in the canonical short form. It is a fact
     * about the construction, not about equality: concatenating a zero row and dropping it again leaves an
     * "unstandard" matrix that is equal to the standard one.
     *
     * <p>This flag tracks only the trailing-zero convention. It does not claim the deeper BMS standard-form
     * conditions (the reference's first-column, column-shape and row-increment rules); see
     * {@link BmsOrdinal#compareTo(BmsOrdinal)} for what the order assumes.
     */
    public boolean isStandard() {
        return standard;
    }

    /**
     * The columns of the canonical form, each an unmodifiable list of rows, top row first. The outer list
     * is unmodifiable as well, so a matrix really is immutable.
     */
    public List<List<Integer>> columns() {
        return columns;
    }

    /** The number of rows: the height of the tallest column, or zero for the empty matrix. */
    public int rows() {
        int rows = 0;
        for (List<Integer> column : columns) {
            rows = Math.max(rows, column.size());
        }
        return rows;
    }

    /** The number of columns. */
    public int length() {
        return columns.size();
    }

    /** Whether this is the empty matrix, which denotes zero. */
    public boolean isEmpty() {
        return columns.isEmpty();
    }

    /**
     * The textual notation: {@code (0)(1,1)(2)} for a matrix, {@code ()} for the empty one. The columns
     * are printed in canonical form, so a trailing zero never appears.
     */
    @Override
    public String toString() {
        if (columns.isEmpty()) {
            return "()";
        }
        StringBuilder text = new StringBuilder();
        for (List<Integer> column : columns) {
            text.append('(');
            for (int row = 0; row < column.size(); row++) {
                if (row > 0) {
                    text.append(',');
                }
                text.append(column.get(row));
            }
            text.append(')');
        }
        return text.toString();
    }

    /** Equality on the canonical form, so redundant trailing zeros do not distinguish two matrices. */
    @Override
    public boolean equals(Object other) {
        return other instanceof BmsMatrix matrix && columns.equals(matrix.columns);
    }

    @Override
    public int hashCode() {
        return columns.hashCode();
    }

    private static List<List<Integer>> copy(int[][] columns) {
        List<List<Integer>> raw = new ArrayList<>(columns.length);
        for (int index = 0; index < columns.length; index++) {
            int[] column = columns[index];
            if (column == null) {
                throw new NullPointerException("column " + index + " is null");
            }
            if (column.length == 0) {
                throw new IllegalArgumentException("column " + index + " is empty; every column needs at least one row");
            }
            List<Integer> values = new ArrayList<>(column.length);
            for (int value : column) {
                if (value < 0) {
                    throw new IllegalArgumentException("column " + index + " contains " + value
                            + "; BMS entries are natural numbers");
                }
                values.add(value);
            }
            raw.add(List.copyOf(values));
        }
        return raw;
    }

    private static BmsMatrix canonicalise(List<List<Integer>> raw) {
        List<List<Integer>> trimmed = new ArrayList<>(raw.size());
        boolean same = true;
        for (List<Integer> column : raw) {
            List<Integer> canonical = trim(column);
            same &= canonical.size() == column.size();
            trimmed.add(canonical);
        }
        return new BmsMatrix(List.copyOf(trimmed), same);
    }

    /** Drops trailing zeros but always keeps one entry, so an all-zero column becomes {@code (0)}. */
    private static List<Integer> trim(List<Integer> column) {
        int end = column.size();
        while (end > 1 && column.get(end - 1) == 0) {
            end--;
        }
        return end == column.size() ? column : List.copyOf(column.subList(0, end));
    }

    /**
     * Builds a matrix column by column. Columns may be written with trailing zeros; {@link #build()}
     * applies the canonical short form, so the result is the same matrix {@link #canonical(int[][])}
     * returns.
     *
     * <p>This is the varargs-style constructor. An {@code of(int[]...)} overload cannot coexist with
     * {@link BmsMatrix#of(int[][])}, because {@code int[]...} and {@code int[][]} erase to the same
     * signature, so the variadic convenience lives on {@link #column(int...)} instead.
     */
    public static final class Builder {

        private final List<List<Integer>> columns = new ArrayList<>();

        private Builder() {
        }

        /** Adds one column, top row first. */
        public Builder column(int... values) {
            Objects.requireNonNull(values, "values");
            if (values.length == 0) {
                throw new IllegalArgumentException("every column needs at least one row");
            }
            List<Integer> column = new ArrayList<>(values.length);
            for (int value : values) {
                if (value < 0) {
                    throw new IllegalArgumentException("column value " + value + " is not a natural number");
                }
                column.add(value);
            }
            columns.add(List.copyOf(column));
            return this;
        }

        /** Adds every column of {@code more}, in order. */
        public Builder columns(int[][] more) {
            Objects.requireNonNull(more, "more");
            for (int[] column : more) {
                column(column);
            }
            return this;
        }

        /** The canonical matrix of the columns added so far. */
        public BmsMatrix build() {
            return canonicalise(List.copyOf(columns));
        }
    }
}
