package io.github.sob1234509876.oc.rts.entity;

import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.combat.CombatStats;
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
 *
 * <p>Since the combat stage a unit also carries the mutable half of its fighting state - the attack
 * cooldown and the enemy it is currently engaging - while the immutable half (damage, range, cooldown,
 * armour, maximum health) is the {@link CombatStats} view of its {@link UnitType}. Both are written by
 * {@link io.github.sob1234509876.oc.rts.combat.CombatSystem} and read by the attack orders.
 */
public final class Unit extends Entity {

    private final UnitType type;
    private final CombatStats combat;
    private final Motion motion = new Motion();
    private final OrderQueue orders = new OrderQueue();
    private final Inventory cargo;
    private boolean selected;
    private int attackCooldown;
    private EntityId targetId;

    public Unit(EntityId id, Player owner, UnitType type, Vec2 position, long spawnTick) {
        super(id, owner, position, new Health(type.maxHealth()), spawnTick);
        this.type = Objects.requireNonNull(type, "type");
        this.combat = CombatStats.of(type);
        this.cargo = new Inventory("cargo of " + id, Inventory.UNLIMITED, type.cargoCapacity(), item -> true);
    }

    public UnitType type() {
        return type;
    }

    public UnitRole role() {
        return type.role();
    }

    /** Immutable combat profile of this unit, created once from its type. */
    public CombatStats combat() {
        return combat;
    }

    /** Ticks left before this unit may attack again; {@code 0} means ready. */
    public int attackCooldown() {
        return attackCooldown;
    }

    /** Sets the cooldown, clamped at zero; only the combat system writes this. */
    public void setAttackCooldown(int ticks) {
        this.attackCooldown = Math.max(0, ticks);
    }

    /** Whether this unit could hit something this tick: it has an attack and its cooldown elapsed. */
    public boolean canAttackNow() {
        return combat.canAttack() && attackCooldown == 0;
    }

    /**
     * The enemy this unit is currently engaging, or {@code null} when it has none.
     *
     * <p>The combat system writes this every tick from the nearest enemy in range, and clears it again
     * when that enemy dies or leaves the range, so the id never points at a removed entity.
     */
    public EntityId targetId() {
        return targetId;
    }

    public void setTargetId(EntityId targetId) {
        this.targetId = targetId;
    }

    public void clearTarget() {
        this.targetId = null;
    }

    public boolean hasTarget() {
        return targetId != null;
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
