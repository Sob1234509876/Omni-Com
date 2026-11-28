package io.github.sob1234509876.oc.engine.api.gt;

import io.github.sob1234509876.oc.collection.OccObservableList;
import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiInstanceAncestor;
import io.github.sob1234509876.oc.engine.api.gt.production.OcEngineApiBuilderInstance;
import io.github.sob1234509876.oc.engine.api.gt.production.OcEngineApiFactoryInstance;
import io.github.sob1234509876.oc.engine.api.gt.turret.OcEngineAntiBulletInstance;
import io.github.sob1234509876.oc.engine.api.gt.turret.OcEngineApiTurretInstance;
import io.github.sob1234509876.oc.googology.NaturalNumber;
import lombok.NonNull;
import org.jetbrains.annotations.Range;

public interface OcEngineApiEntityInstance extends OcEngineApiInstanceAncestor<@NonNull OcEngineApiEntity> {

    double getX();

    double getY();

    void setX(double x);

    void setY(double y);

    @Range(from = 0, to = 1)
    double getRotation();

    void setRotation(@Range(from = 0, to = 1) double rotation);

    @NonNull
    NaturalNumber getHealth();

    void setHealth(@NonNull NaturalNumber health);

    @NonNull
    NaturalNumber getShield();

    void setShield(@NonNull NaturalNumber shield);

    @NonNull
    OccObservableList<@NonNull OcEngineApiFactoryInstance> getFactories();

    @NonNull
    OccObservableList<@NonNull OcEngineApiCarrierInstance> getCarriers();

    @NonNull
    OccObservableList<@NonNull OcEngineApiInventoryInstance> getInventories();

    @NonNull
    OccObservableList<@NonNull OcEngineApiPortalInstance> getPortals();

    @NonNull
    OccObservableList<@NonNull OcEngineApiBuilderInstance> getBuilders();

    @NonNull
    OccObservableList<@NonNull OcEngineApiTurretInstance> getTurrets();

    @NonNull
    OccObservableList<@NonNull OcEngineAntiBulletInstance> getAntiBullet();
}
