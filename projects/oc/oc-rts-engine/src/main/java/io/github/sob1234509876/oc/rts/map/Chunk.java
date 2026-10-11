package io.github.sob1234509876.oc.rts.map;

import io.github.sob1234509876.oc.rts.content.TerrainType;
import io.github.sob1234509876.oc.rts.core.ChunkPos;
import io.github.sob1234509876.oc.rts.core.GridPos;

import java.util.Objects;

/**
 * One square block of terrain tiles, the unit in which the world is generated, stored and streamed.
 *
 * <p>Terrain lives in a flat array instead of a map keyed by position because every tile of a chunk is
 * materialised as soon as the chunk exists: the cost of touching a chunk is paid once, and afterwards a
 * lookup is a single array access, which is what makes path searches cheap enough to run every tick.
 *
 * <p>The chunk also carries a dirty flag. Terrain edits (roads, cleared forests, buildings) happen in
 * memory and only reach the {@link ChunkStore} when the owner flushes the chunk, so the store never
 * sees half-finished edits.
 */
public final class Chunk {

    private final ChunkPos pos;
    private final int size;
    private final TerrainType[] tiles;
    private boolean dirty;

    /**
     * Creates a chunk whose tiles are all null; the creator has to fill every tile before the chunk is
     * handed to a map, which is why only stores and generators use this constructor.
     */
    public Chunk(ChunkPos pos, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("chunk size must be positive, was " + size);
        }
        this.pos = Objects.requireNonNull(pos, "pos");
        this.size = size;
        this.tiles = new TerrainType[size * size];
    }

    /** Creates a chunk filled with the same terrain. */
    public static Chunk filled(ChunkPos pos, int size, TerrainType terrain) {
        Chunk chunk = new Chunk(pos, size);
        chunk.fill(terrain);
        return chunk;
    }

    public ChunkPos pos() {
        return pos;
    }

    /** Side length of this chunk in tiles. */
    public int size() {
        return size;
    }

    /**
     * Terrain of a tile inside this chunk.
     *
     * @throws IndexOutOfBoundsException when the coordinate does not belong to this chunk, because
     *         silently clamping would hide a coordinate conversion bug in the caller
     */
    public TerrainType terrainAt(int localX, int localY) {
        return tiles[offset(localX, localY)];
    }

    public void setTerrain(int localX, int localY, TerrainType terrain) {
        tiles[offset(localX, localY)] = Objects.requireNonNull(terrain, "terrain");
        dirty = true;
    }

    /** Terrain of a world tile that belongs to this chunk. */
    public TerrainType terrainAt(GridPos tile) {
        return terrainAt(Math.floorMod(tile.x(), size), Math.floorMod(tile.y(), size));
    }

    /** Whether this chunk contains the given world tile. */
    public boolean contains(GridPos tile) {
        return Math.floorDiv(tile.x(), size) == pos.x() && Math.floorDiv(tile.y(), size) == pos.y();
    }

    /** Whether this chunk contains the given world coordinate. */
    public boolean contains(int x, int y) {
        return Math.floorDiv(x, size) == pos.x() && Math.floorDiv(y, size) == pos.y();
    }

    /** Whether the chunk has been edited since the last {@link #markClean()}. */
    public boolean isDirty() {
        return dirty;
    }

    /** Records that the current contents have been persisted. */
    public void markClean() {
        dirty = false;
    }

    /** Marks the chunk as needing another save without changing any tile. */
    public void markDirty() {
        dirty = true;
    }

    /**
     * The backing tile array, in row major order with the index {@code localY * size + localX}.
     *
     * <p>Exposed without copying for bulk consumers such as renderers, serialisers and map analysis
     * tools. Callers must not keep a reference across a chunk reload.
     */
    public TerrainType[] rawTiles() {
        return tiles;
    }

    /** Replaces every tile of this chunk. */
    public void fill(TerrainType terrain) {
        Objects.requireNonNull(terrain, "terrain");
        java.util.Arrays.fill(tiles, terrain);
        dirty = true;
    }

    /** Number of tiles holding the given terrain, for map statistics and tests. */
    public int count(TerrainType terrain) {
        int count = 0;
        for (TerrainType tile : tiles) {
            if (terrain.equals(tile)) {
                count++;
            }
        }
        return count;
    }

    private int offset(int localX, int localY) {
        if (localX < 0 || localY < 0 || localX >= size || localY >= size) {
            throw new IndexOutOfBoundsException(
                    "(" + localX + ", " + localY + ") is outside " + pos + " of size " + size);
        }
        return localY * size + localX;
    }

    @Override
    public String toString() {
        return "Chunk{" + pos + ", size=" + size + ", dirty=" + dirty + "}";
    }
}
