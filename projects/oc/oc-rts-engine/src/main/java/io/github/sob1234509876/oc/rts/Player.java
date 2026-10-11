package io.github.sob1234509876.oc.rts;

import io.github.sob1234509876.oc.rts.economy.Logistics;

import java.util.Objects;

/**
 * One participant of a match: who owns entities and which logistics network their warehouses form.
 *
 * <p>The logistics network is attached by the engine when it is built; it is the only place where
 * stored goods of this player can be found, so nothing in the engine reads resources from anywhere
 * else.
 */
public final class Player {

    private final PlayerId id;
    private final String name;
    private final int colorArgb;
    private final int team;
    private final boolean human;
    private Logistics logistics;

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
