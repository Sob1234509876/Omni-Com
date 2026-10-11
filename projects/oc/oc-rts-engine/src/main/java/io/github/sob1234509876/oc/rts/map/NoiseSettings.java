package io.github.sob1234509876.oc.rts.map;

import io.github.sob1234509876.oc.rts.content.ResourceKey;

import java.util.Objects;

/**
 * Tuning of {@link NoiseChunkGenerator}: which noise field is sampled, where the terrain boundaries
 * sit and which content path each resulting biome uses.
 *
 * <p>Terrain is addressed by {@link ResourceKey} path because the engine does not own any concrete
 * terrain definitions; a scenario decides whether it calls its water {@code water}, {@code ocean} or
 * {@code lake}. A generator can therefore be reused with a different content catalogue without any
 * code change, and a missing path degrades to grass instead of failing the world.
 *
 * <p>Up to four values steer the shape of the world:
 * <ul>
 *   <li>{@link #frequency()} and {@link #octaves()} set how large the land masses are,</li>
 *   <li>{@link #seaThreshold()} sets how much of the raw noise field ends up under water,</li>
 *   <li>{@link #originElevation()} and {@link #originRadius()} force the area around the origin to a
 *       known height, which is the guarantee a scenario relies on when it places its first units.</li>
 * </ul>
 *
 * <p>The origin guarantee is a blend, not a downward push: inside {@link #originRadius()} the sampled
 * elevation is mixed towards {@link #originElevation()}, fully at the origin and not at all at the rim.
 * Adding or subtracting an offset could not guarantee anything, because raw noise that already sits
 * below the sea line would stay below it; mixing pins the origin to a height that is chosen to be above
 * the sea line and below the hill line, so the opening area is walkable, buildable land.
 *
 * <p>The blend only reaches all the way down to the raw noise at the rim, so passability is guaranteed
 * for certain within a fraction of {@link #originRadius()}: the blend is still about {@code 0.65} at
 * one third of the radius, which is more than enough to keep a tile above the sea line for any raw
 * elevation in {@code [0, 1]}. {@link NoiseChunkGenerator} exposes {@link
 * NoiseChunkGenerator#guaranteedLandRadius()} for callers that need the exact number.
 *
 * @param frequency        base frequency of the elevation field in cycles per tile; smaller means
 *                         larger continents
 * @param octaves          number of noise octaves added together, at least one
 * @param lacunarity       frequency multiplier between octaves, usually about two
 * @param gain             amplitude factor between octaves, in {@code (0, 1)}
 * @param seaThreshold     elevation below which a tile is water
 * @param sandThreshold    elevation above the sea but below this value is beach
 * @param forestThreshold  moisture above which dry land becomes forest
 * @param hillThreshold    elevation above which land becomes hill
 * @param rockThreshold    elevation above which land becomes rock
 * @param moistureFrequency frequency of the independent moisture field
 * @param originElevation  elevation the noise is blended to at the origin; must lie above the sea line
 *                         and below the rock line so that the opening area is solid, walkable ground
 * @param originRadius     distance in tiles at which the origin blend has faded away completely
 * @param waterPath        content path of the water terrain
 * @param sandPath         content path of the beach terrain
 * @param grassPath        content path of the buildable open terrain used for the origin area
 * @param forestPath       content path of the forest terrain
 * @param hillPath         content path of the hill terrain
 * @param rockPath         content path of the rock terrain, or {@code null} to let high ground stay
 *                         passable
 */
