package io.github.sob1234509876.oc.rts.visual;

import io.github.sob1234509876.oc.rts.core.TileRect;

/**
 * One frame of a sprite sheet, described only in pixels and tile-independent data.
 *
 * <p>A frame deliberately does <em>not</em> hold its {@link SpriteSheet}. Clips are built while the
 * library is still being assembled, so a back reference would either force the sheet to exist first or
 * create a cycle between sheet and frames; a renderer that needs the image path resolves
 * {@link #sheetId()} through {@link VisualLibrary#sheet(String)} instead.
 *
 * <p>The pivot is the point of the frame that is aligned with the entity position: {@code pivotX} and
 * {@code pivotY} are measured from the top left corner of the frame, so a unit drawn with its feet at
 * the bottom middle uses {@code (width / 2, height)}.
 *
 * @param sheetId       identifier of the sheet this frame belongs to
 * @param index         index of the frame inside its sheet, in {@code [0, sheet.frameCount())}
 * @param pixelX        left edge of the frame inside the sheet image, in pixels
 * @param pixelY        top edge of the frame inside the sheet image, in pixels
 * @param width         width of the frame in pixels
 * @param height        height of the frame in pixels
 * @param pivotX        anchor of the frame on the x axis, in pixels from its left edge
 * @param pivotY        anchor of the frame on the y axis, in pixels from its top edge
 * @param durationTicks ticks this frame lasts; a non positive value means "use the clip default"
 */
public record SpriteFrame(
        String sheetId,
        int index,
        int pixelX,
        int pixelY,
        int width,
        int height,
        float pivotX,
        float pivotY,
        int durationTicks) {

    public SpriteFrame {
        if (sheetId == null || sheetId.isBlank()) {
            throw new IllegalArgumentException("sheetId must not be blank");
        }
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative, was " + index);
        }
        if (pixelX < 0 || pixelY < 0) {
            throw new IllegalArgumentException(
                    "frame origin must not be negative, was (" + pixelX + ", " + pixelY + ")");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "frame size must be positive, was " + width + "x" + height);
        }
    }

    /** Creates a frame with a bottom center pivot, the usual anchor for ground units. */
    public static SpriteFrame of(String sheetId, int index, int pixelX, int pixelY, int width, int height) {
        return new SpriteFrame(sheetId, index, pixelX, pixelY, width, height, width / 2f, height, 0);
    }

    /** Same frame with an explicit anchor. */
    public SpriteFrame withPivot(float newPivotX, float newPivotY) {
        return new SpriteFrame(sheetId, index, pixelX, pixelY, width, height, newPivotX, newPivotY, durationTicks);
    }

    /** Same frame with an explicit per frame duration. */
    public SpriteFrame withDuration(int ticks) {
        return new SpriteFrame(sheetId, index, pixelX, pixelY, width, height, pivotX, pivotY, ticks);
    }

    /** Whether this frame overrides the default frame duration of its clip. */
    public boolean hasOwnDuration() {
        return durationTicks > 0;
    }

    /**
     * Duration to use for this frame, given the default of the clip it belongs to.
     *
     * <p>Keeping the fallback in one place means a clip may mix cheap "hold the last pose" frames with
     * frames that need their own timing without every caller having to know about it.
     */
    public int effectiveDuration(int clipDefaultTicks) {
        return durationTicks > 0 ? durationTicks : clipDefaultTicks;
    }

    /** Pixel rectangle of this frame inside its sheet image, useful for atlases and debug overlays. */
    public TileRect pixelRect() {
        return new TileRect(pixelX, pixelY, pixelX + width - 1, pixelY + height - 1);
    }
}
