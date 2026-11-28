package io.github.sob1234509876.oc.engine.api.gt.turret;

import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiInstanceAncestor;
import io.github.sob1234509876.oc.googology.NaturalNumber;
import lombok.NonNull;

public interface OcEngineAntiBulletInstance extends OcEngineApiInstanceAncestor<@NonNull OcEngineAntiBullet> {

    @NonNull
    NaturalNumber getCurrentPower();

    void setCurrentPower(@NonNull NaturalNumber currentPower);

}
