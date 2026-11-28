package io.github.sob1234509876.oc.engine.api;

import io.github.sob1234509876.oc.collection.OccObservableList;
import io.github.sob1234509876.oc.engine.api.gt.OcEngineApiBuildingInstance;
import io.github.sob1234509876.oc.engine.api.gt.OcEngineApiEntityInstance;
import io.github.sob1234509876.oc.engine.api.gt.logistic.OcEngineApiPipeInstance;
import lombok.NonNull;

public interface OcEngineApiChunk {

    long getCreationDate();

    long getX();

    long getY();

    int DEFAULT_WIDTH = 64;
    int DEFAULT_HEIGHT = 64;

    @NonNull
    OcEngineApiTile getTile(int x, int y);

    void setTile(int x, int y, @NonNull OcEngineApiTile tile);

    @NonNull
    OccObservableList<@NonNull OcEngineApiBuildingInstance> getBuildings();

    @NonNull
    OccObservableList<@NonNull OcEngineApiEntityInstance> getEntities();

    @NonNull
    OccObservableList<@NonNull OcEngineApiPipeInstance> getPipes();

}
