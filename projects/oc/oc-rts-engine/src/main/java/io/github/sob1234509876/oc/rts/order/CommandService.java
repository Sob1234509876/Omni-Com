package io.github.sob1234509876.oc.rts.order;

import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Tick;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.formation.FormationPlan;
import io.github.sob1234509876.oc.rts.formation.FormationPlanner;
import io.github.sob1234509876.oc.rts.formation.FormationShape;
import io.github.sob1234509876.oc.rts.movement.Motion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The one place a player, an AI or a scenario turns an intention into unit orders.
 *
 * <p>Everything that wants a group to move goes through here, which is what makes the formation logic
 * invisible to its callers: they pass a collection of units and a destination and get back a group that
 * walks in slots, keeps its spacing, detours around water and re-plans when it is stuck. Nobody outside
 * this class has to know about {@link FormationPlanner}, formation ids or slot offsets.
 *
 * <p>Commands are issued from outside the simulation loop - a player click, a rally point, a script - so
 * they happen between ticks and must build an {@link OrderContext} themselves. The tick number is read
 * from {@link World#tickNumber()} and the tick rate from the constructor (20 per second by default,
 * matching the engine default). The number is only used for order bookkeeping such as timeouts, so
 * reading the world clock instead of receiving a tick is safe and keeps commands usable at any moment.
 *
 * <p>Units are always processed in entity id order, never in the order the caller happened to collect
 * them: the slot assignment and therefore the whole formation must not depend on a selection rectangle.
 */
public final class CommandService implements UnitDirector {

    /** Simulation rate assumed when a command is issued without one; the engine default. */
    public static final int DEFAULT_TICKS_PER_SECOND = 20;

    private final World world;
    private final FormationPlanner planner;
    private final int ticksPerSecond;

    /** Orders of every live formation, so the anchor of a group can be moved as a whole. */
    private final Map<Integer, List<FormationMoveOrder>> formations = new LinkedHashMap<>();

    private int nextFormationId = 1;

    public CommandService(World world) {
        this(world, DEFAULT_TICKS_PER_SECOND);
    }

    public CommandService(World world, int ticksPerSecond) {
        this.world = Objects.requireNonNull(world, "world");
        if (ticksPerSecond <= 0) {
            throw new IllegalArgumentException("ticksPerSecond must be positive, was " + ticksPerSecond);
        }
        this.ticksPerSecond = ticksPerSecond;
        this.planner = new FormationPlanner();
    }

    /** Planner used to lay groups out; exposed so a scenario can lay out a formation of its own. */
    public FormationPlanner planner() {
        return planner;
    }

    // ------------------------------------------------------------------ single units

    @Override
    public void move(Unit unit, GridPos destination) {
        Objects.requireNonNull(destination, "destination");
        if (!isCommandable(unit)) {
            return;
        }
        unit.orders().replace(context(unit), new MoveOrder(destination));
    }

    @Override
    public void move(Unit unit, Vec2 destination) {
        Objects.requireNonNull(destination, "destination");
        if (!isCommandable(unit)) {
            return;
        }
        unit.orders().replace(context(unit), new MoveOrder(destination));
    }

    @Override
    public void stop(Unit unit) {
        if (!isCommandable(unit)) {
            return;
        }
        OrderContext context = context(unit);
        unit.orders().clear(context);
        Motion motion = unit.motion();
        // Clearing the order queue alone would leave the unit walking to the goal of an order that no
        // longer exists, so the intent has to be dropped with it. The path service discards the queued
        // request of its own accord once the goal is gone.
        motion.clearGoal();
        motion.clearFormation();
        motion.setVelocity(Vec2.ZERO);
    }

    @Override
    public void stop(Collection<Unit> units) {
        Objects.requireNonNull(units, "units");
        for (Unit unit : units) {
            stop(unit);
        }
    }

    @Override
    public void hold(Unit unit) {
        if (!isCommandable(unit)) {
            return;
        }
        unit.orders().replace(context(unit), new HoldOrder());
    }

