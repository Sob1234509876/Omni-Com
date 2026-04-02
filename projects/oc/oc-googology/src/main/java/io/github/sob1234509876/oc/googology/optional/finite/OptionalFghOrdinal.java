package io.github.sob1234509876.oc.googology.optional.finite;

import io.github.sob1234509876.oc.googology.ordinal.finite.hierarchy.FghOrdinal;
import lombok.NonNull;

import java.util.Optional;

public interface OptionalFghOrdinal extends OptionalLongOrdinal {

    @NonNull
    Optional<FghOrdinal> toFghOrdinal();

}
