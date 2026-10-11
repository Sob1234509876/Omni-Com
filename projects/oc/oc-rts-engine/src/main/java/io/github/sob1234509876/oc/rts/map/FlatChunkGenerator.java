package io.github.sob1234509876.oc.rts.map;

import io.github.sob1234509876.oc.rts.content.TerrainType;
import io.github.sob1234509876.oc.rts.core.ChunkPos;

import java.util.Objects;

/**
 * Generator of a completely uniform world, used by tests and by scenarios that build their map by hand.
 *
 * <p>A flat world removes terrain from the equation: path searches, streaming and streaming tests then
 * only measure the behaviour of the engine instead of the shape of a noise field.
 */
public final class FlatChunkGenerator implements ChunkGenerator {

    /** Default chunk size; matches {@link NoiseChunkGenerator#DEFAULT_CHUNK_SIZE}. */
    public static final int DEFAULT_CHUNK_SIZE = 16;

    private final TerrainType terrain;
    private final int chunkSize;

    public FlatChunkGenerator(TerrainType terrain) {
        this(terrain, DEFAULT_CHUNK_SIZE);
    }

    public FlatChunkGenerator(TerrainType terrain, int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunk size must be positive, was " + chunkSize);
        }
        this.terrain = Objects.requireNonNull(terrain, "terrain");
        this.chunkSize = chunkSize;
    }

    /** The terrain every tile of every chunk receives. */
    public TerrainType terrain() {
        return terrain;
    }

    @Override
    public Chunk generate(ChunkPos pos) {
        return Chunk.filled(pos, chunkSize, terrain);
    }

    @Override
    public int chunkSize() {
        return chunkSize;
    }

    @Override
    public String toString() {
        return "FlatChunkGenerator{" + terrain + ", chunkSize=" + chunkSize + "}";
    }
}
