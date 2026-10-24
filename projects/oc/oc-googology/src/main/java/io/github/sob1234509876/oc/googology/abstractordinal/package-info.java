/**
 * Named ordinals at and above the limit of the Bashicu matrix system, kept as a register of names with a
 * declared order rather than as a notation.
 *
 * <p>{@link io.github.sob1234509876.oc.googology.abstractordinal.AbstractOrdinal} lists the community's
 * stages from the BMS limit upwards - SHO (the limit itself), ΩSSO, GHO, SYO and MHO - each with a short
 * description and an integer rank. The rank defines a total order between the constants, and
 * {@link io.github.sob1234509876.oc.googology.abstractordinal.AbstractOrdinal#compareTo} is exactly that
 * order. Two names are marked as <em>declared but not established</em> (SHO against MHO, because the name
 * MHO has been used for two different stages), and
 * {@link io.github.sob1234509876.oc.googology.abstractordinal.AbstractOrdinal#compareMagnitude} answers
 * {@link io.github.sob1234509876.oc.googology.ComparisonResult#UNKNOWN} for them.
 *
 * <p>These are names, not values. Nothing in this package can be expanded into a fundamental sequence,
 * added, or compared with a {@link io.github.sob1234509876.oc.googology.Natural} or an
 * {@link io.github.sob1234509876.oc.googology.Ordinal}. The one link to the rest of the library is
 * {@link io.github.sob1234509876.oc.googology.abstractordinal.AbstractOrdinal#compareWithBms}, which answers
 * {@link io.github.sob1234509876.oc.googology.ComparisonResult#GREATER} for every matrix by an explicit
 * axiom: the stages are above everything BMS can write, and the placeholder refuses to pretend otherwise.
 * The nodes that the BMS reference lists <em>below</em> the limit, such as {@code ψ(ω-Projection) = TSSO},
 * are BMS-representable and are therefore deliberately not constants of this package; the help document
 * {@code help/abstract-ordinals.md} lists them as the rungs below the limit.
 *
 * <p><b>The ordinal ladder.</b> The package also contains the register of {@code documents/Human/
 * Ordinals.md}'s first three segments: {@link io.github.sob1234509876.oc.googology.abstractordinal.LadderOrdinal}
 * holds a delegated {@link io.github.sob1234509876.oc.googology.bms.BmsOrdinal} for {@code 0 ~ SHO}, an
 * abstract {@code f(alpha) = TTBMS(0)(1^alpha)} value for {@code SHO ~ LRPO} (printed as
 * {@code TBMS(0)(1^alpha)} below {@code omega_1^CK} and as {@code TTBMS(0)(1^alpha)} at or above it), the
 * stage-two NRBMS segment {@code omega_1^CK ~ X} (NRBMS expressions below
 * {@code lim(NRBMS) = TNRBMS(0)(1^omega)} and abstract {@code TNRBMS(0)(1^alpha)} values above it), and
 * the named placeholders {@code psi}, {@code SHO}, {@code LRPO}, {@code omega_1^CK}, {@code lim(NRBMS)}
 * and {@code X} ({@link io.github.sob1234509876.oc.googology.abstractordinal.LadderPlaceholder}).
 * {@link io.github.sob1234509876.oc.googology.abstractordinal.LadderArgument} is the separate argument
 * register: the step functions accept only an argument, never a produced value, which is the stage-two
 * separation of {@code OPEN QUESTIONS.md} Q6. The comparison reports the
 * {@link io.github.sob1234509876.oc.googology.abstractordinal.LadderSegment}s it took place in, and the
 * owner assumptions are documented as assumptions rather than proved. The Chinese guide is
 * {@code help/ordinal-ladder.md}.
 *
 * @since 2.0a
 */
package io.github.sob1234509876.oc.googology.abstractordinal;
