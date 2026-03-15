package io.github.sob1234509876.oc.engine.api.type;

import io.github.sob1234509876.oc.engine.api.Parent;
import io.github.sob1234509876.oc.engine.api.ui.Position;
import lombok.NonNull;

public interface Portal extends Parent {

    @NonNull
    Area getDestination();

    @NonNull
    Position getDestinationPosition();
}
