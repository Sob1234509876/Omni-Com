package io.github.sob1234509876.oc.rts;

import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.economy.Logistics;

import java.util.Objects;
import java.util.Optional;

/**
 * One participant of a match: who owns entities and which logistics network their warehouses form.
 *
 * <p>The logistics network is attached by the engine when it is built; it is the only place where
 * stored goods of this player can be found, so nothing in the engine reads resources from anywhere
 * else.
 *
 * <p><strong>The faction anchor.</strong> A player may publish the tile its faction started from - the
 * human's base-slot anchor, the world host's sample-scene anchor, or the deterministic spawn anchor a
 * bot's opening was searched around. It is presentation metadata, not simulation state: the engine never
 * reads it, and a player created by an embedding program may leave it empty. The host and a plugin set it
 * once the anchor is known, and the server snapshot forwards it as the player row's optional
 * {@code anchor} pair; see {@code WorldHost#snapshotJson()}. It deliberately does not change entity
 * ownership, AI decisions or path finding.
 */
public final class Player {

    private final PlayerId id;
    private final String name;
    private final int colorArgb;
    private final int team;
    private final boolean human;
    private Logistics logistics;
    private GridPos anchor;

    public Player(PlayerId id, String name, int colorArgb, int team, boolean human) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = name == null ? id.toString() : name;
        this.colorArgb = colorArgb;
        this.team = team;
        this.human = human;
    }

    public static Player of(int id, String name, int colorArgb) {
        return new Player(PlayerId.of(id), name, colorArgb, id, true);
    }

    public PlayerId id() {
        return id;
    }

    public String name() {
        return name;
    }

    /** Recommended colour for a renderer, {@code 0xAARRGGBB}. */
    public int colorArgb() {
        return colorArgb;
    }

    /** Players sharing a team do not attack each other. */
    public int team() {
        return team;
    }

    public boolean isHuman() {
        return human;
    }

    /**
     * The tile this player's faction started from, when its host or plugin knows one.
     *
     * <p>For the world host that is the spawn anchor it found for the sample scene, for a joining account
     * the deterministic base-slot anchor, and for a classic bot the deterministic anchor its opening base
     * search started from. A player created without an anchor answers empty, which the snapshot mirrors by
     * omitting the field.
     *
     * @return the faction anchor, or empty when nobody published one
     */
    public Optional<GridPos> anchor() {
        return Optional.ofNullable(anchor);
    }

    /**
     * Publishes this player's faction anchor; {@code null} clears it.
     *
     * <p>This is a one-time presentation fact, not a simulation input: the engine never moves a player's
     * entities because of it. The host and plugins set it at creation time so the browser can point at a
     * known starting position without a fog-of-war reveal.
     *
     * @param anchor absolute tile the faction started from, or {@code null} to clear
     */
    public void setAnchor(GridPos anchor) {
        this.anchor = anchor;
    }

    public boolean isAlliedWith(Player other) {
        return other != null && other.team == team;
    }

    /** The player's warehouses, i.e. the only place their items can be used from. */
    public Logistics logistics() {
        if (logistics == null) {
            throw new IllegalStateException("player " + id + " has no logistics network; "
                    + "build the engine with the logistics system enabled");
        }
        return logistics;
    }

    public boolean hasLogistics() {
        return logistics != null;
    }

    public void attachLogistics(Logistics logistics) {
        this.logistics = Objects.requireNonNull(logistics, "logistics");
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Player player && id.equals(player.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return name + "(" + id + ")";
    }
}
