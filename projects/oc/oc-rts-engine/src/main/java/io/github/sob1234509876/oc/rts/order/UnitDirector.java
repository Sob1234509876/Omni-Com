package io.github.sob1234509876.oc.rts.order;

import io.github.sob1234509876.oc.rts.content.BuildingType;
import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.formation.FormationShape;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * The single entry point for giving units something to do.
 *
 * <p>Everything that wants a unit to move - player input, production rally points, hauling - goes
 * through this interface, so formation handling, path requests and steering stay in one implementation
 * and no caller needs to know about them.
 */
public interface UnitDirector {

    /** Sends the unit to the tile, replacing whatever it was doing. */
    void move(Unit unit, GridPos destination);

    /** Sends the unit to an exact world position, replacing whatever it was doing. */
    void move(Unit unit, Vec2 destination);

    /**
     * Sends a group to the tile and arranges it in the default formation.
     *
     * @return the identifier of the formation, or {@code 0} when no unit could take the order; pass it
     *         to {@code CommandService.updateFormationAnchor(...)} to steer the group while it marches
     */
    int moveGroup(Collection<Unit> units, GridPos destination);

    /**
     * Sends a group to the tile, spreading the units over the slots of the given formation. The unit
     * closest to a slot gets it, so the group does not walk through itself when it sets off.
     *
     * @return the identifier of the formation, or {@code 0} when no unit could take the order
     */
    int moveGroup(Collection<Unit> units, GridPos destination, FormationShape shape);

    /** Cancels the current order of the unit and tells it to stand still. */
    void stop(Unit unit);

    /**
     * Orders the unit to attack one specific entity and keep chasing it until it dies or the order is
     * replaced.
     *
     * <p>The order walks the unit into range and holds it there; the damage is dealt by
     * {@link io.github.sob1234509876.oc.rts.combat.CombatSystem} every time the unit's cooldown elapses.
     * The target is resolved by id on every tick, so a target that was killed or removed ends the order
     * instead of leaving the unit chasing nothing.
     */
    void attack(Unit unit, EntityId target);

    /**
     * Sends the unit towards the position, engaging any enemy that comes within its attack range on the
     * way, then continuing to the position.
     */
    void attackMove(Unit unit, Vec2 destination);

    /** Convenience for {@link #attackMove(Unit, Vec2)} with the centre of the tile. */
    void attackMove(Unit unit, GridPos destination);

    /**
     * Attack-moves onto one entity, unit or building: the unit walks into reach of the target, attacks
     * it until it dies, and fails when an id that never resolves cannot be found.
     *
     * <p>This is the entity form of {@link #attackMove(Unit, Vec2)}; the target id may name a building
     * exactly like {@link #attack(Unit, EntityId)}. A building is reached at its footprint edge, so the
     * final approach does not have to stand inside the building.
     */
    void attackMove(Unit unit, EntityId target);

    /**
     * Places a building site on the map and orders the unit to build it.
     *
     * <p>The site is created unfinished in the unit's own dimension - coordinates are local to a
     * dimension, so building on the map the builder stands on is the only meaningful placement - and the
     * unit is given a {@link BuildOrder} that keeps it at the site until the building is operational.
     * {@link io.github.sob1234509876.oc.rts.production.ConstructionSystem} advances the site once per
     * tick; this method is the part that decides <em>where</em> a site appears and <em>who</em> builds it.
     *
     * <p><strong>The site is paid for when it is placed.</strong> The type's
     * {@link BuildingType#cost() cost} is reserved in the owner's warehouse network and committed at the
     * moment the site appears, synchronously with the placement, so it can never be spent twice by a
     * production job between reservation and charge. A type whose cost is empty is still free. When the
     * owner cannot cover the cost, or the cost cannot be reserved, nothing is placed and nothing is
     * charged; the reservation is released if the placement itself fails.
     *
     * <p>Nothing is placed when the unit cannot be commanded, the site is out of the builder's reach, or
     * the type does not fit at the origin (the same check
     * {@link io.github.sob1234509876.oc.rts.World#canPlace(DimensionId, BuildingType, GridPos)} performs),
     * so a caller can try candidate tiles in a fixed order and take the first site it gets. Reach means
     * that the site's footprint is adjacent to a tile the builder can walk to and that a deterministic
     * path from the builder's tile to that approach ring is at most
     * {@link CommandService#MAX_BUILD_PATH_TILES} tiles long; the refusal names {@code out of reach}.
     *
     * @param unit   the builder, normally a unit with the content's builder role
     * @param type   the building to raise
     * @param origin tile of the minimum corner of the footprint
     * @return the site that was placed, or empty when nothing was placed
     */
    default Optional<Building> build(Unit unit, BuildingType type, GridPos origin) {
        BuildResult result = tryBuild(unit, type, origin);
        return result.placed() ? Optional.of(result.site()) : Optional.empty();
    }

