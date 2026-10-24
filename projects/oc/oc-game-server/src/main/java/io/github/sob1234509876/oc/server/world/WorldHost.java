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
import io.github.sob1234509876.oc.rts.content.Recipe;
import io.github.sob1234509876.oc.rts.content.ResourceKey;
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
import io.github.sob1234509876.oc.rts.map.InfiniteTerrainMap;
import io.github.sob1234509876.oc.rts.map.NoiseTerrainGenerator;
import io.github.sob1234509876.oc.rts.movement.Motion;
import io.github.sob1234509876.oc.rts.order.CommandService;
import io.github.sob1234509876.oc.rts.production.ProductionJob;
import io.github.sob1234509876.oc.rts.production.ProductionQueue;
import io.github.sob1234509876.oc.rts.production.ProductionState;
import io.github.sob1234509876.oc.rts.production.ProductionSystem;

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
 * <p>The world is unbounded: it is an {@link InfiniteTerrainMap} over the engine's endless noise landscape,
 * with a fixed least-recently-used budget of loaded chunks instead of a map edge or a map size.
 * {@link NoiseTerrainGenerator} derives every tile from the seed through an integer hash field, so the same
 * seed and the same served content always paint the same water, sand, grass, forest, hill and rock at any
 * signed tile coordinate, and no chunk needs its neighbours to be generated first. There is no
 * map size at all: a snapshot serialises one square window around an arbitrary requested centre (see
 * {@link #snapshotJson(int, int, int)}), and both its origin and the entities it reports may be negative.
 * Entities keep absolute world coordinates and are always sent in full, so a window that does not contain
 * an entity still lets the client track it.
 *
 * <p><strong>Buildable anchors.</strong> Noise terrain contains water and rock, so no fixed tile may carry
 * the sample scene or a starting base. The host scans outward from {@code (0, 0)} - the origin of the
 * unbounded world - in a fixed ring order, for the first tile whose terrain is buildable for the served
 * catalogue; that tile is {@link #spawnAnchor()}. The search is bounded by
 * {@value #SEARCH_RADIUS_TILES} tiles so server start cannot walk an endless world. The scene, the sample
 * player's stock and every human starting base are then placed relative to that anchor by deterministic
 * ring searches, never by randomness. The anchor depends only on the seed and the content, which is what
 * lets two hosts with the same seed agree and lets a check assert it.
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

    /**
     * Loaded chunk budget of the unbounded terrain, used when the caller does not name one.
     *
     * <p>The map has no size; this is a memory ceiling. Chunks beyond it are evicted least-recently-used
     * and regenerated deterministically when somebody looks at them again. The server exposes it as
     * {@code --chunks N}, and the engine's own default is the same number.
     */
    public static final int DEFAULT_CHUNK_BUDGET = InfiniteTerrainMap.DEFAULT_MAX_LOADED_CHUNKS;

    /**
     * How far, in tiles, a deterministic site search may walk from its anchor before giving up.
     *
     * <p>The world is unbounded, so every search that used to fall back to "scan the whole map" is bounded
     * by this radius instead. It applies to the spawn anchor search from {@code (0, 0)}, to the scene and
     * starting-base placement fallbacks, and - through its own copy of the rule - to the classic AI's site
     * search. A radius of 64 is large enough for noisy terrain (the square holds 16,641 tiles) and small
     * enough that startup and a single AI decision stay fast.
     */
    public static final int SEARCH_RADIUS_TILES = 64;

    /** Radius of a snapshot window when the request names none, in tiles. */
    public static final int DEFAULT_SNAPSHOT_RADIUS = 32;

    /** Smallest snapshot radius a request may ask for, in tiles. */
    public static final int MIN_SNAPSHOT_RADIUS = 16;

    /** Largest snapshot radius a request may ask for, in tiles. */
    public static final int MAX_SNAPSHOT_RADIUS = 64;

    /**
     * Largest number of route points one entity row carries for a moving unit.
     *
     * <p>It is the engine's snapshot cap ({@link io.github.sob1234509876.oc.rts.visual.SnapshotFactory#MAX_SNAPSHOT_PATH_POINTS})
     * repeated here because this class serialises the live world itself rather than going through the
     * engine's snapshot factory. The document stays small: at most 64 tile pairs plus one target pair,
     * omitted entirely for a unit that has no remaining route.
     */
    public static final int MAX_SNAPSHOT_PATH_POINTS = 64;

    /** Pixel size of one tile the browser is told to expect; the client still scales to its camera. */
    public static final int TILE_PIXELS = 16;

    /** Fixed simulation rate: 20 ticks per second, one tick every 50 ms. */
    public static final int TICKS_PER_SECOND = 20;

    /** Seed used when the caller does not name one, matching {@code --seed}'s default. */
    public static final long DEFAULT_SEED = 1L;

    /**
     * First player id a human connection can be assigned.
     *
     * <p>Player {@code 1} owns the sample scene the host builds. The classic plugin's first bot owns player
     * {@code 2}, and its extra bots use ids above the human range (9 and up), so the two populations cannot
     * collide. Human players therefore start at {@code 3}; see {@link #joinPlayer(String)} for the exact
     * assignment rule.
     */
    public static final int FIRST_HUMAN_PLAYER_ID = 3;

    /**
     * Maximum number of players one world holds, counting the scene player and the classic bots.
     *
     * <p>This is the "room" of {@link #joinPlayer(String)}: ids {@code 3} through {@code MAX_PLAYERS}
     * are the human slots, so a default world admits six distinct account names. The classic plugin's first
     * bot owns player {@code 2} and its extra bots use ids above this range, so bots never reduce the human
     * room. An id is bound to a name for the life of the world and is not reused for a different name, which
     * keeps an entity's owner stable for a replay and lets a reconnecting account find its own base again.
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

    /**
     * Prefix of the content tag that publishes a building's gold income, in gold per minute.
     *
     * <p>The convention belongs to the content, not to this class: a building type carries a tag
     * {@code income_<n>} where {@code n} is a non-negative decimal integer and means gold per minute. The
     * classic catalogue publishes {@code income_30}, {@code income_60} and {@code income_120} for its
     * three mine tiers, and its {@code ClassicEconomy} pays exactly those rates. This host cannot import
     * that plugin, so it reads the same published tags through the served {@link ContentRegistry}; see
     * {@link #incomePerMinute(Player, String)} and {@link #snapshotJson()}.
     */
    private static final String INCOME_TAG_PREFIX = "income_";

    /**
     * Prefix of the content tag that publishes a building's wood income, in wood per minute.
     *
     * <p>It is deliberately longer than {@link #INCOME_TAG_PREFIX} so a lumber camp can never be
     * mistaken for a gold mine: {@code wood_income_20} is a wood rate, while {@code income_30} is a gold
     * rate. See {@link #INCOME_TAG_PREFIX}.
     */
    private static final String WOOD_INCOME_TAG_PREFIX = "wood_income_";

    /** Number of human slots a starting base anchor exists for: ids 3 through 8. */
    private static final int BASE_SLOT_COUNT = MAX_PLAYERS - FIRST_HUMAN_PLAYER_ID + 1;

    /** Anchors per row; the six human slots form a 3 x 2 grid around the spawn anchor. */
    private static final int BASE_ANCHOR_COLUMNS = 3;

    /** Distance between two anchor columns, in tiles. */
    private static final int BASE_ANCHOR_STRIDE_X = 24;

    /** Distance from the spawn anchor row to the two rows of human base anchors, in tiles. */
    private static final int BASE_ANCHOR_STRIDE_Y = 48;

    /** Near rings around its anchor the warehouse site search visits before the wider bounded fallback. */
    private static final int BASE_SITE_SEARCH_RADIUS = 16;

    /** Near rings around the warehouse the builder tile search visits before the wider bounded fallback. */
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

    /** How many rings a scene placement walks around its preferred tile before the wider fallback. */
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

    /** Hard least-recently-used ceiling on the chunks the unbounded terrain keeps in memory. */
    private final int chunkBudget;

    /**
     * The first buildable tile the host found when scanning outwards from the world origin {@code (0, 0)}.
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
     * Builds the noise world and its sample scene from the given content and the given tick hooks, using
     * the default chunk budget.
     *
     * <p>Exactly {@code new WorldHost(seed, content, tickHooks, DEFAULT_CHUNK_BUDGET)}.
     *
     * @param seed      seed of the noise terrain, of the spawn anchor and of the engine's own streams
     * @param content   the catalogue to serve, for example the one a plugin registered
     * @param tickHooks the plugin hooks to run after every fixed step, in order; never {@code null}
     */
    public WorldHost(long seed, WorldContent content, List<WorldTickHook> tickHooks) {
        this(seed, content, tickHooks, DEFAULT_CHUNK_BUDGET);
    }

    /**
     * Builds the unbounded noise world and its sample scene from the given content, tick hooks and chunk
     * budget.
     *
     * <p>{@link NoiseTerrainGenerator} paints the six biomes of the served catalogue and the builder's
     * {@code infiniteTerrain(seed)} gives the world an {@link InfiniteTerrainMap}: any signed tile
     * coordinate exists, the focus streams chunks around the camera, and the budget evicts the least
     * recently used chunks beyond it, so memory stays bounded however far a unit walks. The host scans out
     * from {@code (0, 0)} for the first buildable tile, bounded by {@value #SEARCH_RADIUS_TILES} tiles. The
     * scene is derived from the catalogue, never from constants in this class: one finished building of
     * every declared building type around that anchor, one unit of every declared unit type below them, and
     * the first two units ordered across the map so successive snapshots show the real movement pipeline
     * working instead of a frozen picture. {@link WorldContent#startingResources()} is deposited into the
     * player's warehouses after the scene exists, because a stock has to live in a warehouse to be
     * spendable.
     *
     * <p>The hooks run at the end of every fixed step, after the engine tick, in the order given; see
     * {@link #step()}. The list is copied, so a caller may change its own list afterwards without
     * affecting a running host.
     *
     * <p>No thread is started until {@link #start()}.
     *
     * @param seed        seed of the noise terrain, of the spawn anchor and of the engine's own streams
     * @param content     the catalogue to serve, for example the one a plugin registered
     * @param tickHooks   the plugin hooks to run after every fixed step, in order; never {@code null}
     * @param chunkBudget hard upper bound on chunks kept in memory; must be positive
     * @throws IllegalArgumentException when {@code chunkBudget} is not positive
     * @throws IllegalStateException    when the content declares starting resources no warehouse accepts,
     *     defines no buildable terrain to put the world on, or paints no buildable tile within
     *     {@value #SEARCH_RADIUS_TILES} tiles of the origin
     */
    public WorldHost(long seed, WorldContent content, List<WorldTickHook> tickHooks, int chunkBudget) {
        if (chunkBudget <= 0) {
            throw new IllegalArgumentException("chunk budget must be positive, was " + chunkBudget);
        }
        this.seed = seed;
        this.worldContent = Objects.requireNonNull(content, "content");
        this.tickHooks = List.copyOf(Objects.requireNonNull(tickHooks, "tickHooks"));
        this.chunkBudget = chunkBudget;
        this.content = registryOf(this.worldContent);
        BiomeTable biomes = biomeTable(this.content, this.worldContent);
        this.player = Player.of(1, "Player 1", 0xFF4FC3F7);
        this.engine = OcRtsEngine.builder(this.content)
                .seed(seed)
                .ticksPerSecond(TICKS_PER_SECOND)
                .chunkSize(NoiseTerrainGenerator.DEFAULT_CHUNK_SIZE)
                .viewRadiusChunks(2)
                .generator(new NoiseTerrainGenerator(seed, biomes))
                .infiniteTerrain(seed)
                .maxLoadedChunks(chunkBudget)
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
     * Finds the deterministic spawn anchor: the first buildable tile on rings growing out from the world
     * origin {@code (0, 0)}.
     *
     * <p>The search is bounded by {@value #SEARCH_RADIUS_TILES} tiles. There is no map edge to fall back on,
     * so the radius is what keeps startup fast; if no buildable tile exists inside it the content cannot
     * carry the sample scene and the constructor fails with a clear message instead of searching an endless
     * landscape.
     *
     * <p>The scan is a pure function of the terrain field: no random stream, no clock and no content
     * iteration order. Within one ring the tiles are visited in row-major order ({@code dy} then {@code dx}),
     * so two hosts with the same seed and the same catalogue answer the same anchor. "Buildable" is the same
     * predicate the engine uses for construction
     * ({@link io.github.sob1234509876.oc.rts.map.TerrainMap#isBuildable}), so the tile can carry a building
     * of any served catalogue without a second rule.
     *
     * @return the first buildable tile within the search radius; never {@code null}
     * @throws IllegalStateException when no buildable tile exists within {@value #SEARCH_RADIUS_TILES}
     *     tiles of the origin
     */
    private static GridPos findSpawnAnchor(World world) {
        for (int ring = 0; ring <= SEARCH_RADIUS_TILES; ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) {
                        continue;
                    }
                    GridPos tile = new GridPos(dx, dy);
                    if (world.map().isBuildable(tile)) {
                        return tile;
                    }
                }
            }
        }
        throw new IllegalStateException("world content paints no buildable tile within "
                + SEARCH_RADIUS_TILES + " tiles of the origin (0, 0), so there is nowhere to place the"
                + " sample scene or a starting base");
    }

    /**
     * Places the sample scene: one finished building of every building type and one unit of every unit
     * type, then orders the first two units across the map.
     *
     * <p>The preferred grid is relative to {@link #spawnAnchor}, so the scene follows the buildable ground
     * the host found instead of a fixed patch of a flat map. A preferred tile is only a hint: each building
     * is placed by the same deterministic ring search a starting base uses, with a bounded wider ring as
     * its fallback. Noise terrain therefore cannot drop a warehouse into water, and the result is still a
     * pure function of the seed and the catalogue.
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
                origin = scanFreeOrigin(world, type, preferred, SEARCH_RADIUS_TILES);
            }
            if (origin == null) {
                throw new IllegalStateException("world content " + worldContent.id()
                        + " has no buildable tile for the sample " + type.key().path() + " within "
                        + SEARCH_RADIUS_TILES + " tiles of " + spawnAnchor);
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
                tile = scanFreeTile(world, preferred, SEARCH_RADIUS_TILES, NO_TILES);
            }
            if (tile == null) {
                throw new IllegalStateException("world content " + worldContent.id()
                        + " has no free buildable tile for the sample " + type.key().path() + " within "
                        + SEARCH_RADIUS_TILES + " tiles of " + spawnAnchor);
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

    /** A movement target near the anchor; the world is unbounded, so there is no edge to clamp to. */
    private GridPos sceneMoveTarget(int dx, int dy) {
        return new GridPos(spawnAnchor.x() + dx, spawnAnchor.y() + dy);
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
     * anchored search exhausts its rings, a wider ring search around the same anchor is the documented
     * fallback. It is bounded by {@value #SEARCH_RADIUS_TILES} tiles because the world has no map to scan;
     * a crowded or watery anchor therefore cannot make the result random, and a site that does not exist
     * inside the bound leaves the player without a base instead of searching forever.
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

    /** The warehouse origin for a player: near rings around the slot anchor, then the bounded wider rings. */
    private GridPos findWarehouseSite(World world, BuildingType type, int playerId) {
        GridPos anchor = baseAnchor(playerId);
        GridPos site = scanFreeOrigin(world, type, anchor, BASE_SITE_SEARCH_RADIUS);
        return site != null ? site : scanFreeOrigin(world, type, anchor, SEARCH_RADIUS_TILES);
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

    /**
     * Whether a building of the type can be spawned at the origin without overlapping anything alive.
     *
     * <p>{@link World#canPlace} covers buildable ground and other buildings; the unit check is added here
     * because {@code canPlace} deliberately only tests buildings, and a starting base must not stand on
     * top of a unit either.
     */
    private static boolean freeOrigin(World world, BuildingType type, GridPos origin) {
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

    /** The builder tile for a warehouse: near rings around the footprint, then the bounded wider rings. */
    private static GridPos findBuilderTile(World world, BuildingType warehouse, GridPos origin) {
        TileRect footprint = warehouse.footprint(origin.x(), origin.y());
        GridPos anchor = new GridPos(origin.x() + warehouse.sizeX() / 2, origin.y() + warehouse.sizeY() + 1);
        GridPos tile = scanFreeTile(world, anchor, BASE_UNIT_SEARCH_RADIUS, footprint);
        return tile != null ? tile : scanFreeTile(world, anchor, SEARCH_RADIUS_TILES, footprint);
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

    /**
     * Whether a tile can carry a freshly spawned unit without touching any existing entity.
     *
     * <p>A unit's radius is below half a tile in every catalogue this host serves, so treating the tile of
     * its centre as its footprint is enough for a starting base, and it keeps the check simple and
     * deterministic.
     */
    private static boolean freeTile(World world, GridPos tile, TileRect excluded) {
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
     * The deterministic buildable tile the host found near the world origin.
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
     * connection closed is still admitted and still appears. The list is the account-bound players - the
     * identities {@link #joinPlayer(String)} created - not simply "every player with a human id": a world
     * plugin may own bot players in the same id range, and those are not humans. Use {@link #accounts()} when
     * the names are what matters, and {@link #findPlayer(int)} to inspect a specific id (including a bot).
     *
     * @return the account-bound players, in id order
     */
    public List<Player> onlinePlayers() {
        synchronized (stateLock) {
            List<Player> players = new ArrayList<>();
            for (int id : accountIds.values()) {
                engine.world().findPlayer(PlayerId.of(id)).ifPresent(players::add);
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

    /**
     * The least-recently-used chunk budget of the unbounded terrain.
     *
     * <p>This is a memory ceiling, not a world size: it is the {@code --chunks N} value the host was built
     * with (or {@link #DEFAULT_CHUNK_BUDGET}). Exposed so a diagnostic can report it and a check can assert
     * that the configured budget really reached the engine.
     *
     * @return the positive chunk budget
     */
    public int chunkBudget() {
        return chunkBudget;
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
     * Captures a default snapshot window: the spawn anchor with {@value #DEFAULT_SNAPSHOT_RADIUS} tiles of
     * radius on every side.
     *
     * <p>The shape is intentionally small and stable:
     * <pre>{@code
     * {"tick":42,"originX":-32,"originY":-32,"width":64,"height":64,"tilePixels":16,
     *  "tiles":[["grass","grass", ...], ...],
     *  "entities":[{"id":1,"kind":"worker","x":-3.5,"y":4.5,"player":1,"health":1.0,
     *               "w":1,"h":1,"progress":1.00,"queue":0},
     *              {"id":9,"kind":"building.armory.t1","x":6.5,"y":-4.5,"player":3,"health":1.0,
     *               "w":3,"h":3,"progress":1.00,"queue":2,
     *               "queueItems":[{"kind":"unit.melee.t1","ticks":87,"total":120},
     *                             {"kind":"unit.melee.t1","ticks":120,"total":120}]}, ...],
     *  "players":[{"id":1,"name":"Player 1","gold":0,"wood":0,"goldPerMinute":210,"woodPerMinute":140},
     *             {"id":3,"name":"alice","gold":300,"wood":60,"goldPerMinute":0,"woodPerMinute":0}]}
     * }</pre>
     *
     * <p>{@code width} and {@code height} are the <em>window</em> size; the document deliberately carries no
     * map width or height, because the world is unbounded. {@code originX}/{@code originY} are the window's
     * top left tile and may be negative or arbitrarily large. {@code tiles} is row major for that window and
     * has exactly {@code width * height} terrain content paths. Entities are <em>not</em> clipped to the
     * window: the array is the whole living entity list, each with absolute world coordinates, so the client
     * can map an entity into the window when it is visible and still track it when it is not. Entities,
     * players and the document itself are deterministic for an unchanged tick.
     *
     * <p><strong>Footprint, progress and queue of an entity row.</strong> {@code w} and {@code h} are the
     * footprint in tiles: a building reports the footprint of its content definition ({@code sizeX()} /
     * {@code sizeY()}), while a unit reports {@code 1} and {@code 1}, because the engine gives a unit one
     * ground tile no matter how large its sprite is. {@code progress} is the completion fraction in
     * {@code [0,1]}, formatted with two decimals. For a building it is
     * {@link Building#constructionFraction()} while the site is unfinished and exactly {@code 1.00} once it
     * is constructed; an unfinished site is clamped to {@code [0.01, 0.99]} on the wire, because two
     * decimals would otherwise round a 99.6% site up to a finished {@code 1.00}. A unit has no construction
     * counter in the engine - it enters the world only after its production has already finished - so its
     * row reports its own health fraction, the only per-unit completeness the engine exposes; a full
     * health unit therefore reads {@code 1.00}, and a damaged one reports the damage. {@code queue} is the
     * number of unfinished jobs in the building's {@link ProductionSystem} queue ({@code 0} for a unit or
     * for a building that has no queue). {@code queueTicks} appears only while that queue's current job is
     * {@link ProductionState#PRODUCING} and carries {@link ProductionJob#remainingTicks()}; in the other
     * job states the engine's remaining-tick counter is only the recipe duration and says nothing about
     * progress, so the field is omitted rather than fabricated.
     *
     * <p><strong>{@code queueItems} lists the unfinished jobs in queue order.</strong> A building row whose
     * building has a production queue carries {@code queueItems}, an array of
     * {@code {"kind":...,"ticks":...,"total":...}} objects; a unit row and a building that never had a queue
     * omit the array entirely. {@code queue} stays the array's length. {@code total} is the job's recipe
     * duration in ticks ({@link Recipe#durationTicks()}). {@code ticks} is what the engine reports as
     * remaining for the job that is currently {@link ProductionState#PRODUCING}
     * ({@link ProductionJob#remainingTicks()}). A job that has not started yet - it is waiting for
     * materials, being delivered to, or standing behind the current job - has no engine progress of its
     * own, so it reports its recipe duration in both fields rather than a fabricated countdown; a job in
     * {@link ProductionState#WAITING_OUTPUT} has already finished counting down and is reported the same
     * way, because it is still unfinished but no longer producing. {@code kind} is the content path of the
     * unit the recipe makes, when it makes one ({@code unit.melee.t1}); an item-only recipe falls back to
     * the recipe's own content path, which is the only stable name it has. The client resolves
     * {@code kind} through the plugin manifest for the display name; a legacy client can ignore the whole
     * array and keep drawing the existing {@code 生产中 ×N} marker from {@code queue}.
     *
     * <p><strong>Contents of a warehouse or a unit.</strong> A row describing a building whose content
     * type is flagged as a warehouse also carries {@code contents}, a compact object of
     * {@code path: amount} pairs for the items currently stored there, for example
     * {@code "contents":{"gold":120,"wood":40}}. A row describing a unit whose cargo is non-empty carries
     * the same object for the items it carries. Only non-zero amounts are written and an empty warehouse
     * or an empty cargo hold omits the field; other buildings never carry it, because a producer's
     * private buffer is not part of the logistics network. The field is additive: an older client
     * ignores it, and a client that reads it can show the warehouse's stock or the unit's cargo in the
     * selection HUD. It is read from {@link Building#inventory()} for a warehouse and
     * {@link Unit#cargo()} for a unit, the same sources the logistics network uses, so the two cannot
     * disagree about a stored or carried item.
     *
     * <p><strong>Upgrade offers.</strong> A finished building whose content type names a successor also
     * carries {@code upgrade}, for example
     * {@code "upgrade":{"to":"building.armory.t2","gold":140,"wood":40}}. {@code to} is the successor's
     * content path and {@code gold} / {@code wood} are the two resources the command will charge. An
     * unfinished site, a top-tier building, a type with no successor and a dangling successor reference all
     * omit the field, so the client never offers an upgrade the command would refuse; the
     * {@link #GOLD_ITEM_PATH gold and wood} item paths are the same ones the player rows use. The field is
     * additive: an older client ignores it, and a client that reads it paints the 升级 entry and disables it
     * from its own player row when the stock is short, which is why affordability is not encoded here.
     *
     * <p><strong>Repair, reclaim and route fields.</strong> A finished damaged building whose content
     * declares a repair price also carries {@code "repair":{"gold":<cost>,"wood":<cost>}}, the total cost of
     * repairing every missing hit point (see {@link #appendBuildingServices}). An unfinished site carries
     * {@code "reclaim":{"gold":<refund>,"wood":<refund>}}, half of its recorded placement cost (see
     * {@link CommandService#reclaimRefund(io.github.sob1234509876.oc.rts.entity.Building)}). A moving unit
     * with a route also carries {@code "path":[[x,y],...]} with at most
     * {@value #MAX_SNAPSHOT_PATH_POINTS} remaining points and {@code "target":[x,y]} for the goal; both are
     * omitted when the unit is idle or stopped (see {@link #appendUnitPath}). These fields are additive and
     * presentational; the documented client uses them for the {@code 修理 / 回收} entries and the route
     * overlay, and an older client ignores them.
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
     * <p><strong>Each player row also carries the income rate.</strong> {@code goldPerMinute} and
     * {@code woodPerMinute} are non-negative integers, the sum over that player's alive and
     * {@linkplain Building#isConstructed() constructed} buildings of the content's income tags:
     * {@code income_<n>} for gold and {@code wood_income_<n>} for wood, where {@code n} is the amount per
     * minute. The tags are read from the {@link BuildingType} the served {@link ContentRegistry} resolved,
     * through the same prefix convention {@code ClassicEconomy} pays - an unfinished site, a destroyed
     * building and a building type without a matching tag contribute nothing, so a content which publishes
     * no tags gets zeros for both fields. Per building the first matching tag wins, exactly like the
     * classic payout hook, and a malformed or negative tag is treated as no income instead of failing the
     * snapshot; a sum beyond {@link Long#MAX_VALUE} saturates. The field is the rate the content publishes,
     * not a measured remainder: it is the same number the classic hook adds to the warehouse every 60
     * ticks. The two fields are additive: an older server may omit them and a client must keep working
     * without them.
     *
     * <p>Amounts are emitted as JSON integers saturated to {@link Long#MAX_VALUE}; the browser could not
     * use more precision than a number anyway, and a stock that large is already far past playable.
     *
     * <p>The default window is 64 x 64 tiles around the spawn anchor. The document is rebuilt per request
     * rather than cached, because a cached document could disagree with the tick the browser reports in its
     * HUD.
     *
     * @return the snapshot as UTF-8 JSON text, never {@code null}
     */
    public String snapshotJson() {
        return snapshotJson(spawnAnchor.x(), spawnAnchor.y(), DEFAULT_SNAPSHOT_RADIUS);
    }

    /**
     * Captures one square window of the unbounded world around any requested tile.
     *
     * <p>The window is {@code 2 * radius} tiles on a side; {@code radius} is clamped to
     * [{@value #MIN_SNAPSHOT_RADIUS}, {@value #MAX_SNAPSHOT_RADIUS}] and defaults to
     * {@value #DEFAULT_SNAPSHOT_RADIUS} in {@link #snapshotJson()}. <strong>Only the radius is clamped.</strong>
     * The centre is accepted exactly as requested, so {@code cx=100000&cy=-50000} describes a window with
     * origin {@code 99984,-50016}; there is no map edge to shift the window inwards against. The origin is
     * only saturated at the edges of the {@code int} coordinate space so the window cannot overflow, which is
     * a limit of Java coordinates rather than a world edge. A request without parameters (see
     * {@code HttpWebServer}) uses {@link #spawnAnchor()} as its centre, so an old client still receives a
     * valid document.
     *
     * <p>Reading a window also moves the engine focus to its centre, in the same lock and on the same call,
     * so {@code ChunkStreamer} keeps the camera area loaded. That is a cache and streaming decision only:
     * the JSON remains a pure read of the tick and the terrain field.
     *
     * @param centreX requested window centre, in tiles; any {@code int}, no map bounds
     * @param centreY requested window centre, in tiles; any {@code int}, no map bounds
     * @param radius  requested half side, in tiles; clamped to the documented range
     * @return the snapshot as UTF-8 JSON text, never {@code null}
     */
    public String snapshotJson(int centreX, int centreY, int radius) {
        int clampedRadius = clampSnapshotRadius(radius);
        int windowSize = clampedRadius * 2;
        // The terrain itself accepts any signed int. The window origin is computed in long and saturated
        // only so that origin + windowSize stays representable; that is a coordinate-space limit, not a
        // world boundary, and the checks use much smaller centres.
        long lowestOrigin = Integer.MIN_VALUE;
        long highestOrigin = (long) Integer.MAX_VALUE - windowSize;
        int originX = (int) Math.max(lowestOrigin,
                Math.min(highestOrigin, (long) centreX - clampedRadius));
        int originY = (int) Math.max(lowestOrigin,
                Math.min(highestOrigin, (long) centreY - clampedRadius));
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
     * {@value #MAX_SNAPSHOT_RADIUS} is clamped rather than rejected. The bounds keep the document small (at
     * most 128 x 128 tiles). They are the <em>only</em> clamp the snapshot applies: centre coordinates are
     * unbounded.
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
     * <p>The window is a square of {@code size} tiles with its top left at {@code (originX, originY)}.
     * There is no map to lie inside: the unbounded terrain answers every signed coordinate, and the origin
     * is saturated by {@link #snapshotJson(int, int, int)} so {@code origin + size} cannot overflow.
     */
    private String buildSnapshotJson(int originX, int originY, int size) {
        List<Entity> entities = new ArrayList<>(engine.world().entities());
        entities.removeIf(entity -> !entity.isAlive());
        entities.sort(Comparator.comparing(Entity::id));

        // The engine always installs a production system on the host's builder, so reading it once per
        // snapshot is both safe and cheaper than looking it up for every entity row.
        ProductionSystem production = engine.world().production();

        ItemType goldItem = content.itemOrNull(content.key(GOLD_ITEM_PATH));
        ItemType woodItem = content.itemOrNull(content.key(WOOD_ITEM_PATH));

        int capacity = 96 + size * size * 8 + entities.size() * 320;
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
                    .append(",\"health\":").append(twoDecimals(entity.health().fraction()));
            appendFootprintProgressAndQueue(json, entity, production);
            appendContents(json, entity);
            appendUpgradeOffer(json, entity, goldItem, woodItem);
            appendBuildingServices(json, entity, goldItem, woodItem);
            appendUnitPath(json, entity);
            json.append('}');
        }
        json.append("],\"players\":[");
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
            long goldPerMinute = incomePerMinute(worldPlayer, INCOME_TAG_PREFIX);
            long woodPerMinute = incomePerMinute(worldPlayer, WOOD_INCOME_TAG_PREFIX);
            json.append("{\"id\":").append(worldPlayer.id().value())
                    .append(",\"name\":");
            appendJsonString(json, worldPlayer.name());
            json.append(",\"gold\":").append(availableAmount(available, goldItem))
                    .append(",\"wood\":").append(availableAmount(available, woodItem))
                    .append(",\"goldPerMinute\":").append(goldPerMinute)
                    .append(",\"woodPerMinute\":").append(woodPerMinute)
                    .append('}');
        }
        json.append("]}");
        return json.toString();
    }

    /**
     * Appends the footprint, completion and production queue fields of one entity row.
     *
     * <p>The rules are documented on {@link #snapshotJson()}; this method only implements them. It never
     * invents a value: a unit reports one tile and its health as its progress, a building reports its
     * content footprint and its real construction fraction, {@code queueTicks} is written only when the
     * engine's current job is actually in {@link ProductionState#PRODUCING}, and {@code queueItems} is
     * written only for a building that has a queue.
     */
    private static void appendFootprintProgressAndQueue(StringBuilder json, Entity entity,
            ProductionSystem production) {
        int width = 1;
        int height = 1;
        float progress = 1f;
        ProductionQueue queue = null;
        List<ProductionJob> jobs = List.of();
        Integer queueTicks = null;
        if (entity instanceof Building building) {
            width = building.sizeX();
            height = building.sizeY();
            if (building.isConstructed()) {
                progress = 1f;
            } else {
                // An unfinished site has to stay strictly below 1 on the wire, because two decimals would
                // otherwise paint a 99.6% building as complete; 0.01 is the matching floor so a site that
                // has just started is not mistaken for one that has not started.
                progress = Math.min(0.99f, Math.max(0.01f, building.constructionFraction()));
            }
            queue = production.queue(building).orElse(null);
            if (queue != null) {
                List<ProductionJob> unfinished = new ArrayList<>();
                for (ProductionJob job : queue.jobs()) {
                    if (!job.isFinished()) {
                        unfinished.add(job);
                    }
                }
                jobs = unfinished;
                ProductionJob current = queue.current();
                if (current != null && current.state() == ProductionState.PRODUCING) {
                    queueTicks = current.remainingTicks();
                }
            }
        } else if (entity instanceof Unit unit) {
            // A unit only exists after its production finished, so the engine exposes no construction
            // counter for it; its own health fraction is the only completion-like value there is.
            progress = unit.health().fraction();
        }
        json.append(",\"w\":").append(width)
                .append(",\"h\":").append(height)
                .append(",\"progress\":").append(twoDecimals(progress))
                .append(",\"queue\":").append(jobs.size());
        if (queueTicks != null) {
            json.append(",\"queueTicks\":").append(queueTicks);
        }
        if (queue != null) {
            appendQueueItems(json, jobs);
        }
    }

    /**
     * Appends the per-job list of a building that has a queue.
     *
     * <p>Only the job currently in {@link ProductionState#PRODUCING} has an engine countdown worth
     * showing; every other unfinished job reports its recipe duration in both fields, because the
     * engine's remaining-tick counter still holds the full duration before the job starts. A unit recipe
     * is named by the unit it produces, which is the content path the client resolves against its
     * manifest; an item recipe has no unit, so its own path is the only stable name.
     */
    private static void appendQueueItems(StringBuilder json, List<ProductionJob> jobs) {
        json.append(",\"queueItems\":[");
        for (int index = 0; index < jobs.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            ProductionJob job = jobs.get(index);
            Recipe recipe = job.recipe();
            int total = recipe.durationTicks();
            int ticks = job.state() == ProductionState.PRODUCING ? job.remainingTicks() : total;
            String kind = recipe.unitOutput()
                    .map(unit -> unit.key().path())
                    .orElseGet(() -> recipe.key().path());
            json.append("{\"kind\":");
            appendJsonString(json, kind);
            json.append(",\"ticks\":").append(ticks)
                    .append(",\"total\":").append(total)
                    .append('}');
        }
        json.append(']');
    }

    /**
     * Appends the compact inventory of a warehouse row or a carrying unit row:
     * {@code ,"contents":{"gold":120,"wood":40}}.
     *
     * <p>A building only qualifies when its type is flagged as a warehouse, because only then is its
     * inventory part of the player's logistics network; a producer's private input or output buffer is
     * deliberately not reported, since those goods are not spendable or stored in the same sense and
     * mixing them into a "stored" summary would invent a logistics view the engine does not have. A unit
     * qualifies when its cargo is non-empty, because its hold is what the HUD's 携带 line describes. The
     * object is read from {@link Building#inventory()} or {@link Unit#cargo()} respectively. Within the
     * object only components with a non-zero amount are written, in the inventory's own insertion order
     * (deterministic for a given command stream), and the field is omitted entirely for an empty
     * warehouse or an empty cargo hold. Amounts saturate exactly like the player row's.
     */
    private static void appendContents(StringBuilder json, Entity entity) {
        ItemBundle contents;
        if (entity instanceof Building building) {
            if (!building.type().isWarehouse()) {
                return;
            }
            contents = building.inventory().contents();
        } else if (entity instanceof Unit unit) {
            if (!unit.isCarryingItems()) {
                return;
            }
            contents = unit.cargo().contents();
        } else {
            return;
        }
        boolean first = true;
        for (ItemBundle.Amount component : contents.components()) {
            if (component.amount().isZero()) {
                continue;
            }
            if (first) {
                json.append(",\"contents\":{");
                first = false;
            } else {
                json.append(',');
            }
            appendJsonString(json, component.item().key().path());
            json.append(':').append(component.amount().toLongSaturated());
        }
        if (!first) {
            json.append('}');
        }
    }

    /**
     * Appends the optional {@code upgrade} offer of a finished building.
     *
     * <p>The object is written only when the building's content type names a successor
     * ({@link BuildingType#upgradeTo()}), that successor exists in the served catalogue and it has the same
     * footprint, so the client never paints an 升级 entry the command would refuse as a missing target or a
     * footprint mismatch. An unfinished site also
     * omits it, because the engine only upgrades constructed buildings. The shape is
     * {@code ,"upgrade":{"to":"building.armory.t2","gold":140,"wood":40}}: {@code to} is the target's
     * content path, and {@code gold} / {@code wood} are the two resources this protocol already carries in
     * its player rows. Costs of other item kinds are real engine data but have no wire field yet, so they
     * are not invented here; classic upgrades are gold and wood only.
     *
     * <p>The cost belongs to the source type ({@link BuildingType#upgradeCost()}), not to the target: it is
     * exactly what {@code UPGRADE <id>} will charge. Affordability is deliberately not encoded: the row says
     * what the step costs, and the client compares it with the player's own row so the entry can be shown
     * disabled instead of disappearing when the stock is short.
     */
    private void appendUpgradeOffer(StringBuilder json, Entity entity, ItemType goldItem, ItemType woodItem) {
        if (!(entity instanceof Building building) || !building.isConstructed()) {
            return;
        }
        Optional<ResourceKey> targetKey = building.type().upgradeTo();
        if (targetKey.isEmpty()) {
            return;
        }
        BuildingType target = content.buildingOrNull(targetKey.get());
        if (target == null
                || target.key().equals(building.type().key())
                || target.sizeX() != building.type().sizeX()
                || target.sizeY() != building.type().sizeY()) {
            // A target with another footprint is refused by the command, so it is not advertised either.
            return;
        }
        ItemBundle cost = building.type().upgradeCost();
        long gold = goldItem == null ? 0L : cost.amount(goldItem).toLongSaturated();
        long wood = woodItem == null ? 0L : cost.amount(woodItem).toLongSaturated();
        json.append(",\"upgrade\":{\"to\":");
        appendJsonString(json, target.key().path());
        json.append(",\"gold\":").append(gold)
                .append(",\"wood\":").append(wood)
                .append('}');
    }

    /**
     * Appends the optional {@code repair} and {@code reclaim} objects of a building row.
     *
     * <p>{@code repair} appears on a finished building that is damaged and whose content declares a
     * per-health repair price. Its shape is {@code ,"repair":{"gold":300,"wood":0}} and the amounts are
     * the cost of repairing every missing hit point at the moment of the snapshot - exactly what
     * {@code REPAIR <engineer> <building>} would spend in total. The field is omitted for a full-health
     * building, an unfinished site and a type without a repair price, so the client never offers a
     * repair the command would refuse.
     *
     * <p>{@code reclaim} appears on an unfinished site. Its shape is
     * {@code ,"reclaim":{"gold":50,"wood":10}} and the amounts are what
     * {@code RECLAIM <buildingId>} would deposit: half of the bundle the site recorded as its placement
     * investment ({@link CommandService#reclaimRefund(io.github.sob1234509876.oc.rts.entity.Building)}).
     * It is present even for a free site (both amounts {@code 0}), because the action still removes the
     * site.
     *
     * <p>Like every optional field these are additive: an older client ignores them, and the classic
     * catalogue's repairs and reclaims are counted in gold and wood, the two resources this document
     * already carries. A repair price in another item kind is real engine data but has no wire field yet.
     */
    private void appendBuildingServices(StringBuilder json, Entity entity, ItemType goldItem,
            ItemType woodItem) {
        if (!(entity instanceof Building building)) {
            return;
        }
        if (building.isConstructed()) {
            if (building.missingHealth().isZero() || !building.type().canRepair()) {
                return;
            }
            ItemBundle cost = building.repairCost();
            json.append(",\"repair\":{\"gold\":").append(amountOf(cost, goldItem))
                    .append(",\"wood\":").append(amountOf(cost, woodItem))
                    .append('}');
            return;
        }
        ItemBundle refund = CommandService.reclaimRefund(building);
        json.append(",\"reclaim\":{\"gold\":").append(amountOf(refund, goldItem))
                .append(",\"wood\":").append(amountOf(refund, woodItem))
                .append('}');
    }

    /**
     * Appends the remaining route of a moving unit: {@code ,"path":[[x,y],...],"target":[x,y]}.
     *
     * <p>The array holds the first {@value #MAX_SNAPSHOT_PATH_POINTS} tile pairs of the <em>remaining</em>
     * polyline (consumed waypoints are history and are not sent) and {@code target} is the goal tile, so a
     * client can draw the line and mark its end. Coordinates are absolute world tiles in the same space
     * as an entity's {@code x} / {@code y}, and may be negative. A unit with no goal, a unit that has
     * arrived, and a unit that is still waiting for its first route all omit both fields, which is what
     * makes the line disappear when the unit stops.
     */
    private static void appendUnitPath(StringBuilder json, Entity entity) {
        if (!(entity instanceof Unit unit) || !unit.motion().isMoving()) {
            return;
        }
        Motion motion = unit.motion();
        if (motion.goalOrNull() == null) {
            return;
        }
        List<Vec2> waypoints = motion.waypoints();
        int from = motion.waypointIndex();
        int available = Math.max(0, waypoints.size() - from);
        int count = Math.min(available, MAX_SNAPSHOT_PATH_POINTS);
        if (count == 0) {
            return;
        }
        json.append(",\"path\":[");
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                json.append(',');
            }
            var tile = waypoints.get(from + index).tile();
            json.append('[').append(tile.x()).append(',').append(tile.y()).append(']');
        }
        var target = motion.goalOrNull().tile();
        json.append("],\"target\":[").append(target.x()).append(',').append(target.y()).append(']');
    }

    /** One item's amount in a bundle, or {@code 0} when that item is not registered. */
    private static long amountOf(ItemBundle bundle, ItemType item) {
        return item == null ? 0L : bundle.amount(item).toLongSaturated();
    }

    /**
     * One player's income rate for one resource, in units per minute.
     *
     * <p>The sum runs over the player's buildings that are alive and constructed - the same two
     * conditions the classic payout hook applies - and takes each type's first matching
     * {@code prefix<number>} tag. The type is resolved through the served {@link ContentRegistry} rather
     * than trusting a foreign type instance, so the number on the wire is the number the catalogue
     * actually published. A type that is no longer in the registry, a non-matching tag and a malformed or
     * negative number all contribute zero; the sum saturates instead of overflowing.
     *
     * @param player the player whose buildings are summed
     * @param prefix either {@link #INCOME_TAG_PREFIX} or {@link #WOOD_INCOME_TAG_PREFIX}
     * @return a non-negative rate in units per minute, or {@code 0} when nothing publishes one
     */
    private long incomePerMinute(Player player, String prefix) {
        long total = 0L;
        for (Building building : engine.world().buildingsOf(player)) {
            if (!building.isAlive() || !building.isConstructed()) {
                continue;
            }
            BuildingType type = content.buildingOrNull(building.type().key());
            if (type != null) {
                total = saturatedAdd(total, taggedRate(type, prefix));
            }
        }
        return total;
    }

    /** The first {@code prefix<number>} tag of a type as a non-negative rate, or {@code 0}. */
    private static long taggedRate(BuildingType type, String prefix) {
        for (String tag : type.tags()) {
            if (tag.startsWith(prefix)) {
                String amount = tag.substring(prefix.length());
                try {
                    return Math.max(0L, Long.parseLong(amount));
                } catch (NumberFormatException malformed) {
                    return 0L;
                }
            }
        }
        return 0L;
    }

    /** Adds two non-negative rates, pinning at {@link Long#MAX_VALUE} instead of wrapping. */
    private static long saturatedAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
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
