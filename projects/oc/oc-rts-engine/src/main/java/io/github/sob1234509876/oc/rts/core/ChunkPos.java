package io.github.sob1234509876.oc.rts.core;

/**
 * Coordinate of a map chunk, i.e. a square block of {@code chunkSize x chunkSize} tiles.
 */
public record ChunkPos(int x, int y) implements Comparable<ChunkPos> {

    public static ChunkPos of(int x, int y) {
        return new ChunkPos(x, y);
    }

    /** Chunk containing the given tile. */
    public static ChunkPos of(GridPos tile, int chunkSize) {
        return new ChunkPos(Math.floorDiv(tile.x(), chunkSize), Math.floorDiv(tile.y(), chunkSize));
    }

    public ChunkPos plus(int dx, int dy) {
        return new ChunkPos(x + dx, y + dy);
    }

    public int chebyshevDistance(ChunkPos other) {
        return Math.max(Math.abs(x - other.x), Math.abs(y - other.y));
    }

    /** Tile range covered by this chunk, with inclusive bounds. */
    public TileRect tiles(int chunkSize) {
        int minX = x * chunkSize;
        int minY = y * chunkSize;
        return new TileRect(minX, minY, minX + chunkSize - 1, minY + chunkSize - 1);
    }

    @Override
    public int compareTo(ChunkPos other) {
        int byY = Integer.compare(y, other.y);
        return byY != 0 ? byY : Integer.compare(x, other.x);
    }

    @Override
    public String toString() {
        return "chunk(" + x + ", " + y + ")";
    }
}
