package io.github.sob1234509876.oc.googology.optional.op;

import io.github.sob1234509876.oc.googology.optional.finite.OptionalZeroOrdinal;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalLimitOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.op.OperationOrdinal;
import lombok.NonNull;

import java.util.Optional;

public interface OptionalOperationOrdinal extends OptionalZeroOrdinal,
        OptionalLimitOrdinal {
    @NonNull
    Optional<OperationOrdinal> toOperationOrdinal();
}
