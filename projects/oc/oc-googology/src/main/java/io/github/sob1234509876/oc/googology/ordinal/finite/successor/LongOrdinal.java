package io.github.sob1234509876.oc.googology.ordinal.finite.successor;

import io.github.sob1234509876.oc.engine.api.googology.FiniteOrdinal;
import io.github.sob1234509876.oc.engine.api.googology.Ordinal;
import io.github.sob1234509876.oc.engine.api.googology.SuccessorOrdinal;
import io.github.sob1234509876.oc.engine.api.googology.ZeroOrdinal;
import io.github.sob1234509876.oc.googology.annotation.SupportOptional;
import io.github.sob1234509876.oc.googology.impl.ImplSuccessorOrdinal;
import io.github.sob1234509876.oc.googology.impl.ImplZeroOrdinal;
import io.github.sob1234509876.oc.googology.optional.OptionalSuccessorOrdinal;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalFghOrdinal;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalLongOrdinal;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalZeroOrdinal;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalLimitOrdinal;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalTransfiniteOrdinal;
import io.github.sob1234509876.oc.googology.support.Fghs;
import io.github.sob1234509876.oc.googology.support.Functions;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.Optional;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LongOrdinal implements FiniteOrdinal,
        OptionalZeroOrdinal,
        OptionalLongOrdinal {

    long value;

    @NonNull
    @Override
    public String toString() {
        return String.valueOf(value);
    }

    @SupportOptional({OptionalLimitOrdinal.class,
            OptionalFghOrdinal.class,
            OptionalLongOrdinal.class,
            OptionalTransfiniteOrdinal.class,
            OptionalZeroOrdinal.class})
    @Override
    public int compareTo(@NonNull Ordinal o) {

        var res = o.optional(OptionalLimitOrdinal.class)
                .flatMap(OptionalLimitOrdinal::toLimitOrdinal)
                .map(Functions::alwaysLesser);
        if (res.isPresent())
            return res.get();

        var res2 = o.optional(OptionalFghOrdinal.class)
                .flatMap(OptionalFghOrdinal::toFghOrdinal)
                .map(fgh -> Fghs.safeEvaluate(fgh)
                        .map(l -> Long.compare(value, l))
                        .orElse(-1));
        if (res2.isPresent())
            return res2.get();

        var res3 = o.optional(OptionalLongOrdinal.class)
                .flatMap(OptionalLongOrdinal::toLongOrdinal)
                .map(LongOrdinal::getValue)
                .map(l -> Long.compare(value, l));
        if (res3.isPresent())
            return res3.get();

        var res4 = o.optional(OptionalTransfiniteOrdinal.class)
                .flatMap(OptionalTransfiniteOrdinal::toTransfiniteOrdinal)
                .map(Functions::alwaysLesser);
        if (res4.isPresent())
            return res4.get();

        var res5 = o.optional(OptionalZeroOrdinal.class)
                .flatMap(OptionalZeroOrdinal::toZeroOrdinal)
                .map(zero -> Long.compare(value, 0));
        if (res5.isPresent())
            return res5.get();

        throw new UnsupportedOperationException("Unknown ordinal " + o);
    }

    @SupportOptional(OptionalSuccessorOrdinal.class)
    @Override
    public @NonNull Optional<SuccessorOrdinal> toSuccessorOrdinal() {
        return value == 0 ?
                Optional.empty() :
                Optional.of(new ImplSuccessorOrdinal(this,
                        new LongOrdinal(value - 1)));
    }

    @SupportOptional(OptionalZeroOrdinal.class)
    @Override
    public @NonNull Optional<ZeroOrdinal> toZeroOrdinal() {
        return value == 0 ?
                Optional.of(new ImplZeroOrdinal()) :
                Optional.empty();
    }

    @SupportOptional(OptionalLongOrdinal.class)
    @Override
    public @NonNull Optional<LongOrdinal> toLongOrdinal() {
        return Optional.of(this);
    }
}
