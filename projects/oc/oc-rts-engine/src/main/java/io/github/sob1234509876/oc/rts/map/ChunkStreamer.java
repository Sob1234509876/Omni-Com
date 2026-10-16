package io.github.sob1234509876.oc.rts.map;

import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.core.ChunkPos;
import io.github.sob1234509876.oc.rts.core.Tick;
import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.event.Events;
import io.github.sob1234509876.oc.rts.sim.SimulationSystem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Keeps the chunks around the focus of interest in memory and drops the rest, for every dimension at once.
 *
 * <p>Each map streams around its own focus, so the Moon can be watched while Mars is idle: a dimension whose
 * focus never moves simply stays loaded where it was. The per tick budget is shared between dimensions
 * instead of being multiplied by their number, so adding maps cannot make a tick arbitrarily expensive; the
 * remainder goes to the dimensions in registration order, which keeps the split deterministic.
 *
 * <p>Streaming runs as the first system of the tick. Loading a whole square of chunks in one tick would
 * stall the simulation whenever a focus jumps, so the streamer spreads the work over ticks with a budget and
 * always unloads before it loads: dropping a chunk is cheap and frees exactly the memory the new chunks need.
 *
 * <p>Load and unload facts are published on the world event bus, tagged with their dimension, so that
 * rendering, analysis and scenario scripts can react to terrain appearing and disappearing without the
 * streamer knowing anything about them.
 */
public final class ChunkStreamer implements SimulationSystem {

    /** Phase of the pipeline, before orders and movement so that paths see the loaded world. */
    public static final int ORDER = 100;

    /** Chunks loaded or unloaded per tick when the caller does not ask for something else. */
    public static final int DEFAULT_CHUNKS_PER_TICK = 4;

    /** Dimensions and their maps, in registration order. */
    private final Map<DimensionId, ChunkedMap> maps = new LinkedHashMap<>();
    private final int radiusChunks;

    private int chunksPerTick = DEFAULT_CHUNKS_PER_TICK;

    /**
     * A streamer for a single map, registered under {@link DimensionId#overworld()}. Prefer
     * {@link #ChunkStreamer(Map, int)} when the world has several dimensions.
     */
    public ChunkStreamer(ChunkedMap map, int radiusChunks) {
        this(Map.of(DimensionId.overworld(), Objects.requireNonNull(map, "map")), radiusChunks);
    }

    /** A streamer over one map per dimension; insertion order is the order budgets are handed out in. */
    public ChunkStreamer(Map<DimensionId, ChunkedMap> maps, int radiusChunks) {
        if (radiusChunks < 0) {
            throw new IllegalArgumentException("radius in chunks must not be negative, was " + radiusChunks);
        }
        Objects.requireNonNull(maps, "maps");
        if (maps.isEmpty()) {
            throw new IllegalArgumentException("a streamer needs at least one dimension");
        }
        maps.forEach((id, map) -> this.maps.put(Objects.requireNonNull(id, "dimension id"),
                Objects.requireNonNull(map, "map")));
        this.radiusChunks = radiusChunks;
    }

    @Override
    public String name() {
        return "chunk-streamer";
    }

    @Override
    public int order() {
        return ORDER;
    }

    /** Radius in chunks the streamer keeps loaded around each focus. */
    public int radiusChunks() {
        return radiusChunks;
    }

    /** Upper bound of chunk transitions per tick across all dimensions. */
    public int chunksPerTick() {
        return chunksPerTick;
    }

    public void setChunksPerTick(int chunksPerTick) {
        if (chunksPerTick <= 0) {
            throw new IllegalArgumentException("chunks per tick must be positive, was " + chunksPerTick);
        }
        this.chunksPerTick = chunksPerTick;
    }

    /** Dimensions this streamer serves, in registration order. */
    public List<DimensionId> dimensions() {
        return List.copyOf(maps.keySet());
    }

