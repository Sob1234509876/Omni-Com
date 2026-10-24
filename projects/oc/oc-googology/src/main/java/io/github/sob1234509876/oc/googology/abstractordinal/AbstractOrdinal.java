package io.github.sob1234509876.oc.googology.abstractordinal;

import io.github.sob1234509876.oc.googology.ComparisonResult;
import io.github.sob1234509876.oc.googology.bms.BmsOrdinal;

import java.util.List;
import java.util.Objects;

/**
 * Named ordinals at and above the limit of the Bashicu matrix system, kept as a <strong>register of names
 * with a declared order</strong>.
 *
 * <p><b>What this is.</b> The BMS layer stops at the limit of the matrices it can write, and the module has
 * no notation beyond it. This enum names the community's stages from that limit upwards, in the order the
 * Chinese Googology Wiki's ordinal table gives them: {@link #SHO} (the limit itself), {@link #OMEGA_SSO},
 * {@link #GHO}, {@link #SYO} and {@link #MHO}. Each constant carries a short description, the abbreviation
 * the community uses, the notation it is normally written with, and an integer <em>declared rank</em>
 * (see {@link #rank()}).
 *
 * <p><b>A name, not a notation.</b> This is the sentence that matters at every call site: these are
 * <em>names with a declared order</em>, not an ordinal notation. Nothing here can be expanded into a
 * fundamental sequence, added, multiplied by a finite coefficient, compared with
 * {@link io.github.sob1234509876.oc.googology.Natural} or
 * {@link io.github.sob1234509876.oc.googology.Ordinal}, or used as a value of any kind. There is no matrix
 * behind a constant, no successor, no {@code ω^}, and no {@code Ordinal} implementation. The only things a
 * constant can do are: report its name, its description and its declared rank; compare with another
 * constant; and answer the deliberately axiomatic {@link #compareWithBms(BmsOrdinal)}. The module keeps
 * them as an honest placeholder for the region above its own limit, so that code which has to mention
 * "above BMS" uses a typed constant with a documented order instead of a bare string, and so that the
 * ceiling is visible in the API rather than only in the help documents.
 *
 * <p><b>Why there is no {@code PSI_OMEGA_OMEGA}-style constant.</b> The BMS reference's own last nodes
 * below the limit - {@code ψ(Ω^Ω) = FSO = Γ₀} written {@code (0)(1,1)(2,1)(3,1)}, {@code ψ(M_ω) = SMO}
 * written {@code (0)(1,1,1)(2,1,1)(3,1,1)(3,1,1)} and {@code ψ(ω-Projection) = TSSO} written
 * {@code (0,0,0,0)(1,1,1,1)} - are BMS-representable matrices. A register whose every entry is
 * <em>above every BMS value</em> cannot contain them: the "greater than BMS" axiom below would be false
 * for them. They are listed as the rungs just below the limit in
 * {@code help/abstract-ordinals.md}, where their matrix forms make the boundary concrete. The enum begins
 * at the reference's own Limit line, {@code ψ(ψ_H(ε_{H+1})) = SHO}, because that is the first stage that
 * no matrix denotes.
 *
 * <p><b>The declared rank.</b> {@link #rank()} is the position of the constant in this enum, counting from
 * zero, and the {@code compareTo} inherited from {@link Enum} compares exactly those positions, which a
 * static initialiser keeps equal to the declared ranks. That makes the order total, transitive,
 * antisymmetric and consistent with {@code equals} by construction, which is what a {@link Comparable} needs
 * (the method is {@code final} in {@link Enum}, so this type cannot override it; the self check asserts that
 * the enum positions and the declared ranks agree). It is a <em>declaration of this register</em>, not a
 * theorem: the names and their order come from the community's tables, several of those identifications are
 * themselves conjectural, and one pair is explicitly <em>declared but not established</em>:
 * {@link #SHO} against {@link #MHO}, because "MHO" named the BMS limit in the older international usage and
 * names the limit of ω-Y in the current usage. That pair is listed by {@link #declaredUnknownPairs()}, and
 * its {@link #compareMagnitude(AbstractOrdinal)} is {@link ComparisonResult#UNKNOWN} in both directions,
 * while {@link #compareTo(AbstractOrdinal)} still answers with the declared rank so that sorting stays a
 * total order. No other pair is unknown; they are the table's order, not guesses.
 *
 * <p><b>Above BMS, by axiom.</b> {@link #compareWithBms(BmsOrdinal)} answers
 * {@link ComparisonResult#GREATER} for every constant and every matrix, and it always will. That is an
 * <strong>axiom of this placeholder, not a computation</strong>: the enum does not translate a name into an
 * ordinal, so it has nothing to compute with, and it refuses to hand back a comparison that would silently
 * drop the one fact it does carry - that its stages lie beyond everything BMS can write. The axiom is true
 * in the intended reading, because every constant is at or above {@link #SHO}, the BMS limit; it is still
 * an axiom, and it does not say <em>how far</em> above BMS a stage is. Two names from this register cannot
 * be compared with each other through it either; only {@link #compareTo(AbstractOrdinal)} and
 * {@link #compareMagnitude(AbstractOrdinal)} order them, and only by the declared rank.
 *
 * <p><b>Sources.</b> The translations between the names are the Chinese Googology Wiki's
 * <a href="https://wiki.googology.top/index.php/%E5%BA%8F%E6%95%B0%E8%A1%A8">ordinal table</a> and the
 * pages for <a href="https://wiki.googology.top/index.php/SHO">SHO</a>,
 * <a href="https://wiki.googology.top/index.php/%CE%A9SSO">ΩSSO</a> and
 * <a href="https://wiki.googology.top/index.php/GHO">GHO</a>, plus the BMS reference kept in
 * {@code documents/AI/bms/bms-zh.raw.wiki}. They are community usage, not a formal definition, and the
 * descriptions say which parts are claimed rather than proved.
 *
 * @see #compareWithBms(BmsOrdinal)
 * @see #declaredUnknownPairs()
 * @see io.github.sob1234509876.oc.googology.bms.BmsOrdinal
 * @since 2.0a
 */
