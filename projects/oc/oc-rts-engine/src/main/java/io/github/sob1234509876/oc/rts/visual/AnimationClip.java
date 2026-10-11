package io.github.sob1234509876.oc.rts.visual;

import java.util.List;
import java.util.Objects;

/**
 * A named sequence of frames, for example the eight frames of a walk cycle.
 *
 * <p>A clip is pure data: it knows how long each frame lasts and whether it repeats, but not which
 * entity plays it or when. That split is what lets {@link UnitAnimator} be a tiny state machine and
 * keeps the sampling of a frame a total function of the elapsed tick count, which makes replays
 * reproducible and lets a renderer ask "what would this look like at tick N" for free.
 *
 * <p>The elapsed tick count is measured from the first frame of the clip. Frames may override the
 * default duration through {@link SpriteFrame#durationTicks()}, so the mapping from elapsed ticks to a
 * frame is computed rather than looked up in a precomputed table; clips are short, so this is cheap and
 * avoids keeping a parallel timing array in sync with the frames.
 */
public final class AnimationClip {

    private final String name;
    private final List<SpriteFrame> frames;
    private final int ticksPerFrame;
    private final boolean loop;
    private final int durationTicks;

    public AnimationClip(String name, List<SpriteFrame> frames, int ticksPerFrame, boolean loop) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("clip name must not be blank");
        }
        Objects.requireNonNull(frames, "frames");
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("clip '" + name + "' needs at least one frame");
        }
        if (ticksPerFrame <= 0) {
            throw new IllegalArgumentException("clip '" + name + "' needs a positive frame duration, was "
                    + ticksPerFrame);
        }
        this.name = name;
        this.frames = List.copyOf(frames);
        this.ticksPerFrame = ticksPerFrame;
        this.loop = loop;
        int total = 0;
        for (SpriteFrame frame : this.frames) {
            total += frame.effectiveDuration(ticksPerFrame);
        }
        this.durationTicks = total;
    }

    public String name() {
        return name;
    }

    /** The frames in play order; never empty. */
    public List<SpriteFrame> frames() {
        return frames;
    }

    /** Default number of ticks each frame is shown for; frames may override it. */
    public int ticksPerFrame() {
        return ticksPerFrame;
    }

    /** Whether the clip starts over after its last frame. */
    public boolean loop() {
        return loop;
    }

    public int frameCount() {
        return frames.size();
    }

    /** Total length of one pass through the clip in ticks. */
    public int durationTicks() {
        return durationTicks;
    }

    /** Frame at the position inside the clip, without any looping applied. */
    public SpriteFrame frame(int position) {
        if (position < 0 || position >= frames.size()) {
            throw new IndexOutOfBoundsException("clip '" + name + "' has " + frames.size()
                    + " frames, position " + position + " is out of range");
        }
        return frames.get(position);
    }

    /** First frame of the clip. */
    public SpriteFrame firstFrame() {
        return frames.get(0);
    }

    /** Last frame of the clip; this is the pose a finished non looping clip is held at. */
    public SpriteFrame lastFrame() {
        return frames.get(frames.size() - 1);
    }

    /**
     * Frame to display after the given number of ticks.
     *
     * <p>A looping clip wraps around, a non looping clip clamps: asking for an elapsed time beyond its
     * end returns the last frame, so a death animation simply stays on its final pose.
     */
    public SpriteFrame frameAt(int elapsedTicks) {
        return frames.get(framePositionAt(elapsedTicks));
    }

    /** Index inside {@link #frames()} of the frame to display after the given number of ticks. */
    public int framePositionAt(int elapsedTicks) {
        if (elapsedTicks <= 0) {
            return 0;
        }
        if (!loop && elapsedTicks >= durationTicks) {
            return frames.size() - 1;
        }
        int time = loop ? Math.floorMod(elapsedTicks, durationTicks) : elapsedTicks;
        int elapsed = 0;
        for (int position = 0; position < frames.size(); position++) {
            elapsed += frames.get(position).effectiveDuration(ticksPerFrame);
            if (time < elapsed) {
                return position;
            }
        }
        return frames.size() - 1;
    }

    /** Whether a non looping clip has shown its last frame for its full duration. */
    public boolean finishedAt(int elapsedTicks) {
        return !loop && elapsedTicks >= durationTicks;
    }

    @Override
    public String toString() {
        return "AnimationClip{" + name + ", " + frames.size() + " frames, " + durationTicks
                + " ticks, " + (loop ? "looping" : "one shot") + "}";
    }
}
