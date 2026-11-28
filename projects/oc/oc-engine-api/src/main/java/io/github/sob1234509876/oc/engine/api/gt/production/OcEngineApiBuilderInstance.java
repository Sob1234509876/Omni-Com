package io.github.sob1234509876.oc.engine.api.gt.production;

import io.github.sob1234509876.oc.collection.OccObservableQueue;
import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiInstanceAncestor;
import lombok.NonNull;
import org.jetbrains.annotations.Range;

public interface OcEngineApiBuilderInstance extends OcEngineApiInstanceAncestor<@NonNull OcEngineApiBuilder> {

    @Range(from = 0, to = 1)
    double getRotation();

    void setRotation(@Range(from = 0, to = 1) double rotation);

    @NonNull
    OccObservableQueue<@NonNull OcEngineApiBuildOrder> getOrders();


}
