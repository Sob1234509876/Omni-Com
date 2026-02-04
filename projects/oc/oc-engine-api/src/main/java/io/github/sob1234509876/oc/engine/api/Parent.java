package io.github.sob1234509876.oc.engine.api;

import lombok.NonNull;

import java.net.URL;
import java.util.Optional;

public interface Parent {

    @NonNull
    String getType();

    @NonNull
    String getLocalizedName();

    @NonNull
    Optional<URL> getIcon();

    @NonNull
    Tag getTag();
}
