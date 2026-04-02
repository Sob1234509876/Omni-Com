package io.github.sob1234509876.oc.googology.optional.finite;

import io.github.sob1234509876.oc.googology.optional.OptionalSuccessorOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.finite.successor.LongOrdinal;
import lombok.NonNull;

import java.util.Optional;

public interface OptionalLongOrdinal extends OptionalSuccessorOrdinal {

    @NonNull
    Optional<LongOrdinal> toLongOrdinal();

}
