package io.github.sob1234509876.oc.server.classic;

import io.github.sob1234509876.oc.rts.Player;
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
import java.util.TreeSet;

/**
 * Keeps the world's gateway links in step with the {@linkplain ClassicContent#HELL_GATE hell gates} that
 * actually stand, so the buildable gate - not a fixed map feature - is the only passage between the two
 * worlds, and raises the hell side of every passage: a finished overworld gate gets a paired return gate
 * on the hell arrival pad, owned by the same player, so the trip is two way.
 *
 * <p>Every {@value #SYNC_INTERVAL_TICKS} ticks the hook collects the living <em>and finished</em> gates
 * per dimension, sorted by entity id. A gate under construction is no passage: its site must not open a
 * route before the builders are done. Per owner the {@code i}-th overworld gate pairs with the
 * {@code i}-th hell gate of the same owner in both directions, measured from each gate's resolved
 * entrance tile, so a traveller aims at, crosses on and lands on a tile a unit can actually stand on.
 * An overworld gate without a hell partner
 * gets one: the hook spawns a finished hell gate of the player at or near the hell pad
 * ({@link #HELL_PAD} - inside the origin guarantee, so the ground there is buildable), scanning outward
 * in rings when the pad itself is taken. Only when no space is found at all does the overworld gate fall
 * back to the plain pad as its arrival tile, and a hell gate whose opposite side has no partner (its
 * overworld sibling was destroyed) arrives on the overworld pad ({@link #OVERWORLD_PAD}); both fallback
 * pads are moved to the nearest walkable tile first, and a pad that is impassable for several tiles
 * around closes the fallback instead of aiming a traveller at lava. Links that the
 * current gates do not justify are removed: a destroyed gate closes its passage within one sync interval,
 * and a world without gates has
 * no route to hell at all, which is the expedition arc the content promises.
 *
 * <p>The pass is idempotent ({@link World#addGateway} deduplicates) and deterministic: the pairing reads
 * only entity ids, owners and dimensions, never a random source, and the spawn search walks fixed rings
 * in fixed order.
 */
public final class HellGates implements WorldTickHook {

    /** How often the hook reconciles the world's links with the standing gates. */
    public static final long SYNC_INTERVAL_TICKS = 10;

    /** Arrival pad inside hell where the return gate of an overworld gate is raised. */
    public static final GridPos HELL_PAD = new GridPos(8, 8);

    /** Arrival pad on the overworld for a hell gate whose opposite side has no partner. */
    public static final GridPos OVERWORLD_PAD = new GridPos(10, -10);

    /** Largest ring searched around the hell pad for room for the return gate, in tiles. */
    public static final int RETURN_GATE_SEARCH_RADIUS = 8;

    /**
     * Largest ring searched around the pad for a walkable fallback arrival tile.
     *
     * <p>The pad sits inside the origin guarantee on every seed this content has served so far, but the
     * guarantee is a blend, not a wall: a patch of raw low elevation inside it can classify as lava. A
     * fallback link that names an impassable tile would aim every hell-side traveller at a tile nobody
     * can stand on - the travel order would grind at the lava edge until it timed out - so the fallback
     * moves the arrival to the nearest walkable tile instead, and a pad that is lava for several tiles
     * around closes the fallback passage entirely rather than dropping a traveller into the sea.
     */
    public static final int FALLBACK_ARRIVAL_SEARCH_RADIUS = 4;

    /** Creates the hook; it is stateless. */
    public HellGates() {
    }

    @Override
    public void tick(World world, long tick) {
        if (tick % SYNC_INTERVAL_TICKS != 0L) {
            return;
        }
        List<Building> overworldGates = gates(world, DimensionId.overworld());
        List<Building> hellGates = gates(world, hell());
        spawnReturnGates(world, overworldGates, hellGates);
        Set<Gateway> desired = desiredLinks(world, overworldGates, gates(world, hell()));
        for (Gateway link : desired) {
            world.addGateway(link);
        }
        for (Gateway link : world.gateways()) {
            if (!desired.contains(link)) {
                world.removeGateway(link);
            }
        }
    }

