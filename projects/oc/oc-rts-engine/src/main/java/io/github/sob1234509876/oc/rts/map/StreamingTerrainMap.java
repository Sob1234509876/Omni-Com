package io.github.sob1234509876.oc.rts.map;

import io.github.sob1234509876.oc.rts.core.ChunkPos;

import java.util.Map;
import java.util.function.Consumer;

/**
 * A terrain map that can be streamed: tiles are generated on demand and the chunks that are currently in
 * memory can be inspected, dropped and bounded.
 *
 * <p>This is the common supertype of the two engines maps. {@link ChunkedMap} keeps every chunk it ever
 * touched until the {@link ChunkStreamer} unloads it, which is the historical behaviour; the
 * {@link InfiniteTerrainMap} additionally puts a hard least-recently-used budget on the loaded chunks, so
 * memory stays bounded even when nothing streams around a camera.
 *
 * <p>The interface exists so that the engine, its builder and the streamer can be written once against
 * "whatever chunk map this dimension uses" instead of naming one concrete class. No bounds are implied:
 * coordinates may be any {@code int}, and implementations generate whatever chunk a query needs.
 */
public interface StreamingTerrainMap extends TerrainMap, MapStreaming {

    /** The chunk at the position, generating or loading it when it is not in memory. */
    Chunk chunk(ChunkPos pos);

    /** Chunks currently in memory, keyed by position; the returned map is a copy. */
    Map<ChunkPos, Chunk> chunks();

    /** Number of chunks produced by the generator since this map was created. */
    long generatedChunkCount();

    /**
     * Sets how many chunks may stay in memory at once. A caller that wants no limit uses the concrete
     * map that documents zero as "unlimited"; an unbounded map requires a positive budget.
     */
    void setMaxLoadedChunks(int maxLoadedChunks);

    /** The current limit, zero when the implementation has no limit. */
    int maxLoadedChunks();

    /** Writes every edited chunk to the store and marks it persisted. */
    void flushDirty();

    /** Drops every chunk from memory; each dropped chunk is observable through the unload listener. */
    void unloadAll();

    /**
     * Installs the callback invoked after a chunk leaves memory, whether it was unloaded explicitly or
     * evicted by a budget. The default is a no-op, and replacing the listener replaces the previous one.
     */
    void setUnloadListener(Consumer<ChunkPos> listener);
}
