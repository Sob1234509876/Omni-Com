package io.github.sob1234509876.oc.googology.optional.finite;

import io.github.sob1234509876.oc.googology.optional.OptionalSuccessorOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.finite.hierarchy.SghOrdinal;
import lombok.NonNull;

import java.util.Optional;

public interface OptionalSghOrdinal extends OptionalSuccessorOrdinal {

    @NonNull
    Optional<SghOrdinal> toSghOrdinal();

}
