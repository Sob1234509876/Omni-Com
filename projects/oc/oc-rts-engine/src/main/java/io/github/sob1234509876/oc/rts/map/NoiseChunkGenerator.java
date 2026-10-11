package io.github.sob1234509876.oc.rts.map;

import io.github.sob1234509876.oc.rts.content.ContentRegistry;
import io.github.sob1234509876.oc.rts.content.ResourceKey;
import io.github.sob1234509876.oc.rts.content.TerrainType;
import io.github.sob1234509876.oc.rts.core.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Generates an endless, deterministic landscape from a hash based value noise field.
 *
 * <p>Everything a tile needs is derived from its absolute coordinate, the chunk and the world seed.
 * There is no sequential random stream and no state carried between chunks, so a chunk has the same
 * contents no matter in which order chunks are visited, how often one was evicted, or which search
 * reached over it first. That property is what keeps replays and save games identical.
 *
 * <p>Two independent fields are sampled. Elevation decides between water, beach, open land, hill and
 * rock; moisture decides whether open land becomes forest. Inside {@link NoiseSettings#originRadius()}
 * the elevation is blended towards {@link NoiseSettings#originElevation()}, which pins the starting
 * area of a scenario to walkable, buildable ground instead of leaving it to the luck of the noise field.
 * That guarantee is checked when the generator is built, so a scenario notices a terrain catalogue that
 * cannot host a base immediately rather than when it tries to place its first building.
 *
 * <p>Terrain is looked up in the {@link ContentRegistry} by path, so a scenario may name its terrains
 * freely. Undefined paths degrade to grass, except for rock: when no impassable rock terrain exists the
 * generator simply stops producing high ground rather than walling the map off with void.
 */
public final class NoiseChunkGenerator implements ChunkGenerator {

    /** Default chunk size of the noise world. */
    public static final int DEFAULT_CHUNK_SIZE = 16;

    private static final long ELEVATION_SALT = 0x9E3779B97F4A7C15L;
    private static final long MOISTURE_SALT = 0xC2B2AE3D27D4EB4FL;

    /** Moisture the origin is pinned to: dry enough that the opening area never turns into forest. */
    private static final float ORIGIN_MOISTURE = 0.2f;

    /** Radius in tiles over which the pinned origin moisture fades back into the natural field. */
    private static final int ORIGIN_MOISTURE_RADIUS = 64;

    private final long seed;
    private final NoiseSettings settings;
    private final int chunkSize;
    private final float amplitudeSum;

    private final TerrainType water;
    private final TerrainType sand;
    private final TerrainType grass;
    private final TerrainType forest;
    private final TerrainType hill;
    private final TerrainType rock;

    public NoiseChunkGenerator(long seed, ContentRegistry content) {
        this(seed, content, NoiseSettings.defaults(), DEFAULT_CHUNK_SIZE);
    }

    public NoiseChunkGenerator(long seed, ContentRegistry content, NoiseSettings settings) {
        this(seed, content, settings, DEFAULT_CHUNK_SIZE);
    }

    public NoiseChunkGenerator(long seed, ContentRegistry content, NoiseSettings settings, int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunk size must be positive, was " + chunkSize);
        }
        Objects.requireNonNull(content, "content");
        this.seed = seed;
        this.settings = Objects.requireNonNull(settings, "settings");
        this.chunkSize = chunkSize;
        this.amplitudeSum = settings.amplitudeSum();

        TerrainType fallback = content.fallbackTerrain();
        this.grass = passableOr(content.terrainOrNull(settings.grassPath()), fallback);
        this.water = firstNonNull(content.terrainOrNull(settings.waterPath()), fallback);
        this.sand = firstNonNull(content.terrainOrNull(settings.sandPath()), grass);
        this.forest = firstNonNull(content.terrainOrNull(settings.forestPath()), grass);
        this.hill = firstNonNull(content.terrainOrNull(settings.hillPath()), grass);
        this.rock = resolveRock(content, settings.rockPath());

        // Check the origin guarantee once, at construction, instead of discovering it when a scenario
        // fails to place its first building on the terrain this catalogue produces.
        TerrainType origin = terrainFor(0, 0);
        if (!origin.passable() || !origin.buildable()) {
            throw new IllegalStateException("the terrain at the origin is " + origin.key()
                    + " (passable=" + origin.passable() + ", buildable=" + origin.buildable()
                    + "); the origin area must be buildable land, so check the terrain paths in "
                    + settings.grassPath() + ", " + settings.sandPath() + " and the configured thresholds");
        }
    }

    public long seed() {
        return seed;
    }

    public NoiseSettings settings() {
        return settings;
    }

    @Override
    public int chunkSize() {
        return chunkSize;
    }

    /** Terrain this generator puts under the sea line. */
    public TerrainType waterTerrain() {
        return water;
    }

    /** Terrain this generator uses for buildable open ground. */
    public TerrainType grassTerrain() {
        return grass;
    }

    @Override
    public Chunk generate(ChunkPos pos) {
        Objects.requireNonNull(pos, "pos");
        TerrainType[] tiles = new TerrainType[chunkSize * chunkSize];
        int originX = pos.x() * chunkSize;
        int originY = pos.y() * chunkSize;
        int index = 0;
        for (int localY = 0; localY < chunkSize; localY++) {
            int worldY = originY + localY;
            for (int localX = 0; localX < chunkSize; localX++) {
                int worldX = originX + localX;
                tiles[index++] = terrainFor(worldX, worldY);
            }
        }
        Chunk chunk = new Chunk(pos, chunkSize);
        System.arraycopy(tiles, 0, chunk.rawTiles(), 0, tiles.length);
        return chunk;
    }

    /** Terrain a world tile receives; exposed for tests and for map preview tools. */
    public TerrainType terrainFor(int x, int y) {
        float elevation = elevation(x, y);
        if (elevation < settings.seaThreshold()) {
            return water;
        }
        if (elevation < settings.sandThreshold()) {
            return sand;
        }
        if (settings.rockPath() != null && elevation >= settings.rockThreshold()) {
            return rock;
        }
        if (elevation >= settings.hillThreshold()) {
            return hill;
        }
        return moisture(x, y) >= settings.forestThreshold() ? forest : grass;
    }

    /** Whether a world tile ends up above the sea line. */
    public boolean isLand(int x, int y) {
        return elevation(x, y) >= settings.seaThreshold();
    }

    /**
     * The fraction of a tile the origin blend covers at the given distance, for diagnostics and tests.
     */
    public float originBlendAt(int x, int y) {
        return originBlend(x, y);
    }

    /**
     * Radius around the origin in which every tile is guaranteed to be land, whatever the raw noise says.
     *
     * <p>The blend lifts a tile of raw elevation {@code e} to {@code e + (origin - e) * fade}. For the
     * worst possible raw elevation of zero this still clears the sea line as long as
     * {@code origin * fade >= seaThreshold}, which gives a fade of {@code seaThreshold / origin}. The
     * blend is {@code (1 - t)} squared with {@code t = (distance / radius)} squared, so that fade
     * corresponds to {@code distance = radius * sqrt(1 - sqrt(fade))}. The result is deliberately
     * conservative and never larger than the configured radius.
     */
    public int guaranteedLandRadius() {
        float origin = settings.originElevation();
        if (origin <= 0f) {
            return 0;
        }
        float requiredFade = Math.min(1f, settings.seaThreshold() / origin);
        float distanceFactor = (float) Math.sqrt(Math.max(0f, 1f - Math.sqrt(requiredFade)));
        return (int) Math.floor(settings.originRadius() * distanceFactor);
    }

    /**
     * Fraction of land in a square of the given radius around the origin, sampled at every tile.
     *
     * <p>Offered so that a scenario can assert that the world it configured is walkable enough before
     * it starts; sampling is cheap because the noise is evaluated on demand.
     */
    public float landFraction(int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("radius must not be negative, was " + radius);
        }
        int side = radius * 2 + 1;
        int land = 0;
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                if (isLand(x, y)) {
                    land++;
                }
            }
        }
        return (float) land / (float) (side * side);
    }

    // ------------------------------------------------------------------ noise

    /**
     * Elevation of a tile in {@code [0, 1]}, built from {@link NoiseSettings#octaves()} octaves of
     * bilinear value noise and then blended towards the origin height near the origin.
     */
    public float elevation(int x, int y) {
        float sum = 0f;
        float amplitude = 1f;
        float frequency = settings.frequency();
        for (int octave = 0; octave < settings.octaves(); octave++) {
            sum += sampleOctave(x, y, frequency, octave, ELEVATION_SALT) * amplitude;
            amplitude *= settings.gain();
            frequency *= settings.lacunarity();
        }
        float raw = sum / amplitudeSum;
        float blend = originBlend(x, y);
        return raw + (settings.originElevation() - raw) * blend;
    }

    /**
     * Moisture of a tile in {@code [0, 1]}; drives the forest boundary.
     *
     * <p>As with elevation, the value is blended towards open ground near the origin. Moisture decides
     * whether open land becomes forest, and forest is passable but usually not buildable, so pinning
     * only the height would still leave the opening area unusable for a base.
     */
    public float moisture(int x, int y) {
        float raw = sampleOctave(x, y, settings.moistureFrequency(), 0, MOISTURE_SALT);
        float blend = originMoistureBlend(x, y);
        return raw + (ORIGIN_MOISTURE - raw) * blend;
    }

    /**
     * How strongly a position is pulled to {@link #ORIGIN_MOISTURE}: dry open ground at the origin,
     * fading to the natural field a short way out. The radius is deliberately much smaller than
     * {@link NoiseSettings#originRadius()} so that forests still appear close to the starting area.
     */
    private float originMoistureBlend(int x, int y) {
        float distanceSquared = (float) x * x + (float) y * y;
        float radiusSquared = ORIGIN_MOISTURE_RADIUS * ORIGIN_MOISTURE_RADIUS;
        if (distanceSquared >= radiusSquared) {
            return 0f;
        }
        float t = distanceSquared / radiusSquared;
        float fade = 1f - t;
        return fade * fade;
    }

    /**
     * How strongly a position is pulled to {@link NoiseSettings#originElevation()}: {@code 1} exactly at
     * the origin and {@code 0} from {@link NoiseSettings#originRadius()} outwards.
     *
     * <p>The blend makes the guarantee independent of the raw noise: whatever the octaves produce, the
     * tile at the origin ends up at the configured height, which is validated to be above the sea line
     * and below the rock line. A plain offset could not promise that, because noise that already sits
     * below the sea line would merely be pushed further down.
     */
    private float originBlend(int x, int y) {
        float distanceSquared = (float) x * x + (float) y * y;
        float radiusSquared = settings.originRadius() * settings.originRadius();
        if (distanceSquared >= radiusSquared) {
            return 0f;
        }
        float t = distanceSquared / radiusSquared;
        float fade = 1f - t;
        return fade * fade;
    }

    /** One octave of value noise in {@code [0, 1]}. */
    private float sampleOctave(int x, int y, float frequency, int octave, long salt) {
        float scaledX = x * frequency;
        float scaledY = y * frequency;
        int cellX = fastFloor(scaledX);
        int cellY = fastFloor(scaledY);
        float fractionX = scaledX - cellX;
        float fractionY = scaledY - cellY;
        float smoothX = smooth(fractionX);
        float smoothY = smooth(fractionY);
        float value00 = unitHash(hash(cellX, cellY, octave, salt));
        float value10 = unitHash(hash(cellX + 1, cellY, octave, salt));
        float value01 = unitHash(hash(cellX, cellY + 1, octave, salt));
        float value11 = unitHash(hash(cellX + 1, cellY + 1, octave, salt));
        float lower = value00 + (value10 - value00) * smoothX;
        float upper = value01 + (value11 - value01) * smoothX;
        return lower + (upper - lower) * smoothY;
    }

    /** The cheap and exact floor for the coordinate range a map can reach. */
    private int fastFloor(float value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    /** Smoothstep, which gives the interpolated noise a continuous first derivative. */
    private float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    /** Mixes a lattice coordinate, the octave and the seed into one well spread 64 bit hash. */
    private long hash(int x, int y, int octave, long salt) {
        long mixed = seed ^ salt;
        mixed ^= x * 0x9E3779B97F4A7C15L;
        mixed = Long.rotateLeft(mixed, 29);
        mixed ^= y * 0xC2B2AE3D27D4EB4FL;
        mixed = Long.rotateLeft(mixed, 31);
        mixed ^= octave * 0x165667B19E3779F9L;
        mixed ^= mixed >>> 33;
        mixed *= 0xFF51AFD7ED558CCDL;
        mixed ^= mixed >>> 33;
        mixed *= 0xC4CEB9FE1A85EC53L;
        return mixed ^ (mixed >>> 33);
    }

    private float unitHash(long hash) {
        return (hash >>> 40) * 0x1.0p-24f;
    }

    // ------------------------------------------------------------------ terrain resolution

    /**
     * Rock is only used when the catalogue really declares an impassable terrain for it. Falling back
     * to the void terrain instead would coat the whole map in walls wherever a scenario forgot the
     * definition, so a missing rock simply means "high ground stays walkable".
     */
    private static TerrainType resolveRock(ContentRegistry content, ResourceKey rockPath) {
        if (rockPath == null) {
            return null;
        }
        TerrainType declared = content.terrainOrNull(rockPath);
        return declared != null && !declared.passable() ? declared : null;
    }

    /** Prefers the declared terrain when it can be walked on, so the origin area is never blocked. */
    private static TerrainType passableOr(TerrainType declared, TerrainType fallback) {
        if (declared != null && declared.passable()) {
            return declared;
        }
        return fallback;
    }

    private static TerrainType firstNonNull(TerrainType declared, TerrainType fallback) {
        return declared != null ? declared : fallback;
    }

    /** Terrain paths this generator looked up in the registry, for diagnostics. */
    public List<String> resolvedPaths() {
        List<String> paths = new ArrayList<>(6);
        paths.add(settings.waterPath().toString());
        paths.add(settings.sandPath().toString());
        paths.add(settings.grassPath().toString());
        paths.add(settings.forestPath().toString());
        paths.add(settings.hillPath().toString());
        paths.add(settings.rockPath() == null ? "<none>" : settings.rockPath().toString());
        return paths;
    }

    @Override
    public String toString() {
        return "NoiseChunkGenerator{seed=" + seed + ", chunkSize=" + chunkSize + ", " + settings + "}";
    }
}
