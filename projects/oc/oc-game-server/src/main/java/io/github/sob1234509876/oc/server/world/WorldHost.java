package io.github.sob1234509876.oc.server.world;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.OcRtsEngine;
import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.content.BuildingType;
import io.github.sob1234509876.oc.rts.content.ContentRegistry;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.ItemType;
import io.github.sob1234509876.oc.rts.content.ScenarioWorldContent;
import io.github.sob1234509876.oc.rts.content.StartingResources;
import io.github.sob1234509876.oc.rts.content.TerrainType;
import io.github.sob1234509876.oc.rts.content.UnitType;
import io.github.sob1234509876.oc.rts.content.WorldContent;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Entity;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.map.FlatChunkGenerator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The server side world: one {@link OcRtsEngine} advanced on one daemon thread and rendered by the browser.
 *
 * <p>The world is a deterministic 64 x 64 slice of a flat map. {@link FlatChunkGenerator} produces one
 * terrain for every tile, so terrain never depends on a noise field and a snapshot of an unchanged tick is
 * byte for byte reproducible. The seed still matters: it selects which of the buildable flat grounds of
 * the content fills the map and it seeds the engine (world random, path service, production), so two hosts
 * with different seeds describe visibly different worlds. The map is otherwise bounded only by the
 * coordinate window this host reports; entities of the sample scene are spawned well inside it.
 *
 * <p><strong>Content comes from a seam, not from this class.</strong> The catalogue is a
 * {@link WorldContent} handed to the constructor. {@link #WorldHost(long)} keeps the historical behaviour
 * and serves {@link ScenarioWorldContent}; {@link #WorldHost(long, WorldContent)} serves whatever a plugin
 * registered. This class never names a unit, building or item of a concrete catalogue: the starting scene
 * is derived from the registry (one building of each type, one unit of each type), and the starting stock
 * comes from {@link WorldContent#startingResources()}. That is what lets a plugin ship a different game
 * without the server being recompiled against it.
 *
 * <p><strong>Time.</strong> The simulation has no wall clock. The thread reads {@link System#nanoTime()}
 * only to fill a small accumulator and then calls {@link OcRtsEngine#tick()} a whole number of times, so
 * the simulation always advances in fixed 1/20 s steps. A snapshot taken between two steps is therefore a
 * state the engine really passed through, not a half updated frame.
 *
 * <p><strong>Threading.</strong> The engine is not thread safe. Every call that reads it
 * ({@link #tick()}, {@link #snapshotJson()}) and every step of the tick thread is serialised on one lock,
 * which is why an HTTP request may capture the live world while it is being simulated without seeing a
 * torn tile row or an entity at a position it never occupied. {@link #player()} and {@link #content()}
 * hand out live objects for diagnostics and checks and are <em>not</em> synchronised: read them before
 * {@link #start()} or after {@link #stop()}, never while the tick thread runs.
 *
 * <p><strong>The content paths are the client contract.</strong> {@code snapshotJson()} derives every
 * entity's {@code kind} from {@code Entity.typeKey().path()}, which is the exact path the served catalogue
 * registered: for {@link ScenarioWorldContent} that is {@code worker}, {@code soldier}, {@code medic},
 * {@code warehouse}, {@code barracks}, {@code workshop}, {@code ore_silo}. Buildings use their building
 * path in the same shape as units. The browser renderer reads these strings as {@code EntityView.kind}
 * (see {@code web-client/src/render/view.ts}); renaming a catalogue entry therefore renames the kind on
 * the wire, and the client is expected to fall back to its procedural shape for a kind it does not know.
 */
public final class WorldHost {

    /** Side length of the reported map, in tiles. */
    public static final int MAP_SIZE = 64;

    /** Pixel size of one tile the browser is told to expect; the client still scales to its camera. */
    public static final int TILE_PIXELS = 16;

    /** Fixed simulation rate: 20 ticks per second, one tick every 50 ms. */
    public static final int TICKS_PER_SECOND = 20;

    /** Seed used when the caller does not name one, matching {@code --seed}'s default. */
    public static final long DEFAULT_SEED = 1L;

    /** Length of one fixed simulation step, in nanoseconds. */
    private static final long STEP_NANOS = 1_000_000_000L / TICKS_PER_SECOND;

    /** Upper bound on catch up steps per wake, so a suspended machine cannot freeze this thread. */
    private static final int MAX_STEPS_PER_WAKE = 5;

    /** How long {@link #stop()} waits for the tick thread to leave its loop. */
    private static final long STOP_TIMEOUT_MILLIS = 2_000L;

    /** Tile the first sample building is placed on. */
    private static final int SCENE_X = 14;

    /** Tile row the first sample building row is placed on. */
    private static final int SCENE_Y = 14;

    /** Distance between two sample buildings, in tiles; larger than the widest sample footprint. */
    private static final int SCENE_SPACING = 8;

    /** Sample buildings per row before the scene wraps to the next row. */
    private static final int SCENE_COLUMNS = 4;

    /** Tile row the sample units are spawned on. */
    private static final int SCENE_UNIT_Y = 28;

    private final long seed;
    private final WorldContent worldContent;
    private final ContentRegistry content;
    private final Player player;
    private final OcRtsEngine engine;

    /** Guards the mutable engine; every tick and every read holds it. */
    private final Object stateLock = new Object();

    /** Guards the tick thread field and the running flag. */
    private final Object lifecycleLock = new Object();

    private volatile boolean running;
    private volatile Thread tickThread;

    /**
     * Builds the flat world and its sample scene from the engine's own sample catalogue.
     *
     * <p>Kept for callers written before content was a seam; it is exactly
     * {@code new WorldHost(seed, new ScenarioWorldContent())}.
     *
     * @param seed seed of the world and of the flat ground choice
     */
    public WorldHost(long seed) {
        this(seed, new ScenarioWorldContent());
    }

    /**
     * Builds the flat world and its sample scene from the given content.
     *
     * <p>The scene is derived from the catalogue, never from constants in this class: one finished
     * building of every declared building type on a grid, one unit of every declared unit type in a row
     * below them, and the first two units ordered across the map so successive snapshots show the real
     * movement pipeline working instead of a frozen picture. {@link WorldContent#startingResources()} is
     * deposited into the player's warehouses after the scene exists, because a stock has to live in a
     * warehouse to be spendable.
     *
     * <p>No thread is started until {@link #start()}.
     *
     * @param seed    seed of the world and of the flat ground choice
     * @param content the catalogue to serve, for example the one a plugin registered
     * @throws IllegalStateException when the content declares starting resources no warehouse accepts, or
     *     defines no buildable terrain to put the world on
     */
    public WorldHost(long seed, WorldContent content) {
        this.seed = seed;
        this.worldContent = Objects.requireNonNull(content, "content");
        this.content = registryOf(this.worldContent);
        TerrainType ground = flatGround(this.content, this.worldContent, seed);
        this.player = Player.of(1, "Player 1", 0xFF4FC3F7);
        this.engine = OcRtsEngine.builder(this.content)
                .seed(seed)
                .ticksPerSecond(TICKS_PER_SECOND)
                .chunkSize(FlatChunkGenerator.DEFAULT_CHUNK_SIZE)
                .viewRadiusChunks(2)
                .generator(new FlatChunkGenerator(ground))
                .player(player)
                .build();
        engine.focus(Vec2.of(MAP_SIZE / 2f, MAP_SIZE / 2f));
        spawnScene(this.content, player);
        applyStartingResources(this.worldContent, this.content, player);
    }

    /** Builds the immutable catalogue a content seam describes. */
    private static ContentRegistry registryOf(WorldContent worldContent) {
        ContentRegistry.Builder builder = ContentRegistry.builder(worldContent.namespace());
        worldContent.populate(builder);
        return builder.build();
    }

    /**
     * Chooses the uniform terrain of the flat map.
     *
     * <p>Only buildable terrain can carry the sample buildings, so the candidate list is exactly the
     * buildable grounds of the catalogue, in key order. The seed then picks one of them: seed 1 maps to the
     * first ground, an even seed to the last, and every other seed one step below its remainder. That maps
     * the sample catalogue's {@code grass}/{@code sand} pair the way it always did while still working for
     * a catalogue that declares a different set - which is what lets the self check prove that two seeds
     * are not the same world.
     */
    private static TerrainType flatGround(ContentRegistry content, WorldContent worldContent, long seed) {
        List<TerrainType> grounds = new ArrayList<>();
        for (TerrainType terrain : content.terrains()) {
            if (terrain.buildable()) {
                grounds.add(terrain);
            }
        }
        if (grounds.isEmpty()) {
            throw new IllegalStateException("world content " + worldContent.id()
                    + " declares no buildable terrain, so there is no ground to place a world on");
        }
        grounds.sort(Comparator.naturalOrder());
        long remainder = Math.floorMod(seed, (long) grounds.size());
        int index = remainder == 0 ? grounds.size() - 1 : (int) remainder - 1;
        return grounds.get(index);
    }

    /**
     * Places the sample scene: one finished building of every building type and one unit of every unit
     * type, then orders the first two units across the map.
     *
     * <p>Building footprints differ per catalogue, so the grid spacing is a constant that is wider than any
     * plausible building rather than something computed per type; the scene is a starting picture, not a
     * city planner.
     */
    private void spawnScene(ContentRegistry content, Player player) {
        int index = 0;
        for (BuildingType type : content.buildings()) {
            int x = SCENE_X + (index % SCENE_COLUMNS) * SCENE_SPACING;
            int y = SCENE_Y + (index / SCENE_COLUMNS) * SCENE_SPACING;
            engine.world().spawnBuilding(type, player, new GridPos(x, y));
            index++;
        }

        List<Unit> units = new ArrayList<>();
        index = 0;
        for (UnitType type : content.units()) {
            units.add(engine.world().spawnUnit(type, player, new GridPos(SCENE_X + index * 2, SCENE_UNIT_Y)));
            index++;
        }
        if (!units.isEmpty()) {
            engine.commands().move(units.get(0), new GridPos(44, 34));
        }
        if (units.size() > 1) {
            engine.commands().move(units.get(1), new GridPos(40, 28));
        }
    }

    /**
     * Deposits the content's starting stock into the player's warehouses.
     *
     * <p>Resources that no warehouse accepts are a content error, not something to drop silently: a player
     * who is promised a starting fortune and receives part of it has no way to tell the difference from a
     * bug in the economy.
     */
    private static void applyStartingResources(WorldContent worldContent, ContentRegistry content, Player player) {
        StartingResources starting = worldContent.startingResources();
        if (starting.isEmpty()) {
            return;
        }
        ItemBundle.Builder bundle = ItemBundle.builder();
        for (Map.Entry<String, Natural> entry : starting.amounts().entrySet()) {
            ItemType item = content.item(entry.getKey());
            bundle.add(item, entry.getValue());
        }
        ItemBundle items = bundle.build();
        ItemBundle leftover = player.logistics().deposit(items, Vec2.of(MAP_SIZE / 2f, MAP_SIZE / 2f));
        if (!leftover.isEmpty()) {
            throw new IllegalStateException("world content " + worldContent.id()
                    + " declares starting resources that no warehouse accepted: " + leftover);
        }
    }

    /** Starts the daemon tick thread. Calling this on a running host does nothing. */
    public void start() {
        synchronized (lifecycleLock) {
            if (running) {
                return;
            }
            running = true;
            Thread thread = new Thread(this::runLoop, "oc-world");
            thread.setDaemon(true);
            tickThread = thread;
            thread.start();
        }
    }

    /**
     * Stops the tick thread and waits for it to leave the loop. Calling this on a stopped host is harmless,
     * and so is calling it from the tick thread itself (the thread simply stops after its current step).
     */
    public void stop() {
        Thread thread;
        synchronized (lifecycleLock) {
            if (!running && tickThread == null) {
                return;
            }
            running = false;
            thread = tickThread;
            tickThread = null;
        }
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
            try {
                thread.join(STOP_TIMEOUT_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Whether the tick thread is running. */
    public boolean running() {
        return running;
    }

    /**
     * The number of ticks simulated so far.
     *
     * <p>This is a read, not an advance: the tick thread owns the clock. The method acquires the same lock
     * as a step, so a caller never observes a half applied tick.
     *
     * @return the engine's current tick number
     */
    public long tick() {
        synchronized (stateLock) {
            return engine.tickNumber();
        }
    }

    /** Seed this host was built with. */
    public long seed() {
        return seed;
    }

    /** The content seam this host serves; the catalogue it built is {@link #content()}. */
    public WorldContent worldContent() {
        return worldContent;
    }

    /**
     * The catalogue this host serves, built once from {@link #worldContent()} at construction.
     *
     * <p>Exposed for diagnostics and checks. The registry is immutable, so reading it is safe at any time;
     * the entities it is used to spawn are not.
     */
    public ContentRegistry content() {
        return content;
    }

    /**
     * The single player of the world.
     *
     * <p>Exposed for diagnostics and checks - the starting stock, for example, lives in this player's
     * logistics network. It is live game state and is <em>not</em> synchronised: read it before
     * {@link #start()} or after {@link #stop()}.
     */
    public Player player() {
        return player;
    }

    /** Reported map width in tiles. */
    public int width() {
        return MAP_SIZE;
    }

    /** Reported map height in tiles. */
    public int height() {
        return MAP_SIZE;
    }

    /** Pixel size of one tile reported to the browser. */
    public int tilePixels() {
        return TILE_PIXELS;
    }

    /** Fixed simulation rate of the host, in ticks per second. */
    public int ticksPerSecond() {
        return TICKS_PER_SECOND;
    }

    /**
     * Captures the current world as the JSON document the browser consumes.
     *
     * <p>The shape is intentionally small and stable:
     * <pre>{@code
     * {"tick":42,"width":64,"height":64,"tilePixels":16,
     *  "tiles":[["grass","grass", ...], ...],
     *  "entities":[{"id":1,"kind":"worker","x":12.5,"y":8.5,"player":1,"health":1.0}, ...]}
     * }</pre>
     *
     * <p>{@code tiles} is row major with exactly {@code width * height} terrain content paths, and
     * entities are sorted by id so an unchanged tick produces the same bytes. Coordinates are rounded to
     * one decimal, which is the precision the renderer needs and far below one tile.
     *
     * <p>A 64 x 64 snapshot is a few kilobytes; it is rebuilt per request rather than cached, because a
     * cached document could disagree with the tick the browser reports in its HUD.
     *
     * @return the snapshot as UTF-8 JSON text, never {@code null}
     */
    public String snapshotJson() {
        synchronized (stateLock) {
            return buildSnapshotJson();
        }
    }

    /** Runs the fixed step accumulator until {@link #stop()} clears {@link #running}. */
    private void runLoop() {
        long previous = System.nanoTime();
        long accumulator = 0L;
        while (running) {
            long now = System.nanoTime();
            accumulator += Math.max(0L, now - previous);
            previous = now;

            int steps = 0;
            while (accumulator >= STEP_NANOS && steps < MAX_STEPS_PER_WAKE) {
                synchronized (stateLock) {
                    engine.tick();
                }
                accumulator -= STEP_NANOS;
                steps++;
            }
            if (steps == MAX_STEPS_PER_WAKE) {
                // Drop the backlog instead of spiralling: the simulation must not try to catch up on minutes
                // of a suspended process.
                accumulator = 0L;
            }
            try {
                Thread.sleep(1L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private String buildSnapshotJson() {
        List<Entity> entities = new ArrayList<>(engine.world().entities());
        entities.removeIf(entity -> !entity.isAlive());
        entities.sort(Comparator.comparing(Entity::id));

        int capacity = 64 + MAP_SIZE * MAP_SIZE * 8 + entities.size() * 96;
        StringBuilder json = new StringBuilder(capacity);
        json.append("{\"tick\":").append(engine.tickNumber())
                .append(",\"width\":").append(MAP_SIZE)
                .append(",\"height\":").append(MAP_SIZE)
                .append(",\"tilePixels\":").append(TILE_PIXELS)
                .append(",\"tiles\":[");
        for (int y = 0; y < MAP_SIZE; y++) {
            if (y > 0) {
                json.append(',');
            }
            json.append('[');
            for (int x = 0; x < MAP_SIZE; x++) {
                if (x > 0) {
                    json.append(',');
                }
                appendJsonString(json, engine.map().terrainAt(x, y).key().path());
            }
            json.append(']');
        }
        json.append("],\"entities\":[");
        boolean first = true;
        for (Entity entity : entities) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"id\":").append(entity.id().value())
                    .append(",\"kind\":");
            appendJsonString(json, entity.typeKey().path());
            json.append(",\"x\":").append(oneDecimal(entity.position().x()))
                    .append(",\"y\":").append(oneDecimal(entity.position().y()))
                    .append(",\"player\":").append(entity.owner().id().value())
                    .append(",\"health\":").append(twoDecimals(entity.health().fraction()))
                    .append('}');
        }
        json.append("]}");
        return json.toString();
    }

    /** Writes a JSON string, escaping the two structural characters and every control character. */
    private static void appendJsonString(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }

    /** Formats a coordinate with the one decimal place the client contract promises. */
    private static String oneDecimal(float value) {
        return String.format(Locale.ROOT, "%.1f", Float.isFinite(value) ? value : 0f);
    }

    /** Formats a health fraction without ever emitting {@code NaN} or {@code Infinity}. */
    private static String twoDecimals(float value) {
        return String.format(Locale.ROOT, "%.2f", Float.isFinite(value) ? value : 0f);
    }
}
