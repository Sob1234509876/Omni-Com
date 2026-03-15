package io.github.sob1234509876.oc.engine.bean;

import io.github.sob1234509876.oc.engine.AbstractOcParent;
import io.github.sob1234509876.oc.engine.api.Context;
import io.github.sob1234509876.oc.engine.dao.collection.OcAreaMap;
import io.github.sob1234509876.oc.engine.dao.collection.OcPlayerMap;
import io.github.sob1234509876.oc.engine.dao.collection.OcPlayerToAreaMap;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class OcContext extends AbstractOcParent implements Context {

    @NonNull
    OcAreaMap areas;

    @NonNull
    OcPlayerMap players;

    @NonNull
    OcPlayerToAreaMap playerToAreas;
}
