package io.github.sob1234509876.oc.engine.event;

import io.github.sob1234509876.oc.engine.AbstractOcParent;
import io.github.sob1234509876.oc.engine.api.event.EventFactory;
import lombok.NonNull;

import java.util.List;

public class OcContentUpdateEventFactory extends AbstractOcParent implements EventFactory {

    @NonNull
    public OcContentUpdateEvent create(@NonNull List<?> updated) {
        var e = new OcContentUpdateEvent(updated);
        e.setApplicationContext(getApplicationContext());

        return e;
    }

}
