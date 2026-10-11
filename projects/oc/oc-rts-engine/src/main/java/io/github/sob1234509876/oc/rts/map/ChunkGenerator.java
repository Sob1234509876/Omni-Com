package io.github.sob1234509876.oc.rts.map;

import io.github.sob1234509876.oc.rts.core.ChunkPos;

/**
 * Produces the terrain of a chunk that is not in the {@link ChunkStore} yet.
 *
 * <p>Generation must be a pure function of the chunk coordinate and the generator's own seed: a chunk
 * has to look the same whether it is created while the player walks north or while a path search
 * reaches over it, and it has to look the same after it was evicted and generated again. Generators
 * therefore derive their randomness by hashing the coordinates instead of drawing from a sequential
 * random stream.
 */
public interface ChunkGenerator {

    /** Creates the chunk at the given position; the same position always yields the same terrain. */
    Chunk generate(ChunkPos pos);

    /** Side length in tiles of the chunks this generator produces. */
    int chunkSize();
}
