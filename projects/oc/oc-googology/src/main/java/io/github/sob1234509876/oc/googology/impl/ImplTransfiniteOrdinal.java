package io.github.sob1234509876.oc.googology.impl;

import io.github.sob1234509876.oc.engine.api.googology.Ordinal;
import io.github.sob1234509876.oc.engine.api.googology.TransfiniteOrdinal;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalTransfiniteOrdinal;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class ImplTransfiniteOrdinal implements TransfiniteOrdinal,
        OptionalTransfiniteOrdinal {

    @NonNull
    Ordinal ordinal;

    @Override
    public @NonNull Optional<TransfiniteOrdinal> toTransfiniteOrdinal() {
        return Optional.of(this);
    }

    @Override
    public int compareTo(@NonNull Ordinal o) {
        return ordinal.compareTo(o);
    }
}
