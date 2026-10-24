package io.github.sob1234509876.oc.rts.production;

import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.core.Tick;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.event.Events;
import io.github.sob1234509876.oc.rts.order.BuildOrder;
import io.github.sob1234509876.oc.rts.order.Order;
import io.github.sob1234509876.oc.rts.order.OrderContext;
import io.github.sob1234509876.oc.rts.order.OrderState;
import io.github.sob1234509876.oc.rts.sim.SimulationSystem;

import java.util.Objects;

/**
 * Advances building sites until they become operational.
 *
 * <p>Construction is kept apart from production because it is the transition that decides whether a
 * building takes part in the economy at all: while a building is unfinished it does not produce, it does
 * not store goods in the network, and it does not count as a target for deliveries. Publishing
 * {@link Events.ConstructionCompleted} here is what lets the logistics network pick up a finished
 * warehouse and what lets a builder stop paying for a site that is done, so the event is sent exactly
 * once, from the tick that flipped the building.
 *
 * <h2>Several builders on one site</h2>
 *
 * <p>A site can have more than one builder attached. Every builder with a {@link BuildOrder} for the
 * site attaches itself when the order starts and detaches when the order ends, so the number
 * {@link Building#builderCount()} is exactly the set of builders currently working on that site. The
 * site advances by
 *
 * <pre>{@code progressPerTick = min(attachedBuilders, MAX_BUILDERS_PER_SITE)}</pre>
 *
 * <p>construction points per tick, where {@link #MAX_BUILDERS_PER_SITE} is {@value
 * #MAX_BUILDERS_PER_SITE}. The progress is therefore a function of the <em>set</em> of attached
 * builders, not of the order in which they attached: two builders advance twice as fast as one, and a
 * fifth builder adds nothing. A site with no attached builder does not advance at all, which is what
 * makes the builder order the thing that actually raises a building. For a site of
 * {@link Building#constructionRemaining() remaining} ticks and {@code b} attached builders the
 * completion tick is {@code ceil(remaining / min(b, MAX_BUILDERS_PER_SITE))} construction ticks later;
 * with {@code b = 0} it never completes.
 *
 * <p>When the site flips to constructed, every attached builder's order is finished in that same tick
 * through {@link io.github.sob1234509876.oc.rts.order.OrderQueue#finishCurrent(OrderContext,
 * OrderState)}: each builder publishes {@link Events.OrderFinished} with
 * {@link OrderState#COMPLETED}, becomes idle and drops its site reference. The same cleanup runs with
 * {@link OrderState#FAILED} when a site dies, so a destroyed site never leaves a builder holding an
 * order for it.
 */
public final class ConstructionSystem implements SimulationSystem {

    /** Phase of the construction system in the simulation pipeline, before production. */
    public static final int SYSTEM_ORDER = 500;

    /**
     * Most builders whose work speeds up one site.
     *
     * <p>Attaching more builders is allowed - a player can send five engineers - but the fourth
     * additional worker no longer adds progress. The cap is an engine constant rather than a content
     * field because it is a property of the construction rule, not of one building type.
     */
    public static final int MAX_BUILDERS_PER_SITE = 4;

    private final World world;

    public ConstructionSystem(World world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    /**
     * Construction points one site gains per tick with the given number of attached builders.
     *
     * <p>The documented formula is {@code min(attachedBuilders, MAX_BUILDERS_PER_SITE)} for a positive
     * count and {@code 0} for none. It reads only the count, so both the result and the tick a site
     * completes on are independent of the order in which builders attached.
     *
     * @param attachedBuilders number of builders attached to the site
     * @return construction points to subtract from {@link Building#constructionRemaining()} this tick
     */
    public static int progressPerTick(int attachedBuilders) {
        if (attachedBuilders <= 0) {
            return 0;
        }
        return Math.min(attachedBuilders, MAX_BUILDERS_PER_SITE);
    }

    @Override
    public String name() {
        return "construction";
    }

    @Override
    public int order() {
        return SYSTEM_ORDER;
    }

    @Override
    public void tick(World world, Tick tick) {
        for (Building building : world.buildings()) {
            if (!building.isAlive()) {
                // A site killed during this tick (combat runs at 450) is still in the list until flush.
                // Fail its builders now instead of leaving them attached to a corpse for a tick.
                if (building.builderCount() > 0) {
                    releaseBuilders(world, building, tick, OrderState.FAILED);
                }
                continue;
            }
            if (building.isConstructed()) {
                // A site completed outside this system (completeConstruction) can still carry builders.
                if (building.builderCount() > 0) {
                    releaseBuilders(world, building, tick, OrderState.COMPLETED);
                }
                continue;
            }
            building.pruneDeadBuilders();
            int advance = progressPerTick(building.builderCount());
            if (advance == 0) {
                continue;
            }
            if (building.advanceConstruction(advance)) {
                releaseBuilders(world, building, tick, OrderState.COMPLETED);
                world.events().publish(new Events.ConstructionCompleted(building, tick.number()));
            }
        }
    }

    /**
     * Finishes every attached builder's order in this tick and detaches it.
     *
     * <p>The builder list is a snapshot, so finishing an order (which detaches through
     * {@link BuildOrder#onFinish(OrderContext, OrderState)}) is safe while walking it. A unit whose
     * current order is not a {@link BuildOrder} for this site is simply detached: the site must never
     * keep a builder that is no longer building it.
     */
    private static void releaseBuilders(World world, Building building, Tick tick, OrderState state) {
        building.pruneDeadBuilders();
        for (Unit unit : building.builders()) {
            Order current = unit.orders().current();
            if (current instanceof BuildOrder order && order.site() == building) {
                unit.orders().finishCurrent(new OrderContext(world, unit, tick), state);
            } else {
                building.detachBuilder(unit);
            }
        }
    }
}
