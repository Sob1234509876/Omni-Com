package io.github.sob1234509876.oc.rts.movement;

/**
 * Tuning of {@link SteeringSystem}: how hard units push each other apart, when they start to slow
 * down for their goal and how long the movement system tolerates a unit that makes no progress.
 *
 * <p>All knobs live in one immutable record because they interact. A large {@code separationStrength}
 * with a small {@code velocitySmoothing} makes a crowd jitter, a large {@code waypointRadius} makes
 * units cut corners into obstacles and a small {@code congestionSpeedFloor} lets a jammed group crawl
 * to a halt. Keeping them together gives a single place to reason about - and a single object to pass
 * to a scenario or a test that wants a different balance.
 *
 * <p>Everything is expressed in tiles and in multiples of the unit speed, never in ticks, so the same
 * settings behave identically at any tick rate.
 *
 * @param separationRadiusScale multiplier on the sum of two radii at which separation starts; {@code 1}
 *                              means units begin to push each other exactly when their bodies touch.
 * @param separationStrength    how much of its own speed a unit may spend pushing neighbours away.
 * @param separationPressureCap largest pressure one neighbour may contribute, which stops a single
 *                              almost coincident unit from saturating the whole steering vector.
 * @param arrivalSlowdownDistance distance to the goal in tiles at which a unit starts to brake.
 * @param arrivalSpeedFloor     fraction of full speed a unit still has when it is right at its goal.
 * @param maxSpeedMultiplier    hard cap on the length of the steering vector, as a multiple of the unit
 *                              speed; separation may briefly exceed the cruise speed but never this.
 * @param velocitySmoothing     fraction of the desired velocity adopted per tick, which turns both
 *                              acceleration and separation into a smooth ramp instead of a jerky jump.
 * @param waypointRadius        distance at which a waypoint counts as reached and is consumed.
 * @param formationCohesionWeight how strongly a blocked unit is pulled towards its formation neighbours.
 * @param congestionSpeedFloor  fraction of full speed a fully congested unit keeps; above zero so a
 *                              crowd keeps flowing instead of deadlocking.
 * @param stuckTicksBeforeRepath consecutive ticks of negligible movement after which the unit is
 *                              considered stuck and asks for a new path.
 * @param stuckDisplacementPerTick movement in tiles below which a tick counts as "no progress".
 * @param stuckRepathCooldown   ticks a unit waits after it was declared stuck before it may ask for a
 *                              path again, which bounds how often a jammed group re-plans.
 */
