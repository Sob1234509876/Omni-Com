package io.github.sob1234509876.oc.engine.api.gt.turret;

import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiTypeAncestor;
import io.github.sob1234509876.oc.engine.api.render.OcEngineApiRender;
import io.github.sob1234509876.oc.googology.NaturalNumber;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;

public interface OcEngineAntiBullet extends OcEngineApiTypeAncestor {

    double getRange();

    @Nullable URL getTexture();

    @NonNull
    OcEngineApiRender getRender();

    @NonNull
    NaturalNumber getMaxPower();

    @NonNull
    NaturalNumber getPowerRegenerateRate();

}
