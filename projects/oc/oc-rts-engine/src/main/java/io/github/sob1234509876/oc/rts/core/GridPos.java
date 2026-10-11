package io.github.sob1234509876.oc.rts.core;

import java.util.List;

/**
 * Integer coordinate of a single map tile. The world is conceptually unbounded, so coordinates may
 * be negative; chunks and the spatial index use {@link Math#floorDiv} to stay consistent.
 */
public record GridPos(int x, int y) implements Comparable<GridPos> {

    public static GridPos of(int x, int y) {
        return new GridPos(x, y);
    }

    public GridPos plus(int dx, int dy) {
        return new GridPos(x + dx, y + dy);
    }

    public GridPos plus(Direction direction) {
        return new GridPos(x + direction.dx(), y + direction.dy());
    }

    public GridPos minus(GridPos other) {
        return new GridPos(x - other.x, y - other.y);
    }

    /** Manhattan distance, the natural metric for four directional movement. */
    public int manhattanDistance(GridPos other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y);
    }

    /** Chebyshev distance, the natural metric for eight directional movement. */
    public int chebyshevDistance(GridPos other) {
        return Math.max(Math.abs(x - other.x), Math.abs(y - other.y));
    }

    public float distance(GridPos other) {
        float dx = x - other.x;
        float dy = y - other.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** The four axis aligned neighbours, in north, east, south, west order. */
    public List<GridPos> neighbors4() {
        return List.of(new GridPos(x, y + 1), new GridPos(x + 1, y), new GridPos(x, y - 1), new GridPos(x - 1, y));
    }

    /** All eight neighbours, starting north and going clockwise. */
    public List<GridPos> neighbors8() {
        return List.of(
                new GridPos(x, y + 1),
                new GridPos(x + 1, y + 1),
                new GridPos(x + 1, y),
                new GridPos(x + 1, y - 1),
                new GridPos(x, y - 1),
                new GridPos(x - 1, y - 1),
                new GridPos(x - 1, y),
                new GridPos(x - 1, y + 1));
    }

    /** Center of this tile in world units. */
    public Vec2 center() {
        return Vec2.centerOf(this);
    }

    @Override
    public int compareTo(GridPos other) {
        int byY = Integer.compare(y, other.y);
        return byY != 0 ? byY : Integer.compare(x, other.x);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
