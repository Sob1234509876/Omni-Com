package io.github.sob1234509876.oc.googology.ordinal.op;

import io.github.sob1234509876.oc.engine.api.googology.Ordinal;
import io.github.sob1234509876.oc.googology.collection.SortedSequence;
import io.github.sob1234509876.oc.googology.optional.op.OptionalOperationOrdinal;
import lombok.NonNull;

public interface OperationOrdinal extends Ordinal,
        OptionalOperationOrdinal {

    @NonNull
    SortedSequence<@NonNull Ordinal> getOrdinals();

}
