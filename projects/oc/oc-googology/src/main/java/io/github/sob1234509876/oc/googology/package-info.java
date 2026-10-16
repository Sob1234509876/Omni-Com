/**
 * A small library of very large natural numbers, and of the ordinals used as their hyperoperation levels.
 *
 * <p>The single number type is {@link io.github.sob1234509876.oc.googology.Natural}. It is exact (a
 * {@link java.math.BigInteger}) while a value needs at most 4096 bits and switches to a normalised
 * hyperoperation tower beyond that, which covers exponentiation, tetration, pentation and higher arrow
 * levels. Ordering stays faithful to the order of magnitude; arithmetic in the structural range is
 * approximate by design. {@link io.github.sob1234509876.oc.googology.HyperOperation} names the finite
 * levels and {@link io.github.sob1234509876.oc.googology.NaturalFormat} renders values for humans.
 *
 * <p>Ordinals extend that hierarchy past the finite: {@link io.github.sob1234509876.oc.googology.Ordinal}
 * is a comparable ordinal with a successor and fundamental sequences,
 * {@link io.github.sob1234509876.oc.googology.OrdinalNotation} is the seam for plugging in a custom
 * notation, {@link io.github.sob1234509876.oc.googology.CantorNormalForm} is the default notation (Cantor
 * normal form, whose ceiling is {@code ε₀}, the supremum of {@code 1, ω, ω^ω, ω^ω^ω, ...}) and
 * {@link io.github.sob1234509876.oc.googology.TransfiniteHyperoperation} is the hyperoperation at those
 * levels, with {@code n {λ} m = n {λ[m]} m} for limit ordinals {@code λ}.
 *
 * @since 2.0a
 */
package io.github.sob1234509876.oc.googology;
