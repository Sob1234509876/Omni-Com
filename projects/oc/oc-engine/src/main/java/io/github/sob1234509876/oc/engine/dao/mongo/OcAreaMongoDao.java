package io.github.sob1234509876.oc.engine.dao.mongo;

import io.github.sob1234509876.oc.engine.api.type.Area;
import io.github.sob1234509876.oc.engine.dao.repository.OcAreaCrudRepository;
import io.github.sob1234509876.oc.engine.misc.OcPair;
import lombok.NonNull;

import java.util.UUID;

public class OcAreaMongoDao extends OcMongoDao<@NonNull OcPair<@NonNull UUID, @NonNull Area>, @NonNull UUID>
        implements OcAreaCrudRepository {
}