public record SteeringSettings(
        float separationRadiusScale,
        float separationStrength,
        float separationPressureCap,
        float arrivalSlowdownDistance,
        float arrivalSpeedFloor,
        float maxSpeedMultiplier,
        float velocitySmoothing,
        float waypointRadius,
        float formationCohesionWeight,
        float congestionSpeedFloor,
        int stuckTicksBeforeRepath,
        float stuckDisplacementPerTick,
        int stuckRepathCooldown) {

    /** Factor on the sum of two radii at which units start to push each other apart. */
    public static final float DEFAULT_SEPARATION_RADIUS_SCALE = 1f;
    /** How much of its own speed a unit spends on pushing neighbours away. */
    public static final float DEFAULT_SEPARATION_STRENGTH = 1f;
    /** Largest pressure a single neighbour may contribute. */
    public static final float DEFAULT_SEPARATION_PRESSURE_CAP = 3f;
    /** Distance in tiles at which a unit starts to brake for its goal. */
    public static final float DEFAULT_ARRIVAL_SLOWDOWN_DISTANCE = 1.5f;
    /** Fraction of full speed kept right at the goal. */
    public static final float DEFAULT_ARRIVAL_SPEED_FLOOR = 0.25f;
    /** Hard cap on the steering vector, as a multiple of the unit speed. */
    public static final float DEFAULT_MAX_SPEED_MULTIPLIER = 1.35f;
    /** Fraction of the desired velocity adopted per tick. */
    public static final float DEFAULT_VELOCITY_SMOOTHING = 0.5f;
    /** Distance at which a waypoint counts as reached. */
    public static final float DEFAULT_WAYPOINT_RADIUS = 0.35f;
    /** Strength of the pull towards formation neighbours while a unit is blocked. */
    public static final float DEFAULT_FORMATION_COHESION_WEIGHT = 0.4f;
    /** Fraction of full speed a fully congested unit keeps. */
    public static final float DEFAULT_CONGESTION_SPEED_FLOOR = 0.35f;
    /** Consecutive ticks of negligible movement after which a unit is stuck. */
    public static final int DEFAULT_STUCK_TICKS_BEFORE_REPATH = 90;
    /** Movement below this many tiles per tick counts as no progress. */
    public static final float DEFAULT_STUCK_DISPLACEMENT_PER_TICK = 0.02f;
    /** Ticks a stuck unit waits before it may re-plan its route. */
    public static final int DEFAULT_STUCK_REPATH_COOLDOWN = 60;

    /**
     * Validates the settings; a value that would make the steering unusable is rejected here instead of
     * producing units that silently never move.
     */
    public SteeringSettings {
        requirePositive(separationRadiusScale, "separationRadiusScale");
        requirePositive(separationStrength, "separationStrength");
        requirePositive(separationPressureCap, "separationPressureCap");
        requirePositive(arrivalSlowdownDistance, "arrivalSlowdownDistance");
        if (arrivalSpeedFloor < 0f || arrivalSpeedFloor >= 1f) {
            throw new IllegalArgumentException("arrivalSpeedFloor must be in [0, 1), was " + arrivalSpeedFloor);
        }
        if (maxSpeedMultiplier < 1f) {
            throw new IllegalArgumentException("maxSpeedMultiplier must be at least 1, was " + maxSpeedMultiplier);
        }
        if (velocitySmoothing <= 0f || velocitySmoothing > 1f) {
            throw new IllegalArgumentException("velocitySmoothing must be in (0, 1], was " + velocitySmoothing);
        }
        requirePositive(waypointRadius, "waypointRadius");
        if (formationCohesionWeight < 0f) {
            throw new IllegalArgumentException("formationCohesionWeight must not be negative, was "
                    + formationCohesionWeight);
        }
        if (congestionSpeedFloor <= 0f || congestionSpeedFloor > 1f) {
            throw new IllegalArgumentException("congestionSpeedFloor must be in (0, 1], was " + congestionSpeedFloor);
        }
        if (stuckTicksBeforeRepath < 1) {
            throw new IllegalArgumentException("stuckTicksBeforeRepath must be positive, was " + stuckTicksBeforeRepath);
        }
        requirePositive(stuckDisplacementPerTick, "stuckDisplacementPerTick");
        if (stuckRepathCooldown < 0) {
            throw new IllegalArgumentException("stuckRepathCooldown must not be negative, was " + stuckRepathCooldown);
        }
    }

    /** Balanced settings for ground units of roughly one third of a tile in radius. */
    public static SteeringSettings defaults() {
        return new SteeringSettings(
                DEFAULT_SEPARATION_RADIUS_SCALE,
                DEFAULT_SEPARATION_STRENGTH,
                DEFAULT_SEPARATION_PRESSURE_CAP,
                DEFAULT_ARRIVAL_SLOWDOWN_DISTANCE,
                DEFAULT_ARRIVAL_SPEED_FLOOR,
                DEFAULT_MAX_SPEED_MULTIPLIER,
                DEFAULT_VELOCITY_SMOOTHING,
                DEFAULT_WAYPOINT_RADIUS,
                DEFAULT_FORMATION_COHESION_WEIGHT,
                DEFAULT_CONGESTION_SPEED_FLOOR,
                DEFAULT_STUCK_TICKS_BEFORE_REPATH,
                DEFAULT_STUCK_DISPLACEMENT_PER_TICK,
                DEFAULT_STUCK_REPATH_COOLDOWN);
    }

    /** Same settings with a different separation radius factor, for tighter or looser crowds. */
    public SteeringSettings withSeparationRadiusScale(float value) {
        return new SteeringSettings(value, separationStrength, separationPressureCap, arrivalSlowdownDistance,
                arrivalSpeedFloor, maxSpeedMultiplier, velocitySmoothing, waypointRadius, formationCohesionWeight,
                congestionSpeedFloor, stuckTicksBeforeRepath, stuckDisplacementPerTick, stuckRepathCooldown);
    }

    /** Same settings with a different cap on the steering vector. */
    public SteeringSettings withMaxSpeedMultiplier(float value) {
        return new SteeringSettings(separationRadiusScale, separationStrength, separationPressureCap,
                arrivalSlowdownDistance, arrivalSpeedFloor, value, velocitySmoothing, waypointRadius,
                formationCohesionWeight, congestionSpeedFloor, stuckTicksBeforeRepath, stuckDisplacementPerTick,
                stuckRepathCooldown);
    }

    /** Same settings with a different stuck threshold, handy for tests that must not wait 90 ticks. */
    public SteeringSettings withStuckTicksBeforeRepath(int value) {
        return new SteeringSettings(separationRadiusScale, separationStrength, separationPressureCap,
                arrivalSlowdownDistance, arrivalSpeedFloor, maxSpeedMultiplier, velocitySmoothing, waypointRadius,
                formationCohesionWeight, congestionSpeedFloor, value, stuckDisplacementPerTick,
                stuckRepathCooldown);
    }

    private static void requirePositive(float value, String name) {
        if (!(value > 0f)) {
            throw new IllegalArgumentException(name + " must be positive, was " + value);
        }
    }
}
