package io.github.sob1234509876.oc.rts.entity;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.googology.NaturalFormat;

/**
 * Hit points of an entity, kept in a small mutable value object so that damage and healing are the only
 * ways to change them.
 *
 * <p>Hit points are {@link Natural} values: an ordinary unit is an exact integer, while a creature or a
 * building that a scenario declares with an absurd amount of health keeps its magnitude without needing
 * a number type that can actually hold the digits. The remaining share is exposed as a float for health
 * bars, because a renderer draws a ratio, not a number.
 */
public final class Health {

    private final Natural max;
    private Natural current;

    public Health(Natural max) {
        this(max, max);
    }

    public Health(Natural max, Natural current) {
        if (max.isZero()) {
            throw new IllegalArgumentException("max health must be positive");
        }
        this.max = max;
        this.current = current.min(max);
    }

    /** Convenience constructor for hit points that fit a long. */
    public Health(long max) {
        this(Natural.of(max));
    }

    public Natural max() {
        return max;
    }

    public Natural current() {
        return current;
    }

    /** Remaining health as a fraction of the maximum, in {@code [0, 1]}. */
    public float fraction() {
        return current.fractionOf(max);
    }

    public boolean isDead() {
        return current.isZero();
    }

    /** Applies damage and returns how much was actually dealt. */
    public Natural damage(Natural amount) {
        Natural dealt = current.min(amount);
        current = current.subtractClamped(dealt);
        return dealt;
    }

    /** Convenience for {@link #damage(Natural)} with a small literal. */
    public Natural damage(long amount) {
        return damage(Natural.of(amount));
    }

    /** Restores health and returns how much was actually healed. */
    public Natural heal(Natural amount) {
        Natural healed = max.subtractClamped(current).min(amount);
        current = current.add(healed);
        return healed;
    }

    /** Convenience for {@link #heal(Natural)} with a small literal. */
    public Natural heal(long amount) {
        return heal(Natural.of(amount));
    }

    public void setCurrent(Natural value) {
        this.current = value.min(max);
    }

    public void setCurrent(long value) {
        setCurrent(Natural.of(value));
    }

    public void restore() {
        this.current = max;
    }

    @Override
    public String toString() {
        return NaturalFormat.scientific(current) + "/" + NaturalFormat.scientific(max);
    }
}
