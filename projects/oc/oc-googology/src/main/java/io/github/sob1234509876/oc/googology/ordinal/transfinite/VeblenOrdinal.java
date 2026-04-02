package io.github.sob1234509876.oc.googology.ordinal.transfinite;

import io.github.sob1234509876.oc.engine.api.googology.FundamentalSequence;
import io.github.sob1234509876.oc.engine.api.googology.LimitOrdinal;
import io.github.sob1234509876.oc.engine.api.googology.Ordinal;
import io.github.sob1234509876.oc.engine.api.googology.TransfiniteOrdinal;
import io.github.sob1234509876.oc.googology.annotation.SupportOptional;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalLimitOrdinal;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalTransfiniteOrdinal;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalVeblenOrdinal;
import io.github.sob1234509876.oc.googology.optional.transfinite.constant.OptionalOmegaOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.transfinite.constant.OmegaOrdinal;
import io.github.sob1234509876.oc.googology.support.FundamentalSequences;
import io.github.sob1234509876.oc.googology.support.Ordinals;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class VeblenOrdinal implements LimitOrdinal,
        OptionalOmegaOrdinal {

    @NonNull
    Ordinal a;

    @NonNull
    Ordinal b;

    @Override
    public @NonNull FundamentalSequence getFundamentalSequence() {
        return FundamentalSequences.getVeblenFs(this);
    }

    // TODO
    @Override
    public int compareTo(@NonNull Ordinal o) {
        return 0;
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

    @SupportOptional(OptionalOmegaOrdinal.class)
    @Override
    public @NonNull Optional<OmegaOrdinal> toOmegaOrdinal() {
        if (Ordinals.isZero(a) && Ordinals.isZero(b))
            return Optional.of(new OmegaOrdinal());
        return Optional.empty();
    }

    @SupportOptional(OptionalVeblenOrdinal.class)
    @Override
    public @NonNull Optional<VeblenOrdinal> toVeblenOrdinal() {
        return Optional.of(this);
    }
}
