package io.github.sob1234509876.oc.engine.api;

import io.github.sob1234509876.oc.engine.api.collection.BusList;
import io.github.sob1234509876.oc.engine.api.collection.BusMap;
import lombok.NonNull;

import java.io.File;

public interface ResourceManager extends Parent {

    @NonNull
    BusList<@NonNull File> getFiles();

    @NonNull
    BusMap<@NonNull String, @NonNull Class<?>> getClasses();

    @NonNull
    BusList<@NonNull Plugin> getPlugins();

}
