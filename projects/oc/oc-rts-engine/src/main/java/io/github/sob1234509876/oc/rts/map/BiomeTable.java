package io.github.sob1234509876.oc.rts.map;

import io.github.sob1234509876.oc.rts.content.ContentRegistry;
import io.github.sob1234509876.oc.rts.content.ResourceKey;
import io.github.sob1234509876.oc.rts.content.ScenarioContent;
import io.github.sob1234509876.oc.rts.content.TerrainType;

import java.util.List;
import java.util.Objects;

/**
 * The six terrain definitions a {@link NoiseTerrainGenerator} paints, handed over as content rather
 * than as strings.
 *
 * <p>The generator itself never looks up the content catalogue: a scenario resolves the six terrain
 * kinds once and passes the resulting table in. That keeps generation a pure function of the noise
 * field and the table, and it lets a catalogue call its biomes whatever it likes as long as it supplies
 * six {@link TerrainType}s.
 *
 * <p>{@link #of(ContentRegistry)} and {@link #scenario()} resolve the paths the engine's sample
 * scenario registers in {@link ScenarioContent}: {@code water}, {@code sand}, {@code grass},
 * {@code forest}, {@code hill} and {@code rock}. A catalogue that reuses those names does not need any
 * new constants; a catalogue with different names uses {@link #of(TerrainType, TerrainType, TerrainType,
 * TerrainType, TerrainType, TerrainType)}.
 *
 * <p>The record is immutable, so one table may safely be shared by any number of generators.
 */
public record BiomeTable(
        TerrainType water,
        TerrainType sand,
        TerrainType grass,
        TerrainType forest,
        TerrainType hill,
        TerrainType rock) {

    /** Path of the water terrain in {@link ScenarioContent} and in the other in-repo catalogues. */
    public static final String WATER_PATH = "water";

    /** Path of the beach terrain in {@link ScenarioContent}. */
    public static final String SAND_PATH = "sand";

    /** Path of the ordinary buildable open terrain in {@link ScenarioContent}. */
    public static final String GRASS_PATH = "grass";

    /** Path of the wet open terrain in {@link ScenarioContent}; passable but not buildable. */
    public static final String FOREST_PATH = "forest";

    /** Path of the raised terrain in {@link ScenarioContent}; passable but slow. */
    public static final String HILL_PATH = "hill";

    /** Path of the highest terrain in {@link ScenarioContent}; impassable. */
    public static final String ROCK_PATH = "rock";

    public BiomeTable {
        Objects.requireNonNull(water, "water");
        Objects.requireNonNull(sand, "sand");
        Objects.requireNonNull(grass, "grass");
        Objects.requireNonNull(forest, "forest");
        Objects.requireNonNull(hill, "hill");
        Objects.requireNonNull(rock, "rock");
    }

    /**
     * The six terrains of the engine's sample scenario, resolved from a fresh
     * {@link ScenarioContent#omniCom()} catalogue.
     *
     * <p>This is what {@link NoiseTerrainGenerator#NoiseTerrainGenerator(long)} uses, so a bare seed
     * still produces a world with the paths the rest of the sample content knows about.
     */
    public static BiomeTable scenario() {
        return of(ScenarioContent.omniCom());
    }

    /**
     * Resolves the six scenario paths in the namespace of the given catalogue.
     *
     * @throws java.util.NoSuchElementException when the catalogue does not define one of the six paths
     */
    public static BiomeTable of(ContentRegistry content) {
        Objects.requireNonNull(content, "content");
        return new BiomeTable(
                content.terrain(WATER_PATH),
                content.terrain(SAND_PATH),
                content.terrain(GRASS_PATH),
                content.terrain(FOREST_PATH),
                content.terrain(HILL_PATH),
                content.terrain(ROCK_PATH));
    }

    /** Explicit table, for a catalogue whose biome names differ from the sample scenario's. */
    public static BiomeTable of(
            TerrainType water,
            TerrainType sand,
            TerrainType grass,
            TerrainType forest,
            TerrainType hill,
            TerrainType rock) {
        return new BiomeTable(water, sand, grass, forest, hill, rock);
    }

    /** Every terrain this table can produce, in biome order. */
    public List<TerrainType> terrains() {
        return List.of(water, sand, grass, forest, hill, rock);
    }

    /** Keys of every terrain this table can produce, in biome order. */
    public List<ResourceKey> keys() {
        return List.of(water.key(), sand.key(), grass.key(), forest.key(), hill.key(), rock.key());
    }

    @Override
    public String toString() {
        return "BiomeTable{" + water.key() + ", " + sand.key() + ", " + grass.key()
                + ", " + forest.key() + ", " + hill.key() + ", " + rock.key() + "}";
    }
}
