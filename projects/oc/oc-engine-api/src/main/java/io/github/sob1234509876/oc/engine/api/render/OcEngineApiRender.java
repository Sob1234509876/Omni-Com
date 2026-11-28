package io.github.sob1234509876.oc.engine.api.render;

import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

public interface OcEngineApiRender {

    @NonNull
    OcEngineApiRenderInstruction getInstruction(double x, double y, @Nullable Object payload);

}
