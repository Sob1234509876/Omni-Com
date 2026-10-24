package io.github.sob1234509876.oc.rts.entity;

import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.content.BuildingType;
import io.github.sob1234509876.oc.rts.content.ResourceKey;
import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.TileRect;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.economy.Inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A static entity with a footprint: a warehouse, a barracks, a factory.
 *
 * <p>Every building has an {@link Inventory}; it is the input buffer of its recipes and the output
 * buffer of its production. When the type is marked as a warehouse, that inventory also takes part in
 * the owner's logistics network, which is what makes items stored here usable.
 *
 * <p>Buildings can be placed under construction: while {@link #isConstructed()} is false, production
 * does not start and {@link #constructionRemaining()} counts down. A site can have several builders
 * attached at once; each one claims its own adjacent standing tile and
 * {@link io.github.sob1234509876.oc.rts.production.ConstructionSystem} advances the site faster while
 * more of them are attached. The attachment is a two way link that is always torn down explicitly: when
 * a builder's order finishes, fails or is cancelled it calls {@link #detachBuilder(Unit)}, and the
 * construction system (on completion) or the command service (on destruction) detaches every builder
 * of a site that is lost.
 */
public final class Building extends Entity {

    private final BuildingType type;
    private final GridPos origin;
    private final Inventory inventory;
    private final Map<Unit, GridPos> builders = new LinkedHashMap<>();
    private Vec2 rallyPoint;
    private int constructionRemaining;
    private boolean constructed;

    public Building(EntityId id, Player owner, BuildingType type, GridPos origin, long spawnTick) {
        super(id, owner, footprintCenter(type, origin), new Health(type.maxHealth()), spawnTick);
        this.type = Objects.requireNonNull(type, "type");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.inventory = new Inventory(
                "storage of " + id,
                type.storageSlots(),
                Inventory.UNLIMITED,
                item -> item.hasAnyTag(type.storageTags()));
        this.rallyPoint = footprintCenter(type, origin).plus(type.sizeX() / 2f + 1.5f, 0f);
        this.constructionRemaining = type.buildTimeTicks();
        this.constructed = false;
    }

    private static Vec2 footprintCenter(BuildingType type, GridPos origin) {
        return Vec2.of(origin.x() + type.sizeX() / 2f, origin.y() + type.sizeY() / 2f);
    }

    public BuildingType type() {
        return type;
    }

    /** Tile of the minimum corner of the footprint. */
    public GridPos origin() {
        return origin;
    }

    /** Storage and production buffer of this building. */
    public Inventory inventory() {
        return inventory;
    }

    /** Where freshly produced units are sent by default. */
    public Vec2 rallyPoint() {
        return rallyPoint;
    }

    public void setRallyPoint(Vec2 rallyPoint) {
        this.rallyPoint = Objects.requireNonNull(rallyPoint, "rallyPoint");
    }

    public boolean isWarehouse() {
        return type.isWarehouse();
    }

    /** Flat damage reduction of this building, read from its type; zero unless the content set it. */
    public int armour() {
        return type.armour();
    }

    public boolean isConstructed() {
        return constructed;
    }

    public int constructionRemaining() {
        return constructionRemaining;
    }

    /** Construction progress in {@code [0, 1]}. */
    public float constructionFraction() {
        return type.buildTimeTicks() <= 0 ? 1f : 1f - constructionRemaining / (float) type.buildTimeTicks();
    }

    /** Advances construction and returns true when the building became operational. */
    public boolean advanceConstruction(int ticks) {
        if (constructed) {
            return false;
        }
        constructionRemaining = Math.max(0, constructionRemaining - ticks);
        if (constructionRemaining == 0) {
            constructed = true;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ attached builders

    /**
     * Number of builders attached to this site.
     *
     * <p>Every attached builder counts, even one that is still walking to its standing tile; the
     * construction speed is derived from this count only, never from the order builders attached in.
     */
    public int builderCount() {
        return builders.size();
    }

    /**
     * The builders attached to this site, in entity id order.
     *
     * <p>The returned list is a snapshot, so a caller may detach builders while walking it. A finished
     * or destroyed site has no attached builders: the construction system and the builders themselves
     * both tear the attachment down on the tick the site is lost.
     */
    public List<Unit> builders() {
        List<Unit> attached = new ArrayList<>(builders.keySet());
        attached.sort(Comparator.comparing(Unit::id));
        return List.copyOf(attached);
    }

    /** Standing tiles already claimed by attached builders. */
    public Set<GridPos> builderStandTiles() {
        return Set.copyOf(builders.values());
    }

    /** The adjacent tile an attached builder walks to, or {@code null} when it is not attached. */
    public GridPos builderStandTile(Unit builder) {
        return builders.get(builder);
    }

    /**
     * Attaches a builder and the adjacent tile it should work from.
     *
     * <p>Attaching an already attached builder just moves it to the new standing tile. Nothing is
     * attached to a site that is already operational or already dead, which keeps a late join from
     * resurrecting an attachment to a building that no longer needs work.
     *
     * @return whether the builder is attached after this call
     */
    public boolean attachBuilder(Unit builder, GridPos standTile) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(standTile, "standTile");
        if (constructed || !isAlive()) {
            return false;
        }
        builders.put(builder, standTile);
        return true;
    }

    /** Detaches a builder; the builder's order calls this when it finishes, fails or is cancelled. */
    public boolean detachBuilder(Unit builder) {
        return builder != null && builders.remove(builder) != null;
    }

    /** Whether this builder is currently attached to the site. */
    public boolean hasBuilder(Unit builder) {
        return builder != null && builders.containsKey(builder);
    }

    /** Drops builders that died while working; returns how many were removed. */
    public int pruneDeadBuilders() {
        int before = builders.size();
        builders.keySet().removeIf(builder -> !builder.isAlive());
        return before - builders.size();
    }

    /** Makes the building operational immediately. */
    public void completeConstruction() {
        constructionRemaining = 0;
        constructed = true;
    }

    /** Puts the building back into the under-construction state. */
    public void restartConstruction() {
        constructionRemaining = type.buildTimeTicks();
        constructed = false;
    }

    public int sizeX() {
        return type.sizeX();
    }

    public int sizeY() {
        return type.sizeY();
    }

    /** Tile of the building's entry point, used by haulers as their delivery position. */
    public GridPos entranceTile() {
        return new GridPos(origin.x() + type.sizeX(), origin.y() + type.sizeY() / 2);
    }

    /** World position just outside the footprint, where units queue up to interact. */
    public Vec2 entrance() {
        return Vec2.centerOf(entranceTile());
    }

    @Override
    public ResourceKey typeKey() {
        return type.key();
    }

    @Override
    public String displayName() {
        return type.displayName();
    }

    @Override
    public String spriteSetId() {
        return type.spriteSetId();
    }

    @Override
    public float radius() {
        return type.radius();
    }

    @Override
    public TileRect occupiedTiles() {
        return TileRect.fromOrigin(origin, type.sizeX(), type.sizeY());
    }
}