public enum AbstractOrdinal implements Comparable<AbstractOrdinal> {

    /**
     * <strong>Small Hydra Ordinal</strong> - the limit of BMS, written {@code Y(1,3) = lim(BMS) =
     * ψ(ψ_H(ε_{H+1}))} in upward projection. It is above every matrix the BMS layer can write; the
     * reference's {@code Limit} line is exactly this name. Also called BMO (Bashicu Matrix Ordinal), and
     * MHO in the older international usage - see {@link #MHO} for the current meaning of that name. The
     * mainstream conjecture identifies it with {@code PTO(Z2) = PTO(KPω+Π_N-Collection)}, which the same
     * table calls βO (Beta Universe Ordinal); that identification is a conjecture, not part of this
     * constant.
     */
    SHO(0, "Small Hydra Ordinal: the limit of BMS, above every matrix the BMS layer can write; written"
            + " Y(1,3) = lim(BMS) = ψ(ψ_H(ε_{H+1})). Also called BMO (Bashicu Matrix Ordinal) and, in the"
            + " older international usage, MHO; conjecturally PTO(Z2) = βO."),

    /**
     * <strong>Ω Sequence System Ordinal</strong> (ΩSSO), also called WSSO: the limit of TBMS, the
     * transfinite-row extension of BMS, and the first fixed point of the map that sends α to the
     * α-row BMS. The ordinal table gives {@code Y(1,3,4,2,5,8,10)} and the upward-projection form
     * {@code ψ(ψ_H(φ(Ω, H+1)))} (the table marks that form with a query). Above {@link #SHO}.
     */
    OMEGA_SSO(1, "Ω Sequence System Ordinal (ΩSSO), also WSSO: the limit of TBMS, the transfinite-row"
            + " extension of BMS; the first fixed point of α ↦ α-row BMS. Written Y(1,3,4,2,5,8,10) in"
            + " 1-Y, and ψ(ψ_H(φ(Ω, H+1))) in upward projection as the wiki gives it."),

