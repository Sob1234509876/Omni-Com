package io.github.sob1234509876.oc.googology.ordinal.finite.hierarchy;

import io.github.sob1234509876.oc.engine.api.googology.FiniteOrdinal;
import io.github.sob1234509876.oc.engine.api.googology.Ordinal;
import io.github.sob1234509876.oc.engine.api.googology.SuccessorOrdinal;
import io.github.sob1234509876.oc.googology.collection.ListSortedSequence;
import io.github.sob1234509876.oc.googology.collection.SortedSequence;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalFghOrdinal;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalLongOrdinal;
import io.github.sob1234509876.oc.googology.optional.OptionalSuccessorOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.finite.successor.LongOrdinal;
import io.github.sob1234509876.oc.googology.support.Fghs;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.Optional;

@Data
@NoArgsConstructor
public class FghOrdinal implements FiniteOrdinal,
        OptionalLongOrdinal,
        OptionalSuccessorOrdinal,
        OptionalFghOrdinal {

    @NonNull
    SortedSequence<@NonNull Ordinal> recursiveOrdinals = new ListSortedSequence<>();

    long value;

    @NonNull
    @Override
    public String toString() {
        var sb = new StringBuilder();

        recursiveOrdinals.toList()
                .forEach(o ->
                        sb.append("f_{")
                                .append(o)
                                .append("}("));

        sb.append(value)
                .append(")".repeat(recursiveOrdinals.size()));

        return sb.toString();
    }

    @Override
    public int compareTo(@NonNull Ordinal o) {
        return Fghs.compare(this, o);
    }

    @Override
    public @NonNull Optional<FghOrdinal> toFghOrdinal() {
        return Optional.of(this);
    }

    @Override
    public @NonNull Optional<LongOrdinal> toLongOrdinal() {
        return Fghs.safeEvaluate(this)
                .map(LongOrdinal::new);
    }

    @Override
    public @NonNull Optional<SuccessorOrdinal> toSuccessorOrdinal() {
        return toLongOrdinal()
                .flatMap(LongOrdinal::toSuccessorOrdinal);
    }
}
