package io.github.sob1234509876.oc.rts.visual;

import io.github.sob1234509876.oc.rts.PlayerId;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.ResourceKey;
import io.github.sob1234509876.oc.rts.content.UnitRole;
import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Vec2;

/**
 * Immutable description of one entity at the moment a {@link WorldSnapshot} was taken.
 *
 * <p>The snapshot is what a renderer is allowed to see. A GUI thread must never touch the live
 * {@code World} - the simulation keeps mutating it, and reading a half-updated entity would show units
 * at positions they never occupied. Copying the handful of values a drawing pass needs into a record is
 * cheap and makes the presentation side trivially thread safe, which is why this type holds plain
 * values instead of a reference to the entity.
 *
 * <p>Everything a renderer needs is already resolved here: {@link #spriteFrame()} is the exact frame to
 * blit (or {@code null} when the entity has no animation), {@link #clipId()} and {@link #frameIndex()}
 * name it for debugging and for renderers that keep their own frame tables, and
 * {@link #healthFraction()} and {@link #selected()} are ready for health bars and selection rings.
 *
 * @param id              identity of the entity
 * @param typeKey         definition key of the entity, for example {@code oc:soldier}
 * @param displayName     human readable name
 * @param owner           player owning the entity
 * @param ownerColorArgb  recommended colour of the owner, {@code 0xAARRGGBB}
 * @param position        world position of the entity center, in tiles
 * @param anchorTile      tile the entity is drawn at: the tile a unit stands on, the origin of a
 *                        building footprint
 * @param radius          collision radius in tiles, used for selection and health bars
 * @param healthFraction  remaining health in {@code [0, 1]}
 * @param selected        whether the owner selected this entity; purely presentational
 * @param unit            whether this entity is a unit rather than a building
 * @param role            role of the unit, {@code null} for buildings
 * @param spriteSetId     animation set this entity uses, may be {@code null}
 * @param clipId          animation currently playing, may be {@code null}
 * @param frameIndex      index of the current frame inside its sheet, {@code 0} when unknown
 * @param facing          facing in radians
 * @param spriteFrame     frame to draw, {@code null} when the entity has no animation
 * @param moving          whether the entity currently has somewhere to go
 * @param currentOrderName name of the active order, may be {@code null}
 * @param orderCount      number of orders the entity has, active one included
 * @param carrying        items the entity carries, never {@code null}
 */
public record EntitySnapshot(
        EntityId id,
        ResourceKey typeKey,
        String displayName,
        PlayerId owner,
        int ownerColorArgb,
        Vec2 position,
        GridPos anchorTile,
        float radius,
        float healthFraction,
        boolean selected,
        boolean unit,
        UnitRole role,
        String spriteSetId,
        String clipId,
        int frameIndex,
        float facing,
        SpriteFrame spriteFrame,
        boolean moving,
        String currentOrderName,
        int orderCount,
        ItemBundle carrying) {

    public EntitySnapshot {
        if (id == null) {
            throw new IllegalArgumentException("entity snapshot needs an id");
        }
        if (typeKey == null) {
            throw new IllegalArgumentException("entity snapshot " + id + " needs a type key");
        }
        if (position == null) {
            throw new IllegalArgumentException("entity snapshot " + id + " needs a position");
        }
        if (anchorTile == null) {
            throw new IllegalArgumentException("entity snapshot " + id + " needs an anchor tile");
        }
        displayName = displayName == null ? typeKey.path() : displayName;
        carrying = carrying == null ? ItemBundle.EMPTY : carrying;
    }

    /** Whether the entity has a frame to draw right now. */
    public boolean hasSprite() {
        return spriteFrame != null;
    }

    /**
     * Whether the captured entity had run out of health.
     *
     * <p>In practice this reads {@code false} for every snapshot {@code SnapshotFactory} produces: it
     * captures living entities only, because a corpse would show up at a stale position and inflate the
     * "on screen" counts. A renderer that wants to show a death pose has to keep its own short lived
     * effect list and drive it from {@code Events.EntityDestroyed}.
     */
    public boolean isDead() {
        return healthFraction <= 0f;
    }

    /** Whether the entity carries items, which is what makes a hauler look loaded. */
    public boolean isCarrying() {
        return !carrying.isEmpty();
    }

    /** Role of the unit, or {@code null} when this snapshot describes a building. */
    public UnitRole roleOrNull() {
        return role;
    }

    @Override
    public String toString() {
        return "EntitySnapshot{" + id + " " + typeKey + " at " + position
                + (unit ? " unit" : " building")
                + ", clip=" + clipId + "/" + frameIndex + ", hp=" + String.format("%.2f", healthFraction) + "}";
    }
}
