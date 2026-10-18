package io.github.sob1234509876.oc.rts.map;

import io.github.sob1234509876.oc.rts.content.TerrainType;
import io.github.sob1234509876.oc.rts.core.ChunkPos;

import java.util.Objects;

/**
 * Deterministic value-noise terrain generator: an endless landscape derived from a seed and a tile
 * coordinate, with no random stream, no wall clock and no state carried between chunks.
 *
 * <p>The noise is integer-hash based. Every lattice corner is hashed from the world seed, its integer
 * coordinate and the octave, then a smoothstep interpolates between the four corners of the cell. Two
 * independent fields are sampled:
 *
 * <ul>
 *   <li><strong>elevation</strong> ({@link #noise(int, int)}) chooses water, beach, open land, hill and
 *       rock; it is the sum of {@value #OCTAVES} octaves on a {@value #BASE_CELL_TILES} tile base
 *       grid, so the map reads as regions instead of per-tile static;</li>
 *   <li><strong>moisture</strong> ({@link #moisture(int, int)}) uses a different salt and a
 *       {@value #MOISTURE_CELL_TILES} tile base grid; it only decides whether open land becomes forest
 *       or stays grass.</li>
 * </ul>
 *
 * <p>Because the value of a tile depends only on {@code (seed, x, y)} the generator is a pure function:
 * the same seed produces the same world no matter in which order chunks are requested, whether a chunk
 * was evicted and regenerated, or which neighbour was generated first. Strings never appear in the
 * noise; the six content definitions come from a {@link BiomeTable}, normally
 * {@link BiomeTable#scenario()}, which maps the {@link io.github.sob1234509876.oc.rts.content.ScenarioContent}
 * terrain paths as follows:
 *
 * <table>
 *   <caption>Terrain paths used by the scenario table</caption>
 *   <tr><th>Biome</th><th>Path</th></tr>
 *   <tr><td>water</td><td>{@code oc:water}</td></tr>
 *   <tr><td>sand</td><td>{@code oc:sand}</td></tr>
 *   <tr><td>dry open land</td><td>{@code oc:grass} (the sample catalogue has no separate dirt)</td></tr>
 *   <tr><td>wet open land</td><td>{@code oc:forest}</td></tr>
 *   <tr><td>hill</td><td>{@code oc:hill}</td></tr>
 *   <tr><td>rock</td><td>{@code oc:rock}</td></tr>
 * </table>
 *
 * <p>The thresholds are the named constants {@link #WATER_LEVEL}, {@link #SAND_LEVEL},
 * {@link #HILL_LEVEL}, {@link #ROCK_LEVEL} and {@link #FOREST_MOISTURE}; they are deliberately explicit
 * rather than hidden in a settings object so a check can assert the biome edges. A tile is:
 * water below {@code WATER_LEVEL}; sand from there to {@code SAND_LEVEL}; rock at or above
 * {@code ROCK_LEVEL}; hill at or above {@code HILL_LEVEL}; otherwise forest when the moisture reaches
 * {@code FOREST_MOISTURE} and grass when it does not.
 *
 * <p>Generation allocates exactly one {@link TerrainType} array per chunk (inside {@link Chunk}); every
 * tile is filled by scalar arithmetic and no object is created per tile. A 16x16 chunk evaluates roughly
 * 4&nbsp;600 lattice hashes, which measures well below a millisecond on an ordinary developer machine.
 *
 * <p>The class is immutable and therefore safe to share between the world thread and preview tools.
 */
public final class NoiseTerrainGenerator implements ChunkGenerator {

    /** Default side length of a generated chunk in tiles. */
    public static final int DEFAULT_CHUNK_SIZE = 16;

    /** Distance in tiles between elevation lattice points; must stay at or above 16 for biome sized regions. */
    public static final int BASE_CELL_TILES = 32;

    /** Distance in tiles between moisture lattice points. */
    public static final int MOISTURE_CELL_TILES = 24;

    /** Number of octaves added into each field. */
    public static final int OCTAVES = 3;

    /** Frequency multiplier from one octave to the next. */
    public static final double LACUNARITY = 2.0;

    /** Amplitude multiplier from one octave to the next. */
    public static final double GAIN = 0.5;

    /** Elevation below this value is water. */
    public static final float WATER_LEVEL = 0.32f;

