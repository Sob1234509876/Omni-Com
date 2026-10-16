package io.github.sob1234509876.oc.rts.dimension;

import java.util.Objects;

/**
 * The environment of a dimension: everything that makes the Moon different from Mars or from the overworld.
 *
 * <p>The profile is plain data. It travels with the dimension, shows up in snapshots so a renderer can tint a
 * sky or label a map, and gives the systems something to read; the engine itself only acts on
 * {@link #movementSpeedFactor()} today, so a profile with the defaults behaves exactly like the world did
 * before dimensions existed.
 *
 * @param displayName        name for logs, debug output and the future GUI
 * @param gravity            surface gravity relative to the overworld, {@code 1.0} being Earth like
 * @param breathable         whether an unprotected unit can breathe here
 * @param movementSpeedFactor multiplier applied to unit speed; {@code 1.0} is neutral
 * @param hazardDamagePerTick damage per tick dealt to unprotected units, zero when the place is harmless
 */
public record DimensionProfile(String displayName, float gravity, boolean breathable,
        float movementSpeedFactor, long hazardDamagePerTick) {

    public DimensionProfile {
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("dimension display name must not be blank");
        }
        if (gravity < 0f) {
            throw new IllegalArgumentException("gravity must not be negative, was " + gravity);
        }
        if (movementSpeedFactor <= 0f) {
            throw new IllegalArgumentException(
                    "movement speed factor must be positive, was " + movementSpeedFactor);
        }
        if (hazardDamagePerTick < 0L) {
            throw new IllegalArgumentException(
                    "hazard damage must not be negative, was " + hazardDamagePerTick);
        }
    }

    /** A neutral profile: Earth gravity, breathable air, normal speed, no hazard. */
    public static DimensionProfile earth() {
        return new DimensionProfile("Earth", 1f, true, 1f, 0L);
    }

    /** An airless, low gravity rock: faster movement, no breathing, a little radiation damage. */
    public static DimensionProfile moon() {
        return new DimensionProfile("Moon", 0.166f, false, 1.25f, 1L);
    }

    /** A thin atmosphere, medium gravity, dust: slightly faster movement, mild hazard. */
    public static DimensionProfile mars() {
        return new DimensionProfile("Mars", 0.377f, false, 1.1f, 1L);
    }

    /** Convenience for a harmless, breathable map with a custom name and otherwise Earth defaults. */
    public static DimensionProfile of(String displayName) {
        return new DimensionProfile(displayName, 1f, true, 1f, 0L);
    }

    /** Whether an unprotected unit takes damage in this dimension. */
    public boolean hazardous() {
        return hazardDamagePerTick > 0L;
    }

    /** Whether the environment forces units to be sealed or suited. */
    public boolean requiresSealedUnits() {
        return !breathable || hazardous();
    }
}
