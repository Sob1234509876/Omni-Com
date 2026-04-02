package io.github.sob1234509876.oc.googology.optional;

import io.github.sob1234509876.oc.engine.api.googology.SuccessorOrdinal;
import lombok.NonNull;

import java.util.Optional;

public interface OptionalSuccessorOrdinal {

    @NonNull
    Optional<SuccessorOrdinal> toSuccessorOrdinal();

}
