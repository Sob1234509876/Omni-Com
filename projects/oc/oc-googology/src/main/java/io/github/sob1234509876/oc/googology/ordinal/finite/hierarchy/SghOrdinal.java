package io.github.sob1234509876.oc.googology.ordinal.finite.hierarchy;

import io.github.sob1234509876.oc.engine.api.googology.FiniteOrdinal;
import io.github.sob1234509876.oc.engine.api.googology.Ordinal;
import io.github.sob1234509876.oc.engine.api.googology.SuccessorOrdinal;
import io.github.sob1234509876.oc.googology.impl.ImplSuccessorOrdinal;
import io.github.sob1234509876.oc.googology.optional.OptionalSuccessorOrdinal;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalSghOrdinal;
import io.github.sob1234509876.oc.googology.support.Sghs;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.Optional;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SghOrdinal implements FiniteOrdinal,
        OptionalSghOrdinal {

    @NonNull
    Ordinal ordinal;

    long value;

    @NonNull
    @Override
    public String toString() {
        return "g_{" + ordinal + "}(" + value + ")";
    }

    @Override
    public int compareTo(@NonNull Ordinal o) {
        return Sghs.compare(this, o);
    }

    @Override
    public @NonNull Optional<SghOrdinal> toSghOrdinal() {
        return Optional.of(this);
    }

    @Override
    public @NonNull Optional<SuccessorOrdinal> toSuccessorOrdinal() {
        return ordinal.optional(OptionalSuccessorOrdinal.class)
                .flatMap(OptionalSuccessorOrdinal::toSuccessorOrdinal)
                .map(SuccessorOrdinal::getOrigin)
                .map(o -> new ImplSuccessorOrdinal(this,
                        new SghOrdinal(o,
                                value)));
    }
}
