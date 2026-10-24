/**
 * Bashicu matrix ordinals in standard form, with the lexicographic comparison that the notation's
 * standard forms inherit from their ordinals.
 *
 * <p>{@link io.github.sob1234509876.oc.googology.bms.BmsMatrix} is the immutable matrix: a sequence of
 * columns of natural numbers, written {@code (0)(1,1)(2)}, with the trailing-zero convention applied so
 * that padding a column with zeros does not change the matrix. {@link
 * io.github.sob1234509876.oc.googology.bms.BmsOrdinal} reads such a matrix as an ordinal and orders it
 * column by column, row by row, with the longer matrix larger when the shared prefix is equal.
 *
 * <p>This is the ordering half of BMS, not a complete ordinal notation: the expansion rules (parent,
 * bad root, ascension offset, fundamental sequences, and therefore successor and addition) are not
 * implemented here. The class documentation of {@code BmsOrdinal} states exactly which matrices the order
 * is promised for, and why non-standard matrices are rejected or normalised.
 *
 * @since 2.0a
 */
package io.github.sob1234509876.oc.googology.bms;
