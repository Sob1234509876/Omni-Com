package io.github.sob1234509876.oc.googology.optional.transfinite;

import io.github.sob1234509876.oc.engine.api.googology.LimitOrdinal;
import lombok.NonNull;

import java.util.Optional;

public interface OptionalLimitOrdinal extends OptionalTransfiniteOrdinal {
    @NonNull
    Optional<LimitOrdinal> toLimitOrdinal();
}
