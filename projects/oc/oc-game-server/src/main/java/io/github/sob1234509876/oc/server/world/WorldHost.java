package io.github.sob1234509876.oc.server.world;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.OcRtsEngine;
import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.PlayerId;
import io.github.sob1234509876.oc.rts.World;
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
import io.github.sob1234509876.oc.rts.core.TileRect;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.economy.WarehouseLogistics;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Entity;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.map.BiomeTable;
import io.github.sob1234509876.oc.rts.map.NoiseTerrainGenerator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The server side world: one {@link OcRtsEngine} advanced on one daemon thread and rendered by the browser.
 *
 * <p>The world is a deterministic 256 x 256 reported slice of the engine's endless noise landscape.
 * {@link NoiseTerrainGenerator} derives every tile from the seed through an integer hash field, so the same
 * seed and the same served content always paint the same water, sand, grass, forest, hill and rock, and no
 * chunk needs its neighbours to be generated first. A snapshot no longer sends the whole map: it serialises
 * one square window around a requested centre (see {@link #snapshotJson(int, int, int)}), which keeps the
 * document small however large {@link #MAP_SIZE} becomes. Entities keep absolute map coordinates and are
 * always sent in full, so a window that does not contain an entity still lets the client track it.
 *
 * <p><strong>Buildable anchors.</strong> Noise terrain contains water and rock, so no fixed tile may carry
 * the sample scene or a starting base. The host scans outward from the centre of the reported map, in a
 * fixed ring order, for the first tile whose terrain is buildable for the served catalogue; that tile is
 * {@link #spawnAnchor()}. The scene, the sample player's stock and every human starting base are then
 * placed relative to that anchor by deterministic ring searches, never by randomness. The anchor depends
 * only on the seed and the content, which is what lets two hosts with the same seed agree and lets a check
 * assert it.
 *
 * <p><strong>Content comes from a seam, not from this class.</strong> The catalogue is a
 * {@link WorldContent} handed to the constructor. {@link #WorldHost(long)} keeps the historical behaviour
 * and serves {@link ScenarioWorldContent}; {@link #WorldHost(long, WorldContent)} serves whatever a plugin
 * registered. This class never names a unit, building or item of a concrete catalogue: the starting scene
 * is derived from the registry (one building of each type, one unit of each type), and the starting stock
 * comes from {@link WorldContent#startingResources()}. That is what lets a plugin ship a different game
 * without the server being recompiled against it.
 *
 * <p><strong>The account name is the identity.</strong> {@link #joinPlayer(String)} binds one account name
 * to one player id, one base and one set of resources for the life of this host: a disconnect does not
 * reclaim anything, and a later join with the same name - from a new connection, after a close, or after a
 * server side reconnect - returns that very player. A name never seen before takes the smallest unused
 * human slot, so a world still admits at most {@value #MAX_PLAYERS} players in total and answers
 * {@code ERR world full} once ids {@value #FIRST_HUMAN_PLAYER_ID} through {@value #MAX_PLAYERS} are taken.
 * There is deliberately <em>no</em> automatic reclaim of an idle account in this version: a player whose
 * connection is gone keeps its base, its entities and its stock until the world itself is rebuilt, because
 * removing a player would leave its entities ownerless and would make a rejoin lose everything the player
 * built. {@link #joinPlayer()} keeps the older, nameless allocation for callers that have no account name;
 * the command layer always uses the named form.
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
 * <p><strong>Commands.</strong> Client commands arrive on socket threads and may never touch the engine
 * while it is ticking. {@link #submitCommand(WorldCommand)} is the only way in: unless it is called on
 * the world thread itself - where it applies immediately because the caller already holds the simulation
 * - it appends the command to a {@link ConcurrentLinkedQueue}. At the <em>start</em> of every fixed step
 * {@link #step()} drains that queue, one command at a time, before {@link OcRtsEngine#tick()} runs, so a
 * command and the tick it affects form one deterministic sequence and the engine is never observed half
 * mutated. The handle returned by {@code submitCommand} completes with the answer the command produced
 * when it was applied; {@link #drainForTest()} applies the same queue without advancing the clock, which
 * lets a check drive commands deterministically instead of waiting for a wall clock tick. Submission
 * order is application order: commands are applied in the order the queue accepted them, one at a time.
 *
 * <p><strong>The content paths are the client contract.</strong> {@code snapshotJson()} derives every
 * entity's {@code kind} from {@code Entity.typeKey().path()}, which is the exact path the served catalogue
 * registered: for {@link ScenarioWorldContent} that is {@code worker}, {@code soldier}, {@code medic},
 * {@code warehouse}, {@code barracks}, {@code workshop}, {@code ore_silo}. Buildings use their building
 * path in the same shape as units. The browser renderer reads these strings as {@code EntityView.kind}
 * (see {@code web-client/src/render/view.ts}); renaming a catalogue entry therefore renames the kind on
 * the wire, and the client is expected to fall back to its procedural shape for a kind it does not know.
 *
 * <p><strong>Plugin tick hooks.</strong> The host also owns the end of a step: after
 * {@link OcRtsEngine#tick()} it calls every {@link WorldTickHook} it was given, in the order it was given
 * them, on the same thread and under the same lock. The hooks are the plugin seam into the running
 * world - a plugin cannot reach the engine itself - so the host documents the contract here and the
 * interface documents the rules; see {@link WorldTickHook}.
 */
public final class WorldHost {

    private static final System.Logger LOG = System.getLogger("oc.world");

    /** Side length of the reported map, in tiles. The one place this size is stated. */
    public static final int MAP_SIZE = 256;

    /** Radius of a snapshot window when the request names none, in tiles. */
    public static final int DEFAULT_SNAPSHOT_RADIUS = 32;

    /** Smallest snapshot radius a request may ask for, in tiles. */
    public static final int MIN_SNAPSHOT_RADIUS = 16;

    /** Largest snapshot radius a request may ask for, in tiles. */
    public static final int MAX_SNAPSHOT_RADIUS = 64;

    /** Pixel size of one tile the browser is told to expect; the client still scales to its camera. */
    public static final int TILE_PIXELS = 16;

    /** Fixed simulation rate: 20 ticks per second, one tick every 50 ms. */
    public static final int TICKS_PER_SECOND = 20;

    /** Seed used when the caller does not name one, matching {@code --seed}'s default. */
    public static final long DEFAULT_SEED = 1L;

    /**
     * First player id a human connection can be assigned.
     *
     * <p>Player {@code 1} owns the sample scene the host builds and player {@code 2} is reserved for the
     * classic plugin's enemy AI (its bootstrap hard codes that id). Human players therefore start at
     * {@code 3}; see {@link #joinPlayer(String)} for the exact assignment rule.
     */
    public static final int FIRST_HUMAN_PLAYER_ID = 3;

    /**
     * Maximum number of players one world holds, counting the scene player and the classic AI.
     *
     * <p>This is the "room" of {@link #joinPlayer(String)}: ids {@code 3} through {@code MAX_PLAYERS}
     * are the human slots, so a default world admits six distinct account names. An id is bound to a name
     * for the life of the world and is not reused for a different name, which keeps an entity's owner
     * stable for a replay and lets a reconnecting account find its own base again.
     */
    public static final int MAX_PLAYERS = 8;

    /**
     * Content tag that marks a unit as a builder.
     *
     * <p>The classic AI selects its builder with the same tag, so a catalogue that wants human players to
     * start with a builder has to tag it exactly the same way.
     */
    private static final String BUILDER_TAG = "builder";

    /**
     * Content path of the currency item the snapshot reports as {@code gold}.
     *
     * <p>This is the path the classic catalogue registers ({@code ClassicContent.GOLD}) and the server
     * cannot import that plugin, so the path is repeated here as a wire contract. It is resolved through
     * the served {@link ContentRegistry}, never assumed to exist: a catalogue without an item of that path
     * simply reports {@code 0}. See {@link #snapshotJson()} for why the two resources are explicit.
     */
    private static final String GOLD_ITEM_PATH = "gold";

    /**
     * Content path of the building-material item the snapshot reports as {@code wood}.
     *
     * <p>See {@link #GOLD_ITEM_PATH}; this is the classic catalogue's {@code ClassicContent.WOOD}.
     */
    private static final String WOOD_ITEM_PATH = "wood";

    /** Number of human slots a starting base anchor exists for: ids 3 through 8. */
    private static final int BASE_SLOT_COUNT = MAX_PLAYERS - FIRST_HUMAN_PLAYER_ID + 1;

    /** Anchors per row; the six human slots form a 3 x 2 grid around the spawn anchor. */
    private static final int BASE_ANCHOR_COLUMNS = 3;

    /** Distance between two anchor columns, in tiles. */
    private static final int BASE_ANCHOR_STRIDE_X = 24;

    /** Distance from the spawn anchor row to the two rows of human base anchors, in tiles. */
    private static final int BASE_ANCHOR_STRIDE_Y = 48;

    /** How many rings around its anchor the warehouse site search visits before scanning the whole map. */
    private static final int BASE_SITE_SEARCH_RADIUS = 16;

    /** How many rings around the warehouse the builder tile search visits before scanning the whole map. */
    private static final int BASE_UNIT_SEARCH_RADIUS = 6;

    /**
     * Colours handed to human players, cycled by id.
     *
     * <p>The value only reaches a renderer through {@link Player#colorArgb()}; it is a fixed function of
     * the id, never a random choice, so two runs assigning the same ids paint the same players.
     */
    private static final int[] HUMAN_COLORS = {
        0xFF66BB6A, 0xFFFFA726, 0xFFAB47BC, 0xFF26A69A, 0xFF8D6E63, 0xFFEC407A
    };

    /** Length of one fixed simulation step, in nanoseconds. */
    private static final long STEP_NANOS = 1_000_000_000L / TICKS_PER_SECOND;

    /** Upper bound on catch up steps per wake, so a suspended machine cannot freeze this thread. */
    private static final int MAX_STEPS_PER_WAKE = 5;

    /** How long {@link #stop()} waits for the tick thread to leave its loop. */
    private static final long STOP_TIMEOUT_MILLIS = 2_000L;

    /** How far left of the spawn anchor the sample scene's first building column starts, in tiles. */
    private static final int SCENE_ORIGIN_OFFSET_X = 12;

    /** How far above the spawn anchor the sample scene's first building row starts, in tiles. */
    private static final int SCENE_ORIGIN_OFFSET_Y = 8;

    /** Distance between two sample buildings, in tiles; larger than the widest sample footprint. */
    private static final int SCENE_SPACING = 8;

    /** Sample buildings per row before the scene wraps to the next row. */
    private static final int SCENE_COLUMNS = 4;

    /** How far below the scene origin the sample units are spawned, in tiles. */
    private static final int SCENE_UNIT_OFFSET_Y = 24;

    /** How many rings a scene placement may walk around its preferred tile before scanning the whole map. */
    private static final int SCENE_SEARCH_RADIUS = 16;

    /** An empty tile rectangle, used when a unit search excludes no building footprint. */
    private static final TileRect NO_TILES = new TileRect(0, 0, -1, -1);

    /** First movement target of the sample scene, relative to the spawn anchor. */
    private static final int SCENE_MOVE_X = 16;

    /** Second coordinate of the first movement target, relative to the spawn anchor. */
    private static final int SCENE_MOVE_Y = 20;

    private final long seed;
    private final WorldContent worldContent;
    private final ContentRegistry content;
    private final Player player;
    private final OcRtsEngine engine;

    /**
     * The first buildable tile the host found when scanning outwards from the map centre.
     *
     * <p>Every scene building and every human starting base is placed relative to this tile, which is why it
     * is computed once, before anything spawns, and never changes. It is a pure function of the seed and the
     * served catalogue; see {@link #spawnAnchor()}.
     */
    private final GridPos spawnAnchor;

    /**
     * Account name to player id, for every identity this host has admitted.
     *
     * <p>Owned by the world thread exactly like the engine: it is only written from
     * {@link #joinPlayer(String)}, which requires the simulation lock, and it is read from
     * {@link #joinPlayer(String)} and, under that same lock, from the read-only lookups. The map is
     * deliberately never trimmed: a disconnected account keeps its slot and its base until the world is
     * rebuilt, because there is no session concept here to tell "idle" from "gone" and because reclaiming a
     * slot would let a different account inherit an existing player's entities.
     */
    private final Map<String, Integer> accountIds = new HashMap<>();

    /**
     * The plugin hooks that run at the end of every fixed step, in this order.
     *
     * <p>Immutable, and deliberately copied at construction: the list the loader exposes is startup
     * state that {@code unloadAll()} clears, while a running host must keep the hooks it started with
     * until it is rebuilt. The host is the only caller, which is why the order is simply the order the
     * list was handed over in.
     */
    private final List<WorldTickHook> tickHooks;

    /** Guards the mutable engine; every tick and every read holds it. */
    private final Object stateLock = new Object();

    /** Guards the tick thread field and the running flag. */
    private final Object lifecycleLock = new Object();

    /**
     * Commands waiting for the start of the next fixed step, in submission order.
     *
     * <p>A {@link ConcurrentLinkedQueue} is the smallest structure that gives the two properties this
     * seam needs: several client threads may append concurrently, and the world thread takes commands
     * with {@code poll()} in the order the queue accepted them. Enqueueing is additionally serialised on
     * {@link #stateLock} together with {@link #stopped}, so a command cannot slip into the queue after
     * the drain of {@link #stop()} and wait forever.
     */
    private final ConcurrentLinkedQueue<PendingCommand> pendingCommands = new ConcurrentLinkedQueue<>();

    private volatile boolean running;

    /** Whether {@link #stop()} has closed this host; commands submitted afterwards fail immediately. */
    private volatile boolean stopped;

    private volatile Thread tickThread;

    /**
     * Builds the noise world and its sample scene from the engine's own sample catalogue.
     *
     * <p>Kept for callers written before content was a seam; it is exactly
     * {@code new WorldHost(seed, new ScenarioWorldContent(), List.of())}.
     *
     * @param seed seed of the noise terrain, of the spawn anchor and of the engine's own streams
     */
    public WorldHost(long seed) {
        this(seed, new ScenarioWorldContent(), List.of());
    }

    /**
     * Builds the noise world and its sample scene from the given content, without plugin tick hooks.
     *
     * <p>Kept for callers written before plugins could hook the tick; it is exactly
     * {@code new WorldHost(seed, content, List.of())}.
     *
     * @param seed    seed of the noise terrain, of the spawn anchor and of the engine's own streams
     * @param content the catalogue to serve, for example the one a plugin registered
     */
    public WorldHost(long seed, WorldContent content) {
        this(seed, content, List.of());
    }

    /**
     * Builds the noise world and its sample scene from the given content and the given tick hooks.
     *
     * <p>{@link NoiseTerrainGenerator} paints the six biomes of the served catalogue and the host scans out
     * from the centre of {@link #MAP_SIZE} for the first buildable tile. The scene is derived from the
     * catalogue, never from constants in this class: one finished building of every declared building type
     * around that anchor, one unit of every declared unit type below them, and the first two units ordered
     * across the map so successive snapshots show the real movement pipeline working instead of a frozen
     * picture. {@link WorldContent#startingResources()} is deposited into the player's warehouses after the
     * scene exists, because a stock has to live in a warehouse to be spendable.
     *
     * <p>The hooks run at the end of every fixed step, after the engine tick, in the order given; see
     * {@link #step()}. The list is copied, so a caller may change its own list afterwards without
     * affecting a running host.
     *
     * <p>No thread is started until {@link #start()}.
     *
     * @param seed      seed of the noise terrain, of the spawn anchor and of the engine's own streams
     * @param content   the catalogue to serve, for example the one a plugin registered
     * @param tickHooks the plugin hooks to run after every fixed step, in order; never {@code null}
     * @throws IllegalStateException when the content declares starting resources no warehouse accepts, or
     *     defines no buildable terrain to put the world on
     */
    public WorldHost(long seed, WorldContent content, List<WorldTickHook> tickHooks) {
        this.seed = seed;
        this.worldContent = Objects.requireNonNull(content, "content");
        this.tickHooks = List.copyOf(Objects.requireNonNull(tickHooks, "tickHooks"));
        this.content = registryOf(this.worldContent);
        BiomeTable biomes = biomeTable(this.content, this.worldContent);
        this.player = Player.of(1, "Player 1", 0xFF4FC3F7);
        this.engine = OcRtsEngine.builder(this.content)
                .seed(seed)
                .ticksPerSecond(TICKS_PER_SECOND)
                .chunkSize(NoiseTerrainGenerator.DEFAULT_CHUNK_SIZE)
                .viewRadiusChunks(2)
                .generator(new NoiseTerrainGenerator(seed, biomes))
                .player(player)
                .build();
        this.spawnAnchor = findSpawnAnchor(engine.world());
        engine.focus(spawnAnchor.center());
        spawnScene(this.content, player);
        ItemBundle leftover = depositStartingResources(this.worldContent, this.content, player,
                spawnAnchor.center());
        if (!leftover.isEmpty()) {
            throw new IllegalStateException("world content " + this.worldContent.id()
                    + " declares starting resources that no warehouse accepted: " + leftover);
        }
    }

    /** Builds the immutable catalogue a content seam describes. */
    private static ContentRegistry registryOf(WorldContent worldContent) {
        ContentRegistry.Builder builder = ContentRegistry.builder(worldContent.namespace());
        worldContent.populate(builder);
        return builder.build();
    }

    /**
     * Resolves the six biome definitions the noise generator paints from the served catalogue.
     *
     * <p>A catalogue does not have to register all six canonical paths. The full sample scenario does; the
     * classic game registers only water, sand, grass and rock, and a test fixture may register a single
     * ground. Missing kinds therefore collapse to a documented neighbour instead of failing the world:
     * missing <em>grass</em> (the open buildable land) falls back to the first buildable terrain in key
     * order, missing <em>water</em> and <em>rock</em> to the first impassable terrain (or the buildable
     * ground when the catalogue has none), missing <em>sand</em> and <em>forest</em> to the open ground,
     * and missing <em>hill</em> to sand when it exists. A catalogue with no buildable terrain at all is a
     * content error, because no scene or base could be placed.
     *
     * @throws IllegalStateException when the catalogue declares no buildable terrain
     */
    private static BiomeTable biomeTable(ContentRegistry content, WorldContent worldContent) {
        TerrainType ground = firstBuildable(content, worldContent);
        TerrainType impassable = firstImpassable(content).orElse(ground);
        TerrainType water = terrainOr(content, BiomeTable.WATER_PATH, impassable);
        TerrainType sand = terrainOr(content, BiomeTable.SAND_PATH, ground);
        TerrainType grass = terrainOr(content, BiomeTable.GRASS_PATH, ground);
        TerrainType forest = terrainOr(content, BiomeTable.FOREST_PATH, grass);
        TerrainType hill = terrainOr(content, BiomeTable.HILL_PATH, sand);
        TerrainType rock = terrainOr(content, BiomeTable.ROCK_PATH, impassable);
        return BiomeTable.of(water, sand, grass, forest, hill, rock);
    }

    /** The first buildable terrain of the catalogue in key order. */
    private static TerrainType firstBuildable(ContentRegistry content, WorldContent worldContent) {
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
        return grounds.get(0);
    }

    /** The first impassable terrain of the catalogue in key order, if it declares one. */
    private static Optional<TerrainType> firstImpassable(ContentRegistry content) {
        List<TerrainType> impassable = new ArrayList<>();
        for (TerrainType terrain : content.terrains()) {
            if (!terrain.passable()) {
                impassable.add(terrain);
            }
        }
        impassable.sort(Comparator.naturalOrder());
        return impassable.isEmpty() ? Optional.empty() : Optional.of(impassable.get(0));
    }

    /** A terrain path of the catalogue, or the documented fallback when the catalogue omits it. */
    private static TerrainType terrainOr(ContentRegistry content, String path, TerrainType fallback) {
        TerrainType found = content.terrainOrNull(content.key(path));
        return found == null ? fallback : found;
    }

    /**
     * Finds the deterministic spawn anchor: the first buildable tile on rings growing out from the centre
     * of the reported map.
     *
     * <p>The scan is a pure function of the terrain field: no random stream, no clock and no content
     * iteration order. Within one ring the tiles are visited in row-major order ({@code dy} then {@code dx}),
     * so two hosts with the same seed and the same catalogue answer the same anchor. "Buildable" is the same
     * predicate the engine uses for construction ({@link io.github.sob1234509876.oc.rts.map.TerrainMap#isBuildable}),
     * so the tile can carry a building of any served catalogue without a second rule.
     *
     * @return the first buildable tile near the centre; never {@code null} once the catalogue has buildable
     *     terrain, which the constructor guarantees
     */
    private static GridPos findSpawnAnchor(World world) {
        int centre = MAP_SIZE / 2;
        for (int ring = 0; ring <= MAP_SIZE; ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) {
                        continue;
                    }
                    int x = centre + dx;
                    int y = centre + dy;
                    if (x < 0 || y < 0 || x >= MAP_SIZE || y >= MAP_SIZE) {
                        continue;
                    }
                    GridPos tile = new GridPos(x, y);
                    if (world.map().isBuildable(tile)) {
                        return tile;
                    }
                }
            }
        }
        // Defensive only: a catalogue without buildable terrain is rejected before the engine is built. A
        // world that paints no buildable tile inside the reported square still has to report something.
        return new GridPos(centre, centre);
    }

    /**
     * Places the sample scene: one finished building of every building type and one unit of every unit
     * type, then orders the first two units across the map.
     *
     * <p>The preferred grid is relative to {@link #spawnAnchor}, so the scene follows the buildable ground
     * the host found instead of a fixed patch of a flat map. A preferred tile is only a hint: each building
     * is placed by the same deterministic ring search a starting base uses, and falls back to a row-major
     * scan of the reported map. Noise terrain therefore cannot drop a warehouse into water, and the result
     * is still a pure function of the seed and the catalogue.
     *
     * <p>Building footprints differ per catalogue, so the grid spacing is a constant that is wider than any
     * plausible building rather than something computed per type; the scene is a starting picture, not a
     * city planner.
     */
    private void spawnScene(ContentRegistry content, Player player) {
        World world = engine.world();
        int originX = spawnAnchor.x() - SCENE_ORIGIN_OFFSET_X;
        int originY = spawnAnchor.y() - SCENE_ORIGIN_OFFSET_Y;
        int index = 0;
        for (BuildingType type : content.buildings()) {
            GridPos preferred = new GridPos(
                    originX + (index % SCENE_COLUMNS) * SCENE_SPACING,
                    originY + (index / SCENE_COLUMNS) * SCENE_SPACING);
            GridPos origin = scanFreeOrigin(world, type, preferred, SCENE_SEARCH_RADIUS);
            if (origin == null) {
                origin = scanFreeOriginMap(world, type);
            }
            if (origin == null) {
                throw new IllegalStateException("world content " + worldContent.id()
                        + " has no buildable tile for the sample " + type.key().path() + " near "
                        + spawnAnchor + " or anywhere else in the map");
            }
            world.spawnBuilding(type, player, origin);
            index++;
        }

        List<Unit> units = new ArrayList<>();
        index = 0;
        for (UnitType type : content.units()) {
            GridPos preferred = new GridPos(originX + index * 2, originY + SCENE_UNIT_OFFSET_Y);
            GridPos tile = scanFreeTile(world, preferred, SCENE_SEARCH_RADIUS, NO_TILES);
            if (tile == null) {
                tile = scanFreeTileMap(world, NO_TILES);
            }
            if (tile == null) {
                throw new IllegalStateException("world content " + worldContent.id()
                        + " has no free buildable tile for the sample " + type.key().path());
            }
            units.add(world.spawnUnit(type, player, tile));
            index++;
        }
        if (!units.isEmpty()) {
            engine.commands().move(units.get(0), sceneMoveTarget(SCENE_MOVE_X, SCENE_MOVE_Y));
        }
        if (units.size() > 1) {
            engine.commands().move(units.get(1), sceneMoveTarget(SCENE_MOVE_X - 4, SCENE_MOVE_Y - 6));
        }
    }

    /** A movement target near the anchor, clamped to the reported map so a unit never walks off it. */
    private GridPos sceneMoveTarget(int dx, int dy) {
        int x = Math.max(0, Math.min(MAP_SIZE - 1, spawnAnchor.x() + dx));
        int y = Math.max(0, Math.min(MAP_SIZE - 1, spawnAnchor.y() + dy));
        return new GridPos(x, y);
    }

    /**
     * Spawns a human player's starting base and deposits the content's starting stock into it.
     *
     * <p>The base is one drop off building and one builder unit. The building is the first warehouse type
     * of the catalogue in key order - the same drop off the classic AI calls
     * {@code ClassicContent.WAREHOUSE} - and the unit is the first unit carrying the {@value #BUILDER_TAG}
     * tag, which is how the classic AI finds its engineer. A catalogue that declares neither is a sandbox:
     * the player still joins, and one log line records why no base was spawned.
     *
     * <p>Placement is deterministic. Every human slot has an anchor tile on a fixed 3 x 2 grid around
     * {@link #spawnAnchor()}, the warehouse search visits rings of tiles around that anchor in row-major
     * order and takes the first origin where the building fits on buildable ground and overlaps no living
     * entity, and the builder is placed on the first free tile in rings around the warehouse. Two joins
     * therefore never pick the same tiles: the second search sees the first base as occupied. If the
     * anchored search exhausts its rings, a row-major scan of the whole map is the documented fallback, so a
     * crowded or watery map cannot make the result random.
     */
    private void spawnStartingBase(Player player) {
        World world = engine.world();
        BuildingType warehouseType = dropOffBuilding(content);
        UnitType builderType = builderUnit(content);
        if (warehouseType == null || builderType == null) {
            LOG.log(System.Logger.Level.INFO, "world content " + worldContent.id()
                    + " declares " + (warehouseType == null ? "no warehouse building" : "no builder unit")
                    + "; player " + player.id() + " joins without a starting base");
            return;
        }
        GridPos origin = findWarehouseSite(world, warehouseType, player.id().value());
        GridPos builderTile = origin == null ? null : findBuilderTile(world, warehouseType, origin);
        if (origin == null || builderTile == null) {
            LOG.log(System.Logger.Level.INFO, "world content " + worldContent.id()
                    + " has no free starting site for player " + player.id()
                    + "; the player joins without a starting base");
            return;
        }
        Building warehouse = world.spawnBuilding(warehouseType, player, origin);
        world.spawnUnit(builderType, player, builderTile);
        ItemBundle leftover = depositStartingResources(worldContent, content, player,
                warehouse.position());
        if (!leftover.isEmpty()) {
            LOG.log(System.Logger.Level.WARNING, "player " + player.id()
                    + " could not store all starting resources: " + leftover);
        }
    }

    /** The catalogue's drop off building: the lowest keyed type flagged as a warehouse, or {@code null}. */
    private static BuildingType dropOffBuilding(ContentRegistry content) {
        BuildingType best = null;
        for (BuildingType type : content.buildings()) {
            if (!type.isWarehouse()) {
                continue;
            }
            if (best == null || type.key().compareTo(best.key()) < 0) {
                best = type;
            }
        }
        return best;
    }

    /** The catalogue's builder unit: the lowest keyed unit carrying the {@value #BUILDER_TAG} tag. */
    private static UnitType builderUnit(ContentRegistry content) {
        UnitType best = null;
        for (UnitType type : content.units()) {
            if (!type.hasTag(BUILDER_TAG)) {
                continue;
            }
            if (best == null || type.key().compareTo(best.key()) < 0) {
                best = type;
            }
        }
        return best;
    }

    /**
     * The anchor of one human slot: a fixed 3 x 2 grid around the spawn anchor, never a random tile.
     *
     * <p>Columns are 24 tiles apart and the two rows lie 48 tiles above and below the spawn anchor. The
     * order is slot order (id 3, 4, 5 in the top row; 6, 7, 8 in the bottom row), so two joins of different
     * accounts always start from different anchors; the ring searches still make the final base positions
     * collision free on noisy terrain.
     */
    private GridPos baseAnchor(int playerId) {
        int slot = Math.floorMod(playerId - FIRST_HUMAN_PLAYER_ID, BASE_SLOT_COUNT);
        int column = slot % BASE_ANCHOR_COLUMNS;
        int row = slot / BASE_ANCHOR_COLUMNS;
        int dx = (column - (BASE_ANCHOR_COLUMNS / 2)) * BASE_ANCHOR_STRIDE_X;
        int dy = (row == 0 ? -1 : 1) * BASE_ANCHOR_STRIDE_Y;
        return spawnAnchor.plus(dx, dy);
    }

    /** The warehouse origin for a player: rings around the slot anchor first, then the whole map. */
    private GridPos findWarehouseSite(World world, BuildingType type, int playerId) {
        GridPos site = scanFreeOrigin(world, type, baseAnchor(playerId), BASE_SITE_SEARCH_RADIUS);
        return site != null ? site : scanFreeOriginMap(world, type);
    }

    /** Visits rings around an anchor in row-major order and returns the first origin the building fits. */
    private static GridPos scanFreeOrigin(World world, BuildingType type, GridPos anchor, int radius) {
        for (int ring = 0; ring <= radius; ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) {
                        continue;
                    }
                    GridPos origin = new GridPos(anchor.x() + dx, anchor.y() + dy);
                    if (freeOrigin(world, type, origin)) {
                        return origin;
                    }
                }
            }
        }
        return null;
    }

    /** Row-major fallback of {@link #findWarehouseSite(World, BuildingType, int)}. */
    private static GridPos scanFreeOriginMap(World world, BuildingType type) {
        for (int y = 0; y < MAP_SIZE; y++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                GridPos origin = new GridPos(x, y);
                if (freeOrigin(world, type, origin)) {
                    return origin;
                }
            }
        }
        return null;
    }

    /**
     * Whether a building of the type can be spawned at the origin without overlapping anything alive.
     *
     * <p>{@link World#canPlace} covers buildable ground and other buildings; the unit check is added here
     * because {@code canPlace} deliberately only tests buildings, and a starting base must not stand on
     * top of a unit either.
     */
    private static boolean freeOrigin(World world, BuildingType type, GridPos origin) {
        if (origin.x() < 0 || origin.y() < 0
                || origin.x() + type.sizeX() > MAP_SIZE || origin.y() + type.sizeY() > MAP_SIZE) {
            return false;
        }
        if (!world.canPlace(type, origin)) {
            return false;
        }
        TileRect footprint = type.footprint(origin.x(), origin.y());
        for (Unit unit : world.units()) {
            if (unit.isAlive() && footprint.contains(unit.position().tile())) {
                return false;
            }
        }
        return true;
    }

    /** The builder tile for a warehouse: rings around a tile just below the footprint, then the whole map. */
    private static GridPos findBuilderTile(World world, BuildingType warehouse, GridPos origin) {
        TileRect footprint = warehouse.footprint(origin.x(), origin.y());
        GridPos anchor = new GridPos(origin.x() + warehouse.sizeX() / 2, origin.y() + warehouse.sizeY() + 1);
        GridPos tile = scanFreeTile(world, anchor, BASE_UNIT_SEARCH_RADIUS, footprint);
        return tile != null ? tile : scanFreeTileMap(world, footprint);
    }

    /** Visits rings around an anchor in row-major order and returns the first free buildable tile. */
    private static GridPos scanFreeTile(World world, GridPos anchor, int radius, TileRect excluded) {
        for (int ring = 0; ring <= radius; ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) {
                        continue;
                    }
                    GridPos tile = new GridPos(anchor.x() + dx, anchor.y() + dy);
                    if (freeTile(world, tile, excluded)) {
                        return tile;
                    }
                }
            }
        }
        return null;
    }

    /** Row-major fallback of {@link #findBuilderTile(World, BuildingType, GridPos)}. */
    private static GridPos scanFreeTileMap(World world, TileRect excluded) {
        for (int y = 0; y < MAP_SIZE; y++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                GridPos tile = new GridPos(x, y);
                if (freeTile(world, tile, excluded)) {
                    return tile;
                }
            }
        }
        return null;
    }

    /**
     * Whether a tile can carry a freshly spawned unit without touching any existing entity.
     *
     * <p>A unit's radius is below half a tile in every catalogue this host serves, so treating the tile of
     * its centre as its footprint is enough for a starting base, and it keeps the check simple and
     * deterministic.
     */
    private static boolean freeTile(World world, GridPos tile, TileRect excluded) {
        if (tile.x() < 0 || tile.y() < 0 || tile.x() >= MAP_SIZE || tile.y() >= MAP_SIZE) {
            return false;
        }
        if (!world.map().isBuildable(tile) || excluded.contains(tile)) {
            return false;
        }
        for (Building building : world.buildings()) {
            if (building.isAlive() && footprintOf(building).contains(tile)) {
                return false;
            }
        }
        for (Unit unit : world.units()) {
            if (unit.isAlive() && unit.position().tile().equals(tile)) {
                return false;
            }
        }
        return true;
    }

    /** Tile footprint of a spawned building. */
    private static TileRect footprintOf(Building building) {
        return building.type().footprint(building.origin().x(), building.origin().y());
    }

    /**
     * Deposits the content's starting stock into the player's warehouses from {@code at}.
     *
     * <p>The stock is resolved through the catalogue exactly like the classic AI resolves it, so a content
     * keeps one set of item paths for its own opening. Whatever the warehouses cannot hold is returned; the
     * world constructor treats that as a content error, while a joining player only gets a log line because
     * the player must still join.
     *
     * @return the part of the stock no warehouse accepted; empty when everything was stored or there is no
     *     starting stock
     */
    private static ItemBundle depositStartingResources(WorldContent worldContent, ContentRegistry content,
            Player player, Vec2 at) {
        StartingResources starting = worldContent.startingResources();
        if (starting.isEmpty()) {
            return ItemBundle.EMPTY;
        }
        ItemBundle.Builder bundle = ItemBundle.builder();
        for (Map.Entry<String, Natural> entry : starting.amounts().entrySet()) {
            ItemType item = content.item(entry.getKey());
            bundle.add(item, entry.getValue());
        }
        return player.logistics().deposit(bundle.build(), at);
    }

    /** Starts the daemon tick thread. Calling this on a running host does nothing. */
    public void start() {
        synchronized (lifecycleLock) {
            if (running) {
                return;
            }
            stopped = false;
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
        boolean wasRunning;
        synchronized (lifecycleLock) {
            wasRunning = running;
            stopped = true;
            running = false;
            thread = tickThread;
            tickThread = null;
        }
        if (!wasRunning && thread == null) {
            // Even a host that never started may hold queued commands (a check can submit before
            // start()); closing it must complete them rather than leave a caller waiting forever.
            failPendingCommands();
            return;
        }
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
            try {
                thread.join(STOP_TIMEOUT_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        failPendingCommands();
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

    /**
     * Submits one command to the world thread and returns a handle for its answer.
     *
     * <p>Outside the world thread the command is only enqueued; it is applied at the start of the next
     * fixed step (or by {@link #drainForTest()}), never concurrently with a tick. Called on the world
     * thread - a plugin hook, or a command that is already being applied - it runs immediately, because
     * the caller already owns the simulation lock and waiting for a step would deadlock the world.
     *
     * <p>Submission order is application order: the queue is FIFO and a step drains it completely before
     * the engine advances. A command submitted from a connection thread while the engine is ticking is
     * therefore applied at the next step boundary, and two commands of one connection arrive in the
     * order that connection sent them.
     *
     * @param command the command to apply on the world thread; never {@code null}
     * @return a handle that completes with the command's answer when it is applied
     * @throws NullPointerException when {@code command} is {@code null}
     */
    public CommandHandle submitCommand(WorldCommand command) {
        Objects.requireNonNull(command, "command");
        CompletableFuture<String> result = new CompletableFuture<>();
        PendingCommand pending = new PendingCommand(command, result);
        // The tick thread always holds the lock while it steps; a synchronous step() or drainForTest()
        // caller holds it too. Either way the caller already owns the simulation, so waiting for a step
        // would deadlock.
        if (Thread.holdsLock(stateLock) || Thread.currentThread() == tickThread) {
            synchronized (stateLock) {
                apply(pending);
            }
            return new CommandHandle(result);
        }
        synchronized (stateLock) {
            if (stopped) {
                result.complete("ERR world stopped");
            } else {
                pendingCommands.add(pending);
            }
        }
        return new CommandHandle(result);
    }

    /**
     * Applies every queued command without advancing the simulation.
     *
     * <p>This is the deterministic counterpart of the queue drain a fixed step performs: a check can
     * submit a command, assert that the world is untouched, then call this method and observe the command
     * take effect without a wall clock or a tick thread being involved. It is also the explicit drain
     * mentioned by {@link #submitCommand(WorldCommand)}.
     *
     * @return how many commands were applied
     */
    public int drainForTest() {
        synchronized (stateLock) {
            return applyPendingCommands();
        }
    }

    /** Number of commands waiting for the next step; approximate while other threads submit. */
    public int pendingCommandCount() {
        return pendingCommands.size();
    }

    /**
     * Advances the world by exactly one fixed step, including the queued commands and the plugin hooks.
     *
     * <p>This is the synchronous counterpart of the tick thread, and the exact work one step of it does:
     * every command queued at this point is applied first, then the engine advances by one tick, and then
     * every {@link WorldTickHook} runs, in the order the host was constructed with, with the tick number
     * the world has just reached. The step holds the same lock as {@link #snapshotJson()}, so a snapshot
     * taken from another thread is never a torn picture of a step in progress.
     *
     * <p>It exists for callers that must advance a world deterministically - a scripted scenario, or a
     * check proving that two runs produce the same bytes - where the wall-clock driven
     * {@link #start()} thread would make the tick count depend on how fast the machine is.
     */
    public void step() {
        synchronized (stateLock) {
            applyPendingCommands();
            engine.tick();
            long number = engine.tickNumber();
            for (int index = 0; index < tickHooks.size(); index++) {
                tickHooks.get(index).tick(engine.world(), number);
            }
        }
    }

    /** Empties the queue and completes every waiting caller, used when the host closes. */
    private void failPendingCommands() {
        synchronized (stateLock) {
            PendingCommand pending;
            while ((pending = pendingCommands.poll()) != null) {
                pending.result().complete("ERR world stopped");
            }
        }
    }

    /** Applies queued commands in order; the caller already holds {@link #stateLock}. */
    private int applyPendingCommands() {
        int applied = 0;
        PendingCommand pending;
        while ((pending = pendingCommands.poll()) != null) {
            apply(pending);
            applied++;
        }
        return applied;
    }

    /**
     * Runs one command and completes its handle.
     *
     * <p>A command is ordinary code running inside the simulation; if it throws, the exception costs one
     * answer, not the world thread. The message becomes the {@code ERR} reason so a client learns what
     * went wrong without a stack trace on the wire.
     */
    private void apply(PendingCommand pending) {
        String answer;
        try {
            answer = pending.command().apply(this);
        } catch (RuntimeException failure) {
            String message = failure.getMessage();
            answer = "ERR " + (message == null || message.isBlank() ? "command failed" : message);
        }
        pending.result().complete(answer == null ? "ERR command failed" : answer);
    }

    /** A command waiting for the world thread, with the future its submitter waits on. */
    private record PendingCommand(WorldCommand command, CompletableFuture<String> result) {
    }

    /**
     * One piece of work to apply on the world thread.
     *
     * <p>The command sees the host, never a bare engine, because it may need the host's player rule
     * ({@link #joinPlayer(String)}) as well as the engine. It runs with the simulation lock held and
     * must not block, spawn threads or read a clock; see the class comment on commands.
     */
    @FunctionalInterface
    public interface WorldCommand {

        /**
         * Applies the command.
         *
         * @param host the world host, never {@code null}
         * @return the one line answer to send back
         */
        String apply(WorldHost host);
    }

    /**
     * The answer of a submitted command, available as soon as the world thread applied it.
     *
     * <p>{@link #await()} is for the transports, which need the line before they can answer the client;
     * {@link #await(long)} is for callers that must not block forever. A timeout is answered with
     * {@code ERR world busy} by the handle rather than an exception, because every caller of this API is
     * a protocol path whose failure mode is an error line.
     */
    public static final class CommandHandle {

        private final CompletableFuture<String> result;

        private CommandHandle(CompletableFuture<String> result) {
            this.result = result;
        }

        /** Whether the command has been applied and the answer is ready. */
        public boolean isDone() {
            return result.isDone();
        }

        /** Waits until the command is applied and returns its answer. */
        public String await() {
            try {
                return result.get();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return "ERR command interrupted";
            } catch (ExecutionException failed) {
                return "ERR command failed";
            }
        }

        /**
         * Waits at most {@code timeoutMillis} for the answer.
         *
         * @param timeoutMillis the budget in milliseconds; negative means zero
         * @return the answer, or {@code ERR world busy} when the world did not reach the command in time
         */
        public String await(long timeoutMillis) {
            try {
                return result.get(Math.max(0L, timeoutMillis), TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeout) {
                return "ERR world busy";
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return "ERR command interrupted";
            } catch (ExecutionException failed) {
                return "ERR command failed";
            }
        }
    }

    /**
     * The plugin tick hooks of this host, in the order they run after every fixed step.
     *
     * <p>An immutable snapshot handed over at construction; a hook that was unloaded after the host was
     * built does not disappear from a running world, which is why the server builds the host from the
     * loader's list rather than the host polling it.
     *
     * @return the hooks, in running order; empty when no plugin registered one
     */
    public List<WorldTickHook> worldTickHooks() {
        return tickHooks;
    }

    /** Seed this host was built with. */
    public long seed() {
        return seed;
    }

    /**
     * The deterministic buildable tile the host found near the centre of the map.
     *
     * <p>The sample scene's preferred grid and every human starting base anchor are expressed relative to
     * this tile. It is computed once at construction by {@link #findSpawnAnchor(World)}, from the seed and
     * the served catalogue only, so two hosts built with the same seed and content answer the same tile and
     * a different seed normally answers a different one. The tile is buildable for the served content
     * ({@link io.github.sob1234509876.oc.rts.map.TerrainMap#isBuildable}), which is what makes it safe to put
     * a starting base there.
     *
     * <p>Exposed for diagnostics and checks. It is immutable, so reading it from any thread is safe.
     *
     * @return the spawn anchor tile, never {@code null}
     */
    public GridPos spawnAnchor() {
        return spawnAnchor;
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

    /**
     * The engine this host ticks.
     *
     * <p>Exposed for diagnostics and checks, exactly like {@link #player()}; it is the live engine, not a
     * copy. It is <em>not</em> synchronised: read it before {@link #start()} or from inside a
     * {@link WorldCommand}/{@link WorldTickHook}, never from an unrelated thread while the tick thread
     * runs. A command should reach the world through the host rather than through this accessor.
     *
     * @return the live engine, never {@code null}
     */
    public OcRtsEngine engine() {
        return engine;
    }

    /**
     * Returns the player bound to an account name, creating that identity on first use.
     *
     * <p><strong>Identity rule.</strong> The account name, not the connection, is the identity. The first
     * join of a name takes the smallest unused id from {@value #FIRST_HUMAN_PLAYER_ID} to
     * {@value #MAX_PLAYERS} and binds the name to it for the life of this host; every later join of the
     * same name returns the same {@link Player} with its base, entities and stock untouched. A disconnect
     * therefore changes nothing: the player stays in the world and a new connection with the same account
     * finds it again, which is what makes a reconnect answer the same id and keeps a base from piling up.
     * A different name always gets a different, still unused slot. When every slot is taken the answer is
     * {@link Optional#empty()}, which the dispatcher reports as {@code ERR world full}.
     *
     * <p>The assignment order is deterministic for a given sequence of joins, and an id is never reused for
     * another name while the world lives, so an entity's owner does not change under a replay. The colour is
     * a fixed function of the id; see the palette constant.
     *
     * <p><strong>No reclaim.</strong> A player whose connection is gone keeps existing; this version does not
     * time out, release or reassign an idle account. Reclaiming an id would either orphan the entities that
     * still belong to the old player or silently hand them to a new account, and neither is acceptable, so
     * the slot stays occupied until the host itself is rebuilt.
     *
     * <p>The player is added to the world with an engine logistics network and, when another player
     * already has one, the world's hauling system. A starting base is then spawned for the player: one
     * drop off building of the catalogue and one builder unit, on free buildable tiles chosen by a fixed
     * scan order that starts at a per-slot anchor on the map (see {@link #spawnStartingBase(Player)}).
     * Finally the content's {@link WorldContent#startingResources()} are deposited into that building, so
     * the player can immediately use the build and train commands. A sandbox catalogue that declares no
     * drop off or no builder gets a log line and no base, but still joins.
     *
     * <p>This method mutates the world and must therefore run on the world thread, which in practice
     * means from a {@link WorldCommand} (the command dispatcher does exactly that). It is not a client
     * API; it is public so the command layer and checks can call it.
     *
     * <p>A blank name has no identity to bind, so it follows {@link #joinPlayer()} instead of occupying a
     * named slot. The command layer never sends a blank name: a successful {@code LOGIN} always carries one.
     *
     * @param name account name that identifies the player, normally the authenticated login name
     * @return the existing or newly created player, or empty when every human slot is taken
     * @throws IllegalStateException when called outside a world command, or when the name is already bound
     *         to a player that a future world reset removed (the mapping is never silently reassigned)
     */
    public Optional<Player> joinPlayer(String name) {
        requireWorldThread("joinPlayer");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            return joinPlayer();
        }
        Integer boundId = accountIds.get(name);
        if (boundId != null) {
            Optional<Player> bound = engine.world().findPlayer(PlayerId.of(boundId));
            if (bound.isEmpty()) {
                // A future reset that removes a player must clear the map with it; until then, reassigning
                // the id would silently give one account another account's base and entities.
                throw new IllegalStateException("account '" + name + "' is bound to player " + boundId
                        + ", which is no longer in the world; refusing to reassign that identity");
            }
            return bound;
        }
        Optional<Player> joined = allocatePlayer(name);
        joined.ifPresent(player -> accountIds.put(name, player.id().value()));
        return joined;
    }

    /**
     * Creates a player without an account identity, using the historical allocation rule.
     *
     * <p>Kept for callers that have no name - a scripted scenario or an embedding program. It takes the
     * smallest unused id from {@value #FIRST_HUMAN_PLAYER_ID} to {@value #MAX_PLAYERS}, spawns the base and
     * deposits the starting stock exactly like {@link #joinPlayer(String)}, but records no name, so two
     * calls allocate two different players and neither can be found by {@link #playerFor(String)}. The
     * command layer and every self check use the named form; a blank name in
     * {@link #joinPlayer(String)} is the only in-tree path that reaches this method.
     *
     * @return the new player, or empty when every human slot is taken
     * @throws IllegalStateException when called outside a world command
     */
    public Optional<Player> joinPlayer() {
        requireWorldThread("joinPlayer");
        return allocatePlayer(null);
    }

    /** Allocates the smallest unused human slot and spawns its base; the caller owns the identity map. */
    private Optional<Player> allocatePlayer(String name) {
        for (int id = FIRST_HUMAN_PLAYER_ID; id <= MAX_PLAYERS; id++) {
            PlayerId candidateId = PlayerId.of(id);
            if (engine.world().findPlayer(candidateId).isPresent()) {
                continue;
            }
            String displayName = name == null || name.isBlank() ? "Player " + id : name;
            Player joined = new Player(candidateId, displayName, colorFor(id), id, true);
            engine.world().addPlayer(joined);
            joined.attachLogistics(new WarehouseLogistics(engine.world(), joined));
            joinHauling(engine.world(), joined);
            spawnStartingBase(joined);
            return Optional.of(joined);
        }
        return Optional.empty();
    }

    /** Requires the simulation lock, which is what "runs on the world thread" means for this host. */
    private void requireWorldThread(String operation) {
        if (!Thread.holdsLock(stateLock)) {
            throw new IllegalStateException(operation + " must run on the world thread, inside a WorldCommand");
        }
    }

    /**
     * The player bound to an account name, without creating anything.
     *
     * <p>Read-only: a name that was never admitted, or whose player a future reset removed, answers empty.
     * The lookup takes the simulation lock, so it is safe from a connection thread. A stale mapping is kept
     * rather than cleared, so a later {@link #joinPlayer(String)} refuses it explicitly instead of
     * reassigning the identity.
     *
     * @param name the account name
     * @return the bound player, or empty when this world has no live player for that name
     */
    public Optional<Player> playerFor(String name) {
        Objects.requireNonNull(name, "name");
        synchronized (stateLock) {
            Integer id = accountIds.get(name);
            return id == null ? Optional.empty() : engine.world().findPlayer(PlayerId.of(id));
        }
    }

    /**
     * Every account name currently bound to a live player, sorted.
     *
     * <p>This is the identity map, not a session list: a disconnected account is still present, because this
     * host deliberately keeps it (see {@link #joinPlayer(String)}). The method is cheap - one pass over at
     * most six names - and takes the simulation lock, so it is safe from a diagnostics caller.
     *
     * @return the bound account names in natural order; empty when only the nameless path has been used
     */
    public List<String> accounts() {
        synchronized (stateLock) {
            List<String> names = new ArrayList<>(accountIds.size());
            for (Map.Entry<String, Integer> entry : accountIds.entrySet()) {
                if (engine.world().findPlayer(PlayerId.of(entry.getValue())).isPresent()) {
                    names.add(entry.getKey());
                }
            }
            names.sort(Comparator.naturalOrder());
            return names;
        }
    }

    /**
     * Every human player currently admitted to the world, in id order.
     *
     * <p>"Online" is the wrong word for this host: there is no session object here, so a player whose
     * connection closed is still admitted and still appears. The method is the cheap diagnostic view of the
     * human slots; use {@link #accounts()} when the names are what matters.
     *
     * @return the human players (ids {@value #FIRST_HUMAN_PLAYER_ID} and above), in id order
     */
    public List<Player> onlinePlayers() {
        synchronized (stateLock) {
            List<Player> players = new ArrayList<>();
            for (Player player : engine.world().players()) {
                if (player.id().value() >= FIRST_HUMAN_PLAYER_ID) {
                    players.add(player);
                }
            }
            players.sort(Comparator.comparingInt((Player human) -> human.id().value()));
            return players;
        }
    }

    /**
     * A player of this world by id, for a caller that only kept the id (a connection's identity).
     *
     * <p>The lookup takes the simulation lock, so it is safe from a connection thread; it never creates
     * anything.
     *
     * @param id the player id
     * @return the player, or empty when no player of that id exists
     */
    public Optional<Player> findPlayer(int id) {
        synchronized (stateLock) {
            return engine.world().findPlayer(PlayerId.of(id));
        }
    }

    /** The deterministic colour of a human player id. */
    private static int colorFor(int id) {
        int index = Math.floorMod(id - FIRST_HUMAN_PLAYER_ID, HUMAN_COLORS.length);
        return HUMAN_COLORS[index];
    }

    /**
     * Joins a player created after the engine was built to the world's hauling system.
     *
     * <p>The engine wires hauling to the players it was built with, and a command cannot reach the
     * engine's builder. It does not have to: every network exposes the hauling system attached to it, so
     * a system that already serves the world is reachable through any existing player's network - and it
     * is the same instance the pipeline ticks.
     */
    private static void joinHauling(World world, Player player) {
        WarehouseLogistics network = WarehouseLogistics.of(player);
        if (network == null || network.hauling().isPresent()) {
            return;
        }
        for (Player other : world.players()) {
            WarehouseLogistics existing = WarehouseLogistics.of(other);
            if (existing != null && existing.hauling().isPresent()) {
                network.attachHauling(existing.hauling().orElseThrow());
                return;
            }
        }
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
     * Captures a default snapshot window: the centre of the map with {@value #DEFAULT_SNAPSHOT_RADIUS}
     * tiles of radius on every side.
     *
     * <p>The shape is intentionally small and stable:
     * <pre>{@code
     * {"tick":42,"originX":96,"originY":96,"width":64,"height":64,"tilePixels":16,
     *  "tiles":[["grass","grass", ...], ...],
     *  "entities":[{"id":1,"kind":"worker","x":12.5,"y":8.5,"player":1,"health":1.0}, ...],
     *  "players":[{"id":1,"name":"Player 1","gold":0,"wood":0},
     *             {"id":3,"name":"alice","gold":300,"wood":60}]}
     * }</pre>
     *
     * <p>{@code width} and {@code height} are the <em>window</em> size, not {@link #MAP_SIZE}, and
     * {@code originX}/{@code originY} are the window's top left tile. {@code tiles} is row major for that
     * window and has exactly {@code width * height} terrain content paths. Entities are <em>not</em>
     * clipped to the window: the array is the whole living entity list, each with absolute map coordinates,
     * so the client can map an entity into the window when it is visible and still track it when it is not.
     * Entities, players and the document itself are deterministic for an unchanged tick.
     *
     * <p><strong>The {@code players} array carries two starting resources explicitly.</strong> Every
     * player that exists in the world, not only a human connection, contributes one row with its id, its
     * display name and the amounts of the two classic resources: the flat keys are {@code gold} and
     * {@code wood} because those are the item paths the classic catalogue registers
     * ({@link #GOLD_ITEM_PATH}, {@link #WOOD_ITEM_PATH}); the wire format does not repeat their display
     * names (金币 / 木材), which belong to the client atlas. The amounts are read from the player's
     * {@link io.github.sob1234509876.oc.rts.economy.Logistics#available()} view - the warehouses are the
     * engine's drop-off rule, and an item that has not been stored in a warehouse cannot be spent - so a
     * player with no warehouse, no logistics network or a catalogue without that item path reports
     * {@code 0}. Any other item kind a
     * future catalogue registers stays out of this snapshot for now: the document does not enumerate the
     * inventory, and adding an item to the wire means extending this shape and its documents.
     *
     * <p>Amounts are emitted as JSON integers saturated to {@link Long#MAX_VALUE}; the browser could not
     * use more precision than a number anyway, and a stock that large is already far past playable.
     *
     * <p>The default window is 64 x 64 tiles, about the same size as the old whole-map document, but the
     * map it samples is {@value #MAP_SIZE} x {@value #MAP_SIZE}. The document is rebuilt per request rather
     * than cached, because a cached document could disagree with the tick the browser reports in its HUD.
     *
     * @return the snapshot as UTF-8 JSON text, never {@code null}
     */
    public String snapshotJson() {
        return snapshotJson(MAP_SIZE / 2, MAP_SIZE / 2, DEFAULT_SNAPSHOT_RADIUS);
    }

    /**
     * Captures one square window of the world around a requested tile.
     *
     * <p>The window is {@code 2 * radius} tiles on a side; {@code radius} is clamped to
     * [{@value #MIN_SNAPSHOT_RADIUS}, {@value #MAX_SNAPSHOT_RADIUS}] and defaults to
     * {@value #DEFAULT_SNAPSHOT_RADIUS} in {@link #snapshotJson()}. The centre is clamped into the reported
     * map and the origin is shifted inwards when the requested square would cross an edge, so a corner
     * request still describes a full window fully inside {@code [0, MAP_SIZE)} and never reads a tile out
     * of range. A request without parameters (see {@code HttpWebServer}) is exactly {@link #snapshotJson()},
     * so an old client still receives a valid document.
     *
     * <p>Reading a window also moves the engine focus to its centre, in the same lock and on the same call,
     * so {@code ChunkStreamer} keeps the camera area loaded. That is a cache and streaming decision only:
     * the JSON remains a pure read of the tick and the terrain field.
     *
     * @param centreX requested window centre, in tiles; clamped into the map
     * @param centreY requested window centre, in tiles; clamped into the map
     * @param radius  requested half side, in tiles; clamped to the documented range
     * @return the snapshot as UTF-8 JSON text, never {@code null}
     */
    public String snapshotJson(int centreX, int centreY, int radius) {
        int clampedRadius = clampSnapshotRadius(radius);
        int windowSize = clampedRadius * 2;
        int maxOrigin = MAP_SIZE - windowSize;
        int centreLimit = MAP_SIZE - 1;
        int cx = Math.max(0, Math.min(centreLimit, centreX));
        int cy = Math.max(0, Math.min(centreLimit, centreY));
        int originX = Math.max(0, Math.min(cx - clampedRadius, maxOrigin));
        int originY = Math.max(0, Math.min(cy - clampedRadius, maxOrigin));
        synchronized (stateLock) {
            engine.focus(Vec2.of(originX + clampedRadius + 0.5f, originY + clampedRadius + 0.5f));
            return buildSnapshotJson(originX, originY, windowSize);
        }
    }

    /**
     * Clamps a requested snapshot radius into the documented range.
     *
     * <p>Exposed so the HTTP route and its checks apply exactly the same rule: a missing parameter becomes
     * {@link #DEFAULT_SNAPSHOT_RADIUS}, and a value below {@value #MIN_SNAPSHOT_RADIUS} or above
     * {@value #MAX_SNAPSHOT_RADIUS} is clamped rather than rejected. The bounds keep the document small
     * (at most 128 x 128 tiles) and guarantee a full window always fits inside the reported map.
     *
     * @param radius the requested radius in tiles
     * @return the radius the snapshot will actually use
     */
    public static int clampSnapshotRadius(int radius) {
        return Math.max(MIN_SNAPSHOT_RADIUS, Math.min(MAX_SNAPSHOT_RADIUS, radius));
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
                try {
                    step();
                } catch (RuntimeException failure) {
                    // A plugin hook is third party code running inside the simulation. If one throws, the
                    // world cannot be advanced any further without risking the same failure on every
                    // step, so the thread stops and the last consistent snapshot keeps being served; the
                    // problem is logged instead of being swallowed.
                    LOG.log(System.Logger.Level.ERROR, "a world tick hook failed; the world thread stops",
                            failure);
                    running = false;
                    failPendingCommands();
                    return;
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

    /**
     * Serialises one already clamped window; the caller holds {@link #stateLock}.
     *
     * <p>The window is a square of {@code size} tiles with its top left at {@code (originX, originY)} and
     * is guaranteed by {@link #snapshotJson(int, int, int)} to lie fully inside the reported map, so the
     * terrain loop can never step outside it.
     */
    private String buildSnapshotJson(int originX, int originY, int size) {
        List<Entity> entities = new ArrayList<>(engine.world().entities());
        entities.removeIf(entity -> !entity.isAlive());
        entities.sort(Comparator.comparing(Entity::id));

        int capacity = 96 + size * size * 8 + entities.size() * 96;
        StringBuilder json = new StringBuilder(capacity);
        json.append("{\"tick\":").append(engine.tickNumber())
                .append(",\"originX\":").append(originX)
                .append(",\"originY\":").append(originY)
                .append(",\"width\":").append(size)
                .append(",\"height\":").append(size)
                .append(",\"tilePixels\":").append(TILE_PIXELS)
                .append(",\"tiles\":[");
        for (int y = originY; y < originY + size; y++) {
            if (y > originY) {
                json.append(',');
            }
            json.append('[');
            for (int x = originX; x < originX + size; x++) {
                if (x > originX) {
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
        json.append("],\"players\":[");
        ItemType goldItem = content.itemOrNull(content.key(GOLD_ITEM_PATH));
        ItemType woodItem = content.itemOrNull(content.key(WOOD_ITEM_PATH));
        List<Player> players = new ArrayList<>(engine.world().players());
        players.sort(Comparator.comparingInt((Player worldPlayer) -> worldPlayer.id().value()));
        boolean firstPlayer = true;
        for (Player worldPlayer : players) {
            if (!firstPlayer) {
                json.append(',');
            }
            firstPlayer = false;
            ItemBundle available = worldPlayer.hasLogistics()
                    ? worldPlayer.logistics().available()
                    : ItemBundle.EMPTY;
            json.append("{\"id\":").append(worldPlayer.id().value())
                    .append(",\"name\":");
            appendJsonString(json, worldPlayer.name());
            json.append(",\"gold\":").append(availableAmount(available, goldItem))
                    .append(",\"wood\":").append(availableAmount(available, woodItem))
                    .append('}');
        }
        json.append("]}");
        return json.toString();
    }

    /**
     * The integer a player row reports for one snapshot resource.
     *
     * <p>A catalogue that does not register the item path has no amount to report, so the row carries
     * {@code 0} rather than a fabricated number. A stock outside the exact {@code long} range saturates,
     * because {@code toLongSaturated()} is the engine's own rule for turning an arbitrary {@link Natural}
     * into a plain number and the browser cannot spend more precision than that anyway.
     */
    private static long availableAmount(ItemBundle available, ItemType item) {
        return item == null ? 0L : available.amount(item).toLongSaturated();
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
