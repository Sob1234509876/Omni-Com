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
import java.util.function.Consumer;

/**
 * A terrain map without any world edge and with a hard least-recently-used chunk budget.
 *
 * <p>Terrain is addressed by absolute tile coordinate. A query for a chunk that is not in memory asks the
 * {@link ChunkStore} first and falls back to the {@link ChunkGenerator}, so <em>every</em> signed tile
 * coordinate has terrain: there is no width, height, edge or out-of-bounds answer anywhere in this class.
 * Generation is delegated to the same {@link ChunkGenerator} contract as {@link ChunkedMap}, which
 * requires a pure function of the chunk coordinate and the generator seed; that is what makes a chunk
 * identical after it was evicted and visited again.
 *
 * <p>Loaded chunks live in a {@link LinkedHashMap} in <strong>access order</strong>. Every lookup through
 * {@link #chunk(ChunkPos)}, {@link #peekChunk(ChunkPos)} or {@link #terrainAt(int, int)} marks that chunk
 * as most recently used. Once the map holds more than {@link #maxLoadedChunks()} chunks, the least
 * recently used ones are evicted until the budget is met again. Only diagnostics such as
 * {@link #isChunkLoaded(ChunkPos)}, {@link #loadedChunks()}, {@link #loadedChunkCount()},
 * {@link #loadedBounds()}, {@link #loadedOrder()} and {@link #chunks()} do not count as accesses, so
 * inspecting the map does not perturb the eviction order. The default budget,
 * {@value #DEFAULT_MAX_LOADED_CHUNKS} chunks, is a memory ceiling rather than a world limit: a unit may
 * walk forever and the terrain behind it is simply regenerated when somebody looks at it again.
 *
 * <p>Eviction and explicit {@link #unload(ChunkPos)} both consult the same path: a dirty chunk is written
 * to the {@link ChunkStore} first, then removed from memory, then reported to the unload listener. The
 * engine's {@code World} wires that listener to {@code Events.ChunkUnloaded} when the map is registered,
 * so streaming, eviction and a scenario calling {@code unload} all produce the same fact exactly once.
 *
 * <p>The streaming focus ({@link #focus(Vec2, int)}) is honoured by {@link ChunkStreamer} and by
 * {@link #loadArea(GridPos, GridPos)}; it does <em>not</em> influence which chunk the LRU evicts. That is
 * deliberate: the budget is a memory guarantee, while the streamer is the policy that decides which part
 * of the endless world should be warm.
 *
 * <p>This class is not thread safe, like the rest of the simulation.
 */
public final class InfiniteTerrainMap implements StreamingTerrainMap {

    /** Chunks kept in memory when no budget is given explicitly. */
    public static final int DEFAULT_MAX_LOADED_CHUNKS = 4096;

    private final ChunkGenerator generator;
    private final ChunkStore store;
    private final int chunkSize;

    /** Access ordered: iteration starts at the least recently used chunk. */
    private final Map<ChunkPos, Chunk> loaded = new LinkedHashMap<>(16, 0.75f, true);

    private Vec2 focus = Vec2.ZERO;
    private int radiusChunks;
    private int maxLoadedChunks;
    private long generatedChunkCount;
    private long evictedChunkCount;
    private Consumer<ChunkPos> unloadListener = position -> {
    };

    /** A map over an in-memory store with the default budget and the generator's chunk size. */
    public InfiniteTerrainMap(ChunkGenerator generator) {
        this(generator, new InMemoryChunkStore(),
                Objects.requireNonNull(generator, "generator").chunkSize(), DEFAULT_MAX_LOADED_CHUNKS);
    }

    /** A map over an in-memory store with an explicit budget and the generator's chunk size. */
    public InfiniteTerrainMap(ChunkGenerator generator, int maxLoadedChunks) {
        this(generator, new InMemoryChunkStore(),
                Objects.requireNonNull(generator, "generator").chunkSize(), maxLoadedChunks);
    }

