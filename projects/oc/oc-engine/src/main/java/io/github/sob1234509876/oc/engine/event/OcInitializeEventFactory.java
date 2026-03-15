package io.github.sob1234509876.oc.engine.event;

import io.github.sob1234509876.oc.engine.AbstractOcParent;
import io.github.sob1234509876.oc.engine.api.event.EventFactory;
import lombok.NonNull;

public class OcInitializeEventFactory extends AbstractOcParent implements EventFactory {

    @NonNull
    public OcInitializeEvent create() {
        return new OcInitializeEvent();
    }

}
