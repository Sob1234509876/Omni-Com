package io.github.sob1234509876.oc.googology.optional.op;

import io.github.sob1234509876.oc.googology.ordinal.op.AdditionOrdinal;
import lombok.NonNull;

import java.util.Optional;

public interface OptionalAdditionOrdinal extends OptionalOperationOrdinal {

    @NonNull
    Optional<AdditionOrdinal> toAdditionOrdinal();

}
