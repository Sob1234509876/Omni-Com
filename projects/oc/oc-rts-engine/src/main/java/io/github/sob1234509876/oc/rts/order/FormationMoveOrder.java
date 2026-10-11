package io.github.sob1234509876.oc.rts.order;

import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.movement.Motion;

import java.util.Objects;

/**
 * Walks one unit to its slot in a moving formation.
 *
 * <p>A group order is not one order but one per unit: every soldier knows the formation it belongs to,
 * the offset of its own slot and the anchor of the group, and walks to {@code anchor + offset}. That
 * keeps the formation alive while the group advances, which a single shared order could not do - the
 * anchor moves, so the destinations have to move with it.
 *
 * <p>The slot survives for the whole march, because avoidance reads it: units of the same formation
 * pull towards each other while they are blocked, which is what makes the group compress through a gap
 * instead of shoving. Only arrival clears it, so a unit that keeps the id of a finished formation would
 * be pulled back towards soldiers it no longer marches with.
 *
 * <p>The anchor of a group is owned by the {@link CommandService} that issued it. When the player drags
 * a move order the service calls {@link #updateAnchor(Vec2)} and the order re-targets its slot, so the
 * formation follows the cursor instead of being stuck at the position where the order started.
 */
public final class FormationMoveOrder implements Order {

    /** Hard limit on how long a unit may try to reach its slot, in ticks. */
    public static final long TIMEOUT_TICKS = 2000L;
    /** Consecutive ticks without a route after which the slot counts as unreachable. */
    public static final int STALLED_TICKS_BEFORE_FAILURE = 300;
    /** The anchor has to move this far before the slot is re-targeted, in tiles. */
    public static final float ANCHOR_EPSILON = 0.25f;

    private final int formationId;
    private final Vec2 slotOffset;
    private Vec2 anchor;
    private Unit unit;
    private long startTick = -1L;
    private int stalledTicks;

    public FormationMoveOrder(int formationId, Vec2 anchor, Vec2 slotOffset) {
        if (formationId == 0) {
            throw new IllegalArgumentException("a formation move needs a non zero formation id");
        }
        this.formationId = formationId;
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.slotOffset = Objects.requireNonNull(slotOffset, "slotOffset");
    }

    @Override
    public String name() {
        return "formation_move";
    }

    /** Identifier shared by every unit of the group; {@code 0} means "not in a formation". */
    public int formationId() {
        return formationId;
    }

    /** Anchor the slots are measured from. */
    public Vec2 anchor() {
        return anchor;
    }

    /** Offset of this unit's slot relative to the anchor. */
    public Vec2 slotOffset() {
        return slotOffset;
    }

    /** World position this unit is walking to. */
    public Vec2 goal() {
        return anchor.plus(slotOffset);
    }

    /**
     * Whether the unit is still marching in this formation. Used by the command service to drop
     * formations that have arrived, been cancelled or been replaced, so it does not keep them forever.
     */
    public boolean isActive() {
        Unit member = unit;
        return member != null && member.isAlive() && member.motion().formationId() == formationId;
    }

    /**
     * Moves the anchor of the group, for example while a player drags the destination. The slot is only
     * re-targeted when the anchor really moved, because setting a goal clears the path and would
     * otherwise make the whole group re-plan every tick.
     */
    public void updateAnchor(Vec2 anchor) {
        Vec2 updated = Objects.requireNonNull(anchor, "anchor");
        if (updated.equals(this.anchor)) {
            return;
        }
        this.anchor = updated;
        Unit member = unit;
        if (member == null) {
            // Not started yet: onStart picks up the newest anchor, so there is nothing to re-target.
            return;
        }
        Motion motion = member.motion();
        if (!member.isAlive() || !motion.hasGoal() || motion.formationId() != formationId) {
            // The unit is gone or was given another order; re-targeting would hijack that order.
            this.unit = null;
            return;
        }
        Vec2 slot = goal();
        if (motion.goal().distance(slot) >= ANCHOR_EPSILON) {
            motion.setFormation(formationId, slotOffset);
            motion.setGoal(slot);
        }
    }

    @Override
    public void onStart(OrderContext context) {
        this.unit = context.unit();
        this.startTick = context.tick().number();
        this.stalledTicks = 0;
        Motion motion = unit.motion();
        motion.setFormation(formationId, slotOffset);
        motion.setGoal(goal());
    }

    @Override
    public OrderState tick(OrderContext context) {
        this.unit = context.unit();
        Motion motion = unit.motion();
        if (motion.formationId() != formationId) {
            // The unit was given another order, which already took the formation over.
            this.unit = null;
            return OrderState.CANCELLED;
        }
        if (motion.isArrived()) {
            // Leaving the formation here is what stops arrived units from being pulled around by the
            // cohesion term of the neighbours that are still walking.
            motion.clearFormation();
            this.unit = null;
            return OrderState.COMPLETED;
        }
        if (!motion.hasGoal()) {
            motion.clearFormation();
            this.unit = null;
            return OrderState.FAILED;
        }
        if (context.tick().number() - startTick > TIMEOUT_TICKS) {
            motion.clearFormation();
            this.unit = null;
            return OrderState.FAILED;
        }
        if (MoveOrder.isStalled(motion)) {
            stalledTicks++;
            if (stalledTicks > STALLED_TICKS_BEFORE_FAILURE) {
                motion.clearFormation();
                this.unit = null;
                return OrderState.FAILED;
            }
        } else {
            stalledTicks = 0;
        }
        return OrderState.RUNNING;
    }

    @Override
    public void onCancel(OrderContext context) {
        Unit member = unit;
        if (member != null && member.motion().formationId() == formationId) {
            member.motion().clearFormation();
        }
        this.unit = null;
    }

    @Override
    public String describe() {
        return "formation " + formationId + " to " + MoveOrder.format(anchor)
                + " slot " + MoveOrder.format(slotOffset);
    }

    @Override
    public String toString() {
        return "FormationMoveOrder{" + describe() + "}";
    }
}
