package io.github.sob1234509876.oc.engine.api.gt;

import io.github.sob1234509876.oc.collection.OccObservableList;
import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiTypeAncestor;
import io.github.sob1234509876.oc.googology.NaturalNumber;
import lombok.NonNull;

public interface OcEngineApiInventory extends OcEngineApiTypeAncestor {

    @NonNull
    OccObservableList<@NonNull OcEngineApiItemTag> getItemWhiteList();

    @NonNull
    OccObservableList<@NonNull OcEngineApiItemTag> getItemBlackList();

    boolean isInputBus();

    boolean isOutputBus();

    @NonNull
    NaturalNumber getSize();

}
