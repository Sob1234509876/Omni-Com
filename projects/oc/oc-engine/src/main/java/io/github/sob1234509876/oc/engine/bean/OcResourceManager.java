package io.github.sob1234509876.oc.engine.bean;

import io.github.sob1234509876.oc.engine.AbstractOcParent;
import io.github.sob1234509876.oc.engine.api.ResourceManager;
import io.github.sob1234509876.oc.engine.rm.collection.OcResourceManagerClassBusMap;
import io.github.sob1234509876.oc.engine.rm.collection.OcResourceManagerFileBusList;
import io.github.sob1234509876.oc.engine.rm.collection.OcResourceManagerPluginBusList;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class OcResourceManager extends AbstractOcParent implements ResourceManager {

    @NonNull
    OcResourceManagerFileBusList files;

    @NonNull
    OcResourceManagerClassBusMap classes;

    @NonNull
    OcResourceManagerPluginBusList plugins;
}
