package io.github.sob1234509876.oc.rts.map;

import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.core.ChunkPos;
import io.github.sob1234509876.oc.rts.core.Tick;
import io.github.sob1234509876.oc.rts.event.Events;
import io.github.sob1234509876.oc.rts.sim.SimulationSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Keeps the chunks around the focus of interest in memory and drops the rest.
 *
 * <p>Streaming runs as the first system of the tick. Loading a whole square of chunks in one tick
 * would stall the simulation whenever the focus jumps, so the streamer spreads the work over ticks
 * with a budget and always unloads before it loads: dropping a chunk is cheap and frees exactly the
 * memory the new chunks need.
 *
 * <p>Load and unload facts are published on the world event bus so that rendering, analysis and
 * scenario scripts can react to terrain appearing and disappearing without the streamer knowing
 * anything about them.
 */
public final class ChunkStreamer implements SimulationSystem {

    /** Phase of the pipeline, before orders and movement so that paths see the loaded world. */
    public static final int ORDER = 100;

    /** Chunks loaded or unloaded per tick when the caller does not ask for something else. */
    public static final int DEFAULT_CHUNKS_PER_TICK = 4;

    private final ChunkedMap map;
    private final int radiusChunks;

    private int chunksPerTick = DEFAULT_CHUNKS_PER_TICK;

    public ChunkStreamer(ChunkedMap map, int radiusChunks) {
        if (radiusChunks < 0) {
            throw new IllegalArgumentException("radius in chunks must not be negative, was " + radiusChunks);
        }
        this.map = Objects.requireNonNull(map, "map");
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

    /** Radius in chunks the streamer keeps loaded around the focus. */
    public int radiusChunks() {
        return radiusChunks;
    }

    /** Upper bound of chunk transitions per tick; a larger value fills the radius faster. */
    public int chunksPerTick() {
        return chunksPerTick;
    }

    public void setChunksPerTick(int chunksPerTick) {
        if (chunksPerTick <= 0) {
            throw new IllegalArgumentException("chunks per tick must be positive, was " + chunksPerTick);
        }
        this.chunksPerTick = chunksPerTick;
    }

    /** Loads the whole radius at once, ignoring the per tick budget. */
    public void loadNow(World world, Tick tick) {
        sync(world, tick, Integer.MAX_VALUE);
    }

    @Override
    public void tick(World world, Tick tick) {
        sync(world, tick, chunksPerTick);
    }

    private void sync(World world, Tick tick, int budget) {
        ChunkPos center = ChunkPos.of(map.focus().tile(), map.chunkSize());
        // Clamp the offsets so that a focus near the coordinate limit cannot overflow the int range.
        int headroom = Integer.MAX_VALUE - Math.max(Math.abs(center.x()), Math.abs(center.y())) - 2;
        int maxOffset = Math.min(radiusChunks, Math.max(0, headroom));
        int remaining = budget;

        remaining = unloadOutside(world, tick, center, maxOffset, remaining);
        if (remaining > 0) {
            loadMissing(world, tick, center, maxOffset, remaining);
        }
    }

    private int unloadOutside(World world, Tick tick, ChunkPos center, int maxOffset, int budget) {
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
            world.events().publish(new Events.ChunkUnloaded(pos, tick.number()));
            remaining--;
        }
        return remaining;
    }

    private int loadMissing(World world, Tick tick, ChunkPos center, int maxOffset, int budget) {
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
                world.events().publish(new Events.ChunkLoaded(pos, tick.number()));
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
        return "ChunkStreamer{radiusChunks=" + radiusChunks + ", chunksPerTick=" + chunksPerTick + "}";
    }
}
