package io.github.sob1234509876.oc.rts.entity;

import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.content.ResourceKey;
import io.github.sob1234509876.oc.rts.content.UnitRole;
import io.github.sob1234509876.oc.rts.content.UnitType;
import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.TileRect;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.economy.Inventory;
import io.github.sob1234509876.oc.rts.movement.Motion;
import io.github.sob1234509876.oc.rts.order.OrderQueue;

import java.util.Objects;

/**
 * A mobile entity: a soldier, a worker, a vehicle.
 *
 * <p>A unit always has a {@link Motion} (where it wants to go), an {@link OrderQueue} (what it is
 * supposed to do), a cargo {@link Inventory} and a {@link VisualState} (which animation to play).
 * Movement itself is performed by the movement system, orders by the order system, so a unit is a
 * data holder that systems cooperate on.
 */
public final class Unit extends Entity {

    private final UnitType type;
    private final Motion motion = new Motion();
    private final OrderQueue orders = new OrderQueue();
    private final Inventory cargo;
    private boolean selected;

    public Unit(EntityId id, Player owner, UnitType type, Vec2 position, long spawnTick) {
        super(id, owner, position, new Health(type.maxHealth()), spawnTick);
        this.type = Objects.requireNonNull(type, "type");
        this.cargo = new Inventory("cargo of " + id, Inventory.UNLIMITED, type.cargoCapacity(), item -> true);
    }

    public UnitType type() {
        return type;
    }

    public UnitRole role() {
        return type.role();
    }

    /** Movement intent and steering state of this unit. */
    public Motion motion() {
        return motion;
    }

    /** Orders waiting to be executed; see {@link io.github.sob1234509876.oc.rts.order.OrderQueue}. */
    public OrderQueue orders() {
        return orders;
    }

    /** Items this unit carries; haulers load here before delivering. */
    public Inventory cargo() {
        return cargo;
    }

    public boolean isCarryingItems() {
        return !cargo.isEmpty();
    }

    /** Selection flag owned by the presentation layer; the simulation never reads it. */
    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    /** Tile the unit currently stands on. */
    public GridPos tile() {
        return position().tile();
    }

    public float speed() {
        return type.speed();
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
        return type.radius();
    }

    @Override
    public TileRect occupiedTiles() {
        GridPos tile = tile();
        return new TileRect(tile.x(), tile.y(), tile.x(), tile.y());
    }
}
