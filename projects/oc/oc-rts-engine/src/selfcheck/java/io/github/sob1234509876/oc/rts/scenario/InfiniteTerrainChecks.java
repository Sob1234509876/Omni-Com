package io.github.sob1234509876.oc.rts.scenario;

import io.github.sob1234509876.oc.rts.OcRtsEngine;
import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.content.ContentRegistry;
import io.github.sob1234509876.oc.rts.content.TerrainType;
import io.github.sob1234509876.oc.rts.content.UnitRole;
import io.github.sob1234509876.oc.rts.core.ChunkPos;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.event.Events;
import io.github.sob1234509876.oc.rts.map.AStarPathFinder;
import io.github.sob1234509876.oc.rts.map.BiomeTable;
import io.github.sob1234509876.oc.rts.map.Chunk;
import io.github.sob1234509876.oc.rts.map.ChunkedMap;
import io.github.sob1234509876.oc.rts.map.FlatChunkGenerator;
import io.github.sob1234509876.oc.rts.map.InfiniteTerrainMap;
import io.github.sob1234509876.oc.rts.map.NoiseTerrainGenerator;
import io.github.sob1234509876.oc.rts.map.Path;
import io.github.sob1234509876.oc.rts.map.PathOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Self checks for the unbounded world: terrain with no edge, the least-recently-used chunk budget,
 * determinism across eviction, the streamer's behaviour around a moving focus, and the unchanged
 * bounded/default map.
 *
 * <p>Run from {@link RtsScenario#main(String[])}, so the same {@code scenario} task prints these lines and
 * counts them in the scenario total. Everything is deterministic: the noise generator is a pure function
 * of seed and coordinate, and the walk uses a flat world and a fixed command.
 */
final class InfiniteTerrainChecks {

    private static final int CHUNK_SIZE = 16;

    /** Seed of the unbounded checks; independent from the other scenario seeds. */
    private static final long SEED = 20261023L;

    /** Distance the walking unit is ordered to cover in one direction, in tiles. */
    private static final int WALK_TILES = 2000;

    private InfiniteTerrainChecks() {
    }

    static void run(Check check, ContentRegistry content) {
        check.section("1c) unbounded terrain: no world edge, an LRU chunk budget and eviction");
        checkLargeCoordinates(check, content);
        checkLruBudget(check, content);
        checkWalkAcrossAnEndlessMap(check);
        checkDeterminismAcrossEviction(check, content);
        checkSameSeedAcrossMaps(check, content);
        checkStreamerAroundMovingFocus(check, content);
        checkBoundedWorldUnchanged(check, content);
        checkPathFinderBoundsItsSearch(check, content);
    }

    // ------------------------------------------------------------------ large coordinates

    private static void checkLargeCoordinates(Check check, ContentRegistry content) {
        NoiseTerrainGenerator generator = new NoiseTerrainGenerator(SEED, BiomeTable.of(content));
        InfiniteTerrainMap map = new InfiniteTerrainMap(generator, 64);

        check.equal(generator.terrainFor(100_000, -100_000), map.terrainAt(100_000, -100_000),
                "terrain at x = 100000 matches the generator's own sample");
        check.equal(generator.terrainFor(-100_000, 100_000), map.terrainAt(-100_000, 100_000),
                "terrain at x = -100000 matches the generator's own sample");
        check.equal(2, map.loadedChunkCount(),
                "two far coordinates loaded exactly their two chunks, not a whole finite map");
        check.equal(generator.terrainFor(99_999, -100_001), map.terrainAt(99_999, -100_001),
                "a tile just across the chunk seam is consistent too");
        check.that(map.isChunkLoaded(ChunkPos.of(new GridPos(100_000, -100_000), CHUNK_SIZE)),
                "querying a far coordinate generated and loaded its chunk");
        check.equal(3, map.loadedChunkCount(),
                "the seam query loaded only the one additional chunk it needed");
        check.equal(ChunkPos.of(new GridPos(-1, -1), CHUNK_SIZE), new ChunkPos(-1, -1),
                "negative tiles floor into negative chunks instead of clamping to zero");
    }

    // ------------------------------------------------------------------ LRU budget

    private static void checkLruBudget(Check check, ContentRegistry content) {
        TerrainType grass = content.terrain("grass");
        InfiniteTerrainMap map = new InfiniteTerrainMap(
                new FlatChunkGenerator(grass, CHUNK_SIZE), 3);
        check.equal(InfiniteTerrainMap.DEFAULT_MAX_LOADED_CHUNKS,
                new InfiniteTerrainMap(new FlatChunkGenerator(grass, CHUNK_SIZE)).maxLoadedChunks(),
                "the default chunk budget is " + InfiniteTerrainMap.DEFAULT_MAX_LOADED_CHUNKS);

        ChunkPos a = ChunkPos.of(new GridPos(0, 0), CHUNK_SIZE);
        ChunkPos b = ChunkPos.of(new GridPos(100, 0), CHUNK_SIZE);
        ChunkPos c = ChunkPos.of(new GridPos(200, 0), CHUNK_SIZE);
        ChunkPos d = ChunkPos.of(new GridPos(300, 0), CHUNK_SIZE);
        map.terrainAt(0, 0);
        map.terrainAt(100, 0);
        map.terrainAt(200, 0);
        check.equal(3, map.loadedChunkCount(), "three distinct chunks fill the budget of three");
        map.terrainAt(0, 0);   // touch A, so B becomes the least recently used chunk
        map.terrainAt(300, 0); // loading D must evict B, not A
        check.that(map.isChunkLoaded(a), "the most recently used chunk survived the eviction");
        check.that(!map.isChunkLoaded(b), "the least recently used chunk was evicted");
        check.that(map.isChunkLoaded(c) && map.isChunkLoaded(d),
                "the other loaded chunks, including the new one, are in memory");
        check.equal(3, map.loadedChunkCount(), "the budget is respected after the eviction");
        check.equal(1L, map.evictedChunkCount(), "the budget reports exactly one eviction");

        List<ChunkPos> unloaded = new ArrayList<>();
        map.setUnloadListener(unloaded::add);
        map.unload(a);
        check.equal(List.of(a), unloaded, "an explicit unload notifies the unload listener");
        check.equal(2, map.loadedChunkCount(), "the explicitly unloaded chunk is gone");
    }

    // ------------------------------------------------------------------ walking 2000 tiles

    /**
     * A single unit really walks {@value #WALK_TILES} tiles in one direction on an unbounded map.
     *
     * <p>The map budget is deliberately smaller than the map the walk touches: the LRU has to drop
     * chunks while the unit walks, and the terrain sweep afterwards regenerates a hundred chunks, so the
     * check proves the budget holds through a real walk rather than by asking the map directly. The fast
     * runner keeps the check to a few hundred ticks instead of the sixteen thousand a normal soldier
     * would need.
     */
    private static void checkWalkAcrossAnEndlessMap(Check check) {
        ContentRegistry content = ContentRegistry.builder("infinite_walk")
                .terrain("grass").name("Grass").buildable().sprite("terrain/grass")
                .unit("runner").name("Runner").role(UnitRole.COMBAT).health(100)
                        .speed(120f).radius(0.3f).spriteSet("runner")
                .build();
        OcRtsEngine engine = OcRtsEngine.builder(content)
                .infiniteTerrain(SEED)
                .generator(new FlatChunkGenerator(content.terrain("grass"), CHUNK_SIZE))
                .chunkSize(CHUNK_SIZE)
                .viewRadiusChunks(1)
                .maxLoadedChunks(16)
                .directDelivery(true)
                .build();
        check.that(engine.map() instanceof InfiniteTerrainMap,
                "the builder builds an unbounded map when infiniteTerrain(...) is asked for");
        InfiniteTerrainMap map = (InfiniteTerrainMap) engine.map();
        check.equal(16, map.maxLoadedChunks(), "the builder applies the requested chunk budget");

        Player player = engine.players().get(0);
        Unit runner = engine.world().spawnUnit(content.unit("runner"), player, new GridPos(0, 0));
        engine.commands().move(runner, new GridPos(WALK_TILES, 0));

        int maxLoaded = 0;
        boolean reached = false;
        for (int tick = 0; tick < 1200; tick++) {
            engine.tick();
            maxLoaded = Math.max(maxLoaded, map.loadedChunkCount());
            if (runner.position().x() >= WALK_TILES - 1f) {
                reached = true;
                break;
            }
        }
        check.that(reached, "a unit walked " + WALK_TILES + " tiles in one direction (reached x "
                + runner.position().x() + ")");
        check.atLeast(WALK_TILES - 10f, runner.motion().travelledDistance(),
                "the distance recorder agrees that the unit really walked the distance");
        check.atMost(16, maxLoaded, "the loaded chunk count never exceeded the configured budget");
        check.that(map.generatedChunkCount() > 16,
                "more chunks were generated than the budget holds (" + map.generatedChunkCount() + ")");
        check.that(map.evictedChunkCount() > 0,
                "the LRU budget evicted chunks while the unit walked (" + map.evictedChunkCount() + ")");

        int missing = 0;
        for (int x = 0; x <= WALK_TILES; x++) {
            if (map.terrainAt(x, 0) == null) {
                missing++;
            }
        }
        check.equal(0, missing, "terrain exists at every one of the " + (WALK_TILES + 1)
                + " tiles along the walked line");
        check.atMost(16, map.loadedChunkCount(), "the terrain sweep left the memory budget intact");
    }

    // ------------------------------------------------------------------ determinism across eviction

    private static void checkDeterminismAcrossEviction(Check check, ContentRegistry content) {
        NoiseTerrainGenerator generator = new NoiseTerrainGenerator(SEED, BiomeTable.of(content));
        InfiniteTerrainMap map = new InfiniteTerrainMap(generator, 3);
        GridPos revisited = new GridPos(100_000, -100_000);
        ChunkPos pos = ChunkPos.of(revisited, CHUNK_SIZE);
        TerrainType first = map.terrainAt(revisited.x(), revisited.y());

        map.terrainAt(100_016, -100_000);
        map.terrainAt(100_032, -100_000);
        map.terrainAt(100_048, -100_000);
        check.that(!map.isChunkLoaded(pos), "the revisited chunk was evicted by the LRU budget");
        TerrainType again = map.terrainAt(revisited.x(), revisited.y());
        check.equal(first, again, "the revisited tile has identical terrain after regeneration");

        Chunk regenerated = map.chunk(pos);
        Chunk fresh = generator.generate(pos);
        check.that(sameTiles(regenerated, fresh),
                "the whole regenerated chunk is bit identical to a freshly generated one");
    }

    private static void checkSameSeedAcrossMaps(Check check, ContentRegistry content) {
        BiomeTable biomes = BiomeTable.of(content);
        InfiniteTerrainMap first = new InfiniteTerrainMap(new NoiseTerrainGenerator(SEED, biomes), 16);
        InfiniteTerrainMap second = new InfiniteTerrainMap(new NoiseTerrainGenerator(SEED, biomes), 16);
        InfiniteTerrainMap other = new InfiniteTerrainMap(new NoiseTerrainGenerator(SEED + 1, biomes), 16);

        int[][] coordinates = {{100_000, -100_000}, {-100_000, 100_000}, {123, -456}, {-1, -1}};
        boolean agree = true;
        for (int[] coordinate : coordinates) {
            agree &= first.terrainAt(coordinate[0], coordinate[1])
                    .equals(second.terrainAt(coordinate[0], coordinate[1]));
        }
        check.that(agree, "two unbounded maps with the same seed agree at every sampled coordinate");
        check.that(!first.terrainAt(100_000, -100_000).equals(other.terrainAt(100_000, -100_000)),
                "a different seed produces different terrain at x = 100000");
    }

    // ------------------------------------------------------------------ streamer and events

    private static void checkStreamerAroundMovingFocus(Check check, ContentRegistry content) {
        TerrainType grass = content.terrain("grass");
        OcRtsEngine engine = OcRtsEngine.builder(content)
                .seed(SEED)
                .infiniteTerrain()
                .generator(new FlatChunkGenerator(grass, CHUNK_SIZE))
                .chunkSize(CHUNK_SIZE)
                .viewRadiusChunks(1)
                .maxLoadedChunks(64)
                .build();
        InfiniteTerrainMap map = (InfiniteTerrainMap) engine.map();
        List<Events.ChunkLoaded> loaded = new ArrayList<>();
        List<Events.ChunkUnloaded> unloaded = new ArrayList<>();
        engine.events().subscribe(Events.ChunkLoaded.class, loaded::add);
        engine.events().subscribe(Events.ChunkUnloaded.class, unloaded::add);

        engine.tick(10);
        check.equal(9, map.loadedChunkCount(), "the streamer fills the 3x3 window around the initial focus");

        ChunkPos originChunk = ChunkPos.of(new GridPos(0, 0), CHUNK_SIZE);
        GridPos far = new GridPos(100_000, -100_000);
        ChunkPos farChunk = ChunkPos.of(far, CHUNK_SIZE);
        engine.focus(far);
        engine.tick(30);

        check.that(map.isChunkLoaded(farChunk), "the streamer loaded chunks around a focus at x = 100000");
        check.that(!map.isChunkLoaded(originChunk), "the streamer unloaded the chunks at the old focus");
        check.that(unloaded.stream().anyMatch(event -> event.pos().equals(originChunk)),
                "ChunkUnloaded fired for a chunk that left the focus");
        long originUnloads = unloaded.stream().filter(event -> event.pos().equals(originChunk)).count();
        check.equal(1L, originUnloads, "the unloaded chunk is reported exactly once, not by map and streamer");
        check.that(unloaded.stream().allMatch(event -> event.dimension().equals(engine.defaultDimensionId())),
                "every unload event names the dimension it belongs to");
        check.that(loaded.stream().anyMatch(event -> event.pos().equals(farChunk)),
                "ChunkLoaded fired for a chunk that entered the focus");
        check.atMost(64, map.loadedChunkCount(), "streaming a far focus stays within the LRU budget");

        InfiniteTerrainMap supplied =
                new InfiniteTerrainMap(new FlatChunkGenerator(grass, CHUNK_SIZE), 5);
        OcRtsEngine suppliedEngine = OcRtsEngine.builder(content)
                .terrainMap(supplied)
                .viewRadiusChunks(1)
                .build();
        check.that(suppliedEngine.map() == supplied && supplied.maxLoadedChunks() == 5,
                "terrainMap(...) uses the supplied unbounded map and keeps its budget");
    }

    // ------------------------------------------------------------------ bounded world unchanged

    private static void checkBoundedWorldUnchanged(Check check, ContentRegistry content) {
        OcRtsEngine engine = OcRtsEngine.builder(content)
                .seed(20240202L)
                .chunkSize(CHUNK_SIZE)
                .viewRadiusChunks(2)
                .directDelivery(true)
                .build();
        engine.tick(12);
        check.that(engine.map() instanceof ChunkedMap,
                "without infiniteTerrain(...) the builder still produces the historical ChunkedMap");
        ChunkedMap map = (ChunkedMap) engine.map();
        check.equal(0, map.maxLoadedChunks(), "the historical map still has no LRU budget by default");
        check.equal(25, map.loadedChunkCount(), "the historical 5x5 streaming window is unchanged");
        check.that(map.terrainAt(100_000, -100_000) != null,
                "the historical map still generates any coordinate on demand");
    }

    // ------------------------------------------------------------------ path search budget

    private static void checkPathFinderBoundsItsSearch(Check check, ContentRegistry content) {
        TerrainType grass = content.terrain("grass");
        InfiniteTerrainMap map = new InfiniteTerrainMap(
                new FlatChunkGenerator(grass, CHUNK_SIZE), 16);
        AStarPathFinder finder = new AStarPathFinder(map, 1f);
        GridPos start = new GridPos(100_000, -100_000);

        Optional<Path> near = finder.findPath(start, start.plus(8, 0), PathOptions.DEFAULT);
        check.that(near.isPresent(), "a short path is found at x = 100000, where no map edge exists");
        check.that(finder.hasLineOfSight(start.center(), start.plus(8, 0).center()),
                "line of sight works at those large coordinates");

        Optional<Path> distant = finder.findPath(start, start.plus(5_000, 0), PathOptions.FAST);
        check.that(distant.isEmpty(),
                "a 5000 tile search gives up on its node budget, so the search is bounded, not the world");
    }

    // ------------------------------------------------------------------ helpers

    private static boolean sameTiles(Chunk left, Chunk right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.rawTiles().length; i++) {
            if (!left.rawTiles()[i].equals(right.rawTiles()[i])) {
                return false;
            }
        }
        return true;
    }
}
