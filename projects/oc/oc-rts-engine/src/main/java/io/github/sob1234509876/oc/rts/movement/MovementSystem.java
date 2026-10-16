package io.github.sob1234509876.oc.rts.movement;

import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.core.Tick;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Entity;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.map.TerrainMap;
import io.github.sob1234509876.oc.rts.sim.SimulationSystem;
import io.github.sob1234509876.oc.rts.visual.VisualState;

import java.util.List;
import java.util.Objects;

/**
 * Moves every unit one tick: plans a route when it has none, steers towards the current waypoint, avoids
 * the neighbours and keeps the result inside walkable terrain.
 *
 * <p>This is the only system that writes {@link io.github.sob1234509876.oc.rts.entity.Entity#setPosition}
 * for units, and the only one that may decide a unit arrived. Orders never move anything themselves; they
 * set a goal on {@link Motion} and wait for {@link Motion#isArrived()}, which is what lets a plain move,
 * a formation slot and a hauling trip share one implementation of steering.
 *
 * <p>The tick of one unit is deliberately ordered:
 * <ol>
 *   <li>consume waypoints that are close enough and notice arrival;</li>
 *   <li>ask {@link PathService} for a route when the unit has a goal but no route - never more often
 *       than the re-plan cooldown allows, and never when the goal is already inside the current tile,
 *       where walking straight there is both shorter and cheaper than a search;</li>
 *   <li>combine the seek velocity, the separation push and, while blocked, the formation pull into one
 *       velocity, capped and smoothed so a crowd cannot make a unit jitter;</li>
 *   <li>integrate the velocity, sliding along an obstacle when the direct step is blocked;</li>
 *   <li>write position, velocity, facing and the spatial index back;</li>
 *   <li>count the tick as stuck when the unit barely moved and, after a long enough run of such ticks,
 *       ask for a new route - a unit that is wedged behind a wall has to re-plan rather than push.</li>
 * </ol>
 *
 * <p>Only {@link Motion} and the entity position are written, plus the visual facing and the spatial
 * index, which are part of the movement contract. Anything else a system wants to know belongs in an
 * event or in the order queue.
 */
public final class MovementSystem implements SimulationSystem {

    /** Movement below this many tiles in a tick is not even worth writing back to the entity. */
    private static final float MOVEMENT_EPSILON = 1e-4f;
    /** Length of one terrain probe along a movement step, in tiles. */
    private static final float TERRAIN_SAMPLE_STEP = 0.25f;
    /** Upper bound on terrain probes per step, which keeps a teleport sized dt from costing a scan. */
    private static final int MAX_TERRAIN_SAMPLES = 8;

    private final World world;
    private final PathService pathService;
    private final SteeringSystem steering;
    private final SteeringSettings settings;

    public MovementSystem(World world, PathService pathService) {
        this(world, pathService, SteeringSettings.defaults());
    }

    public MovementSystem(World world, PathService pathService, SteeringSettings settings) {
        this.world = Objects.requireNonNull(world, "world");
        this.pathService = Objects.requireNonNull(pathService, "pathService");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.steering = new SteeringSystem(settings);
    }

    @Override
    public String name() {
        return "movement";
    }

    /** Runs after the order system and the path service, before production and logistics. */
    @Override
    public int order() {
        return 400;
    }

    /** Steering arithmetic used by this system; exposed so tests can reason about the same weights. */
    public SteeringSystem steering() {
        return steering;
    }

    public SteeringSettings settings() {
        return settings;
    }

