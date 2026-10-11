package io.github.sob1234509876.oc.rts.map;

import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Vec2;

import java.util.Optional;

/**
 * Finds walkable routes across the {@link TerrainMap}.
 *
 * <p>Searches are deterministic: for equal start, goal and options the same path is returned, which
 * keeps replays identical.
 */
public interface PathFinder {

    /** Searches with {@link PathOptions#DEFAULT}. */
    default Optional<Path> findPath(GridPos start, GridPos goal) {
        return findPath(start, goal, PathOptions.DEFAULT);
    }

    /**
     * Searches for a route. Empty when the goal is unreachable, impassable or the search ran out of
     * {@link PathOptions#maxExpansions()}.
     */
    Optional<Path> findPath(GridPos start, GridPos goal, PathOptions options);

    /** Whether a unit could walk in a straight line between the two positions. */
    boolean hasLineOfSight(Vec2 from, Vec2 to);

    /** Whether a route exists at all; callers that only need a yes or no answer should use this. */
    default boolean isReachable(GridPos start, GridPos goal) {
        return findPath(start, goal, PathOptions.FAST).isPresent();
    }
}
