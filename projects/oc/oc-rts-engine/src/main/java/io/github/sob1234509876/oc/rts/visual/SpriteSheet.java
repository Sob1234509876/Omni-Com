package io.github.sob1234509876.oc.rts.visual;

import java.util.Objects;

/**
 * A sprite sheet: one image file cut into a uniform grid of frames.
 *
 * <p>The engine never loads the image, it only carries the path. That keeps the simulation free of any
 * graphics dependency and lets a future renderer decide whether it wants a texture atlas, individual
 * files or a software blitter. Frames are addressed by their index in reading order, which is what the
 * {@link VisualLibrary} builder uses to turn ranges such as "frames 8 to 15" into animations.
 */
public final class SpriteSheet {

    private final String id;
    private final String imagePath;
    private final int frameWidth;
    private final int frameHeight;
    private final int columns;
    private final int rows;
    private final float pivotX;
    private final float pivotY;

    public SpriteSheet(
            String id,
            String imagePath,
            int frameWidth,
            int frameHeight,
            int columns,
            int rows) {
        this(id, imagePath, frameWidth, frameHeight, columns, rows, frameWidth / 2f, frameHeight);
    }

    public SpriteSheet(
            String id,
            String imagePath,
            int frameWidth,
            int frameHeight,
            int columns,
            int rows,
            float pivotX,
            float pivotY) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("sheet id must not be blank");
        }
        this.imagePath = Objects.requireNonNull(imagePath, "imagePath");
        if (imagePath.isBlank()) {
            throw new IllegalArgumentException("sheet '" + id + "' needs an image path");
        }
        if (frameWidth <= 0 || frameHeight <= 0) {
            throw new IllegalArgumentException("sheet '" + id + "' must have a positive frame size, was "
                    + frameWidth + "x" + frameHeight);
        }
        if (columns <= 0 || rows <= 0) {
            throw new IllegalArgumentException("sheet '" + id + "' must have positive dimensions, was "
                    + columns + "x" + rows);
        }
        this.id = id;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.columns = columns;
        this.rows = rows;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
    }

    public String id() {
        return id;
    }

    /** Resource relative path of the image, for example {@code sprites/soldier.png}. */
    public String imagePath() {
        return imagePath;
    }

    public int frameWidth() {
        return frameWidth;
    }

    public int frameHeight() {
        return frameHeight;
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    /** Number of frames the grid holds; frames beyond the ones a clip uses are simply ignored. */
    public int frameCount() {
        return columns * rows;
    }

    /** Width of the image in pixels. */
    public int imageWidth() {
        return columns * frameWidth;
    }

    /** Height of the image in pixels. */
    public int imageHeight() {
        return rows * frameHeight;
    }

    /** Default anchor of frames of this sheet, in pixels from the top left of a frame. */
    public float pivotX() {
        return pivotX;
    }

    /** Default anchor of frames of this sheet, in pixels from the top left of a frame. */
    public float pivotY() {
        return pivotY;
    }

    public boolean contains(int index) {
        return index >= 0 && index < frameCount();
    }

    /** Column of the given frame index. */
    public int columnOf(int index) {
        require(index);
        return index % columns;
    }

    /** Row of the given frame index. */
    public int rowOf(int index) {
        require(index);
        return index / columns;
    }

    /** Left edge of the given frame inside the image, in pixels. */
    public int pixelX(int index) {
        return columnOf(index) * frameWidth;
    }

    /** Top edge of the given frame inside the image, in pixels. */
    public int pixelY(int index) {
        return rowOf(index) * frameHeight;
    }

    /**
     * Builds the frame at the given index, using this sheet's default pivot.
     *
     * @throws IndexOutOfBoundsException when the index is negative or beyond {@link #frameCount()}
     */
    public SpriteFrame frame(int index) {
        require(index);
        return new SpriteFrame(
                id,
                index,
                pixelX(index),
                pixelY(index),
                frameWidth,
                frameHeight,
                pivotX,
                pivotY,
                0);
    }

    /** Builds the frame at the given index with an explicit anchor. */
    public SpriteFrame frame(int index, float framePivotX, float framePivotY) {
        return frame(index).withPivot(framePivotX, framePivotY);
    }

    /** Builds the frame at the given index with an explicit per frame duration. */
    public SpriteFrame frameWithDuration(int index, int durationTicks) {
        return frame(index).withDuration(durationTicks);
    }

    private void require(int index) {
        if (index < 0 || index >= frameCount()) {
            throw new IndexOutOfBoundsException("sheet '" + id + "' has " + frameCount()
                    + " frames, index " + index + " is out of range");
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SpriteSheet sheet && id.equals(sheet.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "SpriteSheet{" + id + " " + imagePath + " " + columns + "x" + rows
                + " of " + frameWidth + "x" + frameHeight + "}";
    }
}
