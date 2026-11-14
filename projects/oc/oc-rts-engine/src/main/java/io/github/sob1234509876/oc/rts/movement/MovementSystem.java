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
 *       velocity, capped and accelerated so a unit builds its speed up over the configured ramp instead
 *       of snapping between standstill and cruise;</li>
 *   <li>integrate the velocity, sliding along an obstacle when the direct step is blocked, with the
 *       unit's body kept clear of building footprints on both axes alike;</li>
 *   <li>write position, velocity, facing and the spatial index back;</li>
 *   <li>count the tick as stuck when the unit barely moved and, after a long enough run of such ticks,
 *       ask for a new route - and re-plan even sooner when the straight step ahead is blocked by an
 *       obstacle, so a unit that grinds against a wall detours within a few ticks.</li>
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
    /** How far ahead of the body the blocked probe looks, in tiles. */
    private static final float BLOCKED_PROBE_DISTANCE = 0.5f;

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

        // While combat holds the unit it stands still to fight: no steering, no waypoint consumption and
        // no arrival decision, so the paused order resumes exactly where it stopped.
        boolean combatPaused = motion.isCombatPaused();
        Vec2 target = combatPaused ? null : updateTargetAndArrival(motion, position, unit.radius());
        if (!combatPaused && motion.isMoving()) {
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
            // Acceleration is the one place a velocity ramp exists: the unit builds its speed up over
            // the configured acceleration time and brakes the same way into the goal.
            velocity = steering.accelerate(motion.velocity(),
                    steering.clamp(desired.plus(separation), maxSpeed), maxSpeed, deltaSeconds);
        } else {
            // Arrived or idle units still get pushed out of each other, but never keep leftover momentum:
            // an idle soldier that drifted would slowly wander across the map.
            velocity = steering.clamp(separation, maxSpeed);
        }

        Vec2 step = velocity.scaled(deltaSeconds);
        // The step is resolved against the obstacle-aware view: a building footprint is a wall, so the
        // unit slides along it instead of walking across it, matching what its path may use.
        Vec2 resolved = resolveStep(world.dimension(unit.dimensionId()), position, step, unit.radius());
        float travelled = position.distance(resolved);
        if (travelled > MOVEMENT_EPSILON) {
            unit.setPosition(resolved);
            // Total distance is what scenarios and tests use to tell a detour from a straight walk.
            motion.addTravelledDistance(travelled);
        } else if (step.lengthSquared() > 0f) {
            // Wedged: report a standstill instead of a speed the unit clearly cannot use, so the
            // acceleration does not build up momentum while the unit is pressed against an obstacle.
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
        if (!combatPaused) {
            updateBlocked(world.dimension(unit.dimensionId()), unit, motion, position, target);
        }
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
        if (!motion.isMoving() || motion.isCombatPaused()) {
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
     * Repaths promptly when the straight step towards the steering target is blocked by an obstacle.
     *
     * <p>The stuck counter above measures displacement, so a unit that slides along a wall without
     * closing in on its target never fills it. This probe looks one step ahead instead: when the tile the
     * unit is heading into is an obstacle for a run of {@link SteeringSettings#blockedTicksBeforeRepath()}
     * ticks, the route is dropped and a fresh search is requested straight away - not paced by the
     * re-plan cooldown, which a trigger here would otherwise keep extending and so defer forever. The
     * obstacle layer answers the fresh search with a detour around the building; a search that fails
     * again sets its own cooldown through the path service, which bounds the retry cost.
     */
    private void updateBlocked(io.github.sob1234509876.oc.rts.dimension.Dimension dimension, Unit unit,
            Motion motion, Vec2 position, Vec2 target) {
        if (!motion.isMoving() || target == null) {
            motion.resetBlockedTicks();
            return;
        }
        Vec2 delta = target.minus(position);
        float length = delta.length();
        if (length <= 1e-6f) {
            motion.resetBlockedTicks();
            return;
        }
        // One body-sized step ahead on the direct line to the target; the probe uses the obstacle view,
        // so a building footprint counts even though the ground under it is plain walkable terrain.
        float probe = Math.min(length, BLOCKED_PROBE_DISTANCE);
        Vec2 ahead = position.plus(delta.scaled(probe / length));
        if (!dimension.pathTerrain().isPassable(floor(ahead.x()), floor(ahead.y()))) {
            motion.addBlockedTick();
            if (motion.blockedTicks() >= settings.blockedTicksBeforeRepath()) {
                motion.clearPath();
                pathService.request(unit, motion.goal());
                motion.resetBlockedTicks();
                motion.resetStuckTicks();
            }
            return;
        }
        motion.resetBlockedTicks();
    }

    /**
     * Moves the unit by the step while respecting the terrain, sliding along an obstacle rather than
     * stopping dead at it. Returns the position unchanged when the step is blocked from every side.
     *
     * <p>The collision rule is symmetric on both axes and reads: a walking unit's body may not overlap
     * any building footprint tile. Terrain passability stays a centre rule, exactly as before, so
     * behaviour on open ground, in water and on one tile wide terrain corridors is unchanged. The body
     * rule closes the asymmetry where a unit's radius let it clip a building edge with half of its
     * diameter while its centre tile was still walkable - which slipped vertically along a bottom edge
     * but ground to a halt along a side wall, depending on which tile the centre happened to enter. A
     * unit whose body already overlaps a footprint (a building completed on top of it) keeps the
     * historical escape hatch: every step away is an improvement, so the body rule is skipped until the
     * unit is clear again.
     */
    private Vec2 resolveStep(io.github.sob1234509876.oc.rts.dimension.Dimension dimension, Vec2 from, Vec2 step,
            float radius) {
        TerrainMap map = dimension.pathTerrain();
        if (step.lengthSquared() <= 0f) {
            return from;
        }
        Vec2 candidate = from.plus(step);
        if (!isWalkable(map, from.x(), from.y())) {
            // Standing on an impossible tile (a map edit under a unit, a spawned building): any step that
            // leads away is an improvement, so the check is skipped until the unit is back on ground.
            return candidate;
        }
        if (isStepClear(dimension, radius, from, candidate)) {
            return candidate;
        }
        Vec2 slideX = new Vec2(candidate.x(), from.y());
        if (isStepClear(dimension, radius, from, slideX)) {
            return slideX;
        }
        Vec2 slideY = new Vec2(from.x(), candidate.y());
        if (isStepClear(dimension, radius, from, slideY)) {
            return slideY;
        }
        return from;
    }

    /** Samples the step so a fast unit cannot tunnel through the corner of an obstacle. */
    private static boolean isStepClear(io.github.sob1234509876.oc.rts.dimension.Dimension dimension, float radius,
            Vec2 from, Vec2 to) {
        TerrainMap map = dimension.pathTerrain();
        float dx = to.x() - from.x();
        float dy = to.y() - from.y();
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        int samples = Math.min(MAX_TERRAIN_SAMPLES, Math.max(1, (int) Math.ceil(distance / TERRAIN_SAMPLE_STEP)));
        for (int i = 1; i <= samples; i++) {
            float t = i / (float) samples;
            float x = from.x() + dx * t;
            float y = from.y() + dy * t;
            if (!isWalkable(map, x, y)) {
                return false;
            }
            if (bodyEntersObstacle(dimension, radius, from, x, y)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the disc of the given radius around {@code (x, y)} overlaps a building footprint tile that
     * the body around {@code from} does not overlap already. The bounding tiles of the disc are checked,
     * which is exact for the axis aligned footprints the obstacle layer stores.
     */
    private static boolean bodyEntersObstacle(io.github.sob1234509876.oc.rts.dimension.Dimension dimension,
            float radius, Vec2 from, float x, float y) {
        int minX = floor(x - radius);
        int maxX = floor(x + radius);
        int minY = floor(y - radius);
        int maxY = floor(y + radius);
        for (int ty = minY; ty <= maxY; ty++) {
            for (int tx = minX; tx <= maxX; tx++) {
                if (!dimension.isObstacleTile(tx, ty)) {
                    continue;
                }
                // A tile the body already overlaps at the current position cannot block the step, or a
                // unit standing with its edge inside a footprint could never move at all.
                int fromMinX = floor(from.x() - radius);
                int fromMaxX = floor(from.x() + radius);
                int fromMinY = floor(from.y() - radius);
                int fromMaxY = floor(from.y() + radius);
                if (tx >= fromMinX && tx <= fromMaxX && ty >= fromMinY && ty <= fromMaxY) {
                    continue;
                }
                return true;
            }
        }
        return false;
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
