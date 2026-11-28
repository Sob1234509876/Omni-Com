package io.github.sob1234509876.oc.engine.api.gt.logistic;

import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiTypeAncestor;
import io.github.sob1234509876.oc.googology.NaturalNumber;
import lombok.NonNull;

public interface OcEngineApiPipe extends OcEngineApiTypeAncestor {

    @NonNull
    NaturalNumber getMaxFlow();

    @NonNull
    NaturalNumber getFlowRegeneration();

}
