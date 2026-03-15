package io.github.sob1234509876.oc.engine.dao.collection;

import io.github.sob1234509876.oc.engine.api.collection.ParentMap;
import io.github.sob1234509876.oc.engine.api.type.Player;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class OcPlayerMap extends OcDaoMap<@NonNull UUID, @NonNull Player> implements ParentMap<@NonNull UUID, @NonNull Player> {
}
