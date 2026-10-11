package io.github.sob1234509876.oc.rts;

import io.github.sob1234509876.oc.rts.content.BuildingType;
import io.github.sob1234509876.oc.rts.content.ContentRegistry;
import io.github.sob1234509876.oc.rts.content.UnitType;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Tick;
import io.github.sob1234509876.oc.rts.core.TileRect;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.event.EventBus;
import io.github.sob1234509876.oc.rts.map.ChunkedMap;
import io.github.sob1234509876.oc.rts.map.MapStreaming;
import io.github.sob1234509876.oc.rts.map.PathFinder;
import io.github.sob1234509876.oc.rts.map.TerrainMap;
import io.github.sob1234509876.oc.rts.movement.PathService;
import io.github.sob1234509876.oc.rts.order.UnitDirector;
import io.github.sob1234509876.oc.rts.production.ProductionSystem;
import io.github.sob1234509876.oc.rts.sim.SystemPipeline;
import io.github.sob1234509876.oc.rts.visual.AnimationSystem;
import io.github.sob1234509876.oc.rts.visual.AsciiMapRenderer;
import io.github.sob1234509876.oc.rts.visual.SnapshotFactory;
import io.github.sob1234509876.oc.rts.visual.VisualLibrary;
import io.github.sob1234509876.oc.rts.visual.WorldSnapshot;

import java.util.List;
import java.util.function.Predicate;

/**
 * A running Omni Com RTS match: the world, the systems that advance it and the services a player, an
 * AI or a test drives it through.
 *
 * <p>The engine is strictly turn based from the caller's point of view: {@link #tick()} advances the
 * simulation by exactly one fixed timestep, runs every {@link io.github.sob1234509876.oc.rts.sim.SimulationSystem}
 * in phase order and then removes entities that died during the tick. There is no thread, no wall
 * clock and no GUI in here, which is what makes a match reproducible from a seed and a command stream.
 *
 * <p>Typical use:
 * <pre>{@code
 * ContentRegistry content = ScenarioContent.omniCom();
 * OcRtsEngine engine = OcRtsEngine.builder(content)
 *         .seed(42L)
 *         .viewRadiusChunks(3)
 *         .build();
 *
 * Player player = engine.players().get(0);
 * World world = engine.world();
 * Building barracks = world.spawnBuilding(content.building("barracks"), player, new GridPos(10, 10));
 * engine.production().enqueue(barracks, content.recipe("train_soldier"), 5);
 *
 * engine.tick(600);
 * engine.commands().moveGroup(world.unitsOf(player), new GridPos(40, 25), FormationShape.BOX);
 * engine.tick(400);
 * System.out.println(engine.render(new GridPos(40, 25), 20));
 * }</pre>
 *
 * <p>Instances are created by {@link OcRtsEngineBuilder}; the package-private constructor exists only
 * so that the builder can wire the systems without exposing them individually.
 */
public final class OcRtsEngine {

    private final World world;
    private final SystemPipeline pipeline;
    private final int ticksPerSecond;
    private final ChunkedMap map;
    private final PathFinder pathFinder;
    private final PathService pathService;
    private final UnitDirector commands;
    private final ProductionSystem production;
    private final VisualLibrary visuals;
    private final SnapshotFactory snapshots;
    private final AsciiMapRenderer asciiRenderer = new AsciiMapRenderer();

    private long tickNumber;

    OcRtsEngine(
            World world,
            SystemPipeline pipeline,
            int ticksPerSecond,
            ChunkedMap map,
            PathFinder pathFinder,
            PathService pathService,
            UnitDirector commands,
            ProductionSystem production,
            VisualLibrary visuals,
            SnapshotFactory snapshots) {
        this.world = world;
        this.pipeline = pipeline;
        this.ticksPerSecond = ticksPerSecond;
        this.map = map;
        this.pathFinder = pathFinder;
        this.pathService = pathService;
        this.commands = commands;
        this.production = production;
        this.visuals = visuals;
        this.snapshots = snapshots;
    }

    /** Starts building an engine for the given content catalogue. */
    public static OcRtsEngineBuilder builder(ContentRegistry content) {
        return new OcRtsEngineBuilder(content);
    }

    // ------------------------------------------------------------------ state

    public World world() {
        return world;
    }

    public ContentRegistry content() {
        return world.content();
    }

    public EventBus events() {
        return world.events();
    }

    public List<Player> players() {
        return world.players();
    }

    /** Number of ticks simulated so far. */
    public long tickNumber() {
        return tickNumber;
    }

