package io.github.sob1234509876.oc.engine.api.gt.production;

import io.github.sob1234509876.oc.engine.api.gt.OcEngineApiInventoryInstance;
import lombok.NonNull;

public interface OcEngineApiBuildOrder {

    @NonNull
    OcEngineApiBuildRecipe getRecipe();

    long getX();

    long getY();

    @NonNull
    OcEngineApiInventoryInstance getOutput();

    @NonNull
    OcEngineApiInventoryInstance getInput();

}
