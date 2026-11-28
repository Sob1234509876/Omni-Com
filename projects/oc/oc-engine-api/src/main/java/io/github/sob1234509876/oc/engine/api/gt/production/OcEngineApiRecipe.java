package io.github.sob1234509876.oc.engine.api.gt.production;

import io.github.sob1234509876.oc.collection.OccObservableList;
import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiTypeAncestor;
import io.github.sob1234509876.oc.engine.api.gt.OcEngineApiItemStack;
import lombok.NonNull;

public interface OcEngineApiRecipe extends OcEngineApiTypeAncestor {

    @NonNull
    OccObservableList<@NonNull OcEngineApiFactory> getRecipeUsers();

    @NonNull
    OccObservableList<@NonNull OcEngineApiItemStack> getInput();

    @NonNull
    OccObservableList<@NonNull OcEngineApiItemStack> getOutput();

}
