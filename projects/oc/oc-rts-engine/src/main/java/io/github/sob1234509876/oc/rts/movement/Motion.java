package io.github.sob1234509876.oc.rts.movement;

import io.github.sob1234509876.oc.rts.core.Vec2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Movement intent and steering state of a single unit.
 *
 * <p>This class holds <em>what</em> a unit wants: a goal, the path towards it, the formation slot it
 * belongs to and how long it has been stuck. The movement and steering systems decide <em>how</em> to
 * get there and write {@link #velocity()}, {@link #facing()} and the entity position back.
 *
 * <p>Orders only set goals and wait for {@link #isArrived()}; that way a formation order, a haul
 * order and a plain move order all drive the same movement code.
 */
public final class Motion {

    private final List<Vec2> waypoints = new ArrayList<>();

    private Vec2 velocity = Vec2.ZERO;
    private float facing;
    private Vec2 goal;
    private float arrivalRadius = 0.25f;
    private int waypointIndex;
    private boolean arrived = true;
    private boolean pathPending;
    private int repathCooldown;
    private int stuckTicks;
    private int blockedTicks;
    private boolean combatPaused;
    private float travelledDistance;
    private int formationId;
    private Vec2 formationSlot = Vec2.ZERO;

    /** Creates a motion with no goal; a unit stands still until an order sets one. */
    public Motion() {
    }

    /** Sets the goal and clears the previous path so the movement system plans a new one. */
    public void setGoal(Vec2 goal) {
        setGoal(goal, arrivalRadius);
    }

    public void setGoal(Vec2 goal, float arrivalRadius) {
        this.goal = goal;
        this.arrivalRadius = Math.max(0.05f, arrivalRadius);
        this.arrived = false;
        this.combatPaused = false;
        clearPath();
        this.stuckTicks = 0;
        this.blockedTicks = 0;
    }

    public void clearGoal() {
        this.goal = null;
        this.arrived = true;
        this.velocity = Vec2.ZERO;
        this.combatPaused = false;
        clearPath();
    }

    public boolean hasGoal() {
        return goal != null;
    }

    public Vec2 goal() {
        if (goal == null) {
            throw new IllegalStateException("unit has no movement goal");
        }
        return goal;
    }

    public Vec2 goalOrNull() {
        return goal;
    }

    public float arrivalRadius() {
        return arrivalRadius;
    }

    public boolean isArrived() {
        return arrived;
    }

    /** Marks the unit as having reached its goal; the movement system calls this. */
    public void markArrived() {
        this.arrived = true;
        this.velocity = Vec2.ZERO;
        this.combatPaused = false;
        clearPath();
        this.stuckTicks = 0;
        this.blockedTicks = 0;
    }

    /** True while the unit still has somewhere to go. */
    public boolean isMoving() {
        return goal != null && !arrived;
    }

    // ------------------------------------------------------------------ path

    public void setPath(List<Vec2> newWaypoints) {
        waypoints.clear();
        if (newWaypoints != null) {
            waypoints.addAll(newWaypoints);
        }
        waypointIndex = 0;
        pathPending = false;
    }

    public void clearPath() {
        waypoints.clear();
        waypointIndex = 0;
    }

    public boolean hasPath() {
        return waypointIndex < waypoints.size();
    }

    public List<Vec2> waypoints() {
        return Collections.unmodifiableList(waypoints);
    }

    public int waypointIndex() {
        return waypointIndex;
    }

    /** Waypoint the unit is currently heading for, or {@code null} when the path is exhausted. */
    public Vec2 currentWaypoint() {
        return hasPath() ? waypoints.get(waypointIndex) : null;
    }

    /** Consumes the current waypoint. */
    public void advanceWaypoint() {
        if (hasPath()) {
            waypointIndex++;
        }
    }

    public int remainingWaypoints() {
        return Math.max(0, waypoints.size() - waypointIndex);
    }

    /** Whether a path request for this unit is currently queued or being computed. */
    public boolean isPathPending() {
        return pathPending;
    }

    public void setPathPending(boolean pathPending) {
        this.pathPending = pathPending;
    }

    // ------------------------------------------------------------------ steering state

    public Vec2 velocity() {
        return velocity;
    }

    public void setVelocity(Vec2 velocity) {
        this.velocity = velocity;
    }

    public float facing() {
        return facing;
    }

    public void setFacing(float radians) {
        this.facing = radians;
    }

    /** Ticks left before this unit may ask for a new path again. */
    public int repathCooldown() {
        return repathCooldown;
    }

    public void requestRepath(int ticks) {
        this.repathCooldown = Math.max(repathCooldown, ticks);
    }

    /** Counts the cooldown down by one tick. */
    public void tickRepathCooldown() {
        if (repathCooldown > 0) {
            repathCooldown--;
        }
    }

    public int stuckTicks() {
        return stuckTicks;
    }

    public void addStuckTick() {
        stuckTicks++;
    }

    public void resetStuckTicks() {
        stuckTicks = 0;
    }

    /**
     * Consecutive ticks whose next step towards the steering target was blocked by an obstacle.
     *
     * <p>The movement system counts them separately from the {@link #stuckTicks()}: a unit that grinds
     * against a wall may still slide a little, so the displacement based counter never fills, while the
     * blocked probe fires within a few ticks and makes the unit re-plan around the wall.
     */
    public int blockedTicks() {
        return blockedTicks;
    }

    public void addBlockedTick() {
        blockedTicks++;
    }

    public void resetBlockedTicks() {
        blockedTicks = 0;
    }

    /**
     * Whether combat has taken over this unit: an enemy is in range and the unit stands still to fight,
     * while its orders and its movement goal wait unchanged for the fight to end. The movement system
     * honours the pause by not steering, not consuming waypoints and not declaring arrival; the combat
     * system clears it as soon as no enemy is in reach any more. Setting a new goal or clearing the goal
     * clears the pause, so a stop or a fresh order always releases a fighting unit.
     */
    public boolean isCombatPaused() {
        return combatPaused;
    }

    public void setCombatPaused(boolean combatPaused) {
        this.combatPaused = combatPaused;
    }

    /** Total distance this unit has travelled, for statistics and tests. */
    public float travelledDistance() {
        return travelledDistance;
    }

    public void addTravelledDistance(float distance) {
        if (distance > 0f) {
            travelledDistance += distance;
        }
    }

    // ------------------------------------------------------------------ formation

    /** Identifier of the formation move this unit takes part in, or {@code 0} when ungrouped. */
    public int formationId() {
        return formationId;
    }

    /** Offset of this unit's slot relative to the formation anchor. */
    public Vec2 formationSlot() {
        return formationSlot;
    }

    public void setFormation(int formationId, Vec2 slotOffset) {
        this.formationId = formationId;
        this.formationSlot = slotOffset;
    }

    public void clearFormation() {
        this.formationId = 0;
        this.formationSlot = Vec2.ZERO;
    }

    public boolean isInFormation() {
        return formationId != 0;
    }

    @Override
    public String toString() {
        return "Motion{goal=" + goal + ", arrived=" + arrived + ", waypoints=" + remainingWaypoints()
                + ", velocity=" + velocity + ", stuck=" + stuckTicks + "}";
    }
}