    /**
     * Adds a dimension that was registered after the engine was built, or replaces the map of a known one.
     * Without this a late {@code world.addDimension(...)} would stay unstreamed.
     */
    public void streamDimension(DimensionId id, ChunkedMap map) {
        maps.put(Objects.requireNonNull(id, "id"), Objects.requireNonNull(map, "map"));
    }

    /** Loads the whole radius of every dimension at once, ignoring the per tick budget. */
    public void loadNow(World world, Tick tick) {
        sync(world, tick, Integer.MAX_VALUE);
    }

    @Override
    public void tick(World world, Tick tick) {
        sync(world, tick, chunksPerTick);
    }

    private void sync(World world, Tick tick, int budget) {
        if (budget == Integer.MAX_VALUE) {
            for (Map.Entry<DimensionId, ChunkedMap> entry : maps.entrySet()) {
                syncOne(world, tick, entry.getKey(), entry.getValue(), Integer.MAX_VALUE);
            }
            return;
        }
        int perDimension = Math.max(1, budget / maps.size());
        int remaining = budget;
        for (Map.Entry<DimensionId, ChunkedMap> entry : maps.entrySet()) {
            if (remaining <= 0) {
                break;
            }
            int share = Math.min(perDimension, remaining);
            int left = syncOne(world, tick, entry.getKey(), entry.getValue(), share);
            remaining -= share - left;
        }
    }

    private int syncOne(World world, Tick tick, DimensionId id, ChunkedMap map, int budget) {
        ChunkPos center = ChunkPos.of(map.focus().tile(), map.chunkSize());
        // Clamp the offsets so that a focus near the coordinate limit cannot overflow the int range.
        int headroom = Integer.MAX_VALUE - Math.max(Math.abs(center.x()), Math.abs(center.y())) - 2;
        int maxOffset = Math.min(radiusChunks, Math.max(0, headroom));
        int remaining = budget;
        remaining = unloadOutside(world, tick, id, map, center, maxOffset, remaining);
        if (remaining > 0) {
            loadMissing(world, tick, id, map, center, maxOffset, remaining);
        }
        return budget - remaining;
    }

    private int unloadOutside(World world, Tick tick, DimensionId id, ChunkedMap map, ChunkPos center,
            int maxOffset, int budget) {
        // A chunk that leaves the radius loses its in-memory copy; if it was edited it is saved first.
        List<ChunkPos> dropping = new ArrayList<>();
        for (ChunkPos pos : map.loadedChunks()) {
            if (chebyshev(pos, center) > maxOffset) {
                dropping.add(pos);
            }
        }
        dropping.sort(null);
        int remaining = budget;
        for (ChunkPos pos : dropping) {
            if (remaining <= 0) {
                break;
            }
            map.unload(pos);
            world.events().publish(new Events.ChunkUnloaded(id, pos, tick.number()));
            remaining--;
        }
        return remaining;
    }

    private int loadMissing(World world, Tick tick, DimensionId id, ChunkedMap map, ChunkPos center,
            int maxOffset, int budget) {
        int remaining = budget;
        int minX = center.x() - maxOffset;
        int maxX = center.x() + maxOffset;
        int minY = center.y() - maxOffset;
        int maxY = center.y() + maxOffset;
        for (int cy = minY; cy <= maxY && remaining > 0; cy++) {
            for (int cx = minX; cx <= maxX && remaining > 0; cx++) {
                ChunkPos pos = new ChunkPos(cx, cy);
                if (map.isChunkLoaded(pos)) {
                    continue;
                }
                map.chunk(pos);
                world.events().publish(new Events.ChunkLoaded(id, pos, tick.number()));
                remaining--;
            }
        }
        return remaining;
    }

    private static int chebyshev(ChunkPos a, ChunkPos b) {
        return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.y() - b.y()));
    }

    @Override
    public String toString() {
        return "ChunkStreamer{dimensions=" + maps.keySet() + ", radiusChunks=" + radiusChunks
                + ", chunksPerTick=" + chunksPerTick + "}";
    }
}
