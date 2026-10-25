package io.github.sob1234509876.oc.server.classic;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.Recipe;
import io.github.sob1234509876.oc.rts.core.GridPos;
import io.github.sob1234509876.oc.rts.economy.WarehouseLogistics;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Entity;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.logistics.DeliveryJob;
import io.github.sob1234509876.oc.rts.logistics.HaulingSystem;
import io.github.sob1234509876.oc.rts.production.ProductionJob;
import io.github.sob1234509876.oc.rts.production.ProductionQueue;
import io.github.sob1234509876.oc.rts.production.ProductionState;
import io.github.sob1234509876.oc.server.world.WorldTickHook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The classic game's engineer crew policy: a pool of nearby engineers serves one arsenal at a time.
 *
 * <p>This hook replaces the engine's per-job nearest-idle search with the policy the owner asked for
 * in item 29. It owns no roster and writes no state on a unit: every pass rescans the world, so an
 * engineer produced yesterday or this tick participates on exactly the same terms as one that has been
 * standing since the opening. The only state is the pass clock and whether the engine's automatic
 * hauler choice was already switched off.
 *
 * <h2>The rules</h2>
 *
 * <ol>
 *   <li><strong>One arsenal at a time.</strong> For each player, only the nearest
 *       <em>unsatisfied</em> producer is served during a pass. "Unsatisfied" means the building has a
 *       pending delivery job, or a queued production job whose inputs are missing and affordable from
 *       the warehouses. The order is <strong>nearest-first, measured from the player's oldest living
 *       building</strong> (the base anchor), with the building id as the tie breaker.</li>
 *   <li><strong>A radius, not a role.</strong> Every own, living, idle engineer (a unit whose order
 *       queue is empty) that can haul, stands in the arsenal's dimension and is within
 *       {@value #RECRUIT_RADIUS_TILES} tiles of the arsenal joins the crew. There is no per-arsenal
 *       quota: the number of engineers is the number of jobs the arsenal still needs. Engineers
 *       farther away are not nominated; the arsenal simply waits for one to come closer.</li>
 *   <li><strong>A busy engineer is not interrupted.</strong> A unit with any order (moving, building,
 *       repairing, fighting, already hauling) is never recruited; it joins the crew only after its
 *       queue is empty again. The pool never replaces a queue.</li>
 *   <li><strong>A pool, not an assignment.</strong> The crew is recomputed for every pass; after a
 *       haul completes, the engineer's order queue is empty and the next pass can hand it the next
 *       demand. An engineer is excluded while any pending job names it, so it never serves two
 *       arsenals at once.</li>
 *   <li><strong>New engineers join by existing.</strong> The pass runs every
 *       {@value #POOL_INTERVAL_TICKS} ticks, so a unit produced on tick {@code t} is considered at the
 *       first pass on or after {@code t}; no registration, restart or explicit enlist command exists.</li>
 * </ol>
 *
 * <h2>How it creates work</h2>
 *
 * <p>The engine's production system creates one delivery job for the job a producer is currently
 * waiting on. That alone would keep at most one engineer busy per arsenal, which is the reported
 * problem. The pool therefore <em>pre-stages</em> the demand of the queued jobs behind the current one:
 * for each still-waiting recipe it reserves the recipe's missing inputs through the ordinary
 * {@link WarehouseLogistics#requestDeliveryJob(Building, ItemBundle)} path and hands the resulting
 * job to one engineer. The goods are real, the reservation is real, and production later consumes
 * them from its buffer exactly as if it had ordered them itself - only the wait is removed.
 *
 * <p>Pre-staging is bounded by the crew and by the stock: one job is created per waiting recipe and
 * only while an idle engineer is available to carry it. Once the nearest arsenal has no unassigned job
 * left, the pass does nothing more; the next pass may serve the next arsenal.
 *
 * <h2>Why the engine needed a seam</h2>
 *
 * <p>{@link HaulingSystem} used to pick a hauler for every waiting job itself, before any world tick
 * hook ran, so a classic-side policy could never say "this arsenal first". {@link #install(World)}
 * turns that automatic choice off through
 * {@link HaulingSystem#setAutoHaulerAssignment(boolean)} and the pool assigns specific units through
 * {@link HaulingSystem#assignHauler(DeliveryJob, Unit)}. The rest of the hauling system - cargo,
 * reservations, recovery and timeout - is untouched and still runs for every job.
 *
 * <p>The pool deliberately does nothing in a non-classic world, in direct-delivery mode (where no
 * hauler exists) or for a player without a warehouse network. Everything is walked in entity id order
 * and every distance tie is broken by id, so the policy is deterministic like the rest of the classic
 * module.
 */
public final class ClassicEngineerPool implements WorldTickHook {

    /** Ticks between two pool passes; a new engineer is considered at the first pass on or after it exists. */
    public static final int POOL_INTERVAL_TICKS = 5;

    /**
     * Distance, in tiles, within which an idle engineer is recruited for an arsenal.
     *
     * <p>The radius is measured from the arsenal's position to the unit's position (Euclidean, the
     * same measure the AI's defence radius uses). It is deliberately larger than the base's opening
     * layout so every engineer standing around one production building is in range, but smaller than
     * the AI's 64-tile site search so a unit working at a far expansion is left alone.
     */
    public static final float RECRUIT_RADIUS_TILES = 24f;

    private static final float RECRUIT_RADIUS_SQUARED = RECRUIT_RADIUS_TILES * RECRUIT_RADIUS_TILES;

    /** Whether {@link #install(World)} already disabled the engine's own hauler search. */
    private boolean installed;

    /** Tick of the last pass, or {@link Long#MIN_VALUE} before the first one. */
    private long lastPassTick = Long.MIN_VALUE;

    /** Creates the pool; a plugin owns one instance per loaded world. */
    public ClassicEngineerPool() {
    }

    /**
     * Disables the engine's automatic hauler search for this classic world.
     *
     * <p>The plugin does not call this eagerly because a plugin is loaded before the world exists; the
     * first {@link #tick(World, long)} performs it. A caller that wants the policy active from the very
     * first engine step (a test, or a host that builds its world first) may call it explicitly. Calling
     * it again, or on a non-classic world, is a no-op.
     *
     * @param world the world this pool will serve
     */
    public void install(World world) {
        Objects.requireNonNull(world, "world");
        if (installed) {
            return;
        }
        installed = true;
        if (!isClassicWorld(world)) {
            // Another plugin owns the world: leave the engine's own hauler search in charge.
            return;
        }
        for (Player player : world.players()) {
            WarehouseLogistics logistics = WarehouseLogistics.of(player);
            if (logistics != null) {
                logistics.hauling().ifPresent(system -> system.setAutoHaulerAssignment(false));
            }
        }
    }

    /** Whether the pool already installed itself (and therefore turned the engine search off). */
    public boolean installed() {
        return installed;
    }

    @Override
    public void tick(World world, long tick) {
        Objects.requireNonNull(world, "world");
        install(world);
        if (!isClassicWorld(world)) {
            return;
        }
        if (lastPassTick != Long.MIN_VALUE && tick >= lastPassTick
                && tick - lastPassTick < POOL_INTERVAL_TICKS) {
            return;
        }
        lastPassTick = tick;
        for (Player player : world.players()) {
            servePlayer(world, player);
        }
    }

    // ------------------------------------------------------------------ one player's pass

    /**
     * Serves the nearest unsatisfied arsenal of one player, if its crew is not empty.
     *
     * <p>Nothing is changed for a player with no warehouses, no hauling system or no producer in need,
     * which is the normal state of the bot (it stocks its own building) and of the opening scene.
     */
    private static void servePlayer(World world, Player player) {
        WarehouseLogistics logistics = WarehouseLogistics.of(player);
        if (logistics == null) {
            return;
        }
        HaulingSystem hauling = logistics.hauling().orElse(null);
        if (hauling == null || hauling.directDelivery()) {
            return;
        }
        List<Building> buildings = ownBuildings(world, player);
        GridPos base = oldestOrigin(buildings);
        if (base == null) {
            return;
        }
        Building arsenal = nearestUnsatisfied(world, player, logistics, hauling, buildings, base);
        if (arsenal == null) {
            return;
        }
        List<Unit> crew = recruitables(world, player, arsenal, hauling);
        if (crew.isEmpty()) {
            // The nearest arsenal keeps the focus even when nobody is close enough to serve it yet.
            return;
        }
        int used = assignWaitingJobs(hauling, arsenal, crew);
        stageQueuedDemands(world, logistics, hauling, arsenal, crew, used);
    }

    /**
     * Assigns the arsenal's unassigned delivery jobs to the crew, in job registration order.
     *
     * @return how many engineers received a job
     */
    private static int assignWaitingJobs(HaulingSystem hauling, Building arsenal, List<Unit> crew) {
        int used = 0;
        for (DeliveryJob job : hauling.jobs()) {
            if (used >= crew.size()) {
                break;
            }
            if (!job.isPending() || job.target() != arsenal
                    || job.state() != DeliveryJob.State.WAITING_HAULER) {
                continue;
            }
            if (hauling.assignHauler(job, crew.get(used))) {
                used++;
            }
        }
        return used;
    }

    /**
     * Pre-stages the demand of the arsenal's queued production jobs for the remaining crew.
     *
     * <p>The queue is walked in production order. A job already covered by a pending delivery is
     * counted and skipped, so a staged job is never duplicated. A still-waiting recipe becomes a new
     * delivery job for exactly its missing inputs, reserved through the ordinary logistics network and
     * assigned to the next idle engineer. The walk stops when the crew or the stock runs out.
     */
    private static void stageQueuedDemands(World world, WarehouseLogistics logistics,
            HaulingSystem hauling, Building arsenal, List<Unit> crew, int alreadyUsed) {
        if (alreadyUsed >= crew.size()) {
            return;
        }
        ProductionQueue queue = world.production().queue(arsenal).orElse(null);
        if (queue == null) {
            return;
        }
        int pending = pendingJobs(hauling, arsenal);
        int used = alreadyUsed;
        for (ProductionJob job : queue.jobs()) {
            if (used >= crew.size()) {
                break;
            }
            ProductionState state = job.state();
            if (state == ProductionState.PRODUCING || state == ProductionState.WAITING_OUTPUT) {
                continue;
            }
            if (state == ProductionState.DELIVERING) {
                if (pending > 0) {
                    pending--;
                }
                continue;
            }
            if (state != ProductionState.WAITING_MATERIALS) {
                continue;
            }
            if (pending > 0) {
                // The engine (or an earlier pass) already has a job for this position in the queue.
                pending--;
                continue;
            }
            ItemBundle missing = missingInputs(arsenal, job.recipe());
            if (missing.isEmpty() || !logistics.hasAvailable(missing)) {
                continue;
            }
            DeliveryJob created = logistics.requestDeliveryJob(arsenal, missing).orElse(null);
            if (created == null || !hauling.assignHauler(created, crew.get(used))) {
                // An unassigned job is harmless: the next pass can hand it to somebody. It is not
                // counted as staged because it did not take this engineer.
                continue;
            }
            used++;
        }
    }

    // ------------------------------------------------------------------ selection

    /**
     * The nearest unsatisfied producer of the player, or {@code null} when everything is supplied.
     *
     * <p>Distance is measured from the oldest living building and ties are broken by entity id, which
     * makes the same world state always pick the same arsenal.
     */
    private static Building nearestUnsatisfied(World world, Player player, WarehouseLogistics logistics,
            HaulingSystem hauling, List<Building> buildings, GridPos base) {
        Building best = null;
        float bestDistance = 0f;
        for (Building building : buildings) {
            if (!building.isAlive() || !building.isConstructed() || !building.type().canProduce()) {
                continue;
            }
            if (!hasDemand(world, logistics, hauling, building)) {
                continue;
            }
            float distance = building.position().distanceSquared(base.center());
            if (best == null || distance < bestDistance
                    || (distance == bestDistance && building.id().compareTo(best.id()) < 0)) {
                best = building;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Whether a producer still needs the pool.
     *
     * <p>A pending delivery job always counts, even one already in transit: the arsenal keeps the
     * focus until its shipments have arrived, which is what "one arsenal at a time" means. Without a
     * pending job, a queued job waiting for materials counts only when those materials are actually
     * missing and the warehouses can cover them; an unaffordable demand must not block the next
     * arsenal forever.
     */
    private static boolean hasDemand(World world, WarehouseLogistics logistics, HaulingSystem hauling,
            Building arsenal) {
        for (DeliveryJob job : hauling.jobs()) {
            if (job.isPending() && job.target() == arsenal) {
                return true;
            }
        }
        ProductionQueue queue = world.production().queue(arsenal).orElse(null);
        if (queue == null) {
            return false;
        }
        for (ProductionJob job : queue.jobs()) {
            if (job.state() != ProductionState.WAITING_MATERIALS) {
                continue;
            }
            ItemBundle missing = missingInputs(arsenal, job.recipe());
            if (!missing.isEmpty() && logistics.hasAvailable(missing)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The idle engineers inside the recruit radius of the arsenal, nearest first.
     *
     * <p>The idle rule is the "do not interrupt" rule: only a unit with an empty order queue is
     * eligible. A unit already named by a pending job is excluded even if its queue has not been
     * filled yet, so two passes can never load one engineer with two arsenals.
     */
    private static List<Unit> recruitables(World world, Player player, Building arsenal,
            HaulingSystem hauling) {
        Set<Entity> assigned = new LinkedHashSet<>();
        for (DeliveryJob job : hauling.jobs()) {
            if (!job.isPending() || job.hauler().isEmpty()) {
                continue;
            }
            Entity entity = world.findEntity(job.hauler().orElseThrow()).orElse(null);
            if (entity != null && entity.owner().equals(player)) {
                assigned.add(entity);
            }
        }
        List<Unit> crew = new ArrayList<>();
        for (Unit unit : world.unitsOf(player)) {
            if (!unit.isAlive() || !unit.type().canHaul() || !unit.orders().isEmpty()) {
                continue;
            }
            if (!unit.dimensionId().equals(arsenal.dimensionId()) || assigned.contains(unit)) {
                continue;
            }
            if (unit.position().distanceSquared(arsenal.position()) > RECRUIT_RADIUS_SQUARED) {
                continue;
            }
            crew.add(unit);
        }
        crew.sort(Comparator
                .comparingDouble((Unit unit) -> unit.position().distanceSquared(arsenal.position()))
                .thenComparing(Unit::id));
        return crew;
    }

    // ------------------------------------------------------------------ helpers

    /** The player's living buildings in creation (id) order. */
    private static List<Building> ownBuildings(World world, Player player) {
        List<Building> buildings = new ArrayList<>();
        for (Building building : world.buildingsOf(player)) {
            if (building.isAlive()) {
                buildings.add(building);
            }
        }
        buildings.sort(Comparator.comparing(Building::id));
        return buildings;
    }

    /** The origin of the oldest living building, i.e. the player's base anchor for distance ordering. */
    private static GridPos oldestOrigin(List<Building> buildings) {
        return buildings.isEmpty() ? null : buildings.get(0).origin();
    }

    /** How many pending delivery jobs currently name this building. */
    private static int pendingJobs(HaulingSystem hauling, Building arsenal) {
        int count = 0;
        for (DeliveryJob job : hauling.jobs()) {
            if (job.isPending() && job.target() == arsenal) {
                count++;
            }
        }
        return count;
    }

    /**
     * The inputs of a recipe that are not yet in the building's buffer.
     *
     * <p>Each queued job is a separate demand, so a job's own missing inputs are not reduced by what
     * another job reserved: the reservation layer already prevents the two from claiming the same
     * stock. A delivery staged for one recipe therefore never makes the next recipe look supplied.
     */
    private static ItemBundle missingInputs(Building arsenal, Recipe recipe) {
        ItemBundle.Builder missing = ItemBundle.builder();
        for (ItemBundle.Amount component : recipe.inputs().components()) {
            Natural present = arsenal.inventory().amount(component.item());
            Natural rest = component.amount().subtractClamped(present);
            if (!rest.isZero()) {
                missing.add(component.item(), rest);
            }
        }
        return missing.build();
    }

    /** Whether this world really serves the classic catalogue; a foreign world is left untouched. */
    private static boolean isClassicWorld(World world) {
        var content = world.content();
        return content.buildingOrNull(content.key(ClassicContent.ARMORY_T1)) != null
                && content.unitOrNull(content.key(ClassicContent.ENGINEER)) != null
                && content.buildingOrNull(content.key(ClassicContent.WAREHOUSE)) != null;
    }
}
