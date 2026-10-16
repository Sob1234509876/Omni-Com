package io.github.sob1234509876.oc.googology;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An ordinal below {@code ε₀} written in Cantor normal form: a finite decreasing sum
 * {@code ω^α₁·c₁ + ω^α₂·c₂ + ... + ω^αₖ·cₖ} with {@code α₁ > α₂ > ... > αₖ} and positive finite
 * coefficients, plus zero.
 *
 * <p>This is the ordinal notation whose ceiling is {@code ε₀}, the supremum of
 * {@code 1, ω, ω^ω, ω^ω^ω, ...} - the notation the transfinite hyperoperation is normally parameterised
 * by. Values are immutable and canonical: zero terms are dropped, equal exponents are merged into one
 * coefficient, and the terms always come out in decreasing exponent order, so two values built in
 * different ways are equal exactly when they are the same ordinal.
 *
 * <p>The fundamental sequence of a limit ordinal follows the usual rules, with the index written
 * {@code n} and starting at zero:
 *
 * <ul>
 *   <li>{@code (ω^(β+1))[n] = ω^β·(n+1)}, so {@code ω[n] = n+1};</li>
 *   <li>{@code (ω^β)[n] = ω^(β[n])} when {@code β} is a limit ordinal;</li>
 *   <li>{@code (γ + ω^β·c)[n] = γ + ω^β·(c-1) + (ω^β)[n]}.</li>
 * </ul>
 *
 * @see CantorNormalForm
 */
public final class CantorOrdinal implements Ordinal {

    /** One normal form term {@code ω^exponent · coefficient}. */
    public record Term(Ordinal exponent, Natural coefficient) {

        public Term {
            Objects.requireNonNull(exponent, "exponent");
            Objects.requireNonNull(coefficient, "coefficient");
            if (coefficient.isZero()) {
                throw new IllegalArgumentException("a term must have a positive coefficient");
            }
        }

        @Override
        public String toString() {
            if (exponent.isZero()) {
                return coefficient.toString();
            }
            String exponentText = exponent.toString();
            if (exponent instanceof CantorOrdinal cantor && cantor.terms().size() > 1) {
                exponentText = "(" + exponentText + ")";
            }
            String power = exponent.isOne() ? "ω" : "ω^" + exponentText;
            return coefficient.isOne() ? power : power + "·" + coefficient;
        }
    }

    private static final Comparator<Term> BY_DESCENDING_EXPONENT =
            (left, right) -> right.exponent().compareTo(left.exponent());

    private final CantorNormalForm notation;
    private final List<Term> terms;

    private CantorOrdinal(CantorNormalForm notation, List<Term> terms) {
        this.notation = notation;
        this.terms = List.copyOf(terms);
    }

    /**
     * Builds the canonical value for the given terms: zero coefficients are dropped and terms with equal
     * exponents are merged, so the result is the unique normal form of that ordinal.
     */
    static CantorOrdinal canonical(CantorNormalForm notation, List<Term> rawTerms) {
        List<Term> positive = new ArrayList<>();
        for (Term term : rawTerms) {
            if (!term.coefficient().isZero()) {
                positive.add(term);
            }
        }
        positive.sort(BY_DESCENDING_EXPONENT);
        List<Term> merged = new ArrayList<>();
        for (Term term : positive) {
            if (!merged.isEmpty()) {
                Term previous = merged.get(merged.size() - 1);
                if (previous.exponent().equals(term.exponent())) {
                    merged.set(merged.size() - 1,
                            new Term(previous.exponent(), previous.coefficient().add(term.coefficient())));
                    continue;
                }
            }
            merged.add(term);
        }
        return new CantorOrdinal(notation, merged);
    }

    /** The terms of the normal form, largest exponent first. Zero has no terms. */
    public List<Term> terms() {
        return terms;
    }

    /** The largest term, empty for zero. */
    public Optional<Term> leadingTerm() {
        return terms.isEmpty() ? Optional.empty() : Optional.of(terms.get(0));
    }

    /** The smallest term, empty for zero. */
    public Optional<Term> lastTerm() {
        return terms.isEmpty() ? Optional.empty() : Optional.of(terms.get(terms.size() - 1));
    }

    @Override
    public CantorNormalForm notation() {
        return notation;
    }

    @Override
    public boolean isZero() {
        return terms.isEmpty();
    }

    @Override
    public boolean isLimit() {
        return !terms.isEmpty() && !terms.get(terms.size() - 1).exponent().isZero();
    }

    @Override
    public Ordinal successor() {
        if (terms.isEmpty()) {
            return notation.one();
        }
        List<Term> next = new ArrayList<>(terms);
        Term last = next.get(next.size() - 1);
        if (last.exponent().isZero()) {
            next.set(next.size() - 1, new Term(last.exponent(), last.coefficient().add(Natural.ONE)));
        } else {
            next.add(new Term(notation.zero(), Natural.ONE));
        }
        return canonical(notation, next);
    }

    @Override
    public Optional<Ordinal> predecessor() {
        if (terms.isEmpty() || isLimit()) {
            return Optional.empty();
        }
        List<Term> next = new ArrayList<>(terms);
        Term last = next.get(next.size() - 1);
        Natural reduced = last.coefficient().subtractClamped(Natural.ONE);
        if (reduced.isZero()) {
            next.remove(next.size() - 1);
        } else {
            next.set(next.size() - 1, new Term(last.exponent(), reduced));
        }
        return Optional.of(canonical(notation, next));
    }

