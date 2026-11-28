package io.github.sob1234509876.oc.engine.api.gt.turret;

import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiTypeAncestor;
import io.github.sob1234509876.oc.engine.api.render.OcEngineApiRender;
import io.github.sob1234509876.oc.googology.NaturalNumber;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.Optional;

public interface OcEngineApiBullet extends OcEngineApiTypeAncestor {

    long getLifespan();

    double getSpeed();

    @NonNull
    Optional<@Nullable URL> getTexture();

    @NonNull
    OcEngineApiRender getRender();

    @NonNull
    NaturalNumber getDamage();

    @NonNull
    NaturalNumber getPowerNeededToDestroy();

}
