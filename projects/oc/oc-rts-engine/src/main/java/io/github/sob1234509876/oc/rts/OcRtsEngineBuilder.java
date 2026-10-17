package io.github.sob1234509876.oc.rts;

import io.github.sob1234509876.oc.rts.combat.CombatSystem;
import io.github.sob1234509876.oc.rts.content.ContentRegistry;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.dimension.DimensionProfile;
import io.github.sob1234509876.oc.rts.economy.WarehouseLogistics;
import io.github.sob1234509876.oc.rts.logistics.HaulingSystem;
import io.github.sob1234509876.oc.rts.map.AStarPathFinder;
import io.github.sob1234509876.oc.rts.map.ChunkGenerator;
import io.github.sob1234509876.oc.rts.map.ChunkStore;
import io.github.sob1234509876.oc.rts.map.ChunkStreamer;
import io.github.sob1234509876.oc.rts.map.ChunkedMap;
import io.github.sob1234509876.oc.rts.map.InMemoryChunkStore;
import io.github.sob1234509876.oc.rts.map.NoiseChunkGenerator;
import io.github.sob1234509876.oc.rts.movement.MovementSystem;
import io.github.sob1234509876.oc.rts.movement.PathService;
import io.github.sob1234509876.oc.rts.order.CommandService;
import io.github.sob1234509876.oc.rts.order.OrderSystem;
import io.github.sob1234509876.oc.rts.production.ConstructionSystem;
import io.github.sob1234509876.oc.rts.production.ProductionSystem;
import io.github.sob1234509876.oc.rts.sim.SystemPipeline;
import io.github.sob1234509876.oc.rts.visual.AnimationSystem;
import io.github.sob1234509876.oc.rts.visual.SnapshotFactory;
import io.github.sob1234509876.oc.rts.visual.VisualLibrary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Assembles an {@link OcRtsEngine} from the pieces the engine is made of.
 *
 * <p>Every subsystem has a working default, so a scenario only has to say what it cares about:
 *
 * <pre>{@code
 * OcRtsEngine engine = OcRtsEngine.builder(content)
 *         .seed(7L)
 *         .chunkSize(16)
 *         .viewRadiusChunks(4)
 *         .ticksPerSecond(20)
 *         .directDelivery(true)      // no hauler units needed, deliveries are simulated
 *         .player(Player.of(1, "Blue", 0xFF4FC3F7))
 *         .build();
 * }</pre>
 *
 * <p>The pipeline is wired with the documented phases: chunk streaming (100), orders (200), path
 * requests (300), movement (400), combat (450), construction (500), production (600), hauling (700) and
 * animation (800).
 */
public final class OcRtsEngineBuilder {

    private final ContentRegistry content;
    private final List<Player> players = new ArrayList<>();

    private long seed = 20240101L;
    private int ticksPerSecond = 20;
    private int chunkSize = 16;
    private int viewRadiusChunks = 4;
    private ChunkGenerator generator;
    private ChunkStore chunkStore = new InMemoryChunkStore();
    private DimensionProfile profile = DimensionProfile.earth();
    private final List<DimensionSpec> extraDimensions = new ArrayList<>();
    private VisualLibrary visuals = VisualLibrary.empty();
    private boolean directDelivery;
    private int directDeliveryTicks = 60;
    private int pathRequestsPerTick = 32;
    private int pathNodeBudgetPerTick = 200_000;

    OcRtsEngineBuilder(ContentRegistry content) {
        this.content = Objects.requireNonNull(content, "content");
    }

    /** Seed of the world; the same seed produces the same terrain and the same simulation. */
    public OcRtsEngineBuilder seed(long seed) {
        this.seed = seed;
        return this;
    }

    /** Simulation rate; 20 ticks per second is the default. */
    public OcRtsEngineBuilder ticksPerSecond(int ticksPerSecond) {
        if (ticksPerSecond <= 0) {
            throw new IllegalArgumentException("ticks per second must be positive, was " + ticksPerSecond);
        }
        this.ticksPerSecond = ticksPerSecond;
        return this;
    }

