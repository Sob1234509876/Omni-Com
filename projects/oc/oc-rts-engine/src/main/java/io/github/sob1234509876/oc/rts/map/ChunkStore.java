package io.github.sob1234509876.oc.rts.map;

import io.github.sob1234509876.oc.rts.core.ChunkPos;

import java.util.Optional;

/**
 * Persistence seam for chunks that left memory.
 *
 * <p>Streaming drops chunks that are far from the focus of interest, but an edited chunk (a road, a
 * cleared forest) must survive that eviction. The store is how those edits outlive the chunk: a map
 * asks the store first and only generates terrain when the store does not have the chunk.
 *
 * <p>The interface is deliberately tiny and synchronous so that the engine stays free of threading and
 * I/O concerns; implementations decide whether they write to memory, to disk or nowhere at all.
 */
public interface ChunkStore {

    /** Loads the stored chunk, or empty when this store has never seen it. */
    Optional<Chunk> load(ChunkPos pos);

    /** Stores a chunk, replacing any earlier copy. */
    void save(Chunk chunk);

    /** Whether a chunk can be loaded without generating it. */
    boolean exists(ChunkPos pos);
}
