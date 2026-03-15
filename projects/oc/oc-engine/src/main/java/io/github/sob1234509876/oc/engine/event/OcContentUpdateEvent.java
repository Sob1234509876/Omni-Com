package io.github.sob1234509876.oc.engine.event;

import io.github.sob1234509876.oc.engine.AbstractOcParent;
import io.github.sob1234509876.oc.engine.api.event.Event;
import lombok.*;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class OcContentUpdateEvent extends AbstractOcParent implements Event {

    @NonNull
    List<?> updatedObjects;

}
