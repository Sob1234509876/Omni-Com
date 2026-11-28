package io.github.sob1234509876.oc.engine.api.gt;

import io.github.sob1234509876.oc.collection.OccObservableList;
import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiInstanceAncestor;
import io.github.sob1234509876.oc.engine.api.gt.production.OcEngineApiBuilderInstance;
import io.github.sob1234509876.oc.engine.api.gt.production.OcEngineApiFactoryInstance;
import io.github.sob1234509876.oc.engine.api.gt.turret.OcEngineAntiBulletInstance;
import io.github.sob1234509876.oc.engine.api.gt.turret.OcEngineApiTurretInstance;
import io.github.sob1234509876.oc.googology.NaturalNumber;
import lombok.NonNull;

public interface OcEngineApiBuildingInstance extends OcEngineApiInstanceAncestor<@NonNull OcEngineApiBuilding> {

    long getX();

    long getY();

    void setX(long x);

    void setY(long y);

    @NonNull
    NaturalNumber getHealth();

    void setHealth(@NonNull NaturalNumber health);

    @NonNull
    NaturalNumber getShield();

    void setShield(@NonNull NaturalNumber shield);

    @NonNull
    OccObservableList<@NonNull OcEngineApiFactoryInstance> getFactories();

    @NonNull
    OccObservableList<@NonNull OcEngineApiPortalInstance> getPortals();

    @NonNull
    OccObservableList<@NonNull OcEngineApiInventoryInstance> getInventories();

    @NonNull
    OccObservableList<@NonNull OcEngineApiCarrierInstance> getCarriers();

    @NonNull
    OccObservableList<@NonNull OcEngineApiTurretInstance> getTurrets();

    @NonNull
    OccObservableList<@NonNull OcEngineApiBuilderInstance> getBuilders();

    @NonNull
    OccObservableList<@NonNull OcEngineAntiBulletInstance> getAntiBullet();

}
