/**
 * The cardinal register of {@code documents/Human/Ordinals.md} items 6-21: a <strong>name-and-rank
 * register</strong>, not a notation and not a segment of the ordinal ladder.
 *
 * <p>{@link io.github.sob1234509876.oc.googology.cardinalregister.CardinalRung} lists the nineteen rungs of
 * stage four in the order of the owner's ladder - inaccessible, Mahlo, {@code alpha}-Mahlo, hyper-Mahlo,
 * weakly compact, indescribable, measurable, {@code alpha}-strong and strong, supercompact, I3, I2, I1, I0,
 * Icarus, Reinhardt, Berkeley, Ord-Berkeley, limit club Berkeley, and the fictional
 * {@code Omega ~ Omega_Absolute} segment. Each constant carries its name, its notation from
 * {@code Ordinals.md}, a one-line gloss, a {@link io.github.sob1234509876.oc.googology.cardinalregister.RelativePath}
 * to the document section that defines it, a declared rank, and a {@code verified} flag. The register
 * <strong>defines no cardinal</strong>: every definition is delegated to {@code documents/Human/Cardinals/},
 * and the self check resolves every link and matches the heading.
 *
 * <p>The declared order is consistency strength: {@code rank()} is the enum position and {@code compareTo}
 * compares it, while {@code compareMagnitude} reports the same order as a proof. Berkeley sits above
 * Reinhardt in that order; the owner's convention that Reinhardt is conceptually larger is recorded by a
 * separate accessor, not by a second order. Every rung answers
 * {@link io.github.sob1234509876.oc.googology.ComparisonResult#GREATER} to
 * {@link io.github.sob1234509876.oc.googology.cardinalregister.CardinalRung#compareWithLadder}, including
 * against the ladder's top {@code psi_I(I)}: that is the register's axiom, not a computation.
 *
 * <p>Three entries - Icarus, Ord-Berkeley and limit club Berkeley - are flagged for verification by
 * {@code Big Cardinals.md}. Their {@code isVerified()} is {@code false}, their glosses quote the document's
 * own wording, and the Ord-Berkeley gloss states both readings of the name instead of picking one. The
 * register does not invent definitions. The Chinese guide is {@code help/cardinal-register.md}.
 *
 * @since 2.0a
 */
package io.github.sob1234509876.oc.googology.cardinalregister;
