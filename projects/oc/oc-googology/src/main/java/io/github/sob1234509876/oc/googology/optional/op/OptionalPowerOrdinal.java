package io.github.sob1234509876.oc.googology.optional.op;

import io.github.sob1234509876.oc.googology.ordinal.op.PowerOrdinal;
import lombok.NonNull;

import java.util.Optional;

public interface OptionalPowerOrdinal extends OptionalOperationOrdinal {

    @NonNull
    Optional<PowerOrdinal> toPowerOrdinal();

}
