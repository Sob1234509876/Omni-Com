package io.github.sob1234509876.oc.rts.movement;

import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Entity;
import io.github.sob1234509876.oc.rts.entity.Unit;

import java.util.List;
import java.util.Objects;

/**
 * Turns "go there, do not bump into anybody" into a velocity for one unit.
 *
 * <p>The steering system is deliberately a plain calculator without its own state: {@link
 * MovementSystem} owns the per tick loop, integrates the velocity and writes the entity position, while
 * this class only answers three questions - how fast should this unit move towards a target, how hard
 * should it push its neighbours away and how strongly should it drift back towards its formation. That
 * split keeps the arithmetic testable on its own and lets a future vehicle or naval movement system
 * reuse the same weights.
 *
 * <p>The weight of a separation force uses an inverse distance pressure rather than a thin linear ramp:
 * two units that almost coincide push each other hard, while units that merely brush past each other
 * feel nothing, which is what makes a crowd settle at roughly touching distance instead of oscillating
 * around the exact threshold. Two units standing on the exact same point have no direction to separate
 * along, so a deterministic angle derived from their ids is used instead - random would break replays
 * and, worse, would differ between clients of the same match.
 *
 * <p>All velocities are in tiles per second; the caller multiplies by the tick length.
 */
public final class SteeringSystem {

    /**
     * Radius assumed for neighbouring entities when the broad phase query radius is derived, in tiles.
     * Queries are cheap but not free, so the neighbourhood is searched with a bounded radius instead of
     * asking the spatial index for "everything".
     */
    public static final float NEIGHBOUR_RADIUS_ASSUMPTION = 1f;

    /** Number of distinct directions used to push two units that occupy the very same point apart. */
    private static final int COINCIDENT_DIRECTIONS = 64;
    private static final float COINCIDENT_ANGLE_STEP = (float) (Math.PI * 2.0 / COINCIDENT_DIRECTIONS);
    /** Distances below this are treated as "the same point", i.e. squared epsilon. */
    private static final float COINCIDENT_EPSILON_SQUARED = 1e-8f;

    private final SteeringSettings settings;

