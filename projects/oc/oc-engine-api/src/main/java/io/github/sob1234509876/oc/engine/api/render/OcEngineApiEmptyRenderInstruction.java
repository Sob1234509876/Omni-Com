package io.github.sob1234509876.oc.engine.api.render;

import lombok.NonNull;

public class OcEngineApiEmptyRenderInstruction implements OcEngineApiRenderInstruction {

    @NonNull
    public static final OcEngineApiEmptyRenderInstruction INSTANCE = new OcEngineApiEmptyRenderInstruction();

    @NonNull
    public static OcEngineApiEmptyRenderInstruction getInstance() {
        return INSTANCE;
    }

    private OcEngineApiEmptyRenderInstruction() {
    }

    @Override
    public String toString() {
        return "";
    }
}