    /** The hell dimension of the classic content. */
    private static DimensionId hell() {
        return DimensionId.of(ClassicContent.HELL);
    }

    /**
     * Living, finished gates in one dimension, in entity id order.
     *
     * <p>Finished is the operative word: a construction site carries the gateway tag but must not open a
     * passage, or a traveller could step through a half raised gate - and a return gate could never be
     * raised from it either.
     */
    private static List<Building> gates(World world, DimensionId dimension) {
        List<Building> gates = new ArrayList<>();
        for (Building building : world.buildings(dimension)) {
            if (building.isAlive() && building.isConstructed()
                    && building.type().hasTag(ClassicContent.GATEWAY_TAG)) {
                gates.add(building);
            }
        }
        gates.sort((left, right) -> Long.compare(left.id().value(), right.id().value()));
        return gates;
    }

    // ------------------------------------------------------------------ return gates

    /**
     * Spawns the hell side of every overworld gate that has no hell partner of its owner yet.
     *
     * <p>The return gate is a real {@linkplain ClassicContent#HELL_GATE gate building} the player owns,
     * spawned finished: the paired passage is the point, and making the traveller build the far side by
     * hand would strand anybody without the materials. The site stands at the hell pad when it is free
     * and otherwise on the nearest ring tile around it that fits the footprint, so pads and real gates
     * agree on where a passage opens. The search is bounded; when hell has no room at all the overworld
     * gate keeps the plain pad as its arrival tile instead of failing the sync.
     */
    private static void spawnReturnGates(World world, List<Building> overworldGates, List<Building> hellGates) {
        BuildingType gateType = world.content().building(world.content().key(ClassicContent.HELL_GATE));
        for (Player owner : ownersOf(overworldGates)) {
            List<Building> ownOverworld = gatesOf(overworldGates, owner);
            List<Building> ownHell = gatesOf(hellGates, owner);
            for (int i = ownHell.size(); i < ownOverworld.size(); i++) {
                GridPos origin = returnGateOrigin(world, gateType);
                if (origin == null) {
                    return;
                }
                world.spawnBuilding(hell(), gateType, owner, origin);
            }
        }
    }

