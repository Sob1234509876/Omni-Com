package io.github.sob1234509876.oc.engine.dao.collection;

import io.github.sob1234509876.oc.engine.api.collection.ParentMap;
import io.github.sob1234509876.oc.engine.api.type.Area;
import io.github.sob1234509876.oc.engine.api.type.Player;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class OcPlayerToAreaMap extends OcDaoMap<@NonNull Player, @NonNull Area> implements ParentMap<@NonNull Player, @NonNull Area> {
}
