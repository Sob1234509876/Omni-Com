package io.github.sob1234509876.oc.googology.bms;

import java.util.List;
import java.util.Objects;

/**
 * The non-recursive expansion of a non-recursive BMS expression, as described by the second reference
 * article ({@code documents/AI/bms/non-recursive-bms-zh.raw.wiki}): when the last column has a lowermost
 * non-zero element but that element has no parent, the expression has no bad root, so the article removes
 * the LNZ from the last column, adds one to the rows that remain, and appends an infinite tail of ones.
 *
 * <p>The two worked examples of the article are exactly this shape:
 *
 * <pre>
 * (1,1,1)(2,2,1)   becomes   (1,1,1)(2,2)(3,3,1,1,1,1,...)
 * (1,1)(2,2,1)(3,1) becomes  (1,1)(2,2,1)(3)(4,1,1,1,1,...)
 * </pre>
 *
 * <p>The truncated last column stays in place - it is part of {@link #fixed()} - and the infinite column is
 * appended after it. The result is a <em>description</em>, not a finite matrix: its new last column has
 * infinitely many ones, while every column of a {@link BmsMatrix} is finite. It therefore cannot be a
 * {@link NonRecursiveBms} value (and is not one), which is why {@code NonRecursiveBms.fundamentalSequence}
 * reports "no finite fundamental sequence element" for this case and this record is what exposes the
 * article's rule. The article does not define a comparison between two such infinite columns, so this
 * record deliberately has no order: it is the rendering of the expansion, not a new ordinal value.
 *
 * @param fixed every column of the expression up to the truncated last column: the original columns, then
 *        the last column with its LNZ and the zero rows below it removed (so the truncated column may be
 *        absent when the LNZ was in the first row); never {@code null}
 * @param column the new column's finite rows before the infinite tail of ones, top row first; may be empty
 * @see NonRecursiveBms#nonRecursiveExpansion()
 */
public record NonRecursiveExpansion(BmsMatrix fixed, List<Integer> column) {

    /** Validates and freezes the two components. */
    public NonRecursiveExpansion {
        Objects.requireNonNull(fixed, "fixed");
        Objects.requireNonNull(column, "column");
        column = List.copyOf(column);
    }

    /**
     * The article's notation for the infinite expansion, for example
     * {@code (1,1,1)(2,2)(3,3,1,1,1,1,…)}. The ellipsis stands for the remaining rows of the infinite tail,
     * all of them ones.
     */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(fixed.toString());
        text.append('(');
        for (int row = 0; row < column.size(); row++) {
            if (row > 0) {
                text.append(',');
            }
            text.append(column.get(row));
        }
        if (!column.isEmpty()) {
            text.append(',');
        }
        text.append("1,1,1,1,…)");
        return text.toString();
    }
}
