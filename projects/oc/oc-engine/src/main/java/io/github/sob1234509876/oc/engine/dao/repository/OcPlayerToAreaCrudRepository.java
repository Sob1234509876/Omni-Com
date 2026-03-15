package io.github.sob1234509876.oc.engine.dao.repository;

import io.github.sob1234509876.oc.engine.api.type.Area;
import io.github.sob1234509876.oc.engine.api.type.Player;
import io.github.sob1234509876.oc.engine.misc.OcPair;
import lombok.NonNull;
import org.springframework.data.repository.CrudRepository;

public interface OcPlayerToAreaCrudRepository extends CrudRepository<@NonNull OcPair<@NonNull Player, @NonNull Area>,
        @NonNull Player> {
}
