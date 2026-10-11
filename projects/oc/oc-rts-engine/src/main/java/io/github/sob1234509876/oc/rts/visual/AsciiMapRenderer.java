package io.github.sob1234509876.oc.rts.visual;

import io.github.sob1234509876.oc.rts.content.TerrainType;
import io.github.sob1234509876.oc.rts.content.UnitRole;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.TileRect;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Draws a {@link WorldSnapshot} as plain text.
 *
 * <p>The engine has no renderer, so without this class the only way to check that the visual layer
 * works would be to write one. A text map needs nothing but a console, which makes it the acceptance
 * test of the snapshot API: if the snapshot were missing anything a real renderer needs - position,
 * owner, facing, animation frame, selection, health - this class could not print it.
 *
 * <p>The output is deterministic: the same snapshot always produces the same text, every tile is
 * exactly one character, and no line has trailing whitespace. That makes it usable as an expected value
 * in tests and as a debugging tool that can be diffed between ticks.
 *
 * <h2>Symbols</h2>
 *
 * <p>Terrain, by the path of its {@link TerrainType#key()} so that scenarios can add their own kinds:
 * <ul>
 *   <li>{@code ~} water, {@code .} grass, {@code f} forest, {@code ^} hills, {@code #} rock,
 *       {@code :} sand, {@code ,} dirt, {@code _} snow, {@code =} road, {@code +} bridge,
 *       {@code V} void;</li>
 *   <li>{@code %} for any other terrain kind, and {@code ?} for a tile that has no terrain at all.</li>
 * </ul>
 *
 * <p>Entities are drawn on top of the terrain:
 * <ul>
 *   <li>units show the first letter of their {@link UnitRole} - {@code C} combat, {@code W} worker,
 *       {@code S} support - and buildings show the first letter of their type key path, upper case;</li>
 *   <li>a selected entity is lower case, an unselected one upper case, so a selection is visible
 *       without colour;</li>
 *   <li>an entity whose animation has finished (a corpse in its death pose) and a loaded hauler keep
 *       their letter but are placed on the map the same way, because the point of the letter is
 *       identity, not pose; use the legend and the snapshot API for the rest.</li>
 * </ul>
 *
 * <p>A building occupies its footprint, so its letter is drawn at the origin tile of the footprint and
 * the remaining tiles stay terrain. The last line is a legend with the tick, the amount of entities and
 * the terrain symbols actually used, which is what makes the output readable without the class in hand.
 */
public final class AsciiMapRenderer {

    /** Symbol used for a tile whose terrain could not be determined. */
    public static final char UNKNOWN_SYMBOL = '?';

    /** Symbol used for a terrain kind without a dedicated character. */
    public static final char OTHER_SYMBOL = '%';

    private static final Map<String, Character> TERRAIN_SYMBOLS = terrainSymbols();

    public AsciiMapRenderer() {
    }

    private static Map<String, Character> terrainSymbols() {
        Map<String, Character> symbols = new LinkedHashMap<>();
        symbols.put("water", '~');
        symbols.put("deep_water", '~');
        symbols.put("shallow_water", '~');
        symbols.put("grass", '.');
        symbols.put("grassland", '.');
        symbols.put("meadow", '.');
        symbols.put("forest", 'f');
        symbols.put("trees", 'f');
        symbols.put("hills", '^');
        symbols.put("hill", '^');
        symbols.put("mountain", '^');
        symbols.put("rock", '#');
        symbols.put("rocky", '#');
        symbols.put("stone", '#');
        symbols.put("sand", ':');
        symbols.put("desert", ':');
        symbols.put("dirt", ',');
        symbols.put("soil", ',');
        symbols.put("snow", '_');
        symbols.put("ice", '_');
        symbols.put("road", '=');
        symbols.put("bridge", '+');
        symbols.put("void", 'V');
        return Map.copyOf(symbols);
    }

    /** Renders the snapshot as a block of text ending in a newline. */
    public String render(WorldSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        TileRect bounds = snapshot.bounds();
        char[][] grid = new char[bounds.height()][bounds.width()];
        Map<String, Character> terrainLegend = new LinkedHashMap<>();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                TerrainType terrain = snapshot.terrainAt(x, y);
                char symbol = symbolOf(terrain);
                grid[y - bounds.minY()][x - bounds.minX()] = symbol;
                if (terrain != null) {
                    terrainLegend.putIfAbsent(terrain.key().path(), symbol);
                }
            }
        }
        int placed = 0;
        for (EntitySnapshot entity : snapshot.entities()) {
            if (overlay(grid, snapshot, entity)) {
                placed++;
            }
        }
        StringBuilder text = new StringBuilder();
        for (char[] row : grid) {
            text.append(row).append('\n');
        }
        text.append(legend(snapshot, terrainLegend, placed));
        return text.toString();
    }

    /**
     * Draws one entity onto the grid at its anchor tile.
     *
     * <p>The anchor arrived with the snapshot rather than being recomputed from the position: a unit is
     * drawn on the tile it stands on and a multi tile building at the origin of its footprint, both of
     * which the simulation already knows exactly.
     *
     * @return whether the entity was inside the snapshot bounds
     */
    private static boolean overlay(char[][] grid, WorldSnapshot snapshot, EntitySnapshot entity) {
        GridPos tile = entity.anchorTile();
        TileRect bounds = snapshot.bounds();
        if (!bounds.contains(tile)) {
            return false;
        }
        grid[tile.y() - bounds.minY()][tile.x() - bounds.minX()] = symbolOf(entity);
        return true;
    }

    /** Character that represents an entity on the map. */
    public static char symbolOf(EntitySnapshot entity) {
        char initial = entity.unit() ? unitInitial(entity) : initialOf(entity.typeKey().path());
        return entity.selected() ? Character.toLowerCase(initial) : initial;
    }

    /** First letter of the unit role, so a scenario's own unit types still read sensibly. */
    private static char unitInitial(EntitySnapshot entity) {
        UnitRole role = entity.role();
        return role == null ? initialOf(entity.typeKey().path()) : initialOf(role.id());
    }

    /** Upper case initial of a name, or {@code ?} when the name has no letters. */
    private static char initialOf(String name) {
        for (int index = 0; index < name.length(); index++) {
            char candidate = name.charAt(index);
            if (Character.isLetter(candidate)) {
                return Character.toUpperCase(candidate);
            }
        }
        return '?';
    }

    /** Character of a terrain kind: a known path, otherwise the generic symbol. */
    public static char symbolOf(TerrainType terrain) {
        if (terrain == null) {
            return UNKNOWN_SYMBOL;
        }
        Character symbol = TERRAIN_SYMBOLS.get(terrain.key().path());
        return symbol == null ? OTHER_SYMBOL : symbol;
    }

    /** Last line of the output: tick, entity counts and the terrain symbols that were drawn. */
    private static String legend(WorldSnapshot snapshot, Map<String, Character> terrainLegend, int placed) {
        StringJoiner terrain = new StringJoiner(", ");
        terrainLegend.forEach((path, symbol) -> terrain.add(symbol + " " + path));
        return "tick " + snapshot.tick() + " (" + String.format("%.2f", snapshot.elapsedSeconds()) + "s)"
                + " | entities " + snapshot.entityCount() + " (" + snapshot.unitCount() + " units, "
                + snapshot.buildingCount() + " buildings), drawn " + placed
                + " | tiles " + snapshot.width() + "x" + snapshot.height()
                + " " + snapshot.bounds().min() + ".." + snapshot.bounds().max()
                + " | chunks " + snapshot.loadedChunks().size()
                + " | terrain: " + terrain
                + " | entities: C/W/S units, selected lower case, buildings first letter of their type";
    }
}
