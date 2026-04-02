package io.github.sob1234509876.oc.googology.ordinal.transfinite.constant;

import io.github.sob1234509876.oc.engine.api.googology.FundamentalSequence;
import io.github.sob1234509876.oc.engine.api.googology.LimitOrdinal;
import io.github.sob1234509876.oc.engine.api.googology.Ordinal;
import io.github.sob1234509876.oc.engine.api.googology.TransfiniteOrdinal;
import io.github.sob1234509876.oc.googology.annotation.SupportOptional;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalLimitOrdinal;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalTransfiniteOrdinal;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalVeblenOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.finite.successor.LongOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.transfinite.VeblenOrdinal;
import io.github.sob1234509876.oc.googology.support.FundamentalSequences;
import lombok.NonNull;

import java.util.Optional;

public class OmegaOrdinal implements LimitOrdinal,
        OptionalTransfiniteOrdinal,
        OptionalLimitOrdinal,
        OptionalVeblenOrdinal {
    @Override
    public @NonNull FundamentalSequence getFundamentalSequence() {
        return FundamentalSequences.getOmegaFs();
    }

    @Override
    public int compareTo(@NonNull Ordinal o) {
        return toVeblenOrdinal()
                .orElseThrow(NullPointerException::new)
                .compareTo(o);
    }

    @SupportOptional(OptionalLimitOrdinal.class)
    @Override
    public @NonNull Optional<LimitOrdinal> toLimitOrdinal() {
        return Optional.of(this);
    }

    @SupportOptional(OptionalTransfiniteOrdinal.class)
    @Override
    public @NonNull Optional<TransfiniteOrdinal> toTransfiniteOrdinal() {
        return Optional.of(this);
    }

    @SupportOptional(OptionalVeblenOrdinal.class)
    @Override
    public @NonNull Optional<VeblenOrdinal> toVeblenOrdinal() {
        return Optional.of(new VeblenOrdinal(new LongOrdinal(0),
                new LongOrdinal(0)));
    }
}
