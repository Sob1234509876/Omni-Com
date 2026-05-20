package io.github.sob1234509876.oc.googology.ordinal.transfinite;

import io.github.sob1234509876.oc.engine.api.googology.LimitOrdinal;
import io.github.sob1234509876.oc.engine.api.googology.Ordinal;
import io.github.sob1234509876.oc.engine.api.googology.SuccessorOrdinal;
import io.github.sob1234509876.oc.engine.api.googology.TransfiniteOrdinal;
import io.github.sob1234509876.oc.googology.fs.ZySequenceFs;
import io.github.sob1234509876.oc.googology.fs.mountain.MountainExpander;
import io.github.sob1234509876.oc.googology.impl.ImplLimitOrdinal;
import io.github.sob1234509876.oc.googology.impl.ImplSuccessorOrdinal;
import io.github.sob1234509876.oc.googology.impl.ImplTransfiniteOrdinal;
import io.github.sob1234509876.oc.googology.optional.finite.OptionalSghOrdinal;
import io.github.sob1234509876.oc.googology.optional.transfinite.OptionalZySequenceOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.Constants;
import io.github.sob1234509876.oc.googology.ordinal.finite.hierarchy.SghOrdinal;
import io.github.sob1234509876.oc.googology.support.Ordinals;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.*;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class ZySequenceOrdinal implements Ordinal,
        OptionalZySequenceOrdinal {

    @NonNull
    List<@NonNull Integer> sequence;

    @NonNull
    MountainExpander.MountainGraph internalGraph = new MountainExpander.MountainGraph();

    @Override
    public @NonNull Optional<ZySequenceOrdinal> toZySequenceOrdinal() {
        return Optional.of(this);
    }

    @Override
    public @NonNull Optional<SuccessorOrdinal> toSuccessorOrdinal() {
        if (isLimitOrdinal())
            return Optional.empty();

        var origin = new ZySequenceOrdinal(new LinkedList<>(sequence.subList(0, sequence.size() - 1)));
        return Optional.of(new ImplSuccessorOrdinal(this, origin));
    }

    @Override
    public @NonNull Optional<LimitOrdinal> toLimitOrdinal() {
        return isLimitOrdinal() ?
                Optional.of(
                        new ImplLimitOrdinal(
                                this,
                                new ZySequenceFs(
                                        new LinkedList<>(sequence)
                                ))) :
                Optional.empty();
    }

    @Override
    public @NonNull Optional<TransfiniteOrdinal> toTransfiniteOrdinal() {
        return isTransfinite() ?
                Optional.of(new ImplTransfiniteOrdinal(this)) :
                Optional.empty();
    }

    @Override
    public int compareTo(@NonNull Ordinal o) {

        if (Ordinals.isZeroOrdinal(o))
            return Ordinals.isZeroOrdinal(this) ? 0 : 1;

        var isT = isTransfinite();
        var oIsT = Ordinals.isTransfiniteOrdinal(o);

        if (oIsT && !isT)
            return -1;

        if (!oIsT && isT)
            return 1;

        var res1 = o.optional(OptionalSghOrdinal.class)
                .flatMap(OptionalSghOrdinal::toSghOrdinal)
                .map(sgh -> sgh.compareTo(new SghOrdinal(Constants.Transfinite
                        .FTO,
                        sequence.size())));
        if (res1.isPresent())
            return res1.get();

        var res2 = o.optional(OptionalZySequenceOrdinal.class)
                .flatMap(OptionalZySequenceOrdinal::toZySequenceOrdinal)
                .map(zy -> Arrays.compare(sequence.toArray(new Integer[0]),
                        zy.getSequence()
                                .toArray(new Integer[0])));
        return res2.orElseGet(() -> -o.compareTo(this));
    }

    private boolean isTransfinite() {

        if (sequence.size() < 2)
            return false;

        int currentLowest = sequence.get(0);
        for (var i = 1; i < sequence.size(); i++) {

            int value = sequence.get(i);

            if (value < currentLowest)
                currentLowest = value;

            if (value > currentLowest)
                return true;
        }

        return false;
    }

    private boolean isLimitOrdinal() {
        loadInternalGraph();
        return !MountainExpander.isSuccessor(internalGraph,
                true,
                false,
                true);
    }

    private void loadInternalGraph() {
        internalGraph.setLegs(new LinkedList<>());
        internalGraph.setValues(new LinkedList<>(Collections.singleton(
                new LinkedList<>(sequence)
        )));
    }

    @NonNull
    public String toString() {
        return "0-Y(" + Arrays.toString(sequence.toArray()) + ")";
    }
}
