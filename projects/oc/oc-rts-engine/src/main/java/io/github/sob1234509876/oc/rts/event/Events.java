package io.github.sob1234509876.oc.rts.event;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.Recipe;
import io.github.sob1234509876.oc.rts.core.ChunkPos;
import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Entity;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.order.OrderState;

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

    /** A chunk of a dimension was generated or loaded into memory. */
    public record ChunkLoaded(DimensionId dimension, ChunkPos pos, long tick) {
    }

    /** A chunk of a dimension was dropped from memory, either because it was unloaded or evicted. */
    public record ChunkUnloaded(DimensionId dimension, ChunkPos pos, long tick) {
    }

    /** A unit was given a new order. */
    public record OrderIssued(Unit unit, String orderName, long tick) {
    }

    /** An order stopped being the active order of a unit. */
    public record OrderFinished(Unit unit, String orderName, OrderState state, long tick) {
    }
}
