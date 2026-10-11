package io.github.sob1234509876.oc.rts.visual;

/**
 * Animation state of a single entity, as far as the simulation is concerned.
 *
 * <p>The engine never draws anything. Systems only decide <em>which</em> animation a unit should be in
 * (idle, moving, attacking, dying) and which way it faces; the implementation resolves that into a
 * concrete sprite frame. A renderer later reads {@link #clipId()} and {@link #frameIndex()} through
 * the world snapshot and blits the matching frame.
 */
public interface VisualState {

    /** Switches to the named clip, keeping the current frame when the clip is already playing. */
    void play(String clipId);

    /** Switches to the named clip and always restarts at its first frame. */
    void play(String clipId, boolean restart);

    /** Identifier of the animation set this state belongs to. */
    String spriteSetId();

    /** Identifier of the currently playing clip, e.g. {@code move}. */
    String clipId();

    /** Index of the frame that should be displayed right now. */
    int frameIndex();

    /** Facing of the entity in radians, used to pick a directional animation. */
    float facing();

    void setFacing(float radians);

    /** Whether a non looping clip has played to its end. */
    boolean finished();

    /** Advances the animation by one simulation tick. */
    void tick();
}
