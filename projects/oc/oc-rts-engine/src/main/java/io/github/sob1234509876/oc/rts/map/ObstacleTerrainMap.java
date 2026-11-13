package io.github.sob1234509876.oc.rts.map;

import io.github.sob1234509876.oc.rts.content.TerrainType;
import io.github.sob1234509876.oc.rts.core.ChunkPos;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.TileRect;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A terrain view in which building footprints are obstacles.
 *
 * <p>The plain {@link TerrainMap} answers only about terrain, so before this class existed a unit could
 * plan a route straight through a warehouse and movement let it slide across the footprint, because the
 * tiles of a building are ordinary passable ground. The engine therefore serves pathfinding and stepping
 * this decorated view of the same map: {@link #isPassable} additionally consults a block predicate -
 * the dimension's set of tiles covered by a living, non-gateway building - and refuses those tiles.
 *
 * <p>{@link #isBuildable} deliberately stays terrain-only. Placement legality ({@code canPlace}) already
 * tests overlap against the living entities itself, including the "ignore the building being replaced"
 * case of an in-place upgrade, which a flat occupancy set cannot express; duplicating the rule here would
 * make upgrades impossible.
 *
 * <p>Everything else is delegated untouched, so a chunk generated through this view is byte-identical to
 * one generated through the raw map.
 */
public final class ObstacleTerrainMap implements TerrainMap {

    private final TerrainMap delegate;
    private final Predicate<GridPos> blocked;

    /**
     * Creates the decorated view.
     *
     * @param delegate the terrain the tiles and chunk bookkeeping come from
     * @param blocked  whether a tile is covered by a living, non-gateway building; must be a pure query
     */
    public ObstacleTerrainMap(TerrainMap delegate, Predicate<GridPos> blocked) {
        this.delegate = delegate;
        this.blocked = blocked;
    }

    /** Whether the tile is walkable: passable terrain that no living building covers. */
    @Override
    public boolean isPassable(int x, int y) {
        return delegate.isPassable(x, y) && !blocked.test(new GridPos(x, y));
    }

    @Override
    public int chunkSize() {
        return delegate.chunkSize();
    }

    @Override
    public TerrainType terrainAt(int x, int y) {
        return delegate.terrainAt(x, y);
    }

    @Override
    public void setTerrain(GridPos tile, TerrainType terrain) {
        delegate.setTerrain(tile, terrain);
    }

    @Override
    public boolean isBuildable(GridPos tile) {
        return delegate.isBuildable(tile);
    }

    @Override
    public float moveCost(GridPos tile) {
        return delegate.moveCost(tile);
    }

    @Override
    public boolean isChunkLoaded(ChunkPos chunk) {
        return delegate.isChunkLoaded(chunk);
    }

    @Override
    public Set<ChunkPos> loadedChunks() {
        return delegate.loadedChunks();
    }

    @Override
    public Optional<TileRect> loadedBounds() {
        return delegate.loadedBounds();
    }

    @Override
    public String toString() {
        return "ObstacleTerrainMap{" + delegate + "}";
    }
}
