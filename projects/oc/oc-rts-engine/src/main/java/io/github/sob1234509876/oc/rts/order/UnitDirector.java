package io.github.sob1234509876.oc.rts.order;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.content.BuildingType;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.ItemType;
import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.formation.FormationShape;
import io.github.sob1234509876.oc.rts.logistics.DeliveryJob;

import java.util.Collection;
import java.util.List;
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

    /**
     * Maximum number of points one move order may visit, including the final destination.
     *
     * <p>The wire form {@code ORDER MOVE <id> <x> <y> [<x> <y> ...]} accepts one to eight points; the
     * engine refuses an empty or longer route rather than truncating it, because silently dropping the
     * tail of a route would be a different order than the player issued.
     */
    int MAX_MOVE_POINTS = 8;

    /** Sends the unit to the tile, replacing whatever it was doing. */
    void move(Unit unit, GridPos destination);

    /** Sends the unit to an exact world position, replacing whatever it was doing. */
    void move(Unit unit, Vec2 destination);

    /**
     * Sends the unit through a route of one to {@value #MAX_MOVE_POINTS} tiles, in order.
     *
     * <p>A one-point route is exactly {@link #move(Unit, GridPos)}. With several points, each leg sets
     * the next goal on the unit's motion, so the path service plans every leg with the same terrain,
     * avoidance and reach rules as a plain move. The order completes when the last point is reached, and
     * a leg that cannot be reached fails the whole order with a reason that names that leg (see
     * {@link MoveOrder#failureReason()}).
     *
     * @param unit  the unit to move
     * @param route one to {@value #MAX_MOVE_POINTS} tile destinations, in visit order
     * @return the issued order, or the refusal reason; never {@code null}
     */
    MoveResult moveRoute(Unit unit, List<GridPos> route);

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
     * Like {@link #tryBuild(Unit, BuildingType, GridPos)}, but appends the {@link BuildOrder} to the
     * unit's queue instead of replacing the orders it already has.
     *
     * <p>This is the engine half of Shift-click queueing: three queued build tasks are three sites and
     * three orders in one engineer's queue, executed in the order they were queued. Placement, reach,
     * occupancy and the placement charge are exactly the same transaction as a plain build - each site is
     * placed and paid for when the command is issued, not when the engineer reaches it - so a refusal
     * leaves the queue untouched and a later order in the queue is never affected by an earlier one.
     *
     * <p>A failure of one queued order does not cancel the rest: {@link OrderQueue} starts the next
     * pending order on the following tick, whether the previous one completed, failed or was cancelled.
     * See {@link #tryQueueBuild(Unit, Building)} for the join form.
     *
     * @param unit   the builder
     * @param type   the building to raise
     * @param origin tile of the minimum corner of the footprint
     * @return the placed site, or the refusal reason; never {@code null}
     */
    BuildResult tryQueueBuild(Unit unit, BuildingType type, GridPos origin);

    /**
     * Like {@link #tryBuild(Unit, Building)}, but appends the join order to the unit's queue.
     *
     * <p>Joining is free and this method places nothing; it is the queueing form of the cooperative
     * build command. A finished site, a dead site, another player's site, another dimension and an
     * unreachable site are refused exactly like the plain form, and the existing queue is left intact.
     *
     * @param unit the builder
     * @param site the existing, unfinished site to join
     * @return the joined site, or the refusal reason; never {@code null}
     */
    BuildResult tryQueueBuild(Unit unit, Building site);

    /**
     * Orders an engineer to repair a finished, damaged building of the same player.
     *
     * <p>Repair is priced by {@link BuildingType#repairCostPerHealth()}: the full price of the remaining
     * repair is {@code missingHealth * perHealth}, and the command reserves that whole amount once to
     * prove the owner can cover it before any order is issued. The reservation is released immediately
     * (nothing is charged yet); the {@link RepairOrder} then reserves and commits the exact slice for each
     * tick's restored hit points, so a repair that is cancelled after one tick costs one tick, not the
     * whole job, and a repair that fails charges nothing for work it did not do.
     *
     * <p>Every refusal is free and readable: a unit that cannot be commanded, a building that is gone, a
     * building still under construction, a full-health building ({@code already at full health}), a type
     * whose content declares no repair price, a building of another player or another dimension, and an
     * owner whose warehouses cannot cover the full remaining repair.
     *
     * @param engineer the engineer to send, normally a unit with the content's builder role
     * @param building the finished, damaged building to repair
     * @return the repair order, or the refusal reason; never {@code null}
     */
    RepairResult tryRepair(Unit engineer, Building building);

    /**
     * Appends one order to the unit's queue instead of replacing what it is doing.
     *
     * <p>These are the queue forms the {@code BATCH} macro and the Shift-click build queue use: the
     * command is validated exactly like its replacing twin (same commandability, reach, ownership and
     * affordability checks), then the resulting order is pushed behind the ones already queued and runs
     * after them, one at a time. A refused queue form returns the same readable reason and leaves the
     * queue untouched, so one bad leg cannot cancel the orders after it.
     *
     * <p>Queueing is what makes {@code BATCH} a sequence rather than a race: the server applies the
     * sub-orders in the order they were given, and the unit executes them in that same order.
     *
     * @param unit        the unit to append to
     * @param destination the tile to walk to
     */
    void queueMove(Unit unit, GridPos destination);

    /** Queue form of {@link #moveRoute(Unit, List)}; same validation and the same {@link MoveResult}. */
    MoveResult queueMoveRoute(Unit unit, List<GridPos> route);

    /** Queue form of {@link #attack(Unit, EntityId)}. */
    void queueAttack(Unit unit, EntityId target);

    /** Queue form of {@link #attackMove(Unit, GridPos)}. */
    void queueAttackMove(Unit unit, GridPos destination);

    /** Queue form of {@link #tryRepair(Unit, Building)}. */
    RepairResult tryQueueRepair(Unit engineer, Building building);

    /**
     * Cancels one of the player's unfinished construction sites and refunds a documented fraction of
     * what was spent on it.
     *
     * <p>Only an unfinished site qualifies. A finished building is refused with a sentence that says so,
     * because reclaiming a completed building would be a demolition mechanic with different economics.
     * On success every attached builder is finished with {@link OrderState#FAILED} and a reason that says
     * the site was reclaimed, the site is destroyed, and half (rounded down) of the bundle recorded in
     * {@link Building#invested()} is deposited into the owner's warehouses at the site's position. The
     * command itself charges nothing: it only returns stock that was paid at placement. A building
     * spawned finished has an empty investment record, so it has nothing to refund.
     *
     * @param site the unfinished site to reclaim
     * @return the removed site and the refund actually deposited, or the refusal reason; never
     *         {@code null}
     */
    ReclaimResult tryReclaim(Building site);

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
     *       {@link Building#isConstructed() constructed}; its health does not have to be full. An upgrade
     *       preserves the health fraction, and repair is the separate {@link #tryRepair(Unit, Building)}
     *       command, so demanding full health here would add a hidden requirement and leave a damaged
     *       building unable to be either repaired or upgraded. A half-health building stays half-health,
     *       which is also the property the scenario asserts.</li>
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
     * Moves stock from one of the player's warehouses to another through the ordinary hauling path.
     *
     * <p>This is the engine half of the manual logistics command, and it is deliberately a <em>real
     * haul</em>, not a teleport: the goods are reserved in the source warehouse, a free hauler is
     * dispatched to pick them up and carry them to the target, and only the hauler's arrival puts them
     * into the target inventory. The returned {@link TransferResult#moved() moved} bundle is therefore
     * what was <em>dispatched</em>; the target's stock rises a few ticks later, exactly like a production
     * delivery. Nothing is created or destroyed on the way, and a haul that fails puts the goods back into
     * storage through {@link io.github.sob1234509876.oc.rts.logistics.HaulOrder}.
     *
     * <p><strong>Rules.</strong> Both buildings have to exist, be alive, be
     * {@linkplain Building#isConstructed() finished} warehouses of the same player, and stand in the same
     * dimension with a walkable route between their entrances. The item has to be one the source holds,
     * and the amount has to be positive; it is clamped to the smaller of the source's
     * <em>unreserved</em> stock ({@link io.github.sob1234509876.oc.rts.economy.WarehouseLogistics#freeAmount})
     * and the target's current room, so the dispatch always fits and the answer names the amount that
     * really moves. An amount of {@link Natural#BEYOND} is accepted as "as much as possible" for the same
     * reason: the clamp makes it finite.
     *
     * <p><strong>The hauler.</strong> A free hauler must exist at command time: an own unit that can haul,
     * stands in the same dimension, is idle and carries no other job. The nearest one wins, ties are
     * broken by entity id, and the search is the hauling system's own
     * ({@link io.github.sob1234509876.oc.rts.logistics.HaulingSystem#dispatchNearestHauler}). That
     * explicit dispatch is what makes the command work in a game whose policy turned the automatic hauler
     * search off, and it is why "no free engineer" is a readable refusal instead of a job that waits
     * forever.
     *
     * <p><strong>Charges.</strong> A refusal charges nothing and leaves no reservation: every check runs
     * before the claim is taken, and a claim that cannot be handed to a hauler is released again before
     * this method returns. A success charges the moving goods - they leave the source and arrive at the
     * target - and no gold, wood or other resource is spent on top.
     *
     * <p><strong>Determinism.</strong> The source reservation is single-warehouse, the hauler search is
     * the existing nearest-idle rule, and the world is not ticked inside this call, so the same world state
     * and the same arguments always produce the same dispatch.
     *
     * @param source the finished own warehouse the goods come from; may be {@code null}
     * @param target the finished own warehouse the goods go to; may be {@code null}
     * @param item   the item kind to move; may be {@code null}
     * @param amount the requested amount; positive, clamped by the rules above
     * @return the dispatched bundle and job, or the readable refusal reason; never {@code null}
     */
    TransferResult transfer(Building source, Building target, ItemType item, Natural amount);

    /**
     * Forces one engineer to carry the next missing inputs to one specific producer.
     *
     * <p>This is the manual counterpart of the automatic engineer pool: instead of letting the policy
     * choose a producer and a hauler, the caller names both. The engine looks for the first queued
     * recipe of {@code target} that is still waiting for materials, reserves exactly its missing inputs
     * through the owner's {@link io.github.sob1234509876.oc.rts.economy.WarehouseLogistics}, creates an
     * ordinary delivery job and assigns {@code engineer} to it.
     *
     * <p><strong>Force means force.</strong> An engineer with other orders is stopped first (the queue is
     * cleared through the normal cancel path), so the haul starts immediately; the engineer then walks the
     * usual haul route. A busy engineer is never silently skipped. Nothing is teleported: the goods leave
     * the warehouse when the engineer picks them up and enter the producer's buffer on arrival, exactly
     * like an automatic delivery.
     *
     * <p>Readable refusals, all free and without a reservation: an engineer that cannot be commanded or
     * cannot haul, a producer that is gone/unfinished/owned by another player/in another dimension, a
     * player without a hauling network, a producer with no waiting recipe that is short of anything, a
     * recipe the warehouses cannot cover, and a job the hauler cannot take.
     *
     * @param engineer the engineer that will carry the goods
     * @param target   the finished own producer to supply
     * @return the reserved bundle and job, or the readable refusal reason; never {@code null}
     */
    HaulResult tryHaul(Unit engineer, Building target);

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
     * Outcome of one route request: either the issued {@link MoveOrder} or a refusal reason.
     *
     * <p>At most one field is set. An empty route and a route longer than {@value #MAX_MOVE_POINTS} are
     * both refused, and neither replaces the unit's existing order.
     *
     * @param order   the order that was issued, or {@code null} when nothing was issued
     * @param refusal why no order was issued, or {@code null} on success
     */
    record MoveResult(MoveOrder order, String refusal) {

        public MoveResult {
            if ((order == null) == (refusal == null)) {
                throw new IllegalArgumentException("a move result is either an order or a refusal");
            }
        }

        /** An order that is now the unit's active order. */
        public static MoveResult issued(MoveOrder order) {
            return new MoveResult(Objects.requireNonNull(order, "order"), null);
        }

        /** Nothing was issued, for the given reason. */
        public static MoveResult refused(String reason) {
            return new MoveResult(null, Objects.requireNonNull(reason, "reason"));
        }

        /** Whether an order was issued. */
        public boolean issued() {
            return order != null;
        }
    }

    /**
     * Outcome of one repair request: either the building whose repair order was issued, or a refusal.
     *
     * <p>At most one field is set. The building is named rather than the {@link RepairOrder} because the
     * caller's question is "who is being repaired"; the order is the engineer's active order.
     *
     * @param building the building being repaired, or {@code null} when nothing was ordered
     * @param refusal  why no order was issued, or {@code null} on success
     */
    record RepairResult(Building building, String refusal) {

        public RepairResult {
            if ((building == null) == (refusal == null)) {
                throw new IllegalArgumentException("a repair result is either a building or a refusal");
            }
        }

        /** A repair order was issued for the building. */
        public static RepairResult repairing(Building building) {
            return new RepairResult(Objects.requireNonNull(building, "building"), null);
        }

        /** Nothing was ordered, for the given reason. */
        public static RepairResult refused(String reason) {
            return new RepairResult(null, Objects.requireNonNull(reason, "reason"));
        }

        /** Whether a repair order was issued. */
        public boolean repairing() {
            return building != null;
        }
    }

    /**
     * Outcome of one reclaim request: the removed site, the refund actually deposited, or a refusal.
     *
     * <p>On success {@link #site()} is already destroyed and every attached builder has been detached;
     * {@link #refund()} is what the warehouses really accepted (empty when nothing was owed or nothing
     * fit). On refusal both fields are {@code null} except {@link #refusal()}.
     *
     * @param site    the reclaimed site, or {@code null} when nothing changed
     * @param refund  the bundle deposited into the owner's warehouses; empty on a refusal or when
     *                nothing was owed
     * @param refusal why nothing changed, or {@code null} on success
     */
    record ReclaimResult(Building site, ItemBundle refund, String refusal) {

        public ReclaimResult {
            refund = refund == null ? ItemBundle.EMPTY : refund;
            if (site == null) {
                if (!refund.isEmpty()) {
                    throw new IllegalArgumentException("a refused reclaim has no refund");
                }
                if (refusal == null) {
                    throw new IllegalArgumentException("a reclaim result needs a site or a refusal");
                }
            } else if (refusal != null) {
                throw new IllegalArgumentException("a reclaimed site has no refusal");
            }
        }

        /** The site was reclaimed; the refund is what the warehouses accepted. */
        public static ReclaimResult reclaimed(Building site, ItemBundle refund) {
            return new ReclaimResult(Objects.requireNonNull(site, "site"),
                    refund == null ? ItemBundle.EMPTY : refund, null);
        }

        /** Nothing changed, for the given reason. */
        public static ReclaimResult refused(String reason) {
            return new ReclaimResult(null, null, Objects.requireNonNull(reason, "reason"));
        }

        /** Whether a site was reclaimed. */
        public boolean reclaimed() {
            return site != null;
        }
    }

    /**
     * Outcome of one manual warehouse transfer: the dispatched goods and job, or a refusal reason.
     *
     * <p>At most one of {@link #job()} and {@link #refusal()} is set. On success {@link #moved()} is the
     * clamped bundle that was reserved and handed to a hauler; it is <em>in flight</em>, so the target's
     * inventory does not hold it yet. On refusal every field is empty and nothing changed: no stock left
     * the source, no reservation was left behind and no job was registered.
     *
     * <p>The job handle is exposed because it is the observable proof of the dispatch: its
     * {@link DeliveryJob#target() target} is the destination, its
     * {@link DeliveryJob#reservation() reservation} is the claim on the source, and
     * {@link DeliveryJob#hauler()} names the unit that will carry it.
     *
     * @param moved   the bundle that was dispatched; empty on a refusal
     * @param job     the registered hauling job, or {@code null} on a refusal
     * @param refusal why nothing was dispatched, or {@code null} on success
     */
    record TransferResult(ItemBundle moved, DeliveryJob job, String refusal) {

        public TransferResult {
            moved = moved == null ? ItemBundle.EMPTY : moved;
            if (job == null) {
                if (!moved.isEmpty()) {
                    throw new IllegalArgumentException("a refused transfer dispatched nothing");
                }
                if (refusal == null) {
                    throw new IllegalArgumentException("a transfer result needs a job or a refusal");
                }
            } else if (refusal != null) {
                throw new IllegalArgumentException("a dispatched transfer has no refusal");
            }
        }

        /** The goods were dispatched to a hauler; {@code moved} is in flight, not delivered yet. */
        public static TransferResult dispatched(ItemBundle moved, DeliveryJob job) {
            return new TransferResult(Objects.requireNonNull(moved, "moved"),
                    Objects.requireNonNull(job, "job"), null);
        }

        /** Nothing was dispatched, for the given readable reason. */
        public static TransferResult refused(String reason) {
            return new TransferResult(ItemBundle.EMPTY, null, Objects.requireNonNull(reason, "reason"));
        }

        /** Whether a hauling job is carrying the goods. */
        public boolean transferred() {
            return job != null;
        }
    }

    /**
     * Outcome of one forced haul: the reserved goods and job, or a refusal reason.
     *
     * <p>At most one of {@link #job()} and {@link #refusal()} is set. On success {@link #reserved()} is
     * the bundle that was reserved for the producer and handed to the named engineer; it is in flight,
     * not delivered yet.
     *
     * @param reserved the bundle reserved for the producer; empty on a refusal
     * @param job      the registered hauling job, or {@code null} on a refusal
     * @param refusal  why nothing was dispatched, or {@code null} on success
     */
    record HaulResult(ItemBundle reserved, DeliveryJob job, String refusal) {

        public HaulResult {
            reserved = reserved == null ? ItemBundle.EMPTY : reserved;
            if (job == null) {
                if (!reserved.isEmpty()) {
                    throw new IllegalArgumentException("a refused haul reserved nothing");
                }
                if (refusal == null) {
                    throw new IllegalArgumentException("a haul result needs a job or a refusal");
                }
            } else if (refusal != null) {
                throw new IllegalArgumentException("a dispatched haul has no refusal");
            }
        }

        /** The engineer was given the job; the goods are in flight. */
        public static HaulResult hauled(ItemBundle reserved, DeliveryJob job) {
            return new HaulResult(Objects.requireNonNull(reserved, "reserved"),
                    Objects.requireNonNull(job, "job"), null);
        }

        /** Nothing was dispatched, for the given readable reason. */
        public static HaulResult refused(String reason) {
            return new HaulResult(ItemBundle.EMPTY, null, Objects.requireNonNull(reason, "reason"));
        }

        /** Whether a hauling job now carries the goods. */
        public boolean hauled() {
            return job != null;
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