    @Override
    public Optional<Ordinal> fundamentalSequence(Natural index) {
        if (!isLimit()) {
            return Optional.empty();
        }
        Objects.requireNonNull(index, "index");
        Term last = terms.get(terms.size() - 1);
        Ordinal exponent = last.exponent();
        List<Term> next = new ArrayList<>(terms.subList(0, terms.size() - 1));
        Natural coefficient = last.coefficient();
        if (coefficient.compareTo(Natural.ONE) > 0) {
            next.add(new Term(exponent, coefficient.subtractClamped(Natural.ONE)));
        }
        if (exponent.isSuccessor()) {
            // (ω^(β+1))[n] = ω^β·(n+1)
            Ordinal below = exponent.predecessor().orElseThrow();
            next.add(new Term(below, index.add(Natural.ONE)));
        } else {
            // (ω^β)[n] = ω^(β[n]) for a limit exponent
            Optional<Ordinal> exponentStep = exponent.fundamentalSequence(index);
            if (exponentStep.isEmpty()) {
                return Optional.empty();
            }
            next.add(new Term(exponentStep.get(), Natural.ONE));
        }
        return Optional.of(canonical(notation, next));
    }

    @Override
    public Optional<Natural> finiteValue() {
        if (terms.isEmpty()) {
            return Optional.of(Natural.ZERO);
        }
        if (terms.size() == 1 && terms.get(0).exponent().isZero()) {
            return Optional.of(terms.get(0).coefficient());
        }
        return Optional.empty();
    }

    @Override
    public Ordinal add(Ordinal other) {
        Objects.requireNonNull(other, "other");
        if (other instanceof EpsilonZero) {
            // α + ε₀ is ε₀ for every α below ε₀.
            return other;
        }
        if (other.isZero()) {
            return this;
        }
        CantorOrdinal right = requireSameNotation(other);
        Ordinal gamma = right.terms.get(0).exponent();
        Natural leadingCoefficient = right.terms.get(0).coefficient();
        List<Term> merged = new ArrayList<>();
        int index = 0;
        while (index < terms.size() && terms.get(index).exponent().compareTo(gamma) > 0) {
            merged.add(terms.get(index));
            index++;
        }
        if (index < terms.size() && terms.get(index).exponent().equals(gamma)) {
            // ω^γ·c + ω^γ·d = ω^γ·(c+d): the coefficients merge instead of the terms absorbing each other.
            merged.add(new Term(gamma, terms.get(index).coefficient().add(leadingCoefficient)));
            merged.addAll(right.terms.subList(1, right.terms.size()));
        } else {
            // Everything of this ordinal that is smaller than ω^γ is absorbed by the other one.
            merged.addAll(right.terms);
        }
        return canonical(notation, merged);
    }

    /**
     * Multiplication by a finite coefficient. Ordinal multiplication scales the <em>leading</em> term, not
     * the last one: {@code (ω+1)·2 = (ω+1)+(ω+1) = ω·2+1}, because the {@code 1} of the first factor is
     * absorbed when it meets the {@code ω} of the second.
     */
    @Override
    public Ordinal times(Natural factor) {
        Objects.requireNonNull(factor, "factor");
        if (factor.isZero() || terms.isEmpty()) {
            return notation.zero();
        }
        if (factor.isOne()) {
            return this;
        }
        List<Term> next = new ArrayList<>(terms);
        Term leading = next.get(0);
        next.set(0, new Term(leading.exponent(), leading.coefficient().multiply(factor)));
        return canonical(notation, next);
    }

    @Override
    public Ordinal omegaPower() {
        return notation.omegaPower(this);
    }

    @Override
    public int compareTo(Ordinal other) {
        Objects.requireNonNull(other, "other");
        if (other instanceof EpsilonZero) {
            return -1;
        }
        CantorOrdinal right = requireSameNotation(other);
        int shared = Math.min(terms.size(), right.terms.size());
        for (int i = 0; i < shared; i++) {
            Term mine = terms.get(i);
            Term theirs = right.terms.get(i);
            int byExponent = mine.exponent().compareTo(theirs.exponent());
            if (byExponent != 0) {
                return byExponent;
            }
            int byCoefficient = mine.coefficient().compareTo(theirs.coefficient());
            if (byCoefficient != 0) {
                return byCoefficient;
            }
        }
        return Integer.compare(terms.size(), right.terms.size());
    }

    /**
     * Requires the other ordinal to be a value of this notation. Ordinals of another notation cannot be
     * mixed into one normal form; a notation that wants to interoperate should produce the same value type,
     * which is what makes ordinal arithmetic total within a notation.
     */
    private CantorOrdinal requireSameNotation(Ordinal other) {
        if (other instanceof CantorOrdinal cantor) {
            return cantor;
        }
        throw new IllegalArgumentException("cannot combine " + other + " (" + other.notation().name()
                + ") with a " + notation.name() + " ordinal");
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CantorOrdinal cantor && terms.equals(cantor.terms);
    }

    @Override
    public int hashCode() {
        return terms.hashCode();
    }

    @Override
    public String toString() {
        if (terms.isEmpty()) {
            return "0";
        }
        StringBuilder text = new StringBuilder();
        for (Term term : terms) {
            if (text.length() > 0) {
                text.append(" + ");
            }
            text.append(term);
        }
        return text.toString();
    }
}
