package io.github.sob1234509876.oc.engine.api.task;

import lombok.NonNull;

public class OcEngineApiEmptyTask implements OcEngineApiTask {

    @NonNull
    public static final OcEngineApiEmptyTask INSTANCE = new OcEngineApiEmptyTask();

    @NonNull
    public static OcEngineApiEmptyTask getInstance() {
        return INSTANCE;
    }

    private OcEngineApiEmptyTask() {
    }

    @Override
    public void run() {
    }

    @Override
    public String toString() {
        return "";
    }
}
