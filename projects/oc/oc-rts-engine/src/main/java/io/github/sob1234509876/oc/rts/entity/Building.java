package io.github.sob1234509876.oc.rts.entity;

import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.content.BuildingType;
import io.github.sob1234509876.oc.rts.content.ResourceKey;
import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.TileRect;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.economy.Inventory;

import java.util.Objects;

/**
 * A static entity with a footprint: a warehouse, a barracks, a factory.
 *
 * <p>Every building has an {@link Inventory}; it is the input buffer of its recipes and the output
 * buffer of its production. When the type is marked as a warehouse, that inventory also takes part in
 * the owner's logistics network, which is what makes items stored here usable.
 *
 * <p>Buildings can be placed under construction: while {@link #isConstructed()} is false, production
 * does not start and {@link #constructionRemaining()} counts down.
 */
public final class Building extends Entity {

    private final BuildingType type;
    private final GridPos origin;
    private final Inventory inventory;
    private Vec2 rallyPoint;
    private int constructionRemaining;
    private boolean constructed;

    public Building(EntityId id, Player owner, BuildingType type, GridPos origin, long spawnTick) {
        super(id, owner, footprintCenter(type, origin), new Health(type.maxHealth()), spawnTick);
        this.type = Objects.requireNonNull(type, "type");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.inventory = new Inventory(
                "storage of " + id,
                type.storageSlots() > 0 ? type.storageSlots() : 0,
                Inventory.UNLIMITED,
                item -> item.hasAnyTag(type.storageTags()));
        this.rallyPoint = footprintCenter(type, origin).plus(type.sizeX() / 2f + 1.5f, 0f);
        this.constructionRemaining = type.buildTimeTicks();
        this.constructed = false;
    }

    private static Vec2 footprintCenter(BuildingType type, GridPos origin) {
        return Vec2.of(origin.x() + type.sizeX() / 2f, origin.y() + type.sizeY() / 2f);
    }

    public BuildingType type() {
        return type;
    }

    /** Tile of the minimum corner of the footprint. */
    public GridPos origin() {
        return origin;
    }

    /** Storage and production buffer of this building. */
    public Inventory inventory() {
        return inventory;
    }

    /** Where freshly produced units are sent by default. */
    public Vec2 rallyPoint() {
        return rallyPoint;
    }

    public void setRallyPoint(Vec2 rallyPoint) {
        this.rallyPoint = Objects.requireNonNull(rallyPoint, "rallyPoint");
    }

    public boolean isWarehouse() {
        return type.isWarehouse();
    }

    public boolean isConstructed() {
        return constructed;
    }

    public int constructionRemaining() {
        return constructionRemaining;
    }

    /** Construction progress in {@code [0, 1]}. */
    public float constructionFraction() {
        return type.buildTimeTicks() <= 0 ? 1f : 1f - constructionRemaining / (float) type.buildTimeTicks();
    }

    /** Advances construction and returns true when the building became operational. */
    public boolean advanceConstruction(int ticks) {
        if (constructed) {
            return false;
        }
        constructionRemaining = Math.max(0, constructionRemaining - ticks);
        if (constructionRemaining == 0) {
            constructed = true;
            return true;
        }
        return false;
    }

    /** Makes the building operational immediately. */
    public void completeConstruction() {
        constructionRemaining = 0;
        constructed = true;
    }

    /** Puts the building back into the under-construction state. */
    public void restartConstruction() {
        constructionRemaining = type.buildTimeTicks();
        constructed = false;
    }

    public int sizeX() {
        return type.sizeX();
    }

    public int sizeY() {
        return type.sizeY();
    }

    /** Tile of the building's entry point, used by haulers as their delivery position. */
    public GridPos entranceTile() {
        return new GridPos(origin.x() + type.sizeX(), origin.y() + type.sizeY() / 2);
    }

    /** World position just outside the footprint, where units queue up to interact. */
    public Vec2 entrance() {
        return Vec2.centerOf(entranceTile());
    }

    @Override
    public ResourceKey typeKey() {
        return type.key();
    }

    @Override
    public String displayName() {
        return type.displayName();
    }

    @Override
    public String spriteSetId() {
        return type.spriteSetId();
    }

    @Override
    public float radius() {
        return Math.max(type.sizeX(), type.sizeY()) / 2f;
    }

    @Override
    public TileRect occupiedTiles() {
        return TileRect.fromOrigin(origin, type.sizeX(), type.sizeY());
    }
}
