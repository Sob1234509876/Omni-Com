package io.github.sob1234509876.oc.googology.ordinal.op;

import io.github.sob1234509876.oc.engine.api.googology.*;
import io.github.sob1234509876.oc.googology.annotation.SupportOptional;
import io.github.sob1234509876.oc.googology.collection.SortedSequence;
import io.github.sob1234509876.oc.googology.optional.OptionalSuccessorOrdinal;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalFghOrdinal;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalLongOrdinal;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalZeroOrdinal;
import io.github.sob1234509876.oc.googology.optional.op.OptionalOperationOrdinal;
import io.github.sob1234509876.oc.googology.optional.op.OptionalPowerOrdinal;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalLimitOrdinal;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalTransfiniteOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.finite.hierarchy.FghOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.finite.successor.LongOrdinal;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

// TODO
@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class PowerOrdinal implements OperationOrdinal,
        OptionalPowerOrdinal {

    public static final String CONNECTOR = " ^ ";

    @NonNull
    SortedSequence<@NonNull Ordinal> ordinals;

    @SupportOptional(OptionalPowerOrdinal.class)
    @Override
    public @NonNull Optional<PowerOrdinal> toPowerOrdinal() {
        return Optional.of(this);
    }

    @SupportOptional(OptionalOperationOrdinal.class)
    @Override
    public @NonNull Optional<OperationOrdinal> toOperationOrdinal() {
        return Optional.of(this);
    }

    @SupportOptional(OptionalFghOrdinal.class)
    @Override
    public @NonNull Optional<FghOrdinal> toFghOrdinal() {
        return Optional.empty();
    }

    @SupportOptional(OptionalZeroOrdinal.class)
    @Override
    public @NonNull Optional<ZeroOrdinal> toZeroOrdinal() {
        return Optional.empty();
    }

    @SupportOptional(OptionalLongOrdinal.class)
    @Override
    public @NonNull Optional<LongOrdinal> toLongOrdinal() {
        return Optional.empty();
    }

    @SupportOptional(OptionalSuccessorOrdinal.class)
    @Override
    public @NonNull Optional<SuccessorOrdinal> toSuccessorOrdinal() {
        return Optional.empty();
    }

    @SupportOptional(OptionalLimitOrdinal.class)
    @Override
    public @NonNull Optional<LimitOrdinal> toLimitOrdinal() {
        return Optional.empty();
    }

    @SupportOptional(OptionalTransfiniteOrdinal.class)
    @Override
    public @NonNull Optional<TransfiniteOrdinal> toTransfiniteOrdinal() {
        return Optional.empty();
    }

    @Override
    public int compareTo(@NonNull Ordinal o) {
        return 0;
    }

    @NonNull
    @Override
    public String toString() {
        var sb = new StringBuilder();

        for (var o : ordinals.toList())
            sb.append(o)
                    .append(CONNECTOR);

        if (!sb.isEmpty()) {
            var s = sb.length();

            sb.delete(s - CONNECTOR.length(), s);
        }

        return sb.toString();
    }

    public void add(@NonNull Ordinal o) {
        ordinals.add(o);
    }
}
