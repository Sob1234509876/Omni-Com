package io.github.sob1234509876.oc.rts.visual;

import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.TerrainType;
import io.github.sob1234509876.oc.rts.content.UnitRole;
import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.TileRect;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Entity;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.order.OrderQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Turns the live world into a {@link WorldSnapshot}.
 *
 * <p>Capturing is deliberately a single call with a fixed cost: the terrain of the requested rectangle
 * is copied and the entities inside it are read once. A renderer that keeps up with the simulation
 * calls this once per frame and then draws from the snapshot, which is what keeps the live world free
 * of any presentation concerns and lets a GUI run on its own thread.
 *
 * <p>Only living entities are captured. An entity that died this tick has already been marked dead and
 * its death animation is the animator's business, not the snapshot's; including it would make a text
 * map show a unit at a stale position and confuse "on screen" counts.
 */
public final class SnapshotFactory {

    /**
     * Simulation rate assumed when the caller does not name one.
     *
     * <p>A snapshot is taken between ticks, where only the tick counter is authoritative, so the rate has
     * to come from outside. The default matches {@code OcRtsEngineBuilder}'s default, and the engine
     * passes its own rate in, so {@link WorldSnapshot#elapsedSeconds()} agrees with
     * {@code OcRtsEngine.elapsedSeconds()}.
     */
    public static final int DEFAULT_TICKS_PER_SECOND = 20;

    private final World world;
    private final VisualLibrary library;
    private final int ticksPerSecond;

    /** Creates a factory that assumes {@link #DEFAULT_TICKS_PER_SECOND}. */
    public SnapshotFactory(World world, VisualLibrary library) {
        this(world, library, DEFAULT_TICKS_PER_SECOND);
    }

    /** Creates a factory that converts ticks to seconds with the rate of the engine it captures. */
    public SnapshotFactory(World world, VisualLibrary library, int ticksPerSecond) {
        this.world = Objects.requireNonNull(world, "world");
        this.library = Objects.requireNonNull(library, "library");
        if (ticksPerSecond <= 0) {
            throw new IllegalArgumentException("ticks per second must be positive, was " + ticksPerSecond);
        }
        this.ticksPerSecond = ticksPerSecond;
    }

    /** Simulation rate used for elapsed time in the snapshots this factory produces. */
    public int ticksPerSecond() {
        return ticksPerSecond;
    }

    /** The world this factory reads; it is only ever read, never modified. */
    public World world() {
        return world;
    }

    public VisualLibrary library() {
        return library;
    }

    /**
     * Captures the square of {@code 2 * radiusTiles + 1} tiles around the center tile.
     *
     * <p>The rectangle is clipped nowhere: tiles outside the generated chunks are simply whatever the
     * terrain map reports for them, so a snapshot of the map edge is still a complete, usable image.
     * Entities are taken from the spatial index and sorted by {@link EntityId}, which makes the capture
     * of an unchanged world byte for byte reproducible.
     */
    public WorldSnapshot capture(GridPos center, int radiusTiles) {
        Objects.requireNonNull(center, "center");
        if (radiusTiles < 0) {
            throw new IllegalArgumentException("radiusTiles must not be negative, was " + radiusTiles);
        }
        TileRect bounds = TileRect.centered(center, radiusTiles);
        TerrainType[][] terrain = captureTerrain(bounds);
        TerrainType fallback = world.content().fallbackTerrain();
        List<EntitySnapshot> entities = new ArrayList<>();
        for (Entity entity : world.spatial().inRect(bounds)) {
            if (!entity.isAlive()) {
                continue;
            }
            entities.add(snapshot(entity));
        }
        entities.sort((left, right) -> left.id().compareTo(right.id()));
        return new WorldSnapshot(
                world.tickNumber(),
                ticksPerSecond,
                bounds,
                terrain,
                fallback,
                entities,
                List.copyOf(world.map().loadedChunks()));
    }

    /** Reads one terrain row per map line, in row major order matching the bounds. */
    private TerrainType[][] captureTerrain(TileRect bounds) {
        TerrainType[][] terrain = new TerrainType[bounds.height()][bounds.width()];
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                terrain[y - bounds.minY()][x - bounds.minX()] = world.map().terrainAt(x, y);
            }
        }
        return terrain;
    }

    /** Copies everything a renderer needs out of one entity. */
    private EntitySnapshot snapshot(Entity entity) {
        VisualState visual = entity.visual();
        SpriteFrame frame = visual instanceof UnitAnimator animator ? animator.currentFrame() : null;
        String clipId = visual.clipId();
        int frameIndex = visual.frameIndex();
        float facing = visual.facing();
        boolean moving = entity instanceof Unit unit && unit.motion().isMoving();
        OrderQueue orders = entity instanceof Unit unit ? unit.orders() : null;
        String orderName = orders == null || orders.current() == null ? null : orders.current().name();
        int orderCount = orders == null ? 0 : orders.size();
        ItemBundle carrying = entity instanceof Unit unit ? unit.cargo().contents()
                : entity instanceof Building building ? building.inventory().contents()
                : ItemBundle.EMPTY;
        Player owner = entity.owner();
        return new EntitySnapshot(
                entity.id(),
                entity.typeKey(),
                entity.displayName(),
                owner.id(),
                owner.colorArgb(),
                entity.position(),
                entity instanceof Building building ? building.origin() : entity.position().tile(),
                entity.radius(),
                entity.health().fraction(),
                entity instanceof Unit unit && unit.isSelected(),
                entity.isUnit(),
                roleOf(entity),
                entity.spriteSetId(),
                clipId,
                frameIndex,
                facing,
                frame,
                moving,
                orderName,
                orderCount,
                carrying);
    }

    /** Role of a unit as declared by its type; {@code null} for buildings. */
    private static UnitRole roleOf(Entity entity) {
        return entity instanceof Unit unit ? unit.role() : null;
    }
}
