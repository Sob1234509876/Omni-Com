package io.github.sob1234509876.oc.googology.ordinal.op;

import io.github.sob1234509876.oc.engine.api.googology.*;
import io.github.sob1234509876.oc.googology.annotation.SupportOptional;
import io.github.sob1234509876.oc.googology.collection.SortedSequence;
import io.github.sob1234509876.oc.googology.impl.ImplLimitOrdinal;
import io.github.sob1234509876.oc.googology.impl.ImplSuccessorOrdinal;
import io.github.sob1234509876.oc.googology.impl.ImplTransfiniteOrdinal;
import io.github.sob1234509876.oc.googology.impl.ImplZeroOrdinal;
import io.github.sob1234509876.oc.googology.optional.OptionalSuccessorOrdinal;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalFghOrdinal;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalLongOrdinal;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalZeroOrdinal;
import io.github.sob1234509876.oc.googology.optional.op.OptionalAdditionOrdinal;
import io.github.sob1234509876.oc.googology.optional.op.OptionalOperationOrdinal;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalLimitOrdinal;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalTransfiniteOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.finite.hierarchy.FghOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.finite.successor.LongOrdinal;
import io.github.sob1234509876.oc.googology.support.Functions;
import io.github.sob1234509876.oc.googology.support.Longs;
import io.github.sob1234509876.oc.googology.support.Ordinals;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.Optional;

// TODO
@Data
@NoArgsConstructor
public class AdditionOrdinal implements OperationOrdinal,
        OptionalAdditionOrdinal {

    public static final String CONNECTOR = " + ";

    @NonNull
    SortedSequence<@NonNull Ordinal> ordinals;

    @SupportOptional(OptionalSuccessorOrdinal.class)
    public boolean isSuccessorOrdinal() {
        return !ordinals.isEmpty() &&
                ordinals.getLast()
                        .flatMap(o -> o.optional(OptionalSuccessorOrdinal.class)
                                .flatMap(OptionalSuccessorOrdinal::toSuccessorOrdinal)
                                .map(Functions::alwaysTrue))
                        .orElse(false);
    }

    @SupportOptional(OptionalLimitOrdinal.class)
    public boolean isLimitOrdinal() {
        return !ordinals.isEmpty() &&
                ordinals.getLast()
                        .flatMap(o -> o.optional(OptionalLimitOrdinal.class)
                                .flatMap(OptionalLimitOrdinal::toLimitOrdinal)
                                .map(Functions::alwaysTrue))
                        .orElse(false);
    }

    @SupportOptional(OptionalLongOrdinal.class)
    public boolean isTooBig() {

        for (var o : ordinals.toList()) {
            var tmp = o.optional(OptionalLongOrdinal.class)
                    .flatMap(OptionalLongOrdinal::toLongOrdinal);
            if (tmp.isEmpty())
                return true;
        }

        return false;
    }

    @SupportOptional(OptionalTransfiniteOrdinal.class)
    public boolean isTransfiniteOrdinal() {
        return ordinals.getLast()
                .flatMap(o -> o.optional(OptionalTransfiniteOrdinal.class)
                        .flatMap(OptionalTransfiniteOrdinal::toTransfiniteOrdinal)
                        .map(Functions::alwaysTrue))
                .orElse(false);
    }

    @SupportOptional(OptionalFghOrdinal.class)
    @Override
    public @NonNull Optional<FghOrdinal> toFghOrdinal() {
        if (ordinals.isEmpty())
            return Optional.empty();

        return ordinals.getFirst()
                .flatMap(o -> o.optional(OptionalFghOrdinal.class)
                        .flatMap(OptionalFghOrdinal::toFghOrdinal));
    }

    @SupportOptional(OptionalZeroOrdinal.class)
    @Override
    public @NonNull Optional<ZeroOrdinal> toZeroOrdinal() {
        if (ordinals.isEmpty())
            return Optional.empty();

        return ordinals.getFirst()
                .map(Ordinals::isZero)
                .orElse(false) ?
                Optional.of(new ImplZeroOrdinal()) :
                Optional.empty();
    }

    @SupportOptional(OptionalLongOrdinal.class)
    @Override
    public @NonNull Optional<LongOrdinal> toLongOrdinal() {

        if (isTooBig())
            return Optional.empty();

        var lst = ordinals.toList()
                .stream()
                .map(o -> o.optional(OptionalLongOrdinal.class)
                        .flatMap(OptionalLongOrdinal::toLongOrdinal)
                        .map(LongOrdinal::getValue)
                        .orElseThrow(NullPointerException::new))
                .toArray(Long[]::new);

        return Longs.safeAddition(lst)
                .map(LongOrdinal::new);
    }

    @SupportOptional(OptionalOperationOrdinal.class)
    @Override
    public @NonNull Optional<OperationOrdinal> toOperationOrdinal() {
        return Optional.of(this);
    }

    @SupportOptional(OptionalLimitOrdinal.class)
    @Override
    public @NonNull Optional<LimitOrdinal> toLimitOrdinal() {

        if (!isLimitOrdinal())
            return Optional.empty();

        return Optional.of(new ImplLimitOrdinal(this, ordinals.getLast()
                .flatMap(o -> o.optional(OptionalLimitOrdinal.class)
                        .flatMap(OptionalLimitOrdinal::toLimitOrdinal))
                .map(LimitOrdinal::getFundamentalSequence)
                .orElseThrow(NullPointerException::new)));
    }

    @SupportOptional(OptionalTransfiniteOrdinal.class)
    @Override
    public @NonNull Optional<TransfiniteOrdinal> toTransfiniteOrdinal() {

        if (!isTransfiniteOrdinal())
            return Optional.empty();

        return Optional.of(new ImplTransfiniteOrdinal(this));
    }

    @SupportOptional(OptionalSuccessorOrdinal.class)
    @Override
    public @NonNull Optional<SuccessorOrdinal> toSuccessorOrdinal() {
        if (!isSuccessorOrdinal())
            return Optional.empty();

        var ori = new AdditionOrdinal();
        ordinals.toList()
                .forEach(ori.getOrdinals()::add);

        ori.getOrdinals()
                .setLast(ori.getOrdinals()
                        .getLast()
                        .flatMap(o -> o.optional(OptionalSuccessorOrdinal.class)
                                .flatMap(OptionalSuccessorOrdinal::toSuccessorOrdinal)
                                .map(SuccessorOrdinal::getOrigin))
                        .orElseThrow(NullPointerException::new));

        return Optional.of(new ImplSuccessorOrdinal(this, ori));
    }

    @Override
    public @NonNull Optional<AdditionOrdinal> toAdditionOrdinal() {
        return Optional.of(this);
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
    }
}