    /** Elevation from {@link #WATER_LEVEL} up to this value is the narrow sand band. */
    public static final float SAND_LEVEL = 0.36f;

    /** Elevation at or above this value (and below {@link #ROCK_LEVEL}) is hill. */
    public static final float HILL_LEVEL = 0.60f;

    /** Elevation at or above this value is rock, the highest biome. */
    public static final float ROCK_LEVEL = 0.78f;

    /** Moisture at or above this value turns open land into forest; below it the land stays grass. */
    public static final float FOREST_MOISTURE = 0.55f;

    /** Salt separating the elevation field from the moisture field. */
    private static final long ELEVATION_SALT = 0x9E3779B97F4A7C15L;

    /** Salt separating the moisture field from the elevation field. */
    private static final long MOISTURE_SALT = 0xC2B2AE3D27D4EB4FL;

    /** Total weight of the octave stack, used to normalise the sampled field into {@code [0, 1]}. */
    private static final double AMPLITUDE_SUM = amplitudeSum();

    private final long seed;
    private final BiomeTable biomes;
    private final int chunkSize;

    /**
     * A generator seeded with {@code seed} that paints the six sample-scenario terrains.
     *
     * <p>Uses {@link BiomeTable#scenario()}, i.e. {@code oc:water}, {@code oc:sand}, {@code oc:grass},
     * {@code oc:forest}, {@code oc:hill} and {@code oc:rock}. A host with its own catalogue should use
     * {@link #NoiseTerrainGenerator(long, BiomeTable)} instead.
     */
    public NoiseTerrainGenerator(long seed) {
        this(seed, BiomeTable.scenario());
    }

    /** A generator seeded with {@code seed} that paints the terrains of the given table. */
    public NoiseTerrainGenerator(long seed, BiomeTable table) {
        this(seed, table, DEFAULT_CHUNK_SIZE);
    }

