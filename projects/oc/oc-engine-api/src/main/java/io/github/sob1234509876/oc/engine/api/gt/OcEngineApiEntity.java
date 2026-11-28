package io.github.sob1234509876.oc.engine.api.gt;

import io.github.sob1234509876.oc.collection.OccObservableList;
import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiTypeAncestor;
import io.github.sob1234509876.oc.engine.api.gt.production.OcEngineApiBuilder;
import io.github.sob1234509876.oc.engine.api.gt.production.OcEngineApiFactory;
import io.github.sob1234509876.oc.engine.api.gt.turret.OcEngineAntiBullet;
import io.github.sob1234509876.oc.engine.api.gt.turret.OcEngineApiTurret;
import io.github.sob1234509876.oc.engine.api.render.OcEngineApiRender;
import io.github.sob1234509876.oc.googology.NaturalNumber;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.Optional;

public interface OcEngineApiEntity extends OcEngineApiTypeAncestor {

    @NonNull
    Optional<@Nullable URL> getTexture();

    @NonNull
    OcEngineApiRender getRender();

    @NonNull
    EntityType getEntityType();

    double getMass();

    double getRadius();

    @NonNull
    NaturalNumber getSize();

    double getAccelerationSpeed();

    double getDecelerationSpeed();

    double getMaxSpeed();

    @NonNull
    NaturalNumber getMaxHealth();

    @NonNull
    NaturalNumber getHealthRegenerationRate();

    @NonNull
    NaturalNumber getMaxShield();

    @NonNull
    NaturalNumber getShieldRegenerationRate();

    @NonNull
    OccObservableList<@NonNull OcEngineApiFactory> getFactories();

    @NonNull
    OccObservableList<@NonNull OcEngineApiCarrier> getCarriers();

    @NonNull
    OccObservableList<@NonNull OcEngineApiInventory> getInventories();

    @NonNull
    OccObservableList<@NonNull OcEngineApiPortal> getPortals();

    @NonNull
    OccObservableList<@NonNull OcEngineApiBuilder> getBuilders();

    @NonNull
    OccObservableList<@NonNull OcEngineApiTurret> getTurrets();

    @NonNull
    OccObservableList<@NonNull OcEngineAntiBullet> getAntiBullet();

    @NonNull
    OccObservableList<@NonNull OcEngineApiEntityTag> getTags();
}