    public int ticksPerSecond() {
        return ticksPerSecond;
    }

    /** Simulated time in seconds. */
    public float elapsedSeconds() {
        return tickNumber / (float) ticksPerSecond;
    }

    /** The tick that is about to run, as the systems see it. */
    public Tick currentTick() {
        return new Tick(tickNumber, ticksPerSecond);
    }

    // ------------------------------------------------------------------ services

    /** Issues and cancels unit orders, including formation moves. */
    public UnitDirector commands() {
        return commands;
    }

    /** Queues production in buildings. */
    public ProductionSystem production() {
        return production;
    }

    /** Path searches and their per tick budget. */
    public PathService pathService() {
        return pathService;
    }

    public PathFinder pathFinder() {
        return pathFinder;
    }

    /** Terrain of the world; tiles outside loaded chunks are generated on demand. */
    public TerrainMap map() {
        return map;
    }

    /** Chunk streaming control: where the engine keeps the world in memory. */
    public MapStreaming streaming() {
        return map;
    }

    public VisualLibrary visuals() {
        return visuals;
    }

    public SystemPipeline pipeline() {
        return pipeline;
    }

    // ------------------------------------------------------------------ simulation

    /** Advances the simulation by one fixed timestep. */
    public void tick() {
        long number = world.advanceTick();
        Tick tick = new Tick(number, ticksPerSecond);
        pipeline.tick(world, tick);
        world.flush();
        tickNumber = number;
    }

    /** Advances the simulation by the given number of ticks. */
    public void tick(int ticks) {
        for (int i = 0; i < ticks; i++) {
            tick();
        }
    }

    /**
     * Ticks until the condition holds, at most {@code maxTicks} times. Returns whether the condition
     * was reached, which lets scenarios and tests wait for an event instead of guessing a tick count.
     */
    public boolean tickUntil(Predicate<OcRtsEngine> condition, int maxTicks) {
        if (condition.test(this)) {
            return true;
        }
        for (int i = 0; i < maxTicks; i++) {
            tick();
            if (condition.test(this)) {
                return true;
            }
        }
        return false;
    }

    /** Moves the point of interest; chunks around it are streamed in on the next tick. */
    public void focus(Vec2 center) {
        map.focus(center, map.radiusChunks());
    }

    public void focus(GridPos tile) {
        focus(tile.center());
    }

    /** Tiles that are currently held in memory. */
    public TileRect loadedBounds() {
        return map.loadedBounds().orElse(null);
    }

    // ------------------------------------------------------------------ convenience spawning

    /** Spawns a unit by content path, for example {@code spawnUnit("soldier", player, position)}. */
    public Unit spawnUnit(String unitPath, Player owner, Vec2 position) {
        UnitType type = content().unit(unitPath);
        return world.spawnUnit(type, owner, position);
    }

    public Unit spawnUnit(String unitPath, Player owner, GridPos tile) {
        return spawnUnit(unitPath, owner, tile.center());
    }

    /** Spawns a finished building by content path. */
    public Building spawnBuilding(String buildingPath, Player owner, GridPos origin) {
        BuildingType type = content().building(buildingPath);
        return world.spawnBuilding(type, owner, origin);
    }

    // ------------------------------------------------------------------ presentation seams

    /** Immutable view of the world around a point, for a renderer, a minimap or a test. */
    public WorldSnapshot snapshot(GridPos center, int radiusTiles) {
        return snapshots.capture(center, radiusTiles);
    }

    /** Text picture of the world around a point; the engine itself never draws anything. */
    public String render(GridPos center, int radiusTiles) {
        return asciiRenderer.render(snapshot(center, radiusTiles));
    }

    /** Multi line summary of the simulation, handy when a scenario seems to do nothing. */
    public String describe() {
        StringBuilder text = new StringBuilder();
        text.append("tick ").append(tickNumber).append(" (").append(String.format("%.1f", elapsedSeconds())).append("s)")
                .append(", entities ").append(world.entityCount())
                .append(", units ").append(world.units().size())
                .append(", buildings ").append(world.buildings().size())
                .append(", chunks ").append(map.loadedChunkCount())
                .append(", pending paths ").append(pathService.pendingRequests())
                .append(", pending deliveries ").append(world.players().stream()
                        .mapToInt(player -> player.hasLogistics() ? player.logistics().pendingDeliveries() : 0).sum());
        text.append('\n').append("systems: ").append(String.join(", ", pipeline.describe()));
        return text.toString();
    }
}
