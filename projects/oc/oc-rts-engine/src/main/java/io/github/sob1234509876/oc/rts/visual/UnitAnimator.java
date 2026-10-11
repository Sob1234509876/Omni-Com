package io.github.sob1234509876.oc.rts.visual;

import io.github.sob1234509876.oc.rts.core.Direction;

import java.util.Objects;
import java.util.Optional;

/**
 * Animation state machine of a single entity.
 *
 * <p>The simulation decides <em>which</em> activity an entity is doing and which way it faces; this
 * class answers the only question a renderer has: which frame belongs to that activity right now. It
 * owns exactly three pieces of state - the current clip, how long that clip has been playing and the
 * facing - which keeps it trivially serialisable with the rest of the world and free of any timing that
 * depends on the wall clock.
 *
 * <p>Switching to the clip that is already playing does <em>not</em> restart it. The animation system
 * reasserts the desired clip every tick, so restarting would freeze a walking unit on its first frame;
 * callers that really want a restart pass {@code true} to {@link #play(String, boolean)}.
 *
 * <p>A frame is sampled from the clip's elapsed ticks, so the state machine stays correct even if a
 * renderer samples it several times per tick or not at all.
 */
public final class UnitAnimator implements VisualState {

    private final AnimationSet animationSet;

    private String clipId;
    private int elapsedTicks;
    private float facing;

    /**
     * Creates the animation state of an entity that uses the given animation set.
     *
     * <p>The animator starts without a clip: the animation system assigns one on its first tick, which
     * is what makes an entity that spawns already moving start directly in {@code move}.
     */
    public UnitAnimator(AnimationSet animationSet) {
        this.animationSet = Objects.requireNonNull(animationSet, "animationSet");
    }

    /** The animation set this state plays; never {@code null}, may have no clips at all. */
    public AnimationSet animationSet() {
        return animationSet;
    }

    @Override
    public void play(String clipId) {
        play(clipId, false);
    }

    @Override
    public void play(String clipId, boolean restart) {
        if (clipId == null || clipId.isBlank()) {
            return;
        }
        if (clipId.equals(this.clipId) && !restart) {
            return;
        }
        Optional<AnimationClip> requested = animationSet.findClip(clipId, Direction.fromAngle(facing));
        if (requested.isEmpty()) {
            return;
        }
        this.clipId = clipId;
        this.elapsedTicks = 0;
    }

    @Override
    public String spriteSetId() {
        return animationSet.spriteSetId();
    }

    @Override
    public String clipId() {
        return clipId;
    }

    @Override
    public int frameIndex() {
        return currentFrame() == null ? 0 : currentFrame().index();
    }

    /** Ticks the current clip has been playing, counted from its first frame. */
    public int elapsedTicks() {
        return elapsedTicks;
    }

    /** Frame that should be displayed right now, resolved through the current direction. */
    public SpriteFrame currentFrame() {
        AnimationClip resolved = currentClip();
        return resolved == null ? null : resolved.frameAt(elapsedTicks);
    }

    /** The clip that is currently playing, or {@code null} while no clip could be resolved. */
    public AnimationClip currentClip() {
        if (clipId == null) {
            return null;
        }
        return animationSet.findClip(clipId, Direction.fromAngle(facing)).orElse(null);
    }

    @Override
    public float facing() {
        return facing;
    }

    @Override
    public void setFacing(float radians) {
        this.facing = radians;
    }

    @Override
    public boolean finished() {
        AnimationClip resolved = currentClip();
        return resolved != null && resolved.finishedAt(elapsedTicks);
    }

    @Override
    public void tick() {
        AnimationClip resolved = currentClip();
        if (resolved == null) {
            return;
        }
        if (resolved.finishedAt(elapsedTicks)) {
            // A one shot clip stays on its last pose: advancing further would change nothing, and
            // stopping keeps elapsedTicks a meaningful "how long did this take" value for callers.
            return;
        }
        elapsedTicks++;
    }

    @Override
    public String toString() {
        return "UnitAnimator{" + animationSet.spriteSetId() + ", clip=" + clipId
                + ", elapsed=" + elapsedTicks + ", facing=" + facing + "}";
    }
}
