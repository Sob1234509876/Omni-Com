package io.github.sob1234509876.oc.googology.fs;

import io.github.sob1234509876.oc.engine.api.googology.FundamentalSequence;
import io.github.sob1234509876.oc.engine.api.googology.Ordinal;
import io.github.sob1234509876.oc.googology.fs.mountain.MountainExpander;
import io.github.sob1234509876.oc.googology.ordinal.transfinite.ZySequenceOrdinal;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class ZySequenceFs implements FundamentalSequence {

    @NonNull
    List<@NonNull Integer> sequence;

    @NonNull
    MountainExpander.MountainGraph internalGraph = new MountainExpander.MountainGraph();

    @Override
    public @NonNull Ordinal apply(int value) {

        initialize();
        MountainExpander.expand(internalGraph,
                value,
                true,
                false,
                true);
        var seq = internalGraph.getValues()
                .get(0);

        return new ZySequenceOrdinal(seq);
    }

    private void initialize() {
        internalGraph.setLegs(new LinkedList<>());
        internalGraph.setValues(new LinkedList<>(Collections.singleton(
                sequence
        )));
    }

}
