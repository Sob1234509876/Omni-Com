package io.github.sob1234509876.oc.engine.api.gt;

import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiInstanceAncestor;
import io.github.sob1234509876.oc.engine.api.OcEngineApiLocation;
import io.github.sob1234509876.oc.googology.NaturalNumber;
import lombok.NonNull;

public interface OcEngineApiPortalInstance extends OcEngineApiInstanceAncestor<@NonNull OcEngineApiPortal> {

    @NonNull
    OcEngineApiLocation getDestination();

    void setDestination(@NonNull OcEngineApiLocation destination);

    @NonNull
    NaturalNumber getFlow();

    void setFlow(@NonNull NaturalNumber flow);

}
