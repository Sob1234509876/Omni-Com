/**
 * Bashicu matrix ordinals in standard form, with the lexicographic comparison that the notation's
 * standard forms inherit from their ordinals, the reference's expansion rules, and the non-recursive
 * variant.
 *
 * <p>{@link io.github.sob1234509876.oc.googology.bms.BmsMatrix} is the immutable matrix: a sequence of
 * columns of natural numbers, written {@code (0)(1,1)(2)}, with the trailing-zero convention applied so
 * that padding a column with zeros does not change the matrix. {@link
 * io.github.sob1234509876.oc.googology.bms.BmsOrdinal} reads such a matrix as an ordinal and orders it
 * column by column, row by row, with the longer matrix larger when the shared prefix is equal. It also
 * implements the reference's expansion: the empty matrix is zero, a matrix whose last column is all zeros
 * is a successor, and otherwise the lowermost non-zero element gives the bad root, the good and bad parts,
 * the ascension offset vector and the bad-part copies, so {@link
 * io.github.sob1234509876.oc.googology.bms.BmsOrdinal#fundamentalSequence(int)} returns
 * {@code G ~ B_0 ~ ... ~ B_{n-1}} with the reference's ancestor exception.
 *
 * <p>{@link io.github.sob1234509876.oc.googology.bms.NonRecursiveBms} is the second article's variant:
 * the first column is a run of ones, a last {@code (1)} column is the successor, the ordinary bad-root
 * expansion applies when the LNZ has a parent, and the article's non-recursive expansion (an infinite
 * column) is exposed as a {@link io.github.sob1234509876.oc.googology.bms.NonRecursiveExpansion}
 * description because it is not a finite matrix.
 *
 * <p>The two types are not {@link io.github.sob1234509876.oc.googology.Ordinal}s: they have successors
 * and fundamental sequences but no addition, finite-coefficient multiplication or {@code ω^}. The
 * comparison is promised for standard forms only, and the class documentation of the two types states
 * exactly which inputs are normalised, which are rejected, and what is tested versus proved.
 *
 * @since 2.0a
 */
package io.github.sob1234509876.oc.googology.bms;
