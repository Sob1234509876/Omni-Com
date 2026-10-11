/**
 * A small library of very large natural numbers.
 *
 * <p>The single value type is {@link io.github.sob1234509876.oc.googology.Natural}. It is exact (a
 * {@link java.math.BigInteger}) while a value needs at most 4096 bits and switches to a normalised
 * hyperoperation tower beyond that, which covers exponentiation, tetration, pentation and higher arrow
 * levels. Ordering stays faithful to the order of magnitude; arithmetic in the structural range is
 * approximate by design. {@link io.github.sob1234509876.oc.googology.HyperOperation} names the levels and
 * {@link io.github.sob1234509876.oc.googology.NaturalFormat} renders values for humans.
 *
 * @since 2.0a
 */
package io.github.sob1234509876.oc.googology;
