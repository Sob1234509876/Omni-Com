package io.github.sob1234509876.oc.rts.visual;

import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.logistics.HaulOrder;
import io.github.sob1234509876.oc.rts.order.BuildOrder;
import io.github.sob1234509876.oc.rts.order.Order;
import io.github.sob1234509876.oc.rts.order.RepairOrder;
import io.github.sob1234509876.oc.rts.content.UnitRole;

import java.util.Optional;

/**
 * One short, deterministic line describing what an engineer is doing.
 *
 * <p>The line is derived from the head of the unit's order queue, never from its movement: a builder
 * that is walking to its site is already "建造中", and an engineer with no economic order is "空闲"
 * even if a plain move order happens to be running. That keeps the label honest about the task the
 * order queue is executing rather than inventing one from a position change.
 *
 * <p>Only engineers get a task. An engineer is a unit whose type carries the {@code builder} tag, or a
 * unit with the {@link UnitRole#WORKER worker} role (the scenario catalogue's builder has the role but no
 * tag; the classic engineer has the tag). A support unit without either (a medic, for example) answers
 * {@link Optional#empty()}, which the snapshot mirrors by omitting the field.
 */
public final class UnitTask {

    /** The one-line task of an engineer with no build, repair or haul order. */
    public static final String IDLE = "空闲";

    /** Content tag that marks a unit as a builder. */
    public static final String BUILDER_TAG = "builder";

    private UnitTask() {
    }

    /** Whether this unit is an engineer, that is, a builder-tagged or worker-role unit. */
    public static boolean isEngineer(Unit unit) {
        return unit != null
                && (unit.type().hasTag(BUILDER_TAG) || unit.role() == UnitRole.WORKER);
    }

    /** The task of one engineer, or empty for every other unit. */
    public static Optional<String> of(Unit unit) {
        if (!isEngineer(unit)) {
            return Optional.empty();
        }
        return Optional.of(describe(unit.orders().current()));
    }

    /**
     * The task line of one order, or {@link #IDLE} when it is {@code null} or not an economic task.
     *
     * <p>The three task orders each name the building they act on, using the building's own display name
     * so the line stays short. An order that has already dropped its building reference (finished or
     * cancelled) counts as idle instead of printing a dangling arrow.
     */
    public static String describe(Order order) {
        if (order instanceof BuildOrder build) {
            return build.site() == null ? IDLE : "建造中 → " + build.site().displayName();
        }
        if (order instanceof RepairOrder repair) {
            return repair.building() == null ? IDLE : "修理中 → " + repair.building().displayName();
        }
        if (order instanceof HaulOrder haul) {
            return haul.job() == null ? IDLE : "搬运中 → " + haul.job().target().displayName();
        }
        return IDLE;
    }
}
