package io.github.sob1234509876.oc.rts.map;

import io.github.sob1234509876.oc.rts.content.TerrainType;
import io.github.sob1234509876.oc.rts.core.ChunkPos;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.TileRect;

import java.util.Optional;
import java.util.Set;

/**
 * The terrain of the world, addressed tile by tile.
 *
 * <p>Implementations are chunked: tiles are produced on demand and only the chunks near the focus of
 * interest are held in memory, so a map can be much larger than what fits in RAM. Every query on this
 * interface therefore has to be able to trigger the generation or loading of a chunk.
 */
public interface TerrainMap {

    /** Side length of a chunk in tiles; chunks are square. */
    int chunkSize();

    /** Terrain of a tile; coordinates may be negative. */
    TerrainType terrainAt(int x, int y);

    default TerrainType terrainAt(GridPos tile) {
        return terrainAt(tile.x(), tile.y());
    }

    /** Changes the terrain of a single tile, for example when a road is built. */
    void setTerrain(GridPos tile, TerrainType terrain);

    default boolean isPassable(GridPos tile) {
        return terrainAt(tile.x(), tile.y()).passable();
    }

    default boolean isPassable(int x, int y) {
        return terrainAt(x, y).passable();
    }

    /** Whether a building may be placed on that tile: passable terrain that allows construction. */
    default boolean isBuildable(GridPos tile) {
        TerrainType terrain = terrainAt(tile.x(), tile.y());
        return terrain.passable() && terrain.buildable();
    }

    /** Relative cost of entering the tile; {@code 1} is open ground, higher is slower. */
    default float moveCost(GridPos tile) {
        return terrainAt(tile.x(), tile.y()).moveCost();
    }

    boolean isChunkLoaded(ChunkPos chunk);

    /** Chunks currently held in memory. */
    Set<ChunkPos> loadedChunks();

    /** Tile range covered by all loaded chunks, empty when nothing is loaded. */
    Optional<TileRect> loadedBounds();

    /** Number of chunks kept in memory, for statistics and tests. */
    default int loadedChunkCount() {
        return loadedChunks().size();
    }
}
