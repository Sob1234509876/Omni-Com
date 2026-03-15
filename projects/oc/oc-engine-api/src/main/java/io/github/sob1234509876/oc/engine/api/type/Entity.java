package io.github.sob1234509876.oc.engine.api.type;

import io.github.sob1234509876.oc.engine.api.Ordinal;
import io.github.sob1234509876.oc.engine.api.Parent;
import lombok.NonNull;

import java.net.URL;
import java.util.Optional;

public interface Entity extends Parent {

    @NonNull
    Optional<URL> getTexture();

    @NonNull
    Optional<Ordinal> getHp();

    @NonNull
    Optional<Double> getSpeed();
}
