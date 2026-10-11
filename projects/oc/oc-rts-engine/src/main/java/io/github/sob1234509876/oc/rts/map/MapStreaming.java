package io.github.sob1234509876.oc.rts.map;

import io.github.sob1234509876.oc.rts.core.ChunkPos;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Vec2;

import java.util.Set;

/**
 * Controls which part of the world is kept in memory.
 *
 * <p>Without a GUI there is no camera, so the presentation layer (or a scenario) names the point of
 * interest itself. The engine then keeps a square of chunks around it loaded and drops the rest, which
 * is what allows an effectively endless map.
 */
public interface MapStreaming {

    /** Points the streaming at a world position, loading chunks around it and unloading the rest. */
    void focus(Vec2 center, int radiusChunks);

    /** Points the streaming at a tile, using the given radius in chunks. */
    default void focus(GridPos tile, int radiusChunks) {
        focus(tile.center(), radiusChunks);
    }

    /** Current point of interest. */
    Vec2 focus();

    /** Radius in chunks that is kept loaded around {@link #focus()}. */
    int radiusChunks();

    /** Chunks that are currently held in memory. */
    Set<ChunkPos> loadedChunks();

    /** Loads the chunks covering the given tile range immediately, ignoring the focus radius. */
    void loadArea(GridPos min, GridPos max);

    /** Drops the given chunk from memory; it will be generated or reloaded on the next access. */
    void unload(ChunkPos chunk);
}
