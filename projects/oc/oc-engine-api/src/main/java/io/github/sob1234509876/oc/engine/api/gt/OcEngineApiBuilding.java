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

public interface OcEngineApiBuilding extends OcEngineApiTypeAncestor {

    int getWidth();

    int getHeight();

    @NonNull
    NaturalNumber getMaxHealth();

    @NonNull
    NaturalNumber getHealthRegenerateRate();

    @NonNull
    NaturalNumber getMaxShield();

    @NonNull
    NaturalNumber getShieldRegenerateRate();

    @NonNull
    Optional<@Nullable URL> getTexture();

    @NonNull
    OcEngineApiRender getRender();

    @NonNull
    OccObservableList<@NonNull OcEngineApiFactory> getFactories();

    @NonNull
    OccObservableList<@NonNull OcEngineApiPortal> getPortals();

    @NonNull
    OccObservableList<@NonNull OcEngineApiInventory> getInventories();

    @NonNull
    OccObservableList<@NonNull OcEngineApiCarrier> getCarriers();

    @NonNull
    OccObservableList<@NonNull OcEngineApiTurret> getTurrets();

    @NonNull
    OccObservableList<@NonNull OcEngineApiBuilder> getBuilders();

    @NonNull
    OccObservableList<@NonNull OcEngineAntiBullet> getAntiBullet();
}