    public SteeringSystem(SteeringSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public SteeringSettings settings() {
        return settings;
    }

    /** Radius the spatial index should be queried with to see every entity that could push this unit. */
    public float neighbourQueryRadius(Unit unit) {
        return (unit.radius() + NEIGHBOUR_RADIUS_ASSUMPTION) * settings.separationRadiusScale();
    }

    /**
     * Desired velocity in tiles per second towards the target, already slowed down for the final
     * approach so a unit settles on its goal instead of shooting past it and turning around.
     */
    public Vec2 seek(Unit unit, Vec2 target) {
        Vec2 position = unit.position();
        float dx = target.x() - position.x();
        float dy = target.y() - position.y();
        float distanceSquared = dx * dx + dy * dy;
        if (distanceSquared <= 1e-8f) {
            return Vec2.ZERO;
        }
        float distance = (float) Math.sqrt(distanceSquared);
        float factor = arrivalSpeedFactor(position, target, unit.radius());
        if (factor <= 0f) {
            return Vec2.ZERO;
        }
        float speed = unit.speed() * factor / distance;
        return new Vec2(dx * speed, dy * speed);
    }

    /**
     * Fraction of full speed a unit at {@code position} keeps while heading for {@code target}. Braking
     * starts {@link SteeringSettings#arrivalSlowdownDistance()} before the goal and reaches zero half a
     * radius short of it, which is what removes the last bit of overshoot jitter.
     */
    public float arrivalSpeedFactor(Vec2 position, Vec2 target, float radius) {
        float slowdown = settings.arrivalSlowdownDistance() + radius;
        float stop = Math.max(0.05f, radius * 0.5f);
        float distance = position.distance(target);
        if (distance >= slowdown) {
            return 1f;
        }
        if (distance <= stop) {
            return 0f;
        }
        float ramp = (distance - stop) / (slowdown - stop);
        return settings.arrivalSpeedFloor() + (1f - settings.arrivalSpeedFloor()) * ramp;
    }

    /**
     * Velocity that pushes the unit away from the neighbours it overlaps and is exactly zero when it
     * touches nobody, so an idle group never drifts.
     *
     * @param neighbours everything the broad phase returned around the unit, including the unit itself
     */
    public Vec2 separation(Unit unit, List<Entity> neighbours) {
        Vec2 position = unit.position();
        float radius = unit.radius();
        float scale = settings.separationRadiusScale();
        float pressureCap = settings.separationPressureCap();
        float sumX = 0f;
        float sumY = 0f;
        for (int i = 0; i < neighbours.size(); i++) {
            Entity other = neighbours.get(i);
            if (other == unit || !other.isAlive()) {
                continue;
            }
            float threshold = (radius + other.radius()) * scale;
            if (threshold <= 0f) {
                continue;
            }
            Vec2 delta = position.minus(other.position());
            float distanceSquared = delta.lengthSquared();
            if (distanceSquared >= threshold * threshold) {
                continue;
            }
            float directionX;
            float directionY;
            float pressure;
            if (distanceSquared <= COINCIDENT_EPSILON_SQUARED) {
                float angle = coincidentAngle(unit.id().value(), other.id().value());
                directionX = (float) Math.cos(angle);
                directionY = (float) Math.sin(angle);
                pressure = pressureCap;
            } else {
                float distance = (float) Math.sqrt(distanceSquared);
                float inverse = 1f / distance;
                directionX = delta.x() * inverse;
                directionY = delta.y() * inverse;
                pressure = Math.min(pressureCap, threshold * inverse - 1f);
            }
            sumX += directionX * pressure;
            sumY += directionY * pressure;
        }
        float strength = unit.speed() * settings.separationStrength();
        return new Vec2(sumX * strength, sumY * strength);
    }

    /**
     * Pull towards the average position of the units of the same formation that are close by.
     *
     * <p>A marching formation that meets an obstacle would otherwise fan out, because every unit only
     * looks after itself. This term makes the group compress towards its own centre instead, so the
     * line stays a line while it squeezes through a gap. It is weighted by congestion, so it only has
     * an effect while a unit is actually blocked and never disturbs the shape of a formation that has
     * room to spread.
     */
    public Vec2 formationCohesion(Unit unit, List<Entity> neighbours) {
        int formationId = unit.motion().formationId();
        if (formationId == 0) {
            return Vec2.ZERO;
        }
        Vec2 position = unit.position();
        float sumX = 0f;
        float sumY = 0f;
        int count = 0;
        for (int i = 0; i < neighbours.size(); i++) {
            Entity other = neighbours.get(i);
            if (other == unit || !other.isAlive() || !(other instanceof Unit neighbour)) {
                continue;
            }
            if (neighbour.motion().formationId() != formationId) {
                continue;
            }
            sumX += neighbour.position().x();
            sumY += neighbour.position().y();
            count++;
        }
        if (count == 0) {
            return Vec2.ZERO;
        }
        float averageX = sumX / count;
        float averageY = sumY / count;
        float dx = averageX - position.x();
        float dy = averageY - position.y();
        float distanceSquared = dx * dx + dy * dy;
        if (distanceSquared <= 1e-8f) {
            return Vec2.ZERO;
        }
        float distance = (float) Math.sqrt(distanceSquared);
        float speed = unit.speed() * settings.formationCohesionWeight() / distance;
        return new Vec2(dx * speed, dy * speed);
    }

    /**
     * How blocked a unit is, from {@code 0} for free movement to {@code 1} when it has been making no
     * progress for {@link SteeringSettings#stuckTicksBeforeRepath()} ticks. Movement uses it to slow
     * down and to switch the formation cohesion on, which is what stops a jammed group from shoving.
     */
    public float congestionFactor(Motion motion) {
        int stuck = motion.stuckTicks();
        if (stuck <= 0) {
            return 0f;
        }
        float factor = stuck / (float) settings.stuckTicksBeforeRepath();
        return Math.min(1f, factor);
    }

    /** Speed a congested unit keeps: {@code 1} when it is free, down to the configured floor when jammed. */
    public float congestionSpeedFactor(float congestion) {
        return 1f - (1f - settings.congestionSpeedFloor()) * Math.min(1f, Math.max(0f, congestion));
    }

    /** Limits a velocity to the given length, keeping its direction. */
    public Vec2 clamp(Vec2 velocity, float maxSpeed) {
        float lengthSquared = velocity.lengthSquared();
        float maxSquared = maxSpeed * maxSpeed;
        if (lengthSquared <= maxSquared || lengthSquared == 0f) {
            return velocity;
        }
        return velocity.scaled(maxSpeed / (float) Math.sqrt(lengthSquared));
    }

    /**
     * Moves the previous velocity a fraction of the way towards the desired one. Steering without this
     * makes units snap between full speed and standstill, which looks like jitter and, worse, makes
     * two units in contact trade the same push back and forth every tick.
     */
    public Vec2 smooth(Vec2 previous, Vec2 desired) {
        return previous.lerp(desired, settings.velocitySmoothing());
    }

    /**
     * Deterministic direction used when two units occupy the exact same point. The result depends only
     * on the pair of ids and is antisymmetric, so the push on unit A is the exact opposite of the push
     * on unit B and the crowd gains no momentum out of nowhere.
     */
    private static float coincidentAngle(long first, long second) {
        long low = Math.min(first, second);
        long high = Math.max(first, second);
        long hash = low * 0x9E3779B97F4A7C15L + high * 0xC2B2AE3D27D4EB4FL;
        hash ^= hash >>> 29;
        int bucket = Math.floorMod(hash, COINCIDENT_DIRECTIONS);
        float angle = bucket * COINCIDENT_ANGLE_STEP;
        return first == low ? angle : angle + (float) Math.PI;
    }
}