public record NoiseSettings(
        float frequency,
        int octaves,
        float lacunarity,
        float gain,
        float seaThreshold,
        float sandThreshold,
        float forestThreshold,
        float hillThreshold,
        float rockThreshold,
        float moistureFrequency,
        float originElevation,
        float originRadius,
        ResourceKey waterPath,
        ResourceKey sandPath,
        ResourceKey grassPath,
        ResourceKey forestPath,
        ResourceKey hillPath,
        ResourceKey rockPath) {

    /** Chunk size assumed by {@link NoiseSettings#defaults()}. */
    public static final int DEFAULT_CHUNK_SIZE = 16;

    /**
     * Elevation the origin is pinned to by default: comfortably inside the open ground band, so the
     * opening area is buildable even when the beach terrain of a catalogue is not.
     */
    public static final float DEFAULT_ORIGIN_ELEVATION = 0.44f;

    /**
     * Radius in tiles of the default origin blend.
     *
     * <p>Deliberately a few tiles larger than the guaranteed land area and well below the wavelength of
     * the elevation field: a blend that reaches further would smooth the terrain of everything the
     * player can see, which measured as a featureless plain for hundreds of tiles.
     */
    public static final float DEFAULT_ORIGIN_RADIUS = 36f;

    public NoiseSettings {
        Objects.requireNonNull(waterPath, "waterPath");
        Objects.requireNonNull(sandPath, "sandPath");
        Objects.requireNonNull(grassPath, "grassPath");
        Objects.requireNonNull(forestPath, "forestPath");
        Objects.requireNonNull(hillPath, "hillPath");
        if (!(frequency > 0f)) {
            throw new IllegalArgumentException("frequency must be positive, was " + frequency);
        }
        if (octaves < 1) {
            throw new IllegalArgumentException("at least one octave is required, was " + octaves);
        }
        if (!(lacunarity > 1f)) {
            throw new IllegalArgumentException("lacunarity must be greater than one, was " + lacunarity);
        }
        if (!(gain > 0f && gain < 1f)) {
            throw new IllegalArgumentException("gain must be in (0, 1), was " + gain);
        }
        if (!(moistureFrequency > 0f)) {
            throw new IllegalArgumentException("moisture frequency must be positive, was " + moistureFrequency);
        }
        if (!(originRadius > 0f)) {
            throw new IllegalArgumentException("origin radius must be positive, was " + originRadius);
        }
        if (!(seaThreshold < sandThreshold && sandThreshold < hillThreshold && hillThreshold < rockThreshold)) {
            throw new IllegalArgumentException("elevation thresholds must increase: sea=" + seaThreshold
                    + ", sand=" + sandThreshold + ", hill=" + hillThreshold + ", rock=" + rockThreshold);
        }
        // The blend has to be able to place the origin on land and away from the rock line; otherwise the
        // guaranteed opening area would be sea or wall, which is exactly what the setting exists to avoid.
        if (!(originElevation > seaThreshold)) {
            throw new IllegalArgumentException("origin elevation " + originElevation
                    + " must be above the sea threshold " + seaThreshold);
        }
        if (!(originElevation < rockThreshold)) {
            throw new IllegalArgumentException("origin elevation " + originElevation
                    + " must be below the rock threshold " + rockThreshold);
        }
    }

    /**
     * Terrain mix that produces mostly walkable land with buildable ground around the origin.
     *
     * <p>The declared default values pass this record's own validation by construction.
     */
    public static NoiseSettings defaults() {
        return defaults("oc");
    }

    /** {@link #defaults()} with the content paths resolved in the given namespace. */
    public static NoiseSettings defaults(String namespace) {
        return new NoiseSettings(
                1f / 32f,
                3,
                2f,
                0.5f,
                0.38f,
                0.42f,
                0.52f,
                0.56f,
                0.74f,
                1f / 24f,
                DEFAULT_ORIGIN_ELEVATION,
                DEFAULT_ORIGIN_RADIUS,
                new ResourceKey(namespace, "water"),
                new ResourceKey(namespace, "sand"),
                new ResourceKey(namespace, "grass"),
                new ResourceKey(namespace, "forest"),
                new ResourceKey(namespace, "hill"),
                new ResourceKey(namespace, "rock"));
    }

    public NoiseSettings withSeaThreshold(float threshold) {
        return new NoiseSettings(frequency, octaves, lacunarity, gain, threshold, sandThreshold, forestThreshold,
                hillThreshold, rockThreshold, moistureFrequency, originElevation, originRadius, waterPath,
                sandPath, grassPath, forestPath, hillPath, rockPath);
    }

    /** Copy with a different origin guarantee; see {@link #originElevation()} and {@link #originRadius()}. */
    public NoiseSettings withOrigin(float elevation, float radius) {
        return new NoiseSettings(frequency, octaves, lacunarity, gain, seaThreshold, sandThreshold,
                forestThreshold, hillThreshold, rockThreshold, moistureFrequency, elevation, radius, waterPath,
                sandPath, grassPath, forestPath, hillPath, rockPath);
    }

    /** Copy with a different set of content paths. */
    public NoiseSettings withPaths(
            ResourceKey water,
            ResourceKey sand,
            ResourceKey grass,
            ResourceKey forest,
            ResourceKey hill,
            ResourceKey rock) {
        return new NoiseSettings(frequency, octaves, lacunarity, gain, seaThreshold, sandThreshold,
                forestThreshold, hillThreshold, rockThreshold, moistureFrequency, originElevation, originRadius,
                water, sand, grass, forest, hill, rock);
    }

    /** Total amplitude of the octave stack, used to normalise the sampled field into {@code [0, 1]}. */
    public float amplitudeSum() {
        float sum = 0f;
        float amplitude = 1f;
        for (int octave = 0; octave < octaves; octave++) {
            sum += amplitude;
            amplitude *= gain;
        }
        return sum;
    }
}