    /**
     * A generator with an explicit chunk size, for hosts whose {@code ChunkedMap} uses a size other than
     * {@value #DEFAULT_CHUNK_SIZE}. The size must match the size the owning map was built with.
     */
    public NoiseTerrainGenerator(long seed, BiomeTable table, int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunk size must be positive, was " + chunkSize);
        }
        this.seed = seed;
        this.biomes = Objects.requireNonNull(table, "table");
        this.chunkSize = chunkSize;
    }

    public long seed() {
        return seed;
    }

    /** The six terrain definitions this generator paints. */
    public BiomeTable biomes() {
        return biomes;
    }

    @Override
    public int chunkSize() {
        return chunkSize;
    }

    /**
     * Generates a whole chunk by evaluating the noise at each absolute world coordinate.
     *
     * <p>Only this chunk's coordinates are read; nothing outside it is consulted and no state changes,
     * so generating the same position twice always returns the same tiles.
     */
    @Override
    public Chunk generate(ChunkPos pos) {
        Objects.requireNonNull(pos, "pos");
        Chunk chunk = new Chunk(pos, chunkSize);
        TerrainType[] tiles = chunk.rawTiles();
        int originX = pos.x() * chunkSize;
        int originY = pos.y() * chunkSize;
        int index = 0;
        for (int localY = 0; localY < chunkSize; localY++) {
            int worldY = originY + localY;
            for (int localX = 0; localX < chunkSize; localX++) {
                tiles[index++] = terrainFor(originX + localX, worldY);
            }
        }
        return chunk;
    }

    /**
     * The elevation field at a world tile, in {@code [0, 1]}.
     *
     * <p>This is the sample exposed for checks, map previews and any later system that wants to reason
     * about height without generating a chunk. It is a pure function of the seed and the coordinate.
     */
    public float noise(int x, int y) {
        return elevation(x, y);
    }

    /**
     * The elevation field at a world tile, in {@code [0, 1]}, before biome classification.
     *
     * <p>Identical to {@link #noise(int, int)}; the second name spells out what the sample contains.
     */
    public float elevation(int x, int y) {
        double sum = 0.0;
        double amplitude = 1.0;
        double frequency = 1.0 / BASE_CELL_TILES;
        for (int octave = 0; octave < OCTAVES; octave++) {
            sum += sampleOctave(x, y, frequency, octave, ELEVATION_SALT) * amplitude;
            amplitude *= GAIN;
            frequency *= LACUNARITY;
        }
        return (float) (sum / AMPLITUDE_SUM);
    }

    /**
     * The moisture field at a world tile, in {@code [0, 1]}; drives the forest boundary.
     *
     * <p>Independent of the elevation field because it hashes with a different salt and uses a different
     * base grid, so forests are not simply the low or high parts of the height field.
     */
    public float moisture(int x, int y) {
        double sum = 0.0;
        double amplitude = 1.0;
        double frequency = 1.0 / MOISTURE_CELL_TILES;
        for (int octave = 0; octave < OCTAVES; octave++) {
            sum += sampleOctave(x, y, frequency, octave, MOISTURE_SALT) * amplitude;
            amplitude *= GAIN;
            frequency *= LACUNARITY;
        }
        return (float) (sum / AMPLITUDE_SUM);
    }

    /**
     * The terrain a world tile receives, classified from the elevation and moisture fields.
     *
     * <p>Exposed so a check can compare the terrain a chunk holds with the terrain the noise function
     * predicts at the same coordinate, including coordinates on a chunk boundary.
     */
    public TerrainType terrainFor(int x, int y) {
        float height = elevation(x, y);
        if (height < WATER_LEVEL) {
            return biomes.water();
        }
        if (height < SAND_LEVEL) {
            return biomes.sand();
        }
        if (height >= ROCK_LEVEL) {
            return biomes.rock();
        }
        if (height >= HILL_LEVEL) {
            return biomes.hill();
        }
        return moisture(x, y) >= FOREST_MOISTURE ? biomes.forest() : biomes.grass();
    }

    // ------------------------------------------------------------------ noise

    /** One octave of bilinear value noise in {@code [0, 1]}. */
    private double sampleOctave(int x, int y, double frequency, int octave, long salt) {
        double scaledX = x * frequency;
        double scaledY = y * frequency;
        int cellX = fastFloor(scaledX);
        int cellY = fastFloor(scaledY);
        double fractionX = scaledX - cellX;
        double fractionY = scaledY - cellY;
        double smoothX = smooth(fractionX);
        double smoothY = smooth(fractionY);
        double value00 = unitHash(hash(cellX, cellY, octave, salt));
        double value10 = unitHash(hash(cellX + 1, cellY, octave, salt));
        double value01 = unitHash(hash(cellX, cellY + 1, octave, salt));
        double value11 = unitHash(hash(cellX + 1, cellY + 1, octave, salt));
        double lower = value00 + (value10 - value00) * smoothX;
        double upper = value01 + (value11 - value01) * smoothX;
        return lower + (upper - lower) * smoothY;
    }

    /** Exact floor for the coordinate range a map can reach; also correct for negative tiles. */
    private static int fastFloor(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    /** Smoothstep, which gives the interpolated field a continuous first derivative. */
    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * Mixes the seed, the lattice coordinate and the octave into one well spread 64 bit hash.
     *
     * <p>This is a pure integer mixer in the style of a 64 bit xorshift/murmur finaliser; it never reads
     * a clock and never draws from a sequential random stream.
     */
    private long hash(int x, int y, int octave, long salt) {
        long mixed = seed ^ salt;
        mixed ^= (long) x * 0x9E3779B97F4A7C15L;
        mixed = Long.rotateLeft(mixed, 29);
        mixed ^= (long) y * 0xC2B2AE3D27D4EB4FL;
        mixed = Long.rotateLeft(mixed, 31);
        mixed ^= (long) octave * 0xD6E8FEB86659FD93L;
        return mix(mixed);
    }

    /** SplitMix64 finaliser: avalanche every input bit across the whole word. */
    private static long mix(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** The top 24 bits of a hash as a float in {@code [0, 1)}. */
    private static double unitHash(long hash) {
        return (hash >>> 40) * 0x1.0p-24;
    }

    /** Total amplitude of the octave stack; the octave count and gain are constants, so this is too. */
    private static double amplitudeSum() {
        double sum = 0.0;
        double amplitude = 1.0;
        for (int octave = 0; octave < OCTAVES; octave++) {
            sum += amplitude;
            amplitude *= GAIN;
        }
        return sum;
    }

    @Override
    public String toString() {
        return "NoiseTerrainGenerator{seed=" + seed + ", chunkSize=" + chunkSize + ", " + biomes + "}";
    }
}
