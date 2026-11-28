package io.github.sob1234509876.oc.engine.api.gt.turret;

import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiTypeAncestor;
import io.github.sob1234509876.oc.engine.api.render.OcEngineApiRender;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.Optional;

public interface OcEngineApiTurret extends OcEngineApiTypeAncestor {

    @NonNull
    Optional<@Nullable URL> getTexture();

    @NonNull
    OcEngineApiRender getRender();

    int getTicksPerAttack();

    @NonNull
    OcEngineApiBullet getBullet();

}
