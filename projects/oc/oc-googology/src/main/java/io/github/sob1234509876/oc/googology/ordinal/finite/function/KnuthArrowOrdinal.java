package io.github.sob1234509876.oc.googology.ordinal.finite.function;

import io.github.sob1234509876.oc.engine.api.googology.FiniteOrdinal;
import io.github.sob1234509876.oc.engine.api.googology.Ordinal;
import io.github.sob1234509876.oc.engine.api.googology.SuccessorOrdinal;
import io.github.sob1234509876.oc.engine.api.googology.ZeroOrdinal;
import io.github.sob1234509876.oc.googology.annotation.SupportOptional;
import io.github.sob1234509876.oc.googology.collection.ListSortedSequence;
import io.github.sob1234509876.oc.googology.collection.SortedSequence;
import io.github.sob1234509876.oc.googology.impl.ImplZeroOrdinal;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalFghOrdinal;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalLongOrdinal;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalZeroOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.finite.hierarchy.FghOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.finite.successor.LongOrdinal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.Optional;

@Data
@NoArgsConstructor
public class KnuthArrowOrdinal implements FiniteOrdinal,
        OptionalFghOrdinal,
        OptionalZeroOrdinal {

    long value;

    @NonNull
    SortedSequence<@NonNull KnuthArrow> knuthArrows = new ListSortedSequence<>();

    @Override
    public @NonNull Optional<FghOrdinal> toFghOrdinal() {
        var fgh = new FghOrdinal();

        knuthArrows.toList()
                .stream()
                .map(KnuthArrow::getArrows)
                .map(l -> l + 1)
                .map(LongOrdinal::new)
                .forEach(fgh.getRecursiveOrdinals()::add);

        return Optional.of(fgh);
    }

    @SupportOptional(OptionalZeroOrdinal.class)
    @Override
    public @NonNull Optional<ZeroOrdinal> toZeroOrdinal() {
        return isZero() ?
                Optional.of(new ImplZeroOrdinal()) :
                Optional.empty();
    }

    public boolean isZero() {
        if (knuthArrows.isEmpty())
            return value == 0;

        if (knuthArrows.getFirst()
                .isPresent()) {
            var f = knuthArrows.getFirst()
                    .get();

            return f.getA() == 0;
        }

        return false;
    }

    @Override
    public int compareTo(@NonNull Ordinal o) {
        return toFghOrdinal()
                .map(fgh -> fgh.compareTo(o))
                .orElseThrow(NullPointerException::new);
    }

    @NonNull
    @Override
    public String toString() {
        var sb = new StringBuilder();

        knuthArrows.toList()
                .forEach(sb::append);

        sb.append(value);

        return sb.toString();
    }

    @SupportOptional(OptionalLongOrdinal.class)
    @Override
    public @NonNull Optional<LongOrdinal> toLongOrdinal() {
        return toFghOrdinal()
                .flatMap(OptionalLongOrdinal::toLongOrdinal);
    }

    @SupportOptional(SuccessorOrdinal.class)
    @Override
    public @NonNull Optional<SuccessorOrdinal> toSuccessorOrdinal() {
        return toLongOrdinal()
                .flatMap(LongOrdinal::toSuccessorOrdinal);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KnuthArrow implements Comparable<@NonNull KnuthArrow> {

        long arrows;

        long a;

        @Override
        public int compareTo(@NonNull KnuthArrow o) {
            var ra = Long.compare(arrows, o.getArrows());

            if (ra != 0)
                return ra;

            return Long.compare(a, o.getA());
        }

        @NonNull
        @Override
        public String toString() {
            return a + "{" + arrows + "}";
        }
    }
}
