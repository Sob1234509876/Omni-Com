package io.github.sob1234509876.oc.engine.dao.collection;

import io.github.sob1234509876.oc.engine.api.collection.ParentMap;
import io.github.sob1234509876.oc.engine.api.type.Area;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class OcAreaMap extends OcDaoMap<@NonNull UUID, @NonNull Area> implements ParentMap<@NonNull UUID, @NonNull Area> {
}
