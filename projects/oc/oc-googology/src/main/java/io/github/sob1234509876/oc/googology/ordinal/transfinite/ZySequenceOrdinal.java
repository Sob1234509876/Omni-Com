package io.github.sob1234509876.oc.googology.ordinal.transfinite;

import io.github.sob1234509876.oc.engine.api.googology.LimitOrdinal;
import io.github.sob1234509876.oc.engine.api.googology.Ordinal;
import io.github.sob1234509876.oc.engine.api.googology.SuccessorOrdinal;
import io.github.sob1234509876.oc.engine.api.googology.TransfiniteOrdinal;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalZySequenceOrdinal;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class ZySequenceOrdinal implements Ordinal,
        OptionalZySequenceOrdinal {

    @NonNull
    List<@NonNull Integer> sequence;

    @Override
    public @NonNull Optional<ZySequenceOrdinal> toZySequenceOrdinal() {
        return Optional.of(this);
    }

    // TODO
    @Override
    public @NonNull Optional<SuccessorOrdinal> toSuccessorOrdinal() {
        return Optional.empty();
    }

    // TODO
    @Override
    public @NonNull Optional<LimitOrdinal> toLimitOrdinal() {
        return Optional.empty();
    }

    // TODO
    @Override
    public @NonNull Optional<TransfiniteOrdinal> toTransfiniteOrdinal() {
        return Optional.empty();
    }

    @Override
    public int compareTo(@NonNull Ordinal o) {
        return 0;
    }
}
