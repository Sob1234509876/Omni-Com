package io.github.sob1234509876.oc.rts.map;

import io.github.sob1234509876.oc.rts.core.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Keeps stored chunks in a hash map, for tests, tools and scenario editing sessions.
 *
 * <p>An in-memory store behaves exactly like a persistent one from the point of view of the map, which
 * is what makes streaming and terrain editing testable without touching the file system. Stored chunks
 * are handed out as they are: a store is a cache of last resort, and a caller that wants a private copy
 * should copy the tiles itself, because duplicating every save would defeat the purpose of the seam.
 */
public final class InMemoryChunkStore implements ChunkStore {

    private final Map<ChunkPos, Chunk> chunks = new HashMap<>();

    /** Creates an empty store. */
    public InMemoryChunkStore() {
    }

    @Override
    public Optional<Chunk> load(ChunkPos pos) {
        return Optional.ofNullable(chunks.get(Objects.requireNonNull(pos, "pos")));
    }

    @Override
    public void save(Chunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        chunks.put(chunk.pos(), chunk);
        chunk.markClean();
    }

    @Override
    public boolean exists(ChunkPos pos) {
        return chunks.containsKey(Objects.requireNonNull(pos, "pos"));
    }

    /** Number of chunks currently held. */
    public int size() {
        return chunks.size();
    }

    /** Forgets every chunk, for example when a scenario is torn down. */
    public void clear() {
        chunks.clear();
    }

    @Override
    public String toString() {
        return "InMemoryChunkStore{chunks=" + chunks.size() + "}";
    }
}
