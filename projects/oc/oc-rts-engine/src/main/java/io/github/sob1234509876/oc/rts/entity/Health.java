package io.github.sob1234509876.oc.rts.entity;

/**
 * Hit points of an entity, kept in a small mutable value object so that damage and healing are the
 * only ways to change them.
 */
public final class Health {

    private final float max;
    private float current;

    public Health(float max) {
        this(max, max);
    }

    public Health(float max, float current) {
        if (max <= 0f) {
            throw new IllegalArgumentException("max health must be positive, was " + max);
        }
        this.max = max;
        this.current = Math.max(0f, Math.min(max, current));
    }

    public float max() {
        return max;
    }

    public float current() {
        return current;
    }

    /** Remaining health as a fraction of the maximum, in {@code [0, 1]}. */
    public float fraction() {
        return current / max;
    }

    public boolean isDead() {
        return current <= 0f;
    }

    /** Applies damage and returns how much was actually dealt. */
    public float damage(float amount) {
        if (amount <= 0f) {
            return 0f;
        }
        float dealt = Math.min(current, amount);
        current -= dealt;
        return dealt;
    }

    /** Restores health and returns how much was actually healed. */
    public float heal(float amount) {
        if (amount <= 0f) {
            return 0f;
        }
        float healed = Math.min(max - current, amount);
        current += healed;
        return healed;
    }

    public void setCurrent(float value) {
        this.current = Math.max(0f, Math.min(max, value));
    }

    public void restore() {
        this.current = max;
    }

    @Override
    public String toString() {
        return String.format("%.0f/%.0f", current, max);
    }
}
