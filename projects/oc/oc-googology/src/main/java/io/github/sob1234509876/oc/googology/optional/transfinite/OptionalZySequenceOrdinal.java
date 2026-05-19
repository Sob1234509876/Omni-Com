package io.github.sob1234509876.oc.googology.optional.transfinite;

import io.github.sob1234509876.oc.googology.optional.OptionalSuccessorOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.transfinite.ZySequenceOrdinal;
import lombok.NonNull;

import java.util.Optional;

public interface OptionalZySequenceOrdinal extends OptionalSuccessorOrdinal,
        OptionalTransfiniteOrdinal,
        OptionalLimitOrdinal {

    @NonNull
    Optional<ZySequenceOrdinal> toZySequenceOrdinal();

}
