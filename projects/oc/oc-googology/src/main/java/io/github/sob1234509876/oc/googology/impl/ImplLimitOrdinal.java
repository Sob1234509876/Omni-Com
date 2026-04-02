package io.github.sob1234509876.oc.googology.impl;

import io.github.sob1234509876.oc.engine.api.googology.FundamentalSequence;
import io.github.sob1234509876.oc.engine.api.googology.LimitOrdinal;
import io.github.sob1234509876.oc.engine.api.googology.Ordinal;
import io.github.sob1234509876.oc.engine.api.googology.TransfiniteOrdinal;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalLimitOrdinal;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class ImplLimitOrdinal implements LimitOrdinal,
        OptionalLimitOrdinal {

    @NonNull
    Ordinal value;

    @NonNull
    FundamentalSequence fundamentalSequence;

    @Override
    public @NonNull Optional<LimitOrdinal> toLimitOrdinal() {
        return Optional.of(this);
    }

    @Override
    public @NonNull Optional<TransfiniteOrdinal> toTransfiniteOrdinal() {
        return Optional.of(this);
    }

    @Override
    public int compareTo(@NonNull Ordinal o) {
        return value.compareTo(o);
    }
}
