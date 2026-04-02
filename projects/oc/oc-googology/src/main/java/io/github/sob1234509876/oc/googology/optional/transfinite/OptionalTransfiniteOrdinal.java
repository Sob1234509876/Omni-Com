package io.github.sob1234509876.oc.googology.optional.transfinite;

import io.github.sob1234509876.oc.engine.api.googology.TransfiniteOrdinal;
import lombok.NonNull;

import java.util.Optional;

public interface OptionalTransfiniteOrdinal {

    @NonNull
    Optional<TransfiniteOrdinal> toTransfiniteOrdinal();

}
