package io.github.sob1234509876.oc.rts.event;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.Recipe;
import io.github.sob1234509876.oc.rts.core.ChunkPos;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Entity;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.order.OrderState;

import java.util.Objects;

/**
 * Facts published by the simulation. Systems subscribe to the ones they care about instead of the
 * engine having to call every subsystem for every change.
 */
public final class Events {

    private Events() {
    }

    /** An entity was added to the world. */
    public record EntitySpawned(Entity entity, long tick) {
    }

    /** An entity was removed from the world. */
    public record EntityDestroyed(Entity entity, long tick) {
    }

    /**
     * An entity changed dimension: it left {@code from} and now stands in {@code to}. A transfer is not a
     * destroy plus a spawn - the entity keeps its id, its health, its cargo and its orders - so it gets its
     * own fact for subscribers such as a user interface that follows a selected unit across maps.
     */
    public record EntityTransferred(Entity entity, DimensionId from, DimensionId to, long tick) {
    }

    /** An entity took damage; {@code source} may be {@code null} for environmental damage. */
    public record DamageDealt(Entity target, Entity source, Natural amount, long tick) {
    }

    /**
     * A living entity's health reached zero because something hit it.
     *
     * <p>Published by {@link io.github.sob1234509876.oc.rts.combat.CombatSystem} immediately before
     * {@link io.github.sob1234509876.oc.rts.World#destroy} marks the victim dead, so a subscriber still
     * sees it in the world. {@code killer} is the attacker that landed the last hit and may be
     * {@code null} when the source is unknown. {@code EntityDestroyed} follows, as for every removal.
     */
    public record EntityKilled(Entity victim, Entity killer, long tick) {
    }

    /**
     * A ranged attacker released a shot from {@code from} towards {@code to}: the presentation fact a
     * client needs to draw a projectile flying. Published by {@link io.github.sob1234509876.oc.rts.combat.CombatSystem}
     * immediately before the hit is applied, so the flight always precedes its own impact - the damage
     * itself stays instant and this event never changes when or how much the victim loses. Only shots
     * with an attack range of at least two tiles announce themselves; a melee reach of under two tiles
     * is silent, because there is nothing in the air to draw.
     */
    public record ProjectileFired(Entity attacker, Vec2 from, Vec2 to, long tick) {
    }

    /** A building was placed under construction and finished it. */
    public record ConstructionCompleted(Building building, long tick) {
    }

    /** A building finished constructing one or more items. */
    public record ItemProduced(Player player, Building building, ItemBundle items, Recipe recipe, long tick) {
    }

    /** A building finished a unit recipe and the unit was spawned. */
    public record UnitProduced(Player player, Building building, Unit unit, Recipe recipe, long tick) {
    }

    /** A production job changed state, for example from waiting for materials to running. */
    public record ProductionStateChanged(Building building, Recipe recipe, String state, long tick) {
    }

    /** Direction of one {@link ItemsHauled} event: goods entering or leaving a unit's cargo hold. */
    public enum HaulAction {

        /** The unit loaded the goods into its own cargo hold. */
        PICKUP("pickup"),
        /** The unit unloaded goods from its cargo hold into a building or warehouse. */
        DROPOFF("dropoff");

        private final String id;

        HaulAction(String id) {
            this.id = id;
        }

        /** Stable wire token of the action, {@code pickup} or {@code dropoff}. */
        public String id() {
            return id;
        }
    }

    /**
     * A hauler moved goods into or out of its own cargo hold.
     *
     * <p>This is the fact a client needs for the small "resource arrived" animation: it is published
     * exactly at the two places a haul changes a unit's hold - the successful pickup from a warehouse and
     * the successful unload into the target building - and it carries the amount that really changed, not
     * the claim. The simulation never reads this event back; the server records the latest one per unit
     * so a polling snapshot can carry it without adding a network stream.
     */
    public record ItemsHauled(Unit unit, HaulAction action, ItemBundle items, long tick) {

        public ItemsHauled {
            Objects.requireNonNull(unit, "unit");
            Objects.requireNonNull(action, "action");
            items = items == null ? ItemBundle.EMPTY : items;
        }
    }

    /**
     * A dimension was registered on a world by {@link io.github.sob1234509876.oc.rts.World#addDimension}.
     *
     * <p>Dimensions are mutable world state: a plugin tick hook may legitimately add one to a world that is
     * already ticking (the chunk streamer picks it up from the next tick on). This is the fact subscribers
     * such as a server side world host need to notice that the static dimension roster just changed - the
     * event carries the id of the dimension and the tick at which it was registered. The engine builder's
     * own construction time registrations publish the event too, so a subscriber that only cares about
     * <em>late</em> additions must subscribe before the builder runs or filter by its own bookkeeping.
     */
    public record DimensionAdded(DimensionId dimension, long tick) {
    }

    /** A chunk of a dimension was generated or loaded into memory. */
    public record ChunkLoaded(DimensionId dimension, ChunkPos pos, long tick) {
    }

    /** A chunk of a dimension was dropped from memory, either because it was unloaded or evicted. */
    public record ChunkUnloaded(DimensionId dimension, ChunkPos pos, long tick) {
    }

    /** A unit was given a new order. */
    public record OrderIssued(Unit unit, String orderName, long tick) {
    }

    /**
     * An order stopped being the active order of a unit.
     *
     * <p>{@code reason} is the order's own explanation when it failed - which waypoint of a route could
     * not be reached, or that a construction site was reclaimed - and the empty string otherwise. The
     * four-argument constructor is kept for callers written before the reason existed, so existing event
     * subscribers keep compiling and see an empty reason instead of a null.
     */
    public record OrderFinished(Unit unit, String orderName, OrderState state, long tick, String reason) {

        public OrderFinished {
            Objects.requireNonNull(unit, "unit");
            Objects.requireNonNull(orderName, "orderName");
            Objects.requireNonNull(state, "state");
            reason = reason == null ? "" : reason;
        }

        /** Compatibility form for callers that have no reason to report. */
        public OrderFinished(Unit unit, String orderName, OrderState state, long tick) {
            this(unit, orderName, state, tick, "");
        }
    }
}
