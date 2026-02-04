package io.github.sob1234509876.oc.engine.api.type;

import io.github.sob1234509876.oc.engine.api.ArithmeticNumber;
import io.github.sob1234509876.oc.engine.api.Parent;
import io.github.sob1234509876.oc.engine.api.event.EventListener;
import lombok.NonNull;

import java.net.URL;
import java.util.Optional;

public interface Entity extends EventListener, Parent {

    @NonNull
    Optional<URL> getTexture();

    @NonNull
    Optional<ArithmeticNumber> getHp();

    @NonNull
    Optional<Number> getSpeed();
}
