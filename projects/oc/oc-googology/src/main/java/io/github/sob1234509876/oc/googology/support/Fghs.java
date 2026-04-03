package io.github.sob1234509876.oc.googology.support;

import io.github.sob1234509876.oc.engine.api.googology.Ordinal;
import io.github.sob1234509876.oc.engine.api.googology.SuccessorOrdinal;
import io.github.sob1234509876.oc.googology.annotation.SupportOptional;
import io.github.sob1234509876.oc.googology.optional.OptionalSuccessorOrdinal;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalFghOrdinal;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalLongOrdinal;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalZeroOrdinal;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalLimitOrdinal;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalTransfiniteOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.finite.hierarchy.FghOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.finite.successor.LongOrdinal;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Fghs {

    public static long evaluate(@NonNull FghOrdinal fgh) {
        var tmp = fgh.getValue();
        var size = fgh.getRecursiveOrdinals()
                .size();

        for (var i = 0; i < size; i++) {
            tmp = evaluate(fgh.getRecursiveOrdinals()
                    .get(size - 1 - i)
                    .orElseThrow(NullPointerException::new), tmp);
        }

        return tmp;
    }

    @SupportOptional({OptionalZeroOrdinal.class,
            OptionalLimitOrdinal.class,
            OptionalLongOrdinal.class,
            OptionalSuccessorOrdinal.class})
    public static long evaluate(@NonNull Ordinal fghOrd, long value) {

        // Generalized
        if (value < 0)
            throw new ArithmeticException("Long smaller than zero.");

        // Generalized
        var res = fghOrd.optional(OptionalZeroOrdinal.class)
                .flatMap(OptionalZeroOrdinal::toZeroOrdinal)
                .map(o -> {
                    if (value == Long.MAX_VALUE)
                        throw new ArithmeticException("Long too big.");
                    return value + 1;
                });
        if (res.isPresent())
            return res.get();

        // Generalized
        var res2 = fghOrd.optional(OptionalLimitOrdinal.class)
                .flatMap(OptionalLimitOrdinal::toLimitOrdinal)
                .map(l -> {
                    if (value > 2)
                        throw new ArithmeticException("Long too big.");
                    return l;
                })
                .map(l -> evaluate(l.getFundamentalSequence()
                                .apply((int) value),
                        value));
        if (res2.isPresent())
            return res2.get();

        // Special
        var res3 = fghOrd.optional(OptionalLongOrdinal.class)
                .flatMap(OptionalLongOrdinal::toLongOrdinal)
                .map(l -> {
                    var v = l.getValue();

                    if (v == 0) {
                        if (value == Long.MAX_VALUE)
                            throw new ArithmeticException("Long too big.");
                        return value + 1;
                    }

                    if (v == 1) {
                        if (value > (Long.MAX_VALUE >> 1))
                            throw new ArithmeticException("Long too big.");
                        return value * 2;
                    }

                    if (v == 2) {
                        if (value > 57)
                            throw new ArithmeticException("Long too big");
                        return (1L << value) * value;
                    }

                    var tmp = value;
                    for (var i = 0; i < value; i++)
                        tmp = evaluate(new LongOrdinal(v - 1), value);

                    return tmp;
                });
        if (res3.isPresent())
            return res3.get();

        // Generalized
        var res4 = fghOrd.optional(OptionalSuccessorOrdinal.class)
                .flatMap(OptionalSuccessorOrdinal::toSuccessorOrdinal)
                .map(SuccessorOrdinal::getOrigin)
                .map(o -> {
                    var tmp = value;

                    for (var i = 0; i < value; i++) {
                        tmp = evaluate(o, value);
                    }

                    return tmp;
                });
        if (res4.isPresent())
            return res4.get();

        throw new UnsupportedOperationException("Unsupported ordinal " + fghOrd);
    }

    @NonNull
    public static Optional<Long> safeEvaluate(@NonNull FghOrdinal fgh) {
        try {
            return Optional.of(evaluate(fgh));
        } catch (@NonNull RuntimeException ignored) {
        }

        return Optional.empty();
    }

    @SupportOptional({OptionalZeroOrdinal.class,
            OptionalTransfiniteOrdinal.class,
            OptionalLongOrdinal.class,
            OptionalFghOrdinal.class})
    public static int compare(@NonNull FghOrdinal fgh, @NonNull Ordinal ordinal) {

        var res = ordinal.optional(OptionalZeroOrdinal.class)
                .flatMap(OptionalZeroOrdinal::toZeroOrdinal)
                .map(Functions::alwaysGreater);
        if (res.isPresent())
            return res.get();

        var res2 = ordinal.optional(OptionalTransfiniteOrdinal.class)
                .flatMap(OptionalTransfiniteOrdinal::toTransfiniteOrdinal)
                .map(Functions::alwaysLesser);
        if (res2.isPresent())
            return res2.get();

        var res3 = ordinal.optional(OptionalLongOrdinal.class)
                .flatMap(OptionalLongOrdinal::toLongOrdinal)
                .map(l -> safeEvaluate(fgh)
                        .map(r -> Long.compare(r, l.getValue()))
                        .orElse(1));
        if (res3.isPresent())
            return res3.get();

        var res4 = ordinal.optional(OptionalFghOrdinal.class)
                .flatMap(OptionalFghOrdinal::toFghOrdinal)
                .map(f -> {
                    var ror = fgh.getRecursiveOrdinals()
                            .compareTo(f.getRecursiveOrdinals());

                    if (ror != 0)
                        return ror;

                    return Long.compare(fgh.getValue(), f.getValue());
                });
        return res4.orElseGet(() -> -ordinal.compareTo(fgh));

    }

}
