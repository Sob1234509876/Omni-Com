package io.github.sob1234509876.oc.engine.api.type;

import io.github.sob1234509876.oc.engine.api.Parent;
import io.github.sob1234509876.oc.engine.api.event.EventListener;
import lombok.NonNull;

public interface Portal extends EventListener, Parent {

    @NonNull
    Area getDestination();
}
