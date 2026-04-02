package io.github.sob1234509876.oc.googology.optional.transfinite;

import io.github.sob1234509876.oc.googology.ordinal.transfinite.VeblenOrdinal;
import lombok.NonNull;

import java.util.Optional;

public interface OptionalVeblenOrdinal extends OptionalLimitOrdinal {

    @NonNull
    Optional<VeblenOrdinal> toVeblenOrdinal();

}
