package io.github.sob1234509876.oc.engine.api;

import lombok.NonNull;

import java.io.File;
import java.util.List;

public interface ResourceManager {

    @NonNull
    List<@NonNull File> getFiles();

    @NonNull
    List<@NonNull Plugin> getPlugins();

}
