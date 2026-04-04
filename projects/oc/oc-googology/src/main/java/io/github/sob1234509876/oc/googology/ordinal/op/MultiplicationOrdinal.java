package io.github.sob1234509876.oc.googology.ordinal.op;

import io.github.sob1234509876.oc.engine.api.googology.*;
import io.github.sob1234509876.oc.googology.annotation.SupportOptional;
import io.github.sob1234509876.oc.googology.collection.ListSortedSequence;
import io.github.sob1234509876.oc.googology.collection.SortedSequence;
import io.github.sob1234509876.oc.googology.impl.ImplLimitOrdinal;
import io.github.sob1234509876.oc.googology.impl.ImplTransfiniteOrdinal;
import io.github.sob1234509876.oc.googology.impl.ImplZeroOrdinal;
import io.github.sob1234509876.oc.googology.optional.OptionalSuccessorOrdinal;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalLongOrdinal;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalZeroOrdinal;
import io.github.sob1234509876.oc.googology.optional.op.OptionalAdditionOrdinal;
import io.github.sob1234509876.oc.googology.optional.op.OptionalMultiplicationOrdinal;
import io.github.sob1234509876.oc.googology.optional.op.OptionalOperationOrdinal;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalLimitOrdinal;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalTransfiniteOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.finite.successor.LongOrdinal;
import io.github.sob1234509876.oc.googology.support.Longs;
import io.github.sob1234509876.oc.googology.support.Ordinals;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.LinkedList;
import java.util.Optional;

@Data
@NoArgsConstructor
public class MultiplicationOrdinal implements OperationOrdinal,
        OptionalMultiplicationOrdinal {

    public static final String CONNECTOR = " * ";

    @NonNull
    SortedSequence<@NonNull Ordinal> ordinals = new ListSortedSequence<>();

    @SupportOptional(OptionalZeroOrdinal.class)
    public boolean isZeroOrdinal() {
        if (ordinals.isEmpty())
            return true;

        return Ordinals.isZeroOrdinal(ordinals.getLast()
                .orElseThrow(NullPointerException::new));
    }

    @SupportOptional(OptionalZeroOrdinal.class)
    public boolean isPrimitiveOrdinal() {
        if (ordinals.size() < 2)
            return true;

        for (var i = 1; i < ordinals.size(); i++)
            if (ordinals.get(i)
                    .map(o -> o.compareTo(new LongOrdinal(1)) == 0)
                    .orElse(false))
                return true;

        return false;
    }

    @SupportOptional(OptionalLongOrdinal.class)
    @NonNull
    public Optional<Long> calculateLong() {
        var tmp = new LinkedList<Long>();

        for (var o : ordinals.toList()) {
            var opt = o.optional(OptionalLongOrdinal.class)
                    .flatMap(OptionalLongOrdinal::toLongOrdinal)
                    .map(LongOrdinal::getValue);

            if (opt.isEmpty())
                return Optional.empty();

            tmp.add(opt.get());
        }

        return Longs.safeMultiplication(tmp.toArray(Long[]::new));
    }

    @SupportOptional(OptionalLimitOrdinal.class)
    public boolean isLimitOrdinal() {
        if (ordinals.isEmpty())
            return false;

        return Ordinals.isLimitOrdinal(ordinals.getLast()
                .orElseThrow(NullPointerException::new));
    }

    @SupportOptional(OptionalTransfiniteOrdinal.class)
    public boolean isTransfiniteOrdinal() {
        if (ordinals.isEmpty())
            return false;

        return Ordinals.isTransfiniteOrdinal(ordinals.getLast()
                .orElseThrow(NullPointerException::new));
    }

    @SupportOptional(OptionalMultiplicationOrdinal.class)
    @Override
    public @NonNull Optional<MultiplicationOrdinal> toMultiplicationOrdinal() {
        return Optional.of(this);
    }

    @SupportOptional(OptionalOperationOrdinal.class)
    @Override
    public @NonNull Optional<OperationOrdinal> toOperationOrdinal() {
        return Optional.of(this);
    }

    @SupportOptional(OptionalZeroOrdinal.class)
    @Override
    public @NonNull Optional<ZeroOrdinal> toZeroOrdinal() {
        return isZeroOrdinal() ?
                Optional.of(new ImplZeroOrdinal()) :
                Optional.empty();
    }

    @SupportOptional(OptionalLongOrdinal.class)
    @Override
    public @NonNull Optional<LongOrdinal> toLongOrdinal() {
        return calculateLong()
                .map(LongOrdinal::new);
    }

    @SupportOptional(OptionalSuccessorOrdinal.class)
    @Override
    public @NonNull Optional<SuccessorOrdinal> toSuccessorOrdinal() {
        return toLongOrdinal()
                .flatMap(OptionalSuccessorOrdinal::toSuccessorOrdinal);
    }

    @SupportOptional(OptionalLimitOrdinal.class)
    @Override
    public @NonNull Optional<LimitOrdinal> toLimitOrdinal() {

        if (!isLimitOrdinal())
            return Optional.empty();

        return Optional.of(new ImplLimitOrdinal(this,
                ordinals.getLast()
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

    @SupportOptional({OptionalZeroOrdinal.class,
            OptionalLongOrdinal.class,
            OptionalAdditionOrdinal.class,
            OptionalMultiplicationOrdinal.class,
            OptionalOperationOrdinal.class})
    @Override
    public int compareTo(@NonNull Ordinal o) {

        var res = o.optional(OptionalZeroOrdinal.class)
                .flatMap(OptionalZeroOrdinal::toZeroOrdinal)
                .map(zero -> isZeroOrdinal() ? 0 : 1);
        if (res.isPresent())
            return res.get();

        if (isZeroOrdinal())
            return -1;

        var res2 = o.optional(OptionalLongOrdinal.class)
                .flatMap(OptionalLongOrdinal::toLongOrdinal)
                .map(LongOrdinal::getValue)
                .flatMap(l -> calculateLong()
                        .map(r -> Long.compare(r, l)));
        if (res2.isPresent())
            return res2.get();

        var res3 = o.optional(OptionalAdditionOrdinal.class)
                .flatMap(OptionalAdditionOrdinal::toAdditionOrdinal)
                .flatMap(a -> ordinals.getFirst()
                        .map(ord -> ord.compareTo(a)))
                .map(i -> isPrimitiveOrdinal() && i == 0 ? 0 : 1);
        if (res3.isPresent())
            return res3.get();

        var res4 = o.optional(OptionalMultiplicationOrdinal.class)
                .flatMap(OptionalMultiplicationOrdinal::toMultiplicationOrdinal)
                .map(m -> ordinals.compareTo(m.getOrdinals()));
        if (res4.isPresent())
            return res4.get();

        var res5 = o.optional(OptionalOperationOrdinal.class)
                .flatMap(OptionalOperationOrdinal::toOperationOrdinal)
                .map(ord -> -ord.compareTo(this));
        if (res5.isPresent())
            return res5.get();

        var res6 = ordinals.getFirst()
                .map(ord -> ord.compareTo(o))
                .orElseThrow(NullPointerException::new);
        if (res6 < 0)
            return -1;
        if (res6 == 0)
            return isPrimitiveOrdinal() ? 0 : 1;
        return 1;
    }

}
