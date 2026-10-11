package io.github.sob1234509876.oc.rts.entity;

import io.github.sob1234509876.oc.rts.core.TileRect;
import io.github.sob1234509876.oc.rts.core.Vec2;

import java.util.List;
import java.util.function.Predicate;

/**
 * Broad phase spatial queries over the entities of the world.
 *
 * <p>Neighbour searches are the hot path of separation, formations and logistics, so they must not
 * scan every entity. Implementations bucket entities into grid cells and are <em>not</em> aware of
 * position changes: the movement system has to call {@link #update(Entity)} after moving an entity.
 */
public interface SpatialIndex {

    void insert(Entity entity);

    void remove(Entity entity);

    /** Re-buckets an entity whose position changed. */
    void update(Entity entity);

    /** Entities whose center is within the radius, including the entity at the center if any. */
    List<Entity> inRadius(Vec2 center, float radius);

    /** Entities whose center is within the radius and which are instances of the given type. */
    <T extends Entity> List<T> inRadius(Vec2 center, float radius, Class<T> type);

    /** Entities whose occupied tiles intersect the rectangle. */
    List<Entity> inRect(TileRect rect);

    /** Closest entity matching the filter within the radius, or {@code null} when there is none. */
    Entity nearest(Vec2 center, float radius, Predicate<Entity> filter);

    /** Number of indexed entities. */
    int size();

    void clear();
}