    /**
     * <strong>No-Go Hydra Ordinal</strong> (GHO), the 1-Y expression {@code Y(1,3,4,3)}, named by
     * Asheep233. The lifted 4 in that expression is attached directly to the 3, which makes the lifting
     * strong enough to defeat many simple extensions of BMS - hence "no-go". Above {@link #OMEGA_SSO}
     * because both are 1-Y expressions and {@code (1,3,4,2,5,8,10) < (1,3,4,3)} in the notation's order.
     */
    GHO(2, "No-Go Hydra Ordinal (GHO), also written Guo bu qu de Hydra Ordinal: the 1-Y expression"
            + " Y(1,3,4,3), named by Asheep233 for the lifting that defeats many simple extensions of BMS."
            + " Above ΩSSO in the 1-Y order."),

    /**
     * <strong>Small Yukito Ordinal</strong> (SYO): the limit of the 1-Y sequence system, which the ordinal
     * table also writes {@code ω-Y(1,4)}. Above {@link #GHO}, because GHO is a 1-Y expression and SYO is
     * the limit of all of them.
     */
    SYO(3, "Small Yukito Ordinal: the limit of the 1-Y sequence system, also written ω-Y(1,4). Above GHO,"
            + " because GHO is a 1-Y expression and SYO is the limit of the 1-Y expressions."),

    /**
     * <strong>Medium Hydra Ordinal</strong> (MHO), also called ω-Y sequence Ordinal: the limit of the
     * ω-Y sequence system, which the ordinal table writes {@code lim(ω-Y)}. Above {@link #SYO}, because
     * SYO is the ω-Y expression {@code ω-Y(1,4)} and every expression of a system lies below its limit.
     *
     * <p>The name is also the older international name of the BMS limit, which is {@link #SHO} here. That
     * is why the pair ({@link #SHO}, {@link #MHO}) is listed by {@link #declaredUnknownPairs()} and why
     * {@link #compareMagnitude(AbstractOrdinal)} answers {@link ComparisonResult#UNKNOWN} for it: the rank
     * follows the current usage, but the relative strength of the two <em>names</em> is not established.
     */
    MHO(4, "Medium Hydra Ordinal, also called ω-Y sequence Ordinal: the limit of ω-Y. The name was the"
            + " older international name of the BMS limit, which is SHO here, so (SHO, MHO) is declared but"
            + " not established; the rank follows the current usage.");

    private final int rank;
    private final String description;

    AbstractOrdinal(int rank, String description) {
        this.rank = rank;
        this.description = requireText(description, "description");
    }

    /**
     * The pair {@link #SHO} against {@link #MHO}: both names are in use for the BMS limit, and the current
     * meaning of MHO is a later stage, so the comparison of the two names is declared but not established.
     * This is the only such pair in the register.
     */
    private static final List<UnknownPair> DECLARED_UNKNOWN_PAIRS = List.of(new UnknownPair(SHO, MHO,
            "MHO named the BMS limit (SHO here) in the older international usage, and names the limit of"
                    + " ω-Y (a stage above SYO) in the current usage; the rank follows the current usage,"
                    + " but the relative strength of the two names is not established."));

    static {
        AbstractOrdinal[] values = values();
        for (int index = 0; index < values.length; index++) {
            if (values[index].rank != index) {
                throw new ExceptionInInitializerError("the declared rank " + values[index].rank + " of "
                        + values[index] + " is not its enum position " + index);
            }
        }
        for (UnknownPair pair : DECLARED_UNKNOWN_PAIRS) {
            if (!pair.first().isBefore(pair.second())) {
                throw new ExceptionInInitializerError("the unknown pair " + pair + " is not in rank order");
            }
        }
    }

    /** The declared rank: the position of this name in the register, counting from zero. */
    public int rank() {
        return rank;
    }

    /** The short description of this name: the abbreviation, the notation and the one-line meaning. */
    public String description() {
        return description;
    }

