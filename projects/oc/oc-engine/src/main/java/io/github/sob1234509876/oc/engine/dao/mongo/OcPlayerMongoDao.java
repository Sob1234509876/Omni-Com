package io.github.sob1234509876.oc.engine.dao.mongo;

import io.github.sob1234509876.oc.engine.api.type.Player;
import io.github.sob1234509876.oc.engine.dao.repository.OcPlayerCrudRepository;
import io.github.sob1234509876.oc.engine.misc.OcPair;
import lombok.NonNull;

import java.util.UUID;

public class OcPlayerMongoDao extends OcMongoDao<@NonNull OcPair<@NonNull UUID, @NonNull Player>, @NonNull UUID>
        implements OcPlayerCrudRepository {
}