    /** Side length of a map chunk in tiles. */
    public OcRtsEngineBuilder chunkSize(int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunk size must be positive, was " + chunkSize);
        }
        this.chunkSize = chunkSize;
        return this;
    }

    /** How many chunks around the point of interest are kept in memory. */
    public OcRtsEngineBuilder viewRadiusChunks(int radiusChunks) {
        if (radiusChunks < 0) {
            throw new IllegalArgumentException("radius must not be negative, was " + radiusChunks);
        }
        this.viewRadiusChunks = radiusChunks;
        return this;
    }

    /** Terrain generator; defaults to the seeded noise generator of the content registry. */
    public OcRtsEngineBuilder generator(ChunkGenerator generator) {
        this.generator = generator;
        return this;
    }

    /** Storage used to persist chunks; defaults to an in-memory store. */
    public OcRtsEngineBuilder chunkStore(ChunkStore chunkStore) {
        this.chunkStore = Objects.requireNonNull(chunkStore, "chunkStore");
        return this;
    }

    /** Environment of the default dimension; Earth like (breathable, normal speed) unless changed. */
    public OcRtsEngineBuilder profile(DimensionProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
        return this;
    }

    /**
     * Registers another dimension with terrain of its own: a separate noise generator derived from the
     * dimension key, so the Moon and Mars do not look like the overworld.
     */
    public OcRtsEngineBuilder dimension(DimensionId id, DimensionProfile profile) {
        return dimension(id, profile, null, null);
    }

    /** Registers another dimension with an explicit terrain generator. */
    public OcRtsEngineBuilder dimension(DimensionId id, DimensionProfile profile, ChunkGenerator generator) {
        return dimension(id, profile, generator, null);
    }

    /** Registers another dimension with its own generator and chunk storage. */
    public OcRtsEngineBuilder dimension(DimensionId id, DimensionProfile profile, ChunkGenerator generator,
            ChunkStore store) {
        extraDimensions.add(new DimensionSpec(Objects.requireNonNull(id, "id"),
                Objects.requireNonNull(profile, "profile"), generator, store));
        return this;
    }

    /** Convenience for the two maps a Moon and Mars scenario usually wants. */
    public OcRtsEngineBuilder moonAndMars() {
        return dimension(DimensionId.of("moon"), DimensionProfile.moon())
                .dimension(DimensionId.of("mars"), DimensionProfile.mars());
    }

    /** Animation catalogue used to give spawned units their sprites; empty means "no animation". */
    public OcRtsEngineBuilder visuals(VisualLibrary visuals) {
        this.visuals = Objects.requireNonNull(visuals, "visuals");
        return this;
    }

    /**
     * When enabled, deliveries between warehouses and factories complete after a fixed delay instead of
     * being carried by worker units. Convenient for scenarios that do not want to manage haulers.
     */
    public OcRtsEngineBuilder directDelivery(boolean enabled) {
        this.directDelivery = enabled;
        return this;
    }

    /** Ticks a simulated delivery takes when {@link #directDelivery(boolean)} is on. */
    public OcRtsEngineBuilder directDeliveryTicks(int ticks) {
        if (ticks <= 0) {
            throw new IllegalArgumentException("delivery time must be positive, was " + ticks);
        }
        this.directDeliveryTicks = ticks;
        return this;
    }

    /** Budget of the path request queue: how many searches per tick and how many nodes in total. */
    public OcRtsEngineBuilder pathBudget(int requestsPerTick, int nodeBudgetPerTick) {
        if (requestsPerTick <= 0 || nodeBudgetPerTick <= 0) {
            throw new IllegalArgumentException("path budget must be positive");
        }
        this.pathRequestsPerTick = requestsPerTick;
        this.pathNodeBudgetPerTick = nodeBudgetPerTick;
        return this;
    }

    public OcRtsEngineBuilder player(Player player) {
        players.add(Objects.requireNonNull(player, "player"));
        return this;
    }

    public OcRtsEngineBuilder players(List<Player> players) {
        players.forEach(this::player);
        return this;
    }

    /** Wires the world and every subsystem together. */
    public OcRtsEngine build() {
        if (players.isEmpty()) {
            players.add(Player.of(1, "Player 1", 0xFF4FC3F7));
        }
        World world = new World(seed, content);
        players.forEach(world::addPlayer);

        // One map per dimension, each with its own chunk store: a store is keyed by chunk position only,
        // so sharing one between dimensions would make them overwrite each other's terrain.
        Map<DimensionId, ChunkedMap> maps = new LinkedHashMap<>();
        ChunkGenerator defaultGenerator = generator != null ? generator : new NoiseChunkGenerator(seed, content);
        ChunkedMap defaultMap = new ChunkedMap(defaultGenerator, chunkStore, chunkSize);
        defaultMap.focus(Vec2.ZERO, viewRadiusChunks);
        world.addDimension(DimensionId.overworld(), profile, defaultMap);
        maps.put(DimensionId.overworld(), defaultMap);

        for (DimensionSpec spec : extraDimensions) {
            ChunkGenerator dimensionGenerator =
                    spec.generator() != null ? spec.generator() : new NoiseChunkGenerator(seedFor(spec.id()), content);
            ChunkStore dimensionStore = spec.store() != null ? spec.store() : new InMemoryChunkStore();
            ChunkedMap dimensionMap = new ChunkedMap(dimensionGenerator, dimensionStore, chunkSize);
            dimensionMap.focus(Vec2.ZERO, viewRadiusChunks);
            world.addDimension(spec.id(), spec.profile(), dimensionMap);
            maps.put(spec.id(), dimensionMap);
        }

        for (Map.Entry<DimensionId, ChunkedMap> entry : maps.entrySet()) {
            world.setPathFinder(entry.getKey(),
                    new AStarPathFinder(entry.getValue(), AStarPathFinder.minimumMoveCost(content)));
        }
        AStarPathFinder pathFinder = (AStarPathFinder) world.pathFinder();

        for (Player player : players) {
            player.attachLogistics(new WarehouseLogistics(world, player));
        }

        SystemPipeline pipeline = new SystemPipeline("simulation");
        pipeline.add(new ChunkStreamer(maps, viewRadiusChunks));
        pipeline.add(new OrderSystem(world));

        PathService pathService = new PathService(world, pathRequestsPerTick, pathNodeBudgetPerTick);
        pipeline.add(pathService);
        pipeline.add(new MovementSystem(world, pathService));
        // Combat sits directly after movement: it sees the positions the units actually reached this tick
        // and the orders have already decided where they wanted to go.
        pipeline.add(new CombatSystem(world));

        CommandService commands = new CommandService(world);
        world.setDirector(commands);

        pipeline.add(new ConstructionSystem(world));
        ProductionSystem production = new ProductionSystem(world);
        // Like the director, the production system is installed on the world itself: a caller that only
        // holds the world (a plugin tick hook, a scenario) can then queue work without the engine object.
        world.setProduction(production);
        pipeline.add(production);
        pipeline.add(new HaulingSystem(world, directDelivery, directDeliveryTicks));
        pipeline.add(new AnimationSystem(world, visuals));

        SnapshotFactory snapshots = new SnapshotFactory(world, visuals, ticksPerSecond);
        return new OcRtsEngine(world, pipeline, ticksPerSecond, defaultMap, pathFinder, pathService, commands,
                production, visuals, snapshots);
    }

    /**
     * A seed derived from the world seed and the dimension key, so extra dimensions do not generate the
     * same landscape as the overworld while still being reproducible from the world seed alone.
     */
    private long seedFor(DimensionId id) {
        long mixed = seed ^ 0x9E3779B97F4A7C15L;
        for (int i = 0; i < id.key().length(); i++) {
            mixed = (mixed ^ id.key().charAt(i)) * 1099511628211L;
        }
        return mixed;
    }

    /** One registered dimension: its identity, environment and optional terrain plumbing. */
    private record DimensionSpec(DimensionId id, DimensionProfile profile, ChunkGenerator generator,
            ChunkStore store) {
    }
}
