package io.github.sob1234509876.oc.engine.api.gt;

import io.github.sob1234509876.oc.collection.OccObservableList;
import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiInstanceAncestor;
import io.github.sob1234509876.oc.googology.NaturalNumber;
import lombok.NonNull;

public interface OcEngineApiInventoryInstance extends OcEngineApiInstanceAncestor<@NonNull OcEngineApiInventory> {

    @NonNull
    OccObservableList<@NonNull OcEngineApiItemStack> getItems();

    @NonNull
    NaturalNumber getUsedSpace();

    void setUsedSpace(@NonNull NaturalNumber usedSpace);

}
