package io.github.sob1234509876.oc.googology.optional.op;

import io.github.sob1234509876.oc.googology.ordinal.op.MultiplicationOrdinal;
import lombok.NonNull;

import java.util.Optional;

public interface OptionalMultiplicationOrdinal extends OptionalOperationOrdinal {

    @NonNull
    Optional<MultiplicationOrdinal> toMultiplicationOrdinal();

}
