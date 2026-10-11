package io.github.sob1234509876.oc.rts.visual;

import io.github.sob1234509876.oc.rts.PlayerId;
import io.github.sob1234509876.oc.rts.content.TerrainType;
import io.github.sob1234509876.oc.rts.core.ChunkPos;
import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.core.TileRect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable image of the world at one tick: the terrain of a rectangle, the entities inside it and
 * everything needed to draw or inspect them.
 *
 * <p>This is the contract between the simulation and the future GUI. The terrain is <em>copied</em>
 * tile by tile, so the snapshot stays valid while the world keeps running and a renderer can hold it
 * across frames, compare two ticks or hand it to another thread without synchronisation. Nothing in
 * this class references the live {@code World}.
 *
 * <p>Entities are sorted by {@link EntityId}, which makes every derived artifact - draw order, text
 * maps, golden files in tests - deterministic even though the world itself hands out entities in
 * spatial index order.
 */
public final class WorldSnapshot {

    private final long tick;
    private final float elapsedSeconds;
    private final int ticksPerSecond;
    private final TileRect bounds;
    private final TerrainType[][] terrain;
    private final TerrainType fallbackTerrain;
    private final List<EntitySnapshot> entities;
    private final Map<EntityId, EntitySnapshot> byId;
    private final List<ChunkPos> loadedChunks;

    public WorldSnapshot(
            long tick,
            int ticksPerSecond,
            TileRect bounds,
            TerrainType[][] terrain,
            TerrainType fallbackTerrain,
            List<EntitySnapshot> entities,
            List<ChunkPos> loadedChunks) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(terrain, "terrain");
        if (ticksPerSecond <= 0) {
            throw new IllegalArgumentException("ticks per second must be positive, was " + ticksPerSecond);
        }
        if (terrain.length != bounds.height()) {
            throw new IllegalArgumentException("terrain has " + terrain.length + " rows but the bounds are "
                    + bounds.height() + " tiles high");
        }
        for (int row = 0; row < terrain.length; row++) {
            if (terrain[row] == null || terrain[row].length != bounds.width()) {
                throw new IllegalArgumentException("terrain row " + row + " is not " + bounds.width() + " tiles wide");
            }
            for (int column = 0; column < terrain[row].length; column++) {
                if (terrain[row][column] == null) {
                    throw new IllegalArgumentException("terrain at (" + (bounds.minX() + column) + ", "
                            + (bounds.minY() + row) + ") is missing");
                }
            }
        }
        this.tick = tick;
        this.ticksPerSecond = ticksPerSecond;
        this.elapsedSeconds = tick / (float) ticksPerSecond;
        this.bounds = bounds;
        this.terrain = copy(terrain);
        this.fallbackTerrain = Objects.requireNonNull(fallbackTerrain, "fallbackTerrain");
        List<EntitySnapshot> sorted = new ArrayList<>(Objects.requireNonNull(entities, "entities"));
        sorted.sort((left, right) -> left.id().compareTo(right.id()));
        this.entities = Collections.unmodifiableList(sorted);
        Map<EntityId, EntitySnapshot> index = new LinkedHashMap<>();
        for (EntitySnapshot entity : sorted) {
            index.putIfAbsent(entity.id(), entity);
        }
        this.byId = Collections.unmodifiableMap(index);
        this.loadedChunks = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(loadedChunks, "loadedChunks")));
    }

    private static TerrainType[][] copy(TerrainType[][] source) {
        TerrainType[][] target = new TerrainType[source.length][];
        for (int row = 0; row < source.length; row++) {
            target[row] = source[row].clone();
        }
        return target;
    }

    /** Simulation tick the snapshot was taken on. */
    public long tick() {
        return tick;
    }

    /** Simulation time of {@link #tick()} in seconds. */
    public float elapsedSeconds() {
        return elapsedSeconds;
    }

    public int ticksPerSecond() {
        return ticksPerSecond;
    }

    /** Tile rectangle this snapshot covers; terrain queries outside it return the fallback terrain. */
    public TileRect bounds() {
        return bounds;
    }

    public int width() {
        return bounds.width();
    }

    public int height() {
        return bounds.height();
    }

    /** Entities inside the bounds, sorted by {@link EntityId}. */
    public List<EntitySnapshot> entities() {
        return entities;
    }

    public int entityCount() {
        return entities.size();
    }

    public int unitCount() {
        return (int) entities.stream().filter(EntitySnapshot::unit).count();
    }

    public int buildingCount() {
        return entityCount() - unitCount();
    }

    /** Chunks that were in memory when the snapshot was taken, in the order the map reported them. */
    public List<ChunkPos> loadedChunks() {
        return loadedChunks;
    }

    /** Terrain of the given tile as it was when the snapshot was taken. */
    public TerrainType terrainAt(int x, int y) {
        if (!bounds.contains(x, y)) {
            return fallbackTerrain;
        }
        return terrain[y - bounds.minY()][x - bounds.minX()];
    }

    /** Terrain used for tiles outside {@link #bounds()}, typically the world's void terrain. */
    public TerrainType fallbackTerrain() {
        return fallbackTerrain;
    }

    public Optional<EntitySnapshot> entity(EntityId id) {
        return Optional.ofNullable(id == null ? null : byId.get(id));
    }

    /**
     * The snapshot of an entity that is known to be inside the bounds.
     *
     * @throws NoSuchElementException when no such entity was captured
     */
    public EntitySnapshot requireEntity(EntityId id) {
        return entity(id).orElseThrow(() -> new NoSuchElementException(
                "entity " + id + " is not part of the snapshot of tick " + tick));
    }

    public boolean contains(EntityId id) {
        return id != null && byId.containsKey(id);
    }

    /** Entities owned by the given player, sorted by {@link EntityId}. */
    public List<EntitySnapshot> entitiesOf(PlayerId player) {
        return entities.stream().filter(entity -> Objects.equals(entity.owner(), player)).toList();
    }

    /** Entities currently selected by their owner; the flag is owned by the presentation layer. */
    public List<EntitySnapshot> selectedEntities() {
        return entities.stream().filter(EntitySnapshot::selected).toList();
    }

    @Override
    public String toString() {
        return "WorldSnapshot{tick " + tick + " (" + String.format("%.2f", elapsedSeconds) + "s), "
                + bounds + ", " + entities.size() + " entities, " + loadedChunks.size() + " chunks}";
    }
}