    /**
     * The first origin around the hell pad where the return gate fits, searched in fixed square rings.
     *
     * @return the origin, or {@code null} when no ring up to {@value #RETURN_GATE_SEARCH_RADIUS} has room
     */
    private static GridPos returnGateOrigin(World world, BuildingType gateType) {
        for (int ring = 0; ring <= RETURN_GATE_SEARCH_RADIUS; ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) {
                        continue;
                    }
                    GridPos origin = new GridPos(HELL_PAD.x() + dx, HELL_PAD.y() + dy);
                    if (world.canPlace(hell(), gateType, origin) && entranceFree(world, gateType, origin)) {
                        return origin;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Whether the return gate's preferred entrance tile is walkable ground.
     *
     * <p>The gate's links measure from the resolved entrance, so a site whose front is lava or rock
     * would open a passage nobody can stand on; such an origin is skipped for a usable one nearby.
     */
    private static boolean entranceFree(World world, BuildingType gateType, GridPos origin) {
        GridPos entrance = new GridPos(origin.x() + gateType.sizeX() / 2, origin.y() + gateType.sizeY());
        return world.dimension(hell()).map().isPassable(entrance);
    }

    // ------------------------------------------------------------------ links

    /**
     * The links the current gates justify, in both directions.
     *
     * <p>Owners are paired separately, so two players each with one gate get two independent passages
     * instead of a mixed pairing. An overworld gate whose return gate could not be raised arrives on the
     * plain hell pad, and a hell gate without an overworld partner arrives on the overworld pad; both
     * fallbacks keep the worlds reachable while the real gate work catches up.
     */
    private static Set<Gateway> desiredLinks(World world, List<Building> overworldGates, List<Building> hellGates) {
        Set<Gateway> desired = new LinkedHashSet<>();
        // The plain pads of the two fallbacks have to be standable: a link whose arrival (or whose
        // hell-side gate tile) is lava aims travellers at a tile nobody can stand on. One walkable tile
        // near the pad serves both fallback directions; none closes the fallback instead of lying.
        GridPos hellArrival = walkableNear(world, HELL_PAD, FALLBACK_ARRIVAL_SEARCH_RADIUS);
        GridPos overworldArrival = walkableNear(world, OVERWORLD_PAD, FALLBACK_ARRIVAL_SEARCH_RADIUS);
        for (Player owner : ownersOf(overworldGates)) {
            List<Building> ownOverworld = gatesOf(overworldGates, owner);
            List<Building> ownHell = gatesOf(hellGates, owner);
            int pairs = Math.min(ownOverworld.size(), ownHell.size());
            for (int i = 0; i < ownOverworld.size(); i++) {
                Building overworldGate = ownOverworld.get(i);
                if (i < pairs) {
                    Building hellGate = ownHell.get(i);
                    link(desired, DimensionId.overworld(), entranceOf(world, overworldGate),
                            hell(), entranceOf(world, hellGate));
                    link(desired, hell(), entranceOf(world, hellGate),
                            DimensionId.overworld(), entranceOf(world, overworldGate));
                } else if (hellArrival != null) {
                    link(desired, DimensionId.overworld(), entranceOf(world, overworldGate),
                            hell(), hellArrival);
                    link(desired, hell(), hellArrival,
                            DimensionId.overworld(), entranceOf(world, overworldGate));
                }
            }
            for (int i = pairs; i < ownHell.size(); i++) {
                Building hellGate = ownHell.get(i);
                if (overworldArrival != null) {
                    link(desired, hell(), entranceOf(world, hellGate), DimensionId.overworld(), overworldArrival);
                    link(desired, DimensionId.overworld(), overworldArrival, hell(), entranceOf(world, hellGate));
                }
            }
        }
        return desired;
    }

    /**
     * The nearest walkable tile to the pad within the given radius, searched in fixed square rings; the
     * pad itself when it is walkable, {@code null} when the whole search area is impassable.
     */
    private static GridPos walkableNear(World world, GridPos pad, int radius) {
        DimensionId hell = hell();
        if (world.dimension(hell).map().isPassable(pad)) {
            return pad;
        }
        for (int ring = 1; ring <= radius; ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) {
                        continue;
                    }
                    GridPos candidate = new GridPos(pad.x() + dx, pad.y() + dy);
                    if (world.dimension(hell).map().isPassable(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    /**
     * The resolved entrance of a gate: the tile its passage is measured from.
     *
     * <p>A unit crosses while standing within the crossing radius of this tile, and lands on the paired
     * gate's entrance on the other side, so the trip never drops a traveller inside a footprint and the
     * client's gateway view target names a tile a unit can actually stand on.
     */
    private static GridPos entranceOf(World world, Building gate) {
        return world.dimensionOf(gate).entranceTile(gate);
    }

    /** Owners of the given gates in ascending id order, so every pairing pass is deterministic. */
    private static Set<Player> ownersOf(List<Building> gates) {
        Set<Player> owners = new TreeSet<>((left, right) -> Integer.compare(left.id().value(), right.id().value()));
        for (Building gate : gates) {
            owners.add(gate.owner());
        }
        return owners;
    }

    /** The gates of the list that belong to one owner, keeping the entity id order of the input. */
    private static List<Building> gatesOf(List<Building> gates, Player owner) {
        List<Building> own = new ArrayList<>();
        for (Building gate : gates) {
            if (gate.owner().equals(owner)) {
                own.add(gate);
            }
        }
        return own;
    }

    /** Registers the two directions of one pairing. */
    private static void link(Set<Gateway> desired, DimensionId from, GridPos at, DimensionId to, GridPos arrival) {
        desired.add(Gateway.of(from, at, to, arrival));
    }
}
