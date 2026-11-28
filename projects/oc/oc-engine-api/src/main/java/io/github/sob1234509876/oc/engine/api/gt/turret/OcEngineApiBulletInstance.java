package io.github.sob1234509876.oc.engine.api.gt.turret;

import io.github.sob1234509876.oc.engine.api.ancestor.OcEngineApiInstanceAncestor;
import lombok.NonNull;
import org.jetbrains.annotations.Range;

public interface OcEngineApiBulletInstance extends OcEngineApiInstanceAncestor<@NonNull OcEngineApiBullet> {

    double getX();

    double getY();

    void setX(double x);

    void setY(double y);

    @Range(from = 0, to = 1)
    double getRotation();

    void setRotation(@Range(from = 0, to = 1) double rotation);

}
