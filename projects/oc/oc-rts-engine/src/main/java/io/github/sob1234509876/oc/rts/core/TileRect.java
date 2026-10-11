package io.github.sob1234509876.oc.rts.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Inclusive rectangle of tiles, used for chunk extents, building footprints and area queries.
 */
public record TileRect(int minX, int minY, int maxX, int maxY) {

    public static TileRect of(GridPos a, GridPos b) {
        return new TileRect(
                Math.min(a.x(), b.x()),
                Math.min(a.y(), b.y()),
                Math.max(a.x(), b.x()),
                Math.max(a.y(), b.y()));
    }

    /** Smallest rectangle containing both corners. */
    public static TileRect of(int x1, int y1, int x2, int y2) {
        return new TileRect(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2));
    }

    /** Square rectangle of the given radius around a center tile. */
    public static TileRect centered(GridPos center, int radius) {
        return new TileRect(
                center.x() - radius,
                center.y() - radius,
                center.x() + radius,
                center.y() + radius);
    }

    /** Rectangle of the given size whose minimum corner is the given tile. */
    public static TileRect fromOrigin(GridPos origin, int width, int height) {
        return new TileRect(origin.x(), origin.y(), origin.x() + width - 1, origin.y() + height - 1);
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int height() {
        return maxY - minY + 1;
    }

    public int tileCount() {
        return width() * height();
    }

    public GridPos min() {
        return new GridPos(minX, minY);
    }

    public GridPos max() {
        return new GridPos(maxX, maxY);
    }

    public Vec2 center() {
        return new Vec2((minX + maxX + 1) / 2f, (minY + maxY + 1) / 2f);
    }

    public boolean contains(GridPos tile) {
        return contains(tile.x(), tile.y());
    }

    public boolean contains(int x, int y) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    public boolean intersects(TileRect other) {
        return minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY;
    }

    /** Grows the rectangle by the given amount of tiles on every side. */
    public TileRect expanded(int amount) {
        return new TileRect(minX - amount, minY - amount, maxX + amount, maxY + amount);
    }

    public List<GridPos> tiles() {
        List<GridPos> tiles = new ArrayList<>(tileCount());
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                tiles.add(new GridPos(x, y));
            }
        }
        return tiles;
    }

    @Override
    public String toString() {
        return "[" + minX + ".." + maxX + " x " + minY + ".." + maxY + "]";
    }
}
