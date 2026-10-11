package io.github.sob1234509876.oc.rts.map;

import io.github.sob1234509876.oc.rts.content.TerrainType;
import io.github.sob1234509876.oc.rts.core.ChunkPos;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.TileRect;
import io.github.sob1234509876.oc.rts.core.Vec2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A world map made of chunks that are produced on demand and only partly held in memory.
 *
 * <p>The map never answers "there is nothing here". A query for a tile of a chunk that is not in memory
 * asks the {@link ChunkStore} first and falls back to the {@link ChunkGenerator}, so a path search may
 * cross terrain that was never visited before and a scenario may address any coordinate without
 * pre-loading it. That is the property that makes the map effectively endless while memory stays
 * bounded; {@link ChunkStreamer} is the component that decides which of the generated chunks survive.
 *
 * <p>Loaded chunks are held in insertion order rather than a hash order so that eviction and diagnostics
 * are reproducible for a given sequence of accesses.
 */
public final class ChunkedMap implements TerrainMap, MapStreaming {

    private final ChunkGenerator generator;
    private final ChunkStore store;
    private final int chunkSize;
    private final Map<ChunkPos, Chunk> loaded = new LinkedHashMap<>();

    private Vec2 focus = Vec2.ZERO;
    private int radiusChunks;
    private int maxLoadedChunks;
    private long generatedChunkCount;

