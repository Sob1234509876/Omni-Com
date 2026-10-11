package io.github.sob1234509876.oc.rts.content;

import java.util.Objects;

/**
 * Definition of one kind of terrain: whether units may cross it, whether buildings may be placed on
 * it, how much time crossing it takes and which sprite a renderer should draw.
 */
public final class TerrainType implements Comparable<TerrainType> {

    private final ResourceKey key;
    private final String displayName;
    private final boolean passable;
    private final boolean buildable;
    private final float moveCost;
    private final String spriteId;
    private final int mapColor;

    TerrainType(
            ResourceKey key,
            String displayName,
            boolean passable,
            boolean buildable,
            float moveCost,
            String spriteId,
            int mapColor) {
        this.key = Objects.requireNonNull(key, "key");
        this.displayName = displayName == null ? key.path() : displayName;
        this.passable = passable;
        this.buildable = buildable;
        this.moveCost = moveCost;
        this.spriteId = spriteId;
        this.mapColor = mapColor;
    }

    public ResourceKey key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    /** Whether ground units may stand on and cross this terrain. */
    public boolean passable() {
        return passable;
    }

    /** Whether buildings may be placed on this terrain; implies {@link #passable()}. */
    public boolean buildable() {
        return buildable;
    }

    /** Relative cost of entering a tile of this terrain; {@code 1} is open ground. */
    public float moveCost() {
        return moveCost;
    }

    /** Sprite identifier a renderer resolves through the visual library. */
    public String spriteId() {
        return spriteId;
    }

    /** Placeholder colour for text maps and debug output, as {@code 0xRRGGBB}. */
    public int mapColor() {
        return mapColor;
    }

    @Override
    public int compareTo(TerrainType other) {
        return key.compareTo(other.key);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TerrainType terrain && key.equals(terrain.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return key.toString();
    }
}