    /** A map with an explicit store, chunk size and budget; the budget must be positive. */
    public InfiniteTerrainMap(ChunkGenerator generator, ChunkStore store, int chunkSize, int maxLoadedChunks) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunk size must be positive, was " + chunkSize);
        }
        if (maxLoadedChunks <= 0) {
            throw new IllegalArgumentException(
                    "an unbounded map needs a positive chunk budget, was " + maxLoadedChunks);
        }
        this.generator = Objects.requireNonNull(generator, "generator");
        this.store = Objects.requireNonNull(store, "store");
        this.chunkSize = chunkSize;
        this.maxLoadedChunks = maxLoadedChunks;
    }

    // ------------------------------------------------------------------ TerrainMap

    @Override
    public int chunkSize() {
        return chunkSize;
    }

    /**
     * Terrain of any signed tile coordinate, generating the containing chunk when needed.
     *
     * <p>The floor division and floor modulus keep negative coordinates consistent with the chunk grid:
     * tile {@code -1} belongs to chunk {@code -1} at local coordinate {@code chunkSize - 1}, not to chunk
     * {@code 0}.
     */
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

    /** Number of chunks in memory, without copying the key set. */
    @Override
    public int loadedChunkCount() {
        return loaded.size();
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
        return Optional.of(new TileRect(
                minChunkX * chunkSize,
                minChunkY * chunkSize,
                maxChunkX * chunkSize + chunkSize - 1,
                maxChunkY * chunkSize + chunkSize - 1));
    }

    // ------------------------------------------------------------------ chunk access

    /**
     * The loaded chunk, or empty without generating or loading anything.
     *
     * <p>The map is access ordered, so a successful peek counts as an access and makes the chunk the most
     * recently used one; the empty case changes nothing.
     */
    public Optional<Chunk> peekChunk(ChunkPos pos) {
        return Optional.ofNullable(loaded.get(Objects.requireNonNull(pos, "pos")));
    }

    /** The chunk at the position, marking it most recently used and generating or loading it if needed. */
    @Override
    public Chunk chunk(ChunkPos pos) {
        return loadedChunk(Objects.requireNonNull(pos, "pos"));
    }

    /** Chunks currently in memory in least-to-most recently used order; the returned map is a copy. */
    @Override
    public Map<ChunkPos, Chunk> chunks() {
        return new LinkedHashMap<>(loaded);
    }

    @Override
    public long generatedChunkCount() {
        return generatedChunkCount;
    }

    /** Chunks dropped by the budget since this map was created; explicit {@link #unload} is not counted. */
    public long evictedChunkCount() {
        return evictedChunkCount;
    }

    /** Drops a chunk from memory; it is regenerated or reloaded on the next access. */
    @Override
    public void unload(ChunkPos chunk) {
        Objects.requireNonNull(chunk, "chunk");
        Chunk evicted = loaded.remove(chunk);
        if (evicted == null) {
            return;
        }
        if (evicted.isDirty()) {
            store.save(evicted);
        }
        unloadListener.accept(chunk);
    }

    @Override
    public void flushDirty() {
        for (Chunk chunk : loaded.values()) {
            if (chunk.isDirty()) {
                store.save(chunk);
            }
        }
    }

    /** Sets the memory budget and immediately evicts the least recently used chunks beyond it. */
    @Override
    public void setMaxLoadedChunks(int maxLoadedChunks) {
        if (maxLoadedChunks <= 0) {
            throw new IllegalArgumentException(
                    "an unbounded map needs a positive chunk budget, was " + maxLoadedChunks);
        }
        this.maxLoadedChunks = maxLoadedChunks;
        evictExcess();
    }

    @Override
    public int maxLoadedChunks() {
        return maxLoadedChunks;
    }

    @Override
    public void unloadAll() {
        for (ChunkPos pos : new ArrayList<>(loaded.keySet())) {
            unload(pos);
        }
    }

    @Override
    public void setUnloadListener(Consumer<ChunkPos> listener) {
        this.unloadListener = Objects.requireNonNull(listener, "listener");
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

    /** Loads every chunk inside the given tile rectangle; the budget may evict some of them again. */
    public void loadArea(TileRect area) {
        Objects.requireNonNull(area, "area");
        loadArea(area.min(), area.max());
    }

    // ------------------------------------------------------------------ internals

    /** Returns the chunk and marks it most recently used, generating it only when nothing else has it. */
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

    /**
     * Drops least recently used chunks until the memory budget is respected.
     *
     * <p>Eviction goes through {@link #unload(ChunkPos)} so that saving a dirty chunk and publishing the
     * unload fact happen exactly once, on one code path, whether the caller asked for the drop or the
     * budget did. The eldest key is the first key of an access ordered {@link LinkedHashMap}.
     */
    private void evictExcess() {
        while (loaded.size() > maxLoadedChunks) {
            ChunkPos victim = loaded.keySet().iterator().next();
            unload(victim);
            evictedChunkCount++;
        }
    }

    /** Positions of the chunks in memory, from least to most recently used. */
    public List<ChunkPos> loadedOrder() {
        return new ArrayList<>(loaded.keySet());
    }

    @Override
    public String toString() {
        return "InfiniteTerrainMap{chunkSize=" + chunkSize + ", loaded=" + loaded.size()
                + ", maxLoaded=" + maxLoadedChunks + ", generated=" + generatedChunkCount
                + ", evicted=" + evictedChunkCount + ", " + generator + "}";
    }
}
