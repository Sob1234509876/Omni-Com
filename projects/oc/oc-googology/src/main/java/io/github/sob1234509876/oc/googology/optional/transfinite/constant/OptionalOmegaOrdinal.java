package io.github.sob1234509876.oc.googology.optional.transfinite.constant;

import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalVeblenOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.transfinite.constant.OmegaOrdinal;
import lombok.NonNull;

import java.util.Optional;

public interface OptionalOmegaOrdinal extends OptionalVeblenOrdinal {

    @NonNull
    Optional<OmegaOrdinal> toOmegaOrdinal();

}
