package io.github.sob1234509876.oc.rts.dimension;

import io.github.sob1234509876.oc.rts.core.GridPos;

import java.util.Objects;

/**
 * A one way door from one dimension to another: a portal, an elevator shaft, a rocket pad.
 *
 * <p>A gateway is plain map data, not content, so a scenario can wire the Moon to Mars without touching the
 * content registry. It names the tile a unit has to reach, the dimension it arrives in and the tile it
 * arrives on; {@link io.github.sob1234509876.oc.rts.World#route(DimensionId, DimensionId)} chains gateways
 * into a path, so travelling through three dimensions needs no special case anywhere.
 *
 * <p>Gateways are directional on purpose: a one way drop pod and a two way elevator want different rules, and
 * a return trip is simply another gateway in the other direction.
 *
 * @param from   dimension the unit starts in
 * @param at     tile in {@code from} a unit has to stand on to be transferred
 * @param to     dimension the unit arrives in
 * @param arrival tile in {@code to} the unit is placed on
 * @param name   short label for logs and debug output, for example {@code earth-to-moon}
 */
public record Gateway(DimensionId from, GridPos at, DimensionId to, GridPos arrival, String name) {

    public Gateway {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(arrival, "arrival");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("gateway name must not be blank");
        }
        if (from.equals(to)) {
            throw new IllegalArgumentException("a gateway must lead to another dimension, was " + from);
        }
    }

    public static Gateway of(DimensionId from, GridPos at, DimensionId to, GridPos arrival) {
        return new Gateway(from, at, to, arrival, from.key() + "-to-" + to.key());
    }

    /** The gateway in the opposite direction, so a two way link is two records. */
    public Gateway reversed() {
        return new Gateway(to, arrival, from, at, to.key() + "-to-" + from.key());
    }

    @Override
    public String toString() {
        return "Gateway{" + name + ": " + from + at + " -> " + to + arrival + "}";
    }
}
