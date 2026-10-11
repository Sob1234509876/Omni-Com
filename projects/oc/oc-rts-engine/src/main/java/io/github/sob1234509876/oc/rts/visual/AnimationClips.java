package io.github.sob1234509876.oc.rts.visual;

/**
 * Canonical animation clip names.
 *
 * <p>Animation sets may contain any clips they like, but these are the names the simulation uses when
 * it switches a unit between activities. A renderer can therefore rely on them being present.
 */
public final class AnimationClips {

    /** Standing still with nothing to do. */
    public static final String IDLE = "idle";
    /** Walking or driving towards a goal. */
    public static final String MOVE = "move";
    /** Attacking, reserved for the combat layer. */
    public static final String ATTACK = "attack";
    /** Playing the death animation before the entity is removed. */
    public static final String DEATH = "death";
    /** Carrying out a build or repair task. */
    public static final String BUILD = "build";
    /** Collecting a resource. */
    public static final String GATHER = "gather";
    /** Standing still while carrying goods. */
    public static final String CARRY_IDLE = "carry_idle";
    /** Walking while carrying goods. */
    public static final String CARRY_MOVE = "carry_move";
    /** Running a production cycle inside a building. */
    public static final String PRODUCE = "produce";

    private AnimationClips() {
    }
}
