package io.github.sob1234509876.oc.rts.core;

/**
 * Immutable two dimensional vector in world units, where one unit is exactly one map tile.
 *
 * <p>All arithmetic is plain {@code float} math and therefore deterministic on every platform, which
 * matters because the simulation must be reproducible from a seed and a command stream.
 */
public record Vec2(float x, float y) {

    /** The origin. */
    public static final Vec2 ZERO = new Vec2(0f, 0f);

    public static Vec2 of(float x, float y) {
        return new Vec2(x, y);
    }

    /** Center point of the given tile, i.e. {@code (x + 0.5, y + 0.5)}. */
    public static Vec2 centerOf(GridPos tile) {
        return new Vec2(tile.x() + 0.5f, tile.y() + 0.5f);
    }

    /** Unit vector pointing in the given direction, in radians. */
    public static Vec2 fromAngle(float radians) {
        return new Vec2((float) Math.cos(radians), (float) Math.sin(radians));
    }

    public Vec2 plus(Vec2 other) {
        return new Vec2(x + other.x, y + other.y);
    }

    public Vec2 plus(float dx, float dy) {
        return new Vec2(x + dx, y + dy);
    }

    public Vec2 minus(Vec2 other) {
        return new Vec2(x - other.x, y - other.y);
    }

    public Vec2 scaled(float factor) {
        return new Vec2(x * factor, y * factor);
    }

    /** Counts down towards zero without overshooting, useful for arrival tests. */
    public Vec2 movedTowards(Vec2 target, float maxDistance) {
        Vec2 delta = target.minus(this);
        float length = delta.length();
        if (length <= maxDistance || length == 0f) {
            return target;
        }
        return plus(delta.scaled(maxDistance / length));
    }

    public float length() {
        return (float) Math.sqrt(x * x + y * y);
    }

    public float lengthSquared() {
        return x * x + y * y;
    }

    public float distance(Vec2 other) {
        return minus(other).length();
    }

    public float distanceSquared(Vec2 other) {
        return minus(other).lengthSquared();
    }

    public float dot(Vec2 other) {
        return x * other.x + y * other.y;
    }

    /** Same direction with length one, or {@link #ZERO} for a zero length vector. */
    public Vec2 normalized() {
        float length = length();
        return length == 0f ? ZERO : scaled(1f / length);
    }

    /** Same direction with the given length, or {@link #ZERO} when this vector has no direction. */
    public Vec2 withLength(float newLength) {
        return normalized().scaled(newLength);
    }

    /** Rotated ninety degrees counter clockwise. */
    public Vec2 perpendicular() {
        return new Vec2(-y, x);
    }

    public Vec2 rotated(float radians) {
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        return new Vec2(x * cos - y * sin, x * sin + y * cos);
    }

    public Vec2 lerp(Vec2 target, float t) {
        return new Vec2(x + (target.x - x) * t, y + (target.y - y) * t);
    }

    /** Angle of this vector in radians, in {@code (-pi, pi]}. */
    public float angle() {
        return (float) Math.atan2(y, x);
    }

    /** Tile containing this point. */
    public GridPos tile() {
        return new GridPos((int) Math.floor(x), (int) Math.floor(y));
    }
}
