package io.github.sob1234509876.oc.googology.optional.finite;

import io.github.sob1234509876.oc.engine.api.googology.ZeroOrdinal;
import lombok.NonNull;

import java.util.Optional;

public interface OptionalZeroOrdinal extends OptionalLongOrdinal {

    @NonNull
    Optional<ZeroOrdinal> toZeroOrdinal();

}