    /**
     * Orders the builder to join an existing, unfinished site and work on it.
     *
     * <p>This is the cooperative form of {@link #build(Unit, BuildingType, GridPos)}: it places nothing
     * and charges nothing. The builder claims its own free adjacent tile, walks there and adds to the
     * site's construction speed; several builders can be attached to one site at once, and the site
     * completes for all of them in the same tick. A finished site, a dead site, another player's site or
     * a site in another dimension is refused rather than silently accepted.
     *
     * @param unit the builder, normally a unit with the content's builder role
     * @param site the existing site to join
     * @return the site that was joined, or empty when the order was refused
     */
    default Optional<Building> build(Unit unit, Building site) {
        BuildResult result = tryBuild(unit, site);
        return result.placed() ? Optional.of(result.site()) : Optional.empty();
    }

    /**
     * Like {@link #build(Unit, BuildingType, GridPos)}, but says why nothing was placed.
     *
     * <p>This is the form a command channel uses: the answer to a player has to be a readable
     * {@code ERR <reason>} rather than a silent "no", and only the director knows whether the builder
     * could not be commanded, the tile was occupied, or the warehouse could not pay.
     *
     * @param unit   the builder
     * @param type   the building to raise
     * @param origin tile of the minimum corner of the footprint
     * @return the placed site, or the refusal reason; never {@code null}
     */
    BuildResult tryBuild(Unit unit, BuildingType type, GridPos origin);

    /**
     * Like {@link #build(Unit, Building)}, but says why the builder could not join.
     *
     * <p>Joining is free: the site's cost was charged when it was placed, and a second builder must
     * never charge it again. The refusal reasons are readable for a player - the site already finished,
     * the site was destroyed, the site belongs to another player, the site is in another dimension, or
     * the site is out of the builder's reach.
     *
     * <p>{@link BuildResult#site()} is the existing site on success. It was not placed by this call,
     * which is why the result is documented as "a site to work on" rather than "a site that appeared".
     *
     * @param unit the builder
     * @param site the existing, unfinished site to join
     * @return the joined site, or the refusal reason; never {@code null}
     */
    BuildResult tryBuild(Unit unit, Building site);

    /**
     * Upgrades one finished building into the type its content names, paying the source type's
     * {@link BuildingType#upgradeCost() upgrade cost}.
     *
     * <p>This is the building counterpart of {@link #tryBuild(Unit, BuildingType, GridPos)}: it needs no
     * unit, because the owner's warehouses and the building itself are the whole transaction. The rules are
     * deliberately strict and every refusal is a readable sentence:
     *
     * <ul>
     *   <li><strong>Finished only.</strong> The building has to be
     *       {@link Building#isConstructed() constructed}; its health does not have to be full. There is no
     *       repair mechanic in the engine, and an upgrade preserves the health fraction, so demanding full
     *       health would add a hidden repair requirement and let a damaged building become neither repaired
     *       nor upgraded. A half-health building stays half-health, which is also the property the scenario
     *       asserts.</li>
     *   <li><strong>Real target.</strong> {@link BuildingType#upgradeTo()} has to name a building type that
     *       exists in the same {@link io.github.sob1234509876.oc.rts.content.ContentRegistry}. A missing
     *       target, a self reference and a target with a different footprint are refused before anything is
     *       charged.</li>
     *   <li><strong>Clear site.</strong> The replacement has to fit at the old origin with the old
     *       footprint: the terrain stays buildable and no <em>other</em> building may overlap. The old
     *       building itself is ignored for this check because it is the thing being replaced.</li>
     *   <li><strong>Paid once.</strong> The cost is reserved through the owner's
     *       {@link io.github.sob1234509876.oc.rts.economy.Logistics} network, the same path a placement
     *       uses, and committed only after the replacement exists. A refusal charges nothing and leaves no
     *       reservation.</li>
     *   <li><strong>Replaced, not mutated.</strong> The old building is destroyed and a finished building of
     *       the new type is placed at the same origin, owned by the same player and carrying the same
     *       health fraction. Its id is new, so anything that named the old id is cleaned up: production
     *       jobs on the old building are cancelled (the destroy event abandons its queue), delivery jobs
     *       targeting it fail, and the id is cleared from every unit's combat target. The old id leaves the
     *       spatial index with the destroyed building.</li>
     * </ul>
     *
     * @param building the finished building to replace; may be {@code null}
     * @return the replacement, or the refusal reason; never {@code null}
     */
    UpgradeResult upgradeBuilding(Building building);

