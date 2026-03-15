package io.github.sob1234509876.oc.engine.dao.repository;

import io.github.sob1234509876.oc.engine.api.type.Player;
import io.github.sob1234509876.oc.engine.misc.OcPair;
import lombok.NonNull;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface OcPlayerCrudRepository extends CrudRepository<@NonNull OcPair<@NonNull UUID, @NonNull Player>,
        @NonNull UUID> {
}
