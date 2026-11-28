package io.github.sob1234509876.oc.engine.api.gt.production;

import io.github.sob1234509876.oc.collection.OccObservableList;
import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiTypeAncestor;
import io.github.sob1234509876.oc.engine.api.gt.OcEngineApiBuilding;
import io.github.sob1234509876.oc.engine.api.gt.OcEngineApiItemStack;
import lombok.NonNull;

public interface OcEngineApiBuildRecipe extends OcEngineApiTypeAncestor {

    @NonNull
    OccObservableList<@NonNull OcEngineApiBuilder> getRecipeUsers();

    @NonNull
    OccObservableList<@NonNull OcEngineApiItemStack> getInput();

    @NonNull
    OcEngineApiBuilding getOutput();

}
