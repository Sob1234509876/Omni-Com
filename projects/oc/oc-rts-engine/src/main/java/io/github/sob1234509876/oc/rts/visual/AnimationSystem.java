package io.github.sob1234509876.oc.rts.visual;

import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.core.Tick;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.rts.entity.Entity;
import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.event.Events;
import io.github.sob1234509876.oc.rts.sim.SimulationSystem;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Drives the animation state of every entity from the simulation.
 *
 * <p>This is the bridge between "what a unit is doing" and "which frames to show". It runs late
 * ({@link #order()} 800), after orders, movement, construction and production have decided what
 * happened this tick, and it only ever reads their result: a unit is moving because
 * {@link io.github.sob1234509876.oc.rts.movement.Motion#isMoving()} says so, a workshop is producing
 * because the production system published that fact. Nothing in the animation layer can therefore
 * change the outcome of a match, and the same world always animates in the same way.
 *
 * <p>Binding happens through {@link Events.EntitySpawned}, so an entity spawned by any system, by a
 * scenario or by a test is animated without that caller having to know about the animation system.
 * Everything is tolerant by design:
 * <ul>
 *   <li>an entity whose {@code spriteSetId} has no animation set keeps its {@link NullVisualState};</li>
 *   <li>a clip the library does not define falls back to {@code idle}, and if even that is missing the
 *       entity simply keeps the clip it is already playing;</li>
 *   <li>a finished one shot clip ({@code death}) holds its last frame until the world removes the
 *       entity, and starts over only when a different clip becomes relevant.</li>
 * </ul>
 */
public final class AnimationSystem implements SimulationSystem {

    private final World world;
    private final VisualLibrary library;

    /** Buildings whose production system reported that they are currently running a recipe. */
    private final Set<Entity> producing = new LinkedHashSet<>();

    public AnimationSystem(World world, VisualLibrary library) {
        this.world = Objects.requireNonNull(world, "world");
        this.library = Objects.requireNonNull(library, "library");
        world.events().subscribe(Events.EntitySpawned.class, this::onEntitySpawned);
        world.events().subscribe(Events.ProductionStateChanged.class, this::onProductionStateChanged);
        world.events().subscribe(Events.ItemProduced.class, event -> producing.remove(event.building()));
        world.events().subscribe(Events.UnitProduced.class, event -> producing.remove(event.building()));
        world.events().subscribe(Events.EntityDestroyed.class, event -> producing.remove(event.entity()));
    }

    /** The world this system animates; the same instance that was passed to the constructor. */
    public World world() {
        return world;
    }

    /** The library that resolves sprite sets to clips. */
    public VisualLibrary library() {
        return library;
    }

    @Override
    public String name() {
        return "animation";
    }

    @Override
    public int order() {
        return 800;
    }

    /** Number of buildings currently reported as producing; for tests and debug overlays. */
    public int producingCount() {
        return producing.size();
    }

    /** Attaches an animation state to freshly spawned entities whose type has animations. */
    private void onEntitySpawned(Events.EntitySpawned event) {
        Entity entity = event.entity();
        // Units and buildings share the visual slot on Entity, so a workshop animates its construction
        // and production exactly the same way a unit animates its walk.
        library.findAnimationSet(entity.spriteSetId())
                .ifPresent(set -> entity.setVisual(new UnitAnimator(set)));
    }

    /**
     * Remembers that a building started or stopped running a recipe.
     *
     * <p>Production is the only activity of a building that is not visible in its own state, so it is
     * tracked through the event the production system publishes instead of being guessed from inventory
     * contents. The state arrives as a name rather than as the production layer's enum on purpose: the
     * animation package must not depend on the production package, so it matches the lifecycle name
     * ({@code PRODUCING}) and stays out of the way of everything else.
     */
    private void onProductionStateChanged(Events.ProductionStateChanged event) {
        Building building = event.building();
        if (building == null) {
            return;
        }
        if (isProducing(event.state())) {
            producing.add(building);
        } else {
            producing.remove(building);
        }
    }

    /**
     * Whether a production state name means "this building is running a recipe right now".
     *
     * <p>Only the state that actually consumes simulation time counts. Waiting for materials, waiting
     * for the output to be collected and the terminal states all leave the building idle as far as a
     * renderer is concerned.
     */
    private static boolean isProducing(String state) {
        return state != null && (state.equalsIgnoreCase("PRODUCING")
                || state.equalsIgnoreCase("RUNNING")
                || state.equalsIgnoreCase("ACTIVE"));
    }

    @Override
    public void tick(World world, Tick tick) {
        for (Unit unit : world.units()) {
            apply(unit, unitClip(unit));
        }
        for (Building building : world.buildings()) {
            apply(building, buildingClip(building));
        }
    }

    /**
     * Chooses the clip of a unit from its state, most significant condition first: death wins over
     * everything, then whether it has somewhere to go, then whether it is loaded.
     */
    private static String unitClip(Unit unit) {
        if (!unit.isAlive() || unit.health().isDead()) {
            return AnimationClips.DEATH;
        }
        if (unit.motion().isMoving()) {
            return unit.isCarryingItems() ? AnimationClips.CARRY_MOVE : AnimationClips.MOVE;
        }
        return unit.isCarryingItems() ? AnimationClips.CARRY_IDLE : AnimationClips.IDLE;
    }

    /** Chooses the clip of a building: destroyed, under construction, producing, otherwise idle. */
    private String buildingClip(Building building) {
        if (!building.isAlive() || building.health().isDead()) {
            return AnimationClips.DEATH;
        }
        if (!building.isConstructed()) {
            return AnimationClips.BUILD;
        }
        return producing.contains(building) ? AnimationClips.PRODUCE : AnimationClips.IDLE;
    }

    /**
     * Points the visual state of an entity at the clip its state calls for and advances it one tick.
     *
     * <p>The desired clip is only requested when it is defined; a missing {@code carry_move} must not
     * stop a loaded worker from animating its walk, so {@code idle} is used as a fallback. The exception
     * is death: an entity whose set has no death animation keeps whatever pose it had instead of
     * standing up again as an idle corpse.
     */
    private void apply(Entity entity, String desired) {
        VisualState visual = entity.visual();
        if (!desired.equals(visual.clipId()) && !playIfDefined(visual, desired)
                && !AnimationClips.DEATH.equals(desired)) {
            playIfDefined(visual, AnimationClips.IDLE);
        }
        visual.setFacing(facingOf(entity, visual));
        visual.tick();
    }

    /** Plays the clip when the animation set defines it, reporting whether it was accepted. */
    private boolean playIfDefined(VisualState visual, String clipId) {
        return library.findAnimationSet(visual.spriteSetId())
                .map(set -> {
                    if (set.findClip(clipId).isEmpty()) {
                        return false;
                    }
                    visual.play(clipId);
                    return clipId.equals(visual.clipId());
                })
                .orElse(false);
    }

    /** Facing in radians: a moving entity looks where it goes, a still one keeps its heading. */
    private static float facingOf(Entity entity, VisualState visual) {
        Vec2 velocity = entity instanceof Unit unit ? unit.motion().velocity() : Vec2.ZERO;
        if (velocity.x() != 0f || velocity.y() != 0f) {
            return velocity.angle();
        }
        return visual.facing();
    }

    @Override
    public String toString() {
        return "AnimationSystem{library=" + library + ", producing=" + producing.size() + "}";
    }
}