    // ------------------------------------------------------------------ groups

    @Override
    public void moveGroup(Collection<Unit> units, GridPos destination) {
        moveGroup(units, destination, FormationShape.BOX);
    }

    @Override
    public void moveGroup(Collection<Unit> units, GridPos destination, FormationShape shape) {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(shape, "shape");
        List<Unit> group = commandable(units);
        if (group.isEmpty()) {
            return;
        }
        Vec2 anchor = destination.center();
        // Marching towards the anchor from where the group stands gives every shape its natural
        // orientation: a line faces the enemy, a column files towards the destination.
        float facing = facingTowards(centroid(group), anchor);
        float spacing = planner.defaultSpacing(group);
        FormationPlan plan = planner.plan(group, anchor, facing, shape, spacing);
        int formationId = nextFormationId++;
        List<FormationMoveOrder> orders = new ArrayList<>(plan.slotCount());
        for (int slot = 0; slot < plan.slotCount(); slot++) {
            Unit unit = plan.unitInSlot(slot);
            FormationMoveOrder order = new FormationMoveOrder(formationId, anchor, plan.slotOffset(slot));
            unit.orders().replace(context(unit), order);
            orders.add(order);
        }
        formations.put(formationId, orders);
    }

    /**
     * Moves the anchor of a formation that was ordered earlier, so the whole group follows a dragged
     * destination. Unknown or finished formations are ignored.
     */
    public void updateFormationAnchor(int formationId, Vec2 anchor) {
        Objects.requireNonNull(anchor, "anchor");
        List<FormationMoveOrder> orders = formations.get(formationId);
        if (orders == null) {
            return;
        }
        for (int i = 0; i < orders.size(); i++) {
            orders.get(i).updateAnchor(anchor);
        }
    }

    /**
     * Number of formations that still have a unit marching in them. Finished, cancelled and replaced
     * formations are dropped here, which keeps the bookkeeping of a long match from growing forever.
     */
    public int activeFormationCount() {
        formations.entrySet().removeIf(entry -> !isActive(entry.getValue()));
        return formations.size();
    }

    /** Identifier the next group order will use; ids start at one, zero means "no formation". */
    public int nextFormationId() {
        return nextFormationId;
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isActive(List<FormationMoveOrder> orders) {
        for (int i = 0; i < orders.size(); i++) {
            if (orders.get(i).isActive()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Living units in deterministic id order. The ownership filter keeps a group to a single player -
     * the owner of the first unit - because the director has no player of its own and a formation made
     * of two players' units would let one player steer the other's soldiers.
     */
    private static List<Unit> commandable(Collection<Unit> units) {
        List<Unit> living = new ArrayList<>();
        if (units != null) {
            for (Unit unit : units) {
                if (unit != null && unit.isAlive()) {
                    living.add(unit);
                }
            }
        }
        living.sort(Comparator.comparingLong(unit -> unit.id().value()));
        if (living.isEmpty()) {
            return living;
        }
        Player owner = living.get(0).owner();
        living.removeIf(unit -> !unit.owner().equals(owner));
        return living;
    }

    private static Vec2 centroid(List<Unit> units) {
        float sumX = 0f;
        float sumY = 0f;
        for (int i = 0; i < units.size(); i++) {
            sumX += units.get(i).position().x();
            sumY += units.get(i).position().y();
        }
        return new Vec2(sumX / units.size(), sumY / units.size());
    }

    /** Direction of travel of a group order, or {@code +X} when the group already stands on the anchor. */
    private static float facingTowards(Vec2 from, Vec2 to) {
        float dx = to.x() - from.x();
        float dy = to.y() - from.y();
        if (dx * dx + dy * dy <= 1e-6f) {
            return 0f;
        }
        return (float) Math.atan2(dy, dx);
    }

    private static boolean isCommandable(Unit unit) {
        return unit != null && unit.isAlive();
    }

    private OrderContext context(Unit unit) {
        return new OrderContext(world, unit, new Tick(world.tickNumber(), ticksPerSecond));
    }
}
