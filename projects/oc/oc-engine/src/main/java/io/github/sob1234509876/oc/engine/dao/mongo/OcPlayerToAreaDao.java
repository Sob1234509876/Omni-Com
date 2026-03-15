package io.github.sob1234509876.oc.engine.dao.mongo;

import io.github.sob1234509876.oc.engine.api.type.Area;
import io.github.sob1234509876.oc.engine.api.type.Player;
import io.github.sob1234509876.oc.engine.dao.repository.OcPlayerToAreaCrudRepository;
import io.github.sob1234509876.oc.engine.misc.OcPair;
import lombok.NonNull;

public class OcPlayerToAreaDao extends OcMongoDao<@NonNull OcPair<@NonNull Player, @NonNull Area>, @NonNull Player>
        implements OcPlayerToAreaCrudRepository {
}
