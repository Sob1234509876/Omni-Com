package io.github.sob1234509876.oc.server.world;

import io.github.sob1234509876.oc.rts.OcRtsEngine;
import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.content.ContentRegistry;
import io.github.sob1234509876.oc.rts.content.TerrainType;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Entity;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.map.FlatChunkGenerator;
import io.github.sob1234509876.oc.rts.scenario.ScenarioContent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The server side world: one {@link OcRtsEngine} advanced on one daemon thread and rendered by the browser.
 *
 * <p>The world is a deterministic 64 x 64 slice of a flat map. {@link FlatChunkGenerator} produces one
 * terrain for every tile, so terrain never depends on a noise field and a snapshot of an unchanged tick is
 * byte for byte reproducible. The seed still matters: it selects which of the two buildable flat grounds
 * fills the map and it seeds the engine (world random, path service, production), so two hosts with
 * different seeds describe visibly different worlds. The map is otherwise bounded only by the coordinate
 * window this host reports; entities of the sample scene are spawned well inside it.
 *
 * <p><strong>Time.</strong> The simulation has no wall clock. The thread reads {@link System#nanoTime()}
 * only to fill a small accumulator and then calls {@link OcRtsEngine#tick()} a whole number of times, so
 * the simulation always advances in fixed 1/20 s steps. A snapshot taken between two steps is therefore a
 * state the engine really passed through, not a half updated frame.
 *
 * <p><strong>Threading.</strong> The engine is not thread safe. Every call that reads it
 * ({@link #tick()}, {@link #snapshotJson()}) and every step of the tick thread is serialised on one lock,
 * which is why an HTTP request may capture the live world while it is being simulated without seeing a
 * torn tile row or an entity at a position it never occupied.
 *
 * <p><strong>The content paths are the client contract.</strong> {@code snapshotJson()} derives every
 * entity's {@code kind} from {@code Entity.typeKey().path()}, which is the exact path the catalogue of
 * {@link ScenarioContent} registered: {@code worker}, {@code soldier}, {@code medic}, {@code warehouse},
 * {@code barracks}, {@code workshop}, {@code ore_silo}. Buildings use their building path in the same
 * shape as units. The browser renderer reads these strings as {@code EntityView.kind} (see
 * {@code web-client/src/render/view.ts}); renaming a catalogue entry therefore renames the kind on the
 * wire, and the client is expected to fall back to its procedural shape for a kind it does not know.
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

    /** Buildable flat grounds the seed may select; every tile of a map is uniform. */
    private static final String[] FLAT_GROUNDS = {"grass", "sand"};

    private final long seed;
    private final OcRtsEngine engine;

    /** Guards the mutable engine; every tick and every read holds it. */
    private final Object stateLock = new Object();

    /** Guards the tick thread field and the running flag. */
    private final Object lifecycleLock = new Object();

    private volatile boolean running;
    private volatile Thread tickThread;

    /**
     * Builds the flat world and its sample scene. No thread is started until {@link #start()}.
     *
     * @param seed seed of the world and of the flat ground choice
     */
    public WorldHost(long seed) {
        this.seed = seed;
        ContentRegistry content = ScenarioContent.omniCom();
        TerrainType ground = flatGround(content, seed);
        Player player = Player.of(1, "Player 1", 0xFF4FC3F7);
        this.engine = OcRtsEngine.builder(content)
                .seed(seed)
                .ticksPerSecond(TICKS_PER_SECOND)
                .chunkSize(FlatChunkGenerator.DEFAULT_CHUNK_SIZE)
                .viewRadiusChunks(2)
                .generator(new FlatChunkGenerator(ground))
                .player(player)
                .build();
        engine.focus(Vec2.of(MAP_SIZE / 2f, MAP_SIZE / 2f));
        spawnScene(content, player);
    }

    /**
     * Chooses the uniform terrain of the flat map.
     *
     * <p>Seed 1 maps to grass and even seeds map to sand (every odd seed is grass). Both are buildable, so
     * the sample buildings are always legal. This is the one place where the seed changes the tile matrix of
     * an otherwise flat world, which is what lets a self check prove that two seeds are not the same world.
     */
    private static TerrainType flatGround(ContentRegistry content, long seed) {
        int index = Math.floorMod(seed, (long) FLAT_GROUNDS.length) == 0
                ? FLAT_GROUNDS.length - 1
                : (int) Math.floorMod(seed, (long) FLAT_GROUNDS.length) - 1;
        return content.terrain(FLAT_GROUNDS[index]);
    }

    /**
     * Places the sample scene: two buildings that matter for the client's building shapes, three workers,
     * two soldiers and a medic. Two units are ordered across the map, so successive snapshots show the
     * real movement pipeline working instead of a frozen picture.
     */
    private void spawnScene(ContentRegistry content, Player player) {
        engine.spawnBuilding(ScenarioContent.WAREHOUSE, player, new GridPos(18, 18));
        engine.spawnBuilding(ScenarioContent.BARRACKS, player, new GridPos(26, 18));
        engine.spawnBuilding(ScenarioContent.WORKSHOP, player, new GridPos(22, 28));

        Unit firstWorker = engine.spawnUnit(ScenarioContent.WORKER, player, new GridPos(16, 24));
        engine.spawnUnit(ScenarioContent.WORKER, player, new GridPos(18, 24));
        engine.spawnUnit(ScenarioContent.WORKER, player, new GridPos(20, 24));
        Unit firstSoldier = engine.spawnUnit(ScenarioContent.SOLDIER, player, new GridPos(25, 23));
        engine.spawnUnit(ScenarioContent.SOLDIER, player, new GridPos(27, 23));
        engine.spawnUnit(ScenarioContent.MEDIC, player, new GridPos(23, 21));

        engine.commands().move(firstWorker, new GridPos(44, 34));
        engine.commands().move(firstSoldier, new GridPos(40, 28));
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
