package io.github.sob1234509876.oc.rts.visual;

/**
 * Animation state used when no animation set is bound to an entity: it remembers the requested clip
 * and facing so the simulation still behaves identically, but it has no frames.
 */
public final class NullVisualState implements VisualState {

    private final String spriteSetId;
    private String clipId = AnimationClips.IDLE;
    private float facing;

    public NullVisualState(String spriteSetId) {
        this.spriteSetId = spriteSetId;
    }

    @Override
    public void play(String clipId) {
        if (clipId != null) {
            this.clipId = clipId;
        }
    }

    @Override
    public void play(String clipId, boolean restart) {
        play(clipId);
    }

    @Override
    public String spriteSetId() {
        return spriteSetId;
    }

    @Override
    public String clipId() {
        return clipId;
    }

    @Override
    public int frameIndex() {
        return 0;
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
        return false;
    }

    @Override
    public void tick() {
        // nothing to animate
    }

    @Override
    public String toString() {
        return "no-animation(" + spriteSetId + ", " + clipId + ")";
    }
}
