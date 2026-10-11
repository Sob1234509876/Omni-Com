package io.github.sob1234509876.oc.rts.core;

/**
 * The eight compass directions used by sprites, formations and coarse facing logic.
 *
 * <p>Angles are radians in the same convention as {@link Vec2#angle()}: zero points east and the
 * angle grows counter clockwise, so {@code NORTH} is {@code +pi/2}.
 */
public enum Direction {
    NORTH(0, 1, "north"),
    NORTH_EAST(1, 1, "north_east"),
    EAST(1, 0, "east"),
    SOUTH_EAST(1, -1, "south_east"),
    SOUTH(0, -1, "south"),
    SOUTH_WEST(-1, -1, "south_west"),
    WEST(-1, 0, "west"),
    NORTH_WEST(-1, 1, "north_west");

    private static final Direction[] CLOCKWISE = {EAST, NORTH_EAST, NORTH, NORTH_WEST, WEST, SOUTH_WEST, SOUTH, SOUTH_EAST};

    private final int dx;
    private final int dy;
    private final String id;

    Direction(int dx, int dy, String id) {
        this.dx = dx;
        this.dy = dy;
        this.id = id;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    /** Stable identifier usable in resource keys and animation names. */
    public String id() {
        return id;
    }

    /** Angle of this direction in radians. */
    public float angle() {
        return (float) Math.atan2(dy, dx);
    }

    public Direction opposite() {
        return CLOCKWISE[(ordinal() + 4) % 8];
    }

    /** Direction whose angle is closest to the given angle in radians. */
    public static Direction fromAngle(float radians) {
        int index = Math.round(radians / (float) (Math.PI / 4.0));
        return CLOCKWISE[Math.floorMod(index, 8)];
    }

    /** Direction of the given vector, or {@link #EAST} for a zero length vector. */
    public static Direction fromVector(float dx, float dy) {
        if (dx == 0f && dy == 0f) {
            return EAST;
        }
        return fromAngle((float) Math.atan2(dy, dx));
    }
}