    /**
     * Outcome of one upgrade request: either the replacement building or a refusal reason.
     *
     * <p>At most one field is set. The replacement is a new entity with a new id; the old building is
     * already dead when this is returned. The refusal is written for a player and names the reason the
     * upgrade did not happen, including the exact cost when the warehouses cannot cover it.
     *
     * @param replacement the finished building of the new type, or {@code null} when nothing changed
     * @param refusal     why nothing changed, or {@code null} when the upgrade succeeded
     */
    record UpgradeResult(Building replacement, String refusal) {

        public UpgradeResult {
            if ((replacement == null) == (refusal == null)) {
                throw new IllegalArgumentException("an upgrade result is either a replacement or a refusal");
            }
        }

        /** A successful upgrade: the old building is gone and the replacement stands in its place. */
        public static UpgradeResult upgraded(Building replacement) {
            return new UpgradeResult(Objects.requireNonNull(replacement, "replacement"), null);
        }

        /** Nothing changed, for the given reason. */
        public static UpgradeResult refused(String reason) {
            return new UpgradeResult(null, Objects.requireNonNull(reason, "reason"));
        }

        /** Whether the building was replaced. */
        public boolean upgraded() {
            return replacement != null;
        }
    }

    /**
     * Outcome of one build request: either a site to work on or a refusal reason.
     *
     * <p>At most one of the two fields is set. The reason is written for a player - it names the
     * building type and, for an unaffordable type, the exact cost - and never contains an exception
     * stack trace. For a join request ({@link #tryBuild(Unit, Building)}) the site is the existing one
     * that was joined, not a newly placed site; the placement factories read that as "success", and the
     * call charged nothing.
     *
     * @param site    the placed or joined site, or {@code null} when nothing happened
     * @param refusal why nothing happened, or {@code null} on success
     */
    record BuildResult(Building site, String refusal) {

        public BuildResult {
            if ((site == null) == (refusal == null)) {
                throw new IllegalArgumentException("a build result is either a site or a refusal");
            }
        }

        /** A site that was placed and charged. */
        public static BuildResult placed(Building site) {
            return new BuildResult(Objects.requireNonNull(site, "site"), null);
        }

        /** Nothing placed, for the given reason. */
        public static BuildResult refused(String reason) {
            return new BuildResult(null, Objects.requireNonNull(reason, "reason"));
        }

        /** Whether a site was placed. */
        public boolean placed() {
            return site != null;
        }
    }

    /**
     * Sends the unit to another dimension, walking it through the gateways of the world.
     *
     * <p>The unit is ordered to the first gateway on the route, and each arrival hands it to the next hop
     * until it stands in {@code target}. The route is {@link io.github.sob1234509876.oc.rts.World#route}
     * and nothing happens when the target is unreachable, so a scenario that forgot to place a portal sees
     * the order fail rather than a unit teleporting across space.
     */
    void travel(Unit unit, DimensionId target);

    /** Cancels the current order of every given unit. */
    void stop(Collection<Unit> units);

    /** Like {@link #stop(Unit)} but the unit keeps its position and watches for enemies. */
    void hold(Unit unit);
}
