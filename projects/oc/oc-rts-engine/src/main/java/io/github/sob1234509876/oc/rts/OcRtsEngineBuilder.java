package io.github.sob1234509876.oc.rts;

import io.github.sob1234509876.oc.rts.content.ContentRegistry;
import io.github.sob1234509876.oc.rts.core.Vec2;
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
import java.util.List;
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
 * requests (300), movement (400), construction (500), production (600), hauling (700) and animation
 * (800).
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

        ChunkGenerator chunkGenerator = generator != null ? generator : new NoiseChunkGenerator(seed, content);
        ChunkedMap map = new ChunkedMap(chunkGenerator, chunkStore, chunkSize);
        map.focus(Vec2.ZERO, viewRadiusChunks);
        world.setMap(map);

        AStarPathFinder pathFinder = new AStarPathFinder(map);
        world.setPathFinder(pathFinder);

        for (Player player : players) {
            player.attachLogistics(new WarehouseLogistics(world, player));
        }

        SystemPipeline pipeline = new SystemPipeline("simulation");
        pipeline.add(new ChunkStreamer(map, viewRadiusChunks));
        pipeline.add(new OrderSystem(world));

        PathService pathService = new PathService(world, pathRequestsPerTick, pathNodeBudgetPerTick);
        pipeline.add(pathService);
        pipeline.add(new MovementSystem(world, pathService));

        CommandService commands = new CommandService(world);
        world.setDirector(commands);

        pipeline.add(new ConstructionSystem(world));
        ProductionSystem production = new ProductionSystem(world);
        pipeline.add(production);
        pipeline.add(new HaulingSystem(world, directDelivery, directDeliveryTicks));
        pipeline.add(new AnimationSystem(world, visuals));

        SnapshotFactory snapshots = new SnapshotFactory(world, visuals);
        return new OcRtsEngine(
                world, pipeline, ticksPerSecond, map, pathFinder, pathService, commands, production, visuals, snapshots);
    }
}
