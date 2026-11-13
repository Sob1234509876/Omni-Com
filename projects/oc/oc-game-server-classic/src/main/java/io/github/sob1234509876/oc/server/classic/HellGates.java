package io.github.sob1234509876.oc.server.classic;

import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.content.BuildingType;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.dimension.Gateway;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.server.world.WorldTickHook;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps the world's gateway links in step with the {@linkplain ClassicContent#HELL_GATE hell gates} that
 * actually stand, so the buildable gate - not a fixed map feature - is the only passage between the two
 * worlds.
 *
 * <p>Every {@value #SYNC_INTERVAL_TICKS} ticks the hook collects the living gates per dimension, sorted
 * by entity id, and computes the desired link set: the {@code i}-th overworld gate pairs with the
 * {@code i}-th hell gate in both directions, and a gate whose opposite side has no partner falls back to
 * the fixed arrival pad of the other world ({@value #HELL_FALLBACK_X},{@value #HELL_FALLBACK_Y} in hell,
 * {@value #OVERWORLD_FALLBACK_X},{@value #OVERWORLD_FALLBACK_Y} on the overworld - both inside the origin
 * guarantee, so the arrival tile is walkable). Links that the current gates do not justify are removed:
 * a destroyed gate closes its passage within one sync interval, and a world without gates has no route
 * to hell at all, which is the expedition arc the content promises.
 *
 * <p>The pass is idempotent ({@link World#addGateway} deduplicates) and deterministic: the pairing reads
 * only entity ids and dimensions, never a random source.
 */
public final class HellGates implements WorldTickHook {

    /** How often the hook reconciles the world's links with the standing gates. */
    public static final long SYNC_INTERVAL_TICKS = 10;

    /** Arrival pad inside hell for an overworld gate whose opposite side has no partner yet. */
    public static final GridPos HELL_FALLBACK = new GridPos(8, 8);

    /** Arrival pad on the overworld for a hell gate whose opposite side has no partner yet. */
    public static final GridPos OVERWORLD_FALLBACK = new GridPos(10, -10);

    /** Creates the hook; it is stateless. */
    public HellGates() {
    }

    @Override
    public void tick(World world, long tick) {
        if (tick % SYNC_INTERVAL_TICKS != 0L) {
            return;
        }
        List<Building> overworldGates = gates(world, DimensionId.overworld());
        List<Building> hellGates = gates(world, DimensionId.of(ClassicContent.HELL));
        Set<Gateway> desired = desiredLinks(overworldGates, hellGates);
        for (Gateway link : desired) {
            world.addGateway(link);
        }
        for (Gateway link : world.gateways()) {
            if (!desired.contains(link)) {
                world.removeGateway(link);
            }
        }
    }

    /** Living, finished gates in one dimension, in entity id order. */
    private static List<Building> gates(World world, DimensionId dimension) {
        List<Building> gates = new ArrayList<>();
        for (Building building : world.buildings(dimension)) {
            if (building.isAlive() && building.type().hasTag(ClassicContent.GATEWAY_TAG)) {
                gates.add(building);
            }
        }
        gates.sort((left, right) -> Long.compare(left.id().value(), right.id().value()));
        return gates;
    }

    /**
     * The links the current gates justify: index-wise pairing across the two dimensions, plus the fixed
     * fallback pad for a gate whose opposite side has no partner. Every link is stored in both
     * directions, because the engine treats a gateway as one way.
     */
    private static Set<Gateway> desiredLinks(List<Building> overworldGates, List<Building> hellGates) {
        Set<Gateway> desired = new LinkedHashSet<>();
        int pairs = Math.min(overworldGates.size(), hellGates.size());
        for (int i = 0; i < overworldGates.size(); i++) {
            Building overworldGate = overworldGates.get(i);
            if (i < pairs) {
                Building hellGate = hellGates.get(i);
                link(desired, DimensionId.overworld(), overworldGate.origin(),
                        DimensionId.of(ClassicContent.HELL), hellGate.origin());
                link(desired, DimensionId.of(ClassicContent.HELL), hellGate.origin(),
                        DimensionId.overworld(), overworldGate.origin());
            } else {
                link(desired, DimensionId.overworld(), overworldGate.origin(),
                        DimensionId.of(ClassicContent.HELL), HELL_FALLBACK);
                link(desired, DimensionId.of(ClassicContent.HELL), HELL_FALLBACK,
                        DimensionId.overworld(), overworldGate.origin());
            }
        }
        for (int i = pairs; i < hellGates.size(); i++) {
            Building hellGate = hellGates.get(i);
            link(desired, DimensionId.of(ClassicContent.HELL), hellGate.origin(),
                    DimensionId.overworld(), OVERWORLD_FALLBACK);
            link(desired, DimensionId.overworld(), OVERWORLD_FALLBACK,
                    DimensionId.of(ClassicContent.HELL), hellGate.origin());
        }
        return desired;
    }

    /** Registers the two directions of one pairing. */
    private static void link(Set<Gateway> desired, DimensionId from, GridPos at, DimensionId to, GridPos arrival) {
        desired.add(Gateway.of(from, at, to, arrival));
    }
}