    /**
     * Whether this name lies above every BMS-representable ordinal. It is always {@code true}: every
     * constant is at or above {@link #SHO}, the BMS limit.
     *
     * <p>This is the predicate form of the axiom documented on {@link #compareWithBms(BmsOrdinal)}; it is
     * not evaluated from a comparison, because the enum stores no ordinal to compare.
     */
    public boolean isAboveBms() {
        return true;
    }

    /**
     * Answers {@link ComparisonResult#GREATER} for every matrix: an <strong>axiom of this placeholder, not
     * a computation</strong>. The enum does not translate a name into an ordinal, so there is no honest
     * comparison to compute. Because every constant is at or above the BMS limit, the answer is the true
     * one in the intended reading, but it is still an axiom, and it deliberately refuses to let a caller
     * compare a name with a matrix in a way that would drop the "above" fact.
     *
     * <p>It says nothing about <em>how far</em> above the matrix lies, and it cannot compare two names with
     * each other; use {@link #compareTo(AbstractOrdinal)} or
     * {@link #compareMagnitude(AbstractOrdinal)} for that, which answer from the declared rank.
     *
     * @throws NullPointerException if {@code bms} is {@code null}
     */
    public ComparisonResult compareWithBms(BmsOrdinal bms) {
        Objects.requireNonNull(bms, "bms");
        return ComparisonResult.GREATER;
    }

    /**
     * The comparison of this register, reported through the module's comparison API: a proof in the
     * direction of the declared rank, except for the pairs listed by {@link #declaredUnknownPairs()}, which
     * answer {@link ComparisonResult#UNKNOWN}. A lean is never produced, because the declared order has no
     * notion of a probable answer.
     *
     * <p>The direction is the one the inherited {@code compareTo} gives - the constants' positions, which
     * the static initialiser keeps equal to {@link #rank()} - so the two never disagree on the pairs that
     * are established.
     *
     * @throws NullPointerException if {@code other} is {@code null}
     */
    public ComparisonResult compareMagnitude(AbstractOrdinal other) {
        Objects.requireNonNull(other, "other");
        if (isDeclaredUnknownWith(other)) {
            return ComparisonResult.UNKNOWN;
        }
        return ComparisonResult.of(compareTo(other));
    }

    /** Whether this name and {@code other} form a pair whose relative strength is declared but not established. */
    public boolean isDeclaredUnknownWith(AbstractOrdinal other) {
        Objects.requireNonNull(other, "other");
        for (UnknownPair pair : DECLARED_UNKNOWN_PAIRS) {
            if (pair.involves(this, other)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The documented, explicit list of pairs whose order is <em>declared but not established</em> - here
     * only {@link #SHO} against {@link #MHO}, because the name MHO has been used for both stages. The list
     * is immutable and in rank order within each pair.
     */
    public static List<UnknownPair> declaredUnknownPairs() {
        return DECLARED_UNKNOWN_PAIRS;
    }

    /**
     * One pair of names whose relative strength this register does not establish. The pair is symmetric:
     * {@link #involves(AbstractOrdinal, AbstractOrdinal)} accepts either order, and the two fields are
     * stored in declared-rank order.
     */
    public record UnknownPair(AbstractOrdinal first, AbstractOrdinal second, String reason) {

        public UnknownPair {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
            if (first == second) {
                throw new IllegalArgumentException("an unknown pair needs two different names, but both are "
                        + first);
            }
            if (first.rank() > second.rank()) {
                AbstractOrdinal swap = first;
                first = second;
                second = swap;
            }
            reason = requireText(reason, "reason");
        }

        /** Whether the two arguments are this pair, in either order. */
        public boolean involves(AbstractOrdinal left, AbstractOrdinal right) {
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
            return (first == left && second == right) || (first == right && second == left);
        }
    }

    private static String requireText(String text, String what) {
        Objects.requireNonNull(text, what);
        if (text.isBlank()) {
            throw new IllegalArgumentException("the " + what + " must not be blank");
        }
        return text;
    }

    /** Whether this name comes before {@code other} in the declared rank; private, for the static initialiser. */
    private boolean isBefore(AbstractOrdinal other) {
        return rank < other.rank;
    }
}
