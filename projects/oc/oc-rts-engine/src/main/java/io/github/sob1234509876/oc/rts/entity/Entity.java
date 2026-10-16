package io.github.sob1234509876.oc.rts.entity;

import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.content.ResourceKey;
import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.core.TileRect;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.visual.NullVisualState;
import io.github.sob1234509876.oc.rts.visual.VisualState;

import java.util.Objects;
import java.util.Optional;

/**
 * Anything that exists in the world: units and buildings share identity, ownership, position, health
 * and the tick they were created on, and differ in what they add on top.
 *
 * <p>{@link #position()} is the authoritative simulation position and is written by the movement
 * system; {@link io.github.sob1234509876.oc.rts.entity.SpatialIndex} has to be told about changes
 * through {@link SpatialIndex#update(Entity)}.
 */
public abstract class Entity {

    private final EntityId id;
    private final Player owner;
    private final Health health;
    private final long spawnTick;
    private Vec2 position;
    private DimensionId dimensionId;
    private boolean alive = true;
    private VisualState visual;

    protected Entity(EntityId id, Player owner, Vec2 position, Health health, long spawnTick) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.position = Objects.requireNonNull(position, "position");
        this.health = Objects.requireNonNull(health, "health");
        this.spawnTick = spawnTick;
    }

    public EntityId id() {
        return id;
    }

    public Player owner() {
        return owner;
    }

    public Health health() {
        return health;
    }

    public long spawnTick() {
        return spawnTick;
    }

    /**
     * The dimension this entity stands in. Coordinates are local to a dimension, so this is what tells two
     * entities on the same tile of different maps apart.
     *
     * @throws IllegalStateException when the entity is not registered with a dimension yet
     */
    public DimensionId dimensionId() {
        if (dimensionId == null) {
            throw new IllegalStateException(displayName() + "#" + id.value()
                    + " does not stand in a dimension yet");
        }
        return dimensionId;
    }

    /** The dimension, or empty while the entity has not been registered with one. */
    public Optional<DimensionId> dimensionIfAny() {
        return Optional.ofNullable(dimensionId);
    }

    /** Set by the dimension that takes ownership of this entity; not meant for callers. */
    public void setDimension(DimensionId dimensionId) {
        this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
    }

    public Vec2 position() {
        return position;
    }

    public void setPosition(Vec2 position) {
        this.position = Objects.requireNonNull(position, "position");
    }

    public boolean isAlive() {
        return alive;
    }

    /** Marks the entity dead; the world removes it from the simulation and the spatial index. */
    public void kill() {
        alive = false;
        health.setCurrent(0L);
    }

    /**
     * Animation state of this entity, so units and buildings can both be animated.
     *
     * <p>Until the animation system binds a real animator this returns a {@link NullVisualState}, which
     * remembers the requested clip and facing without owning any frames.
     */
    public VisualState visual() {
        if (visual == null) {
            visual = new NullVisualState(spriteSetId());
        }
        return visual;
    }

    /** Binding happens in {@code visual.AnimationSystem} when the entity is spawned. */
    public void setVisual(VisualState visual) {
        this.visual = Objects.requireNonNull(visual, "visual");
    }

    /** Definition key of this entity, for example {@code oc:soldier}. */
    public abstract ResourceKey typeKey();

    /** Human readable name, used by debug output and by the future GUI. */
    public abstract String displayName();

    /** Identifier of the animation set a renderer should use for this entity. */
    public abstract String spriteSetId();

    /** Collision radius in tiles. */
    public abstract float radius();

    /** Tiles this entity occupies; a single tile for units, the footprint for buildings. */
    public abstract TileRect occupiedTiles();

    /** Whether this entity is a {@link Unit}. */
    public boolean isUnit() {
        return this instanceof Unit;
    }

    /** Whether this entity is a {@link Building}. */
    public boolean isBuilding() {
        return this instanceof Building;
    }

    /** This entity as a {@link Unit}, failing when it is not one. */
    public Unit asUnit() {
        if (this instanceof Unit unit) {
            return unit;
        }
        throw new IllegalStateException(displayName() + " is not a unit");
    }

    /** This entity as a {@link Building}, failing when it is not one. */
    public Building asBuilding() {
        if (this instanceof Building building) {
            return building;
        }
        throw new IllegalStateException(displayName() + " is not a building");
    }

    /** Distance to another entity, measured between centers. */
    public float distance(Entity other) {
        return position.distance(other.position);
    }

    @Override
    public String toString() {
        return displayName() + "#" + id.value() + "@" + position;
    }
}
