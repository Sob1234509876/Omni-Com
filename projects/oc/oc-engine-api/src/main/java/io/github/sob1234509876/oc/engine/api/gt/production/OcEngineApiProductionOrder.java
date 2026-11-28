package io.github.sob1234509876.oc.engine.api.gt.production;

import io.github.sob1234509876.oc.engine.api.gt.OcEngineApiInventoryInstance;
import io.github.sob1234509876.oc.googology.NaturalNumber;
import lombok.NonNull;

public interface OcEngineApiProductionOrder {

    @NonNull
    OcEngineApiRecipe getRecipe();

    @NonNull
    NaturalNumber getAmount();

    @NonNull
    OcEngineApiInventoryInstance getOutput();

    @NonNull
    OcEngineApiInventoryInstance getInput();

}