    public ChunkedMap(ChunkGenerator generator, ChunkStore store, int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunk size must be positive, was " + chunkSize);
        }
        this.generator = Objects.requireNonNull(generator, "generator");
        this.store = Objects.requireNonNull(store, "store");
        this.chunkSize = chunkSize;
    }

    /** Map over an in-memory store, the common case for tests and single scenarios. */
    public ChunkedMap(ChunkGenerator generator) {
        this(generator, new InMemoryChunkStore(), generator.chunkSize());
    }

    // ------------------------------------------------------------------ TerrainMap

    @Override
    public int chunkSize() {
        return chunkSize;
    }

    @Override
    public TerrainType terrainAt(int x, int y) {
        Chunk chunk = loadedChunk(ChunkPos.of(new GridPos(x, y), chunkSize));
        return chunk.terrainAt(Math.floorMod(x, chunkSize), Math.floorMod(y, chunkSize));
    }

    @Override
    public void setTerrain(GridPos tile, TerrainType terrain) {
        Objects.requireNonNull(tile, "tile");
        Objects.requireNonNull(terrain, "terrain");
        Chunk chunk = loadedChunk(ChunkPos.of(tile, chunkSize));
        chunk.setTerrain(Math.floorMod(tile.x(), chunkSize), Math.floorMod(tile.y(), chunkSize), terrain);
    }

    @Override
    public boolean isChunkLoaded(ChunkPos chunk) {
        return loaded.containsKey(Objects.requireNonNull(chunk, "chunk"));
    }

    @Override
    public Set<ChunkPos> loadedChunks() {
        return new LinkedHashSet<>(loaded.keySet());
    }

    @Override
    public Optional<TileRect> loadedBounds() {
        if (loaded.isEmpty()) {
            return Optional.empty();
        }
        int minChunkX = Integer.MAX_VALUE;
        int minChunkY = Integer.MAX_VALUE;
        int maxChunkX = Integer.MIN_VALUE;
        int maxChunkY = Integer.MIN_VALUE;
        for (ChunkPos pos : loaded.keySet()) {
            minChunkX = Math.min(minChunkX, pos.x());
            minChunkY = Math.min(minChunkY, pos.y());
            maxChunkX = Math.max(maxChunkX, pos.x());
            maxChunkY = Math.max(maxChunkY, pos.y());
        }
        int minX = minChunkX * chunkSize;
        int minY = minChunkY * chunkSize;
        return Optional.of(new TileRect(
                minX,
                minY,
                maxChunkX * chunkSize + chunkSize - 1,
                maxChunkY * chunkSize + chunkSize - 1));
    }

    // ------------------------------------------------------------------ chunk access

    /** The loaded chunk, or empty without generating or loading anything. */
    public Optional<Chunk> peekChunk(ChunkPos pos) {
        return Optional.ofNullable(loaded.get(Objects.requireNonNull(pos, "pos")));
    }

    /** The chunk at the given position, generating or loading it when it is not in memory. */
    public Chunk chunk(ChunkPos pos) {
        return loadedChunk(Objects.requireNonNull(pos, "pos"));
    }

    /** Chunks currently in memory, keyed by position; the returned map is a copy. */
    public Map<ChunkPos, Chunk> chunks() {
        return new LinkedHashMap<>(loaded);
    }

    /** Number of chunks produced by the generator since this map was created. */
    public long generatedChunkCount() {
        return generatedChunkCount;
    }

    /** Drops a chunk from memory; it is regenerated or reloaded on the next access. */
    public void unload(ChunkPos chunk) {
        Objects.requireNonNull(chunk, "chunk");
        Chunk evicted = loaded.remove(chunk);
        if (evicted != null && evicted.isDirty()) {
            store.save(evicted);
        }
    }

    /** Writes every edited chunk to the store and marks it persisted. */
    public void flushDirty() {
        for (Chunk chunk : loaded.values()) {
            if (chunk.isDirty()) {
                store.save(chunk);
            }
        }
    }

    /**
     * Sets how many chunks may stay in memory at once; zero or a negative value means no limit.
     *
     * <p>When the limit is exceeded the chunks farthest from the focus are dropped first, so a map
     * keeps the area the player is looking at while long searches may still walk out of it and
     * regenerate the terrain they need.
     */
    public void setMaxLoadedChunks(int maxLoadedChunks) {
        this.maxLoadedChunks = maxLoadedChunks;
        evictExcess();
    }

    /** The current limit, zero when unlimited. */
    public int maxLoadedChunks() {
        return maxLoadedChunks;
    }

    /** Drops every chunk that is not needed any more; used when a scenario ends. */
    public void unloadAll() {
        for (ChunkPos pos : new ArrayList<>(loaded.keySet())) {
            unload(pos);
        }
    }

    // ------------------------------------------------------------------ MapStreaming

    @Override
    public void focus(Vec2 center, int radiusChunks) {
        Objects.requireNonNull(center, "center");
        if (radiusChunks < 0) {
            throw new IllegalArgumentException("radius in chunks must not be negative, was " + radiusChunks);
        }
        this.focus = center;
        this.radiusChunks = radiusChunks;
    }

    @Override
    public Vec2 focus() {
        return focus;
    }

    @Override
    public int radiusChunks() {
        return radiusChunks;
    }

    @Override
    public void loadArea(GridPos min, GridPos max) {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        if (min.x() > max.x() || min.y() > max.y()) {
            throw new IllegalArgumentException("empty area " + min + " to " + max);
        }
        ChunkPos minChunk = ChunkPos.of(min, chunkSize);
        ChunkPos maxChunk = ChunkPos.of(max, chunkSize);
        for (int cy = minChunk.y(); cy <= maxChunk.y(); cy++) {
            for (int cx = minChunk.x(); cx <= maxChunk.x(); cx++) {
                loadedChunk(new ChunkPos(cx, cy));
            }
        }
    }

    /** Loads every chunk inside the given tile rectangle. */
    public void loadArea(TileRect area) {
        Objects.requireNonNull(area, "area");
        loadArea(area.min(), area.max());
    }

    // ------------------------------------------------------------------ internals

    /**
     * Returns the chunk, creating it when necessary. The store wins over the generator because it may
     * hold edits that the generator knows nothing about.
     */
    private Chunk loadedChunk(ChunkPos pos) {
        Chunk chunk = loaded.get(pos);
        if (chunk != null) {
            return chunk;
        }
        chunk = store.load(pos).orElse(null);
        if (chunk == null) {
            chunk = generator.generate(pos);
            generatedChunkCount++;
        }
        loaded.put(pos, chunk);
        evictExcess();
        return chunk;
    }

    private void evictExcess() {
        if (maxLoadedChunks <= 0) {
            return;
        }
        while (loaded.size() > maxLoadedChunks) {
            ChunkPos victim = farthestFromFocus();
            if (victim == null) {
                return;
            }
            unload(victim);
        }
    }

    /**
     * The chunk that hurts least to drop: the one farthest from the focus, with the lower
     * {@link ChunkPos#compareTo} order breaking ties so eviction does not depend on map iteration order.
     */
    private ChunkPos farthestFromFocus() {
        ChunkPos focusChunk = ChunkPos.of(focus.tile(), chunkSize);
        ChunkPos victim = null;
        int worstDistance = -1;
        for (ChunkPos candidate : loaded.keySet()) {
            int distance = candidate.chebyshevDistance(focusChunk);
            if (distance > worstDistance) {
                worstDistance = distance;
                victim = candidate;
            } else if (distance == worstDistance && victim != null && candidate.compareTo(victim) < 0) {
                victim = candidate;
            }
        }
        return victim;
    }

    /** Positions of the chunks in memory, in insertion order. */
    public List<ChunkPos> loadedOrder() {
        return new ArrayList<>(loaded.keySet());
    }

    @Override
    public String toString() {
        return "ChunkedMap{chunkSize=" + chunkSize + ", loaded=" + loaded.size()
                + ", generated=" + generatedChunkCount + ", " + generator + "}";
    }
}