    @Override
    public void tick(World world, Tick tick) {
        float deltaSeconds = tick.deltaSeconds();
        List<Unit> units = world.units();
        // Indexed loop on purpose: no iterator is allocated per tick and nothing in here spawns or
        // destroys units, so the list cannot change shape while it is walked.
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            if (!unit.isAlive()) {
                continue;
            }
            tickUnit(world, unit, deltaSeconds);
        }
    }

    private void tickUnit(World world, Unit unit, float deltaSeconds) {
        Motion motion = unit.motion();
        motion.tickRepathCooldown();
        Vec2 position = unit.position();
        // Neighbours come from the unit's own dimension: coordinates are local to a map, so the default
        // dimension's index would report entities that merely happen to stand at the same place elsewhere.
        List<Entity> neighbours =
                world.spatial(unit.dimensionId()).inRadius(position, steering.neighbourQueryRadius(unit));

        Vec2 target = updateTargetAndArrival(motion, position, unit.radius());
        if (motion.isMoving()) {
            requestPathIfNeeded(motion, unit, position);
        }

        // The environment scales movement: the Moon's lower gravity means faster legs, Earth stays neutral.
        float maxSpeed = unit.speed() * settings.maxSpeedMultiplier()
                * world.dimension(unit.dimensionId()).profile().movementSpeedFactor();
        Vec2 separation = steering.separation(unit, neighbours);
        Vec2 velocity;
        if (target != null) {
            float congestion = steering.congestionFactor(motion);
            Vec2 desired = steering.seek(unit, target).scaled(steering.congestionSpeedFactor(congestion));
            if (congestion > 0f && motion.isInFormation()) {
                desired = desired.plus(steering.formationCohesion(unit, neighbours).scaled(congestion));
            }
            velocity = steering.smooth(motion.velocity(), steering.clamp(desired.plus(separation), maxSpeed));
        } else {
            // Arrived or idle units still get pushed out of each other, but never keep leftover momentum:
            // an idle soldier that drifted would slowly wander across the map.
            velocity = steering.clamp(separation, maxSpeed);
        }

        Vec2 step = velocity.scaled(deltaSeconds);
        Vec2 resolved = resolveStep(world.map(unit.dimensionId()), position, step);
        float travelled = position.distance(resolved);
        if (travelled > MOVEMENT_EPSILON) {
            unit.setPosition(resolved);
            // Total distance is what scenarios and tests use to tell a detour from a straight walk.
            motion.addTravelledDistance(travelled);
        } else if (step.lengthSquared() > 0f) {
            // Wedged: report a standstill instead of a speed the unit clearly cannot use, so the
            // smoothing does not build up momentum while the unit is pressed against an obstacle.
            velocity = Vec2.ZERO;
        }

        float facing = facing(unit, motion, velocity, target, position);
        motion.setVelocity(velocity);
        motion.setFacing(facing);
        VisualState visual = unit.visual();
        if (visual != null) {
            visual.setFacing(facing);
        }
        world.spatial(unit.dimensionId()).update(unit);

        updateStuck(motion, travelled);
    }

    /**
     * Consumes the waypoints the unit already stands on and reports whether it arrived. Returns the
     * point to steer towards, or {@code null} when the unit should not steer at all this tick.
     */
    private Vec2 updateTargetAndArrival(Motion motion, Vec2 position, float radius) {
        if (!motion.isMoving()) {
            return null;
        }
        Vec2 target = motion.currentWaypoint();
        if (target != null && position.distance(target) <= settings.waypointRadius()) {
            motion.advanceWaypoint();
            target = motion.currentWaypoint();
        }
        Vec2 goal = motion.goal();
        if (position.distance(goal) <= motion.arrivalRadius() + radius) {
            // Arrival is measured against the goal, not against the end of the route: a formation slot
            // can sit in the same tile as another slot, so stopping at the last waypoint would stack
            // two soldiers on one tile.
            motion.markArrived();
            return null;
        }
        return target != null ? target : goal;
    }

    /**
     * Queues a search when the unit has a goal but nothing to follow. The goal tile check matters: once
     * a unit is inside the goal tile the remaining distance is at most a tile and a half and a search
     * would come back empty, so walking straight there is both faster and cheaper.
     */
    private void requestPathIfNeeded(Motion motion, Unit unit, Vec2 position) {
        if (motion.hasPath() || motion.repathCooldown() > 0) {
            return;
        }
        Vec2 goal = motion.goal();
        if (floor(goal.x()) == floor(position.x()) && floor(goal.y()) == floor(position.y())) {
            return;
        }
        // A pending flag without a matching request is stale - the unit was given a new goal - so the
        // old request has to be replaced instead of waited for.
        if (motion.isPathPending() && pathService.isPending(unit, goal)) {
            return;
        }
        pathService.request(unit, goal);
    }

    /** Counts consecutive ticks of no progress and asks for a new route once the limit is reached. */
    private void updateStuck(Motion motion, float travelled) {
        if (!motion.isMoving()) {
            motion.resetStuckTicks();
            return;
        }
        if (travelled >= settings.stuckDisplacementPerTick()) {
            motion.resetStuckTicks();
            return;
        }
        motion.addStuckTick();
        if (motion.stuckTicks() >= settings.stuckTicksBeforeRepath()) {
            motion.clearPath();
            motion.requestRepath(settings.stuckRepathCooldown());
            motion.resetStuckTicks();
        }
    }

    /**
     * Moves the unit by the step while respecting the terrain, sliding along an obstacle rather than
     * stopping dead at it. Returns the position unchanged when the step is blocked from every side.
     */
    private Vec2 resolveStep(TerrainMap map, Vec2 from, Vec2 step) {
        if (step.lengthSquared() <= 0f) {
            return from;
        }
        Vec2 candidate = from.plus(step);
        if (!isWalkable(map, from.x(), from.y())) {
            // Standing on an impossible tile (a map edit under a unit, a spawned building): any step that
            // leads away is an improvement, so the check is skipped until the unit is back on ground.
            return candidate;
        }
        if (isStepClear(map, from, candidate)) {
            return candidate;
        }
        Vec2 slideX = new Vec2(candidate.x(), from.y());
        if (isStepClear(map, from, slideX)) {
            return slideX;
        }
        Vec2 slideY = new Vec2(from.x(), candidate.y());
        if (isStepClear(map, from, slideY)) {
            return slideY;
        }
        return from;
    }

    /** Samples the step so a fast unit cannot tunnel through the corner of an obstacle. */
    private static boolean isStepClear(TerrainMap map, Vec2 from, Vec2 to) {
        float dx = to.x() - from.x();
        float dy = to.y() - from.y();
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        int samples = Math.min(MAX_TERRAIN_SAMPLES, Math.max(1, (int) Math.ceil(distance / TERRAIN_SAMPLE_STEP)));
        for (int i = 1; i <= samples; i++) {
            float t = i / (float) samples;
            if (!isWalkable(map, from.x() + dx * t, from.y() + dy * t)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWalkable(TerrainMap map, float x, float y) {
        return map.isPassable(floor(x), floor(y));
    }

    /**
     * Facing of the unit: where it is actually moving, otherwise where it wants to go, otherwise the
     * direction it already faced. Visuals use it to pick a directional animation, so keeping the last
     * value for a standing unit avoids a spin towards the origin.
     */
    private static float facing(Unit unit, Motion motion, Vec2 velocity, Vec2 target, Vec2 position) {
        if (velocity.lengthSquared() > 1e-6f) {
            return velocity.angle();
        }
        if (target != null) {
            float dx = target.x() - position.x();
            float dy = target.y() - position.y();
            if (dx * dx + dy * dy > 1e-6f) {
                return (float) Math.atan2(dy, dx);
            }
        }
        return motion.facing();
    }

    private static int floor(float value) {
        return (int) Math.floor(value);
    }
}
