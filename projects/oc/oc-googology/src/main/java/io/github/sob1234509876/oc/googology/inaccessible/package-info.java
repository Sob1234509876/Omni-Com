/**
 * The function-generated inaccessible-cardinal hierarchy of
 * {@code documents/Human/Cardinals/Inaccessible Cardinal Extension.md}: the owner's
 * <strong>stage five</strong>.
 *
 * <p><b>Functions, not a register.</b> There are infinitely many inaccessible cardinals, so this package
 * deliberately contains <strong>no enum and no bounded list</strong> of them. {@link
 * io.github.sob1234509876.oc.googology.inaccessible.InaccessibleCardinal} is an immutable value created
 * only through its factories: {@code inaccessible(alpha)} for {@code I_alpha}, {@code veblen(alpha, beta)}
 * for the inaccessible Veblen function, {@code veblen(prefix, beta)} for the multi-variable form,
 * {@code coefficient(...)} for the ordinal-coefficient form {@code a @ b}, and {@code selfNesting()} for
 * the self-nesting limit description. {@link
 * io.github.sob1234509876.oc.googology.inaccessible.IndexLevel} is the level-term algebra that the
 * self-nesting rule closes under its own construction.
 *
 * <p><b>The register above it.</b> {@code CardinalRung.INACCESSIBLE} does not define the hierarchy: its
 * {@code inaccessibleDelegate()} is exactly {@code InaccessibleCardinal.inaccessible(0)}, the least
 * inaccessible cardinal, and the register only names the ladder's entry points. The Chinese guide is
 * {@code help/inaccessible-extension.md}.
 *
 * <p><b>The order.</b> Comparisons are total inside {@code InaccessibleCardinal} and are proofs. A value
 * compares by its level term first and its trailing {@code beta} second; an ordinal and a tuple share one
 * lexicographic layer (an ordinal is the one-coordinate level), coefficient entries are above that layer
 * and compare level-major then coefficient, and the self-nesting limit is above every finite term. The
 * document's warning that this hierarchy is not a collapse is executable: {@code compareWithOrdinal}
 * returns {@code UNKNOWN} instead of guessing a relation with the countable ordinals of the project.
 *
 * @since 2.0a
 */
package io.github.sob1234509876.oc.googology.inaccessible;
