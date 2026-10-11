package io.github.sob1234509876.oc.rts.visual;

import io.github.sob1234509876.oc.rts.core.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Lookup table from content ids to sprite sheets and animations.
 *
 * <p>Unit and building types name their animations through {@code spriteSetId}, terrain names a sprite
 * through {@code spriteId}, and animations refer to frames of a sheet by id. The library is the single
 * place that resolves those strings, so neither the simulation nor a future renderer ever has to know
 * what a file is called or where a frame sits in an image.
 *
 * <p>A library is immutable after {@link Builder#build()} and cheap to share: the animation system
 * reads it every tick and a renderer may keep one instance for the whole match.
 *
 * <h2>Building a library</h2>
 *
 * <p>The builder walks the same order a person reads a sprite sheet: declare a sheet, then describe
 * clips as ranges of frames of that sheet. {@code sheet(id)} starts a sheet, {@code set(id)} starts (or
 * continues) a sprite set, {@code clip(name)} starts (or continues) a clip inside it and
 * {@code direction(...)} switches the following frame calls to a directional variant of that clip.
 * Every call keeps its current target, so a whole library reads as one chain:
 *
 * <pre>{@code
 * VisualLibrary library = VisualLibrary.builder()
 *         .sheet("soldier").image("sprites/soldier.png").frameSize(32, 32).columns(8)
 *         .set("soldier").clip("idle").frames("soldier", 0, 4).ticksPerFrame(6).loop()
 *         .set("soldier").clip("move").frames("soldier", 8, 8).ticksPerFrame(4).loop()
 *         .set("soldier").clip("death").frames("soldier", 24, 6).ticksPerFrame(8)
 *         .build();
 *
 * AnimationSet soldier = library.animationSet("soldier");
 * AnimationClip idle = soldier.clip("idle");            // 4 frames, loops, 6 ticks each
 * SpriteFrame frame = idle.frameAt(7);                  // frame index 1
 * SpriteSheet sheet = library.sheet(frame.sheetId());   // "sprites/soldier.png"
 * }</pre>
 *
 * <p>Directional variants use the same calls after {@code direction(...)}, which is why a set may mix
 * one clip for every direction with eight variants of another:
 *
 * <pre>{@code
 * VisualLibrary directional = VisualLibrary.builder()
 *         .sheet("soldier").image("sprites/soldier.png").frameSize(32, 32).columns(8)
 *         .sheet("soldier_n").image("sprites/soldier_north.png").frameSize(32, 32).columns(8)
 *         .set("soldier").clip("move").frames("soldier", 0, 8).ticksPerFrame(4).loop()
 *         .set("soldier").clip("move").direction(Direction.NORTH)
 *                 .frames("soldier_n", 0, 8).ticksPerFrame(4).loop()
 *         .build();
 *
 * AnimationClip north = directional.animationSet("soldier").clip("move", Direction.NORTH);
 * AnimationClip south = directional.animationSet("soldier").clip("move", Direction.SOUTH); // falls back
 * }</pre>
 *
 * <h2>How much has to be declared</h2>
 *
 * <p>{@code columns(n)} says "n frames per row" and lets the sheet grow to as many rows as the frames a
 * clip actually references need, so the first example does not have to state that a 32 frame sheet is
 * 8 by 4. Declaring {@code rows(m)}, or {@code frameCount(k)} which implies it, pins the sheet size and
 * turns a reference beyond it into an error. Mistakes are always reported as an
 * {@link IllegalStateException} naming the step that caused them, because a typo in a sprite id would
 * otherwise only show up as a silently missing animation during a match.
 */
public final class VisualLibrary {

    private static final VisualLibrary EMPTY = new VisualLibrary(Map.of(), Map.of());

    private final Map<String, SpriteSheet> sheets;
    private final Map<String, AnimationSet> animationSets;

    private VisualLibrary(Map<String, SpriteSheet> sheets, Map<String, AnimationSet> animationSets) {
        this.sheets = Collections.unmodifiableMap(new LinkedHashMap<>(sheets));
        this.animationSets = Collections.unmodifiableMap(new LinkedHashMap<>(animationSets));
    }

    /** A library without sheets or animations; every lookup is empty and nothing is animated. */
    public static VisualLibrary empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ------------------------------------------------------------------ sheets

    /**
     * The sheet with the given id.
     *
     * @throws NoSuchElementException when no such sheet was declared
     */
    public SpriteSheet sheet(String id) {
        SpriteSheet sheet = sheets.get(id);
        if (sheet == null) {
            throw new NoSuchElementException("no sprite sheet '" + id + "'; declared sheets: " + sheets.keySet());
        }
        return sheet;
    }

    public Optional<SpriteSheet> findSheet(String id) {
        return Optional.ofNullable(id == null ? null : sheets.get(id));
    }

    /** All sheets by id, in declaration order. */
    public Map<String, SpriteSheet> sheets() {
        return sheets;
    }

    public int sheetCount() {
        return sheets.size();
    }

    // ------------------------------------------------------------------ animation sets

    /**
     * The animation set with the given {@code spriteSetId}.
     *
     * @throws NoSuchElementException when no such set was declared
     */
    public AnimationSet animationSet(String spriteSetId) {
        AnimationSet set = animationSets.get(spriteSetId);
        if (set == null) {
            throw new NoSuchElementException("no animation set '" + spriteSetId + "'"
                    + "; declared sets: " + animationSets.keySet());
        }
        return set;
    }

    public Optional<AnimationSet> findAnimationSet(String spriteSetId) {
        return Optional.ofNullable(spriteSetId == null ? null : animationSets.get(spriteSetId));
    }

    public boolean hasAnimationSet(String spriteSetId) {
        return spriteSetId != null && animationSets.containsKey(spriteSetId);
    }

    /** All animation sets by {@code spriteSetId}, in declaration order. */
    public Map<String, AnimationSet> animationSets() {
        return animationSets;
    }

    public int animationSetCount() {
        return animationSets.size();
    }

    @Override
    public String toString() {
        return "VisualLibrary{" + sheets.size() + " sheets, " + animationSets.size() + " animation sets}";
    }

    // ------------------------------------------------------------------ builder

    /**
     * Fluent builder of a {@link VisualLibrary}; see the class documentation for a complete example.
     */
    public static final class Builder {

        private final Map<String, SheetBuilder> sheetDrafts = new LinkedHashMap<>();
        private final Map<String, SetBuilder> setDrafts = new LinkedHashMap<>();

        private SheetBuilder sheet;
        private SetBuilder set;

        private Builder() {
        }

        /** Starts the declaration of a sprite sheet. */
        public SheetBuilder sheet(String id) {
            String key = blankRejected(id, "sheet id");
            if (sheetDrafts.containsKey(key)) {
                throw new IllegalStateException("sheet '" + key + "' was already declared; "
                        + "a sprite sheet exists once and every clip refers to it by id");
            }
            sheet = new SheetBuilder(this, key);
            sheetDrafts.put(key, sheet);
            set = null;
            return sheet;
        }

        /**
         * Starts, or continues, the animation set with the given {@code spriteSetId}.
         *
         * <p>Naming a set that was already started continues it, which lets a library be written sheet
         * by sheet while its sets grow across several chains; a clip that already has frames is still
         * rejected when more are added to it, so nothing is silently overwritten.
         */
        public SetBuilder set(String spriteSetId) {
            String key = blankRejected(spriteSetId, "sprite set id");
            set = setDrafts.computeIfAbsent(key, id -> new SetBuilder(this, id));
            return set;
        }

        /** Finishes the library; the result is immutable. */
        public VisualLibrary build() {
            Map<String, SpriteSheet> sheets = new LinkedHashMap<>();
            sheetDrafts.forEach((id, draft) -> sheets.put(id, draft.toSheet()));
            Map<String, AnimationSet> sets = new LinkedHashMap<>();
            setDrafts.forEach((id, draft) -> sets.put(id, draft.toAnimationSet()));
            return new VisualLibrary(sheets, sets);
        }

        private SetBuilder currentSet(String operation) {
            if (set == null) {
                throw new IllegalStateException(operation + " needs a current animation set; "
                        + "call set(\"spriteSetId\") first (while building: " + describe() + ")");
            }
            return set;
        }

        private SheetBuilder sheetDraft(String sheetId, String step) {
            if (sheetId == null || sheetId.isBlank()) {
                throw new IllegalStateException(step + " needs a sheet id");
            }
            SheetBuilder draft = sheetDrafts.get(sheetId);
            if (draft == null) {
                throw new IllegalStateException(step + " refers to sheet '" + sheetId
                        + "', which was not declared; declare it with sheet(\"" + sheetId + "\")"
                        + " before using its frames (declared sheets: " + sheetDrafts.keySet() + ")");
            }
            return draft;
        }

        private String describe() {
            return "sheet=" + (sheet == null ? "none" : sheet.id())
                    + ", set=" + (set == null ? "none" : set.spriteSetId)
                    + ", declared sheets=" + sheetDrafts.keySet()
                    + ", declared sets=" + setDrafts.keySet();
        }

        private static String blankRejected(String value, String what) {
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(what + " must not be blank");
            }
            return value;
        }
    }

    /** Declares one sprite sheet; see {@link Builder#sheet(String)}. */
    public static final class SheetBuilder {

        private final Builder owner;
        private final String id;
        private String imagePath;
        private int frameWidth = -1;
        private int frameHeight = -1;
        private int columns = -1;
        private int rows = -1;
        private float pivotX = Float.NaN;
        private float pivotY = Float.NaN;
        private int highestFrame = -1;

        private SheetBuilder(Builder owner, String id) {
            this.owner = owner;
            this.id = id;
        }

        public String id() {
            return id;
        }

        /** Resource relative path of the image, for example {@code sprites/soldier.png}. */
        public SheetBuilder image(String path) {
            if (path == null || path.isBlank()) {
                throw new IllegalStateException("sheet '" + id + "' needs a non blank image path");
            }
            this.imagePath = path;
            return this;
        }

        /** Size of a single frame in pixels. */
        public SheetBuilder frameSize(int width, int height) {
            if (width <= 0 || height <= 0) {
                throw new IllegalStateException("sheet '" + id + "' needs a positive frame size, was "
                        + width + "x" + height);
            }
            this.frameWidth = width;
            this.frameHeight = height;
            return this;
        }

        /** Number of frames per row; the sheet gets as many rows as the clips need. */
        public SheetBuilder columns(int value) {
            if (value <= 0) {
                throw new IllegalStateException("sheet '" + id + "' needs a positive column count, was " + value);
            }
            this.columns = value;
            return this;
        }

        /** Pins the number of rows, which turns a frame reference beyond the sheet into an error. */
        public SheetBuilder rows(int value) {
            if (value <= 0) {
                throw new IllegalStateException("sheet '" + id + "' needs a positive row count, was " + value);
            }
            this.rows = value;
            return this;
        }

        /** Pins the number of frames, for example {@code 32} for a strip of 32 frames in one row. */
        public SheetBuilder frameCount(int value) {
            if (value <= 0) {
                throw new IllegalStateException("sheet '" + id + "' needs a positive frame count, was " + value);
            }
            if (columns > 0) {
                if (value % columns != 0) {
                    throw new IllegalStateException("sheet '" + id + "' cannot hold " + value
                            + " frames in rows of " + columns + " columns");
                }
                this.rows = value / columns;
            } else {
                this.columns = value;
                this.rows = 1;
            }
            return this;
        }

        /** Default anchor of every frame of this sheet, in pixels from the top left of a frame. */
        public SheetBuilder pivot(float x, float y) {
            this.pivotX = x;
            this.pivotY = y;
            return this;
        }

        /** Whether the frame count of this sheet was pinned instead of derived from the clips. */
        public boolean hasPinnedFrameCount() {
            return rows > 0;
        }

        /** Frames this sheet currently holds: the pinned count, or what the clips need so far. */
        public int frameCount() {
            requireGrid();
            return columns * (rows > 0 ? rows : Math.max(1, highestFrame / columns + 1));
        }

        /** The sheet as it will appear in the library. */
        public SpriteSheet toSheet() {
            requireGrid();
            return new SpriteSheet(
                    id,
                    imagePath,
                    frameWidth,
                    frameHeight,
                    columns,
                    rows > 0 ? rows : Math.max(1, highestFrame / columns + 1),
                    Float.isNaN(pivotX) ? frameWidth / 2f : pivotX,
                    Float.isNaN(pivotY) ? frameHeight : pivotY);
        }

        /**
         * Builds the frame at the given index and records that the clips reach this far.
         *
         * @throws IllegalStateException when an image, frame size or column count is still missing, or
         *         when the index is beyond a pinned sheet
         */
        public SpriteFrame frame(int index) {
            requireGrid();
            if (index < 0) {
                throw new IllegalStateException("sheet '" + id + "' cannot provide frame " + index);
            }
            if (rows > 0 && index >= columns * rows) {
                throw new IllegalStateException("sheet '" + id + "' holds " + (columns * rows)
                        + " frames but frame " + index + " was requested; enlarge it with columns(n), "
                        + "rows(m) or frameCount(k)");
            }
            highestFrame = Math.max(highestFrame, index);
            int column = index % columns;
            int row = index / columns;
            return new SpriteFrame(
                    id,
                    index,
                    column * frameWidth,
                    row * frameHeight,
                    frameWidth,
                    frameHeight,
                    Float.isNaN(pivotX) ? frameWidth / 2f : pivotX,
                    Float.isNaN(pivotY) ? frameHeight : pivotY,
                    0);
        }

        /** Declares another sprite sheet, as {@link Builder#sheet(String)} does. */
        public SheetBuilder sheet(String sheetId) {
            return owner.sheet(sheetId);
        }

        /** Starts the declaration of an animation set, as {@link Builder#set(String)} does. */
        public SetBuilder set(String spriteSetId) {
            return owner.set(spriteSetId);
        }

        /** Finishes the library, as {@link Builder#build()} does. */
        public VisualLibrary build() {
            return owner.build();
        }

        private void requireGrid() {
            if (imagePath == null) {
                throw new IllegalStateException("sheet '" + id + "' has no image; call image(\"path.png\")");
            }
            if (frameWidth <= 0 || frameHeight <= 0) {
                throw new IllegalStateException("sheet '" + id + "' has no frame size; call frameSize(w, h)");
            }
            if (columns <= 0) {
                throw new IllegalStateException("sheet '" + id + "' has no frame grid; call columns(n), "
                        + "or frameCount(k) for a single row (declared: columns=" + columns + ", rows=" + rows + ")");
            }
        }
    }

    /**
     * Declares, or extends, the animations of one {@code spriteSetId}; see {@link Builder#set(String)}.
     */
    public static final class SetBuilder {

        private final Builder owner;
        private final String spriteSetId;
        private final List<ClipDefinition> clips = new ArrayList<>();

        private SetBuilder(Builder owner, String spriteSetId) {
            this.owner = owner;
            this.spriteSetId = spriteSetId;
        }

        public String spriteSetId() {
            return spriteSetId;
        }

        /**
         * Starts a new clip, or continues a clip that already exists in this set.
         *
         * <p>Continuing is what makes {@code .clip("move").direction(NORTH).frames(...)} work after
         * {@code .clip("move").frames(...)} already defined the direction independent clip. Adding more
         * frames to a target that already has them is still rejected, so a duplicated clip is an error
         * rather than a silent merge.
         */
        public ClipBuilder clip(String name) {
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("clip name of animation set '" + spriteSetId + "' must not be blank");
            }
            String wanted = name;
            ClipDefinition definition = clips.stream()
                    .filter(existing -> existing.name.equals(wanted))
                    .findFirst()
                    .orElseGet(() -> {
                        ClipDefinition created = new ClipDefinition(wanted);
                        clips.add(created);
                        return created;
                    });
            return new ClipBuilder(definition);
        }

        /** Declares another sprite set, as {@link Builder#set(String)} does. */
        public SetBuilder set(String otherSpriteSetId) {
            return owner.set(otherSpriteSetId);
        }

        /** Declares a sprite sheet, as {@link Builder#sheet(String)} does. */
        public SheetBuilder sheet(String sheetId) {
            return owner.sheet(sheetId);
        }

        /** Finishes the library, as {@link Builder#build()} does. */
        public VisualLibrary build() {
            return owner.build();
        }

        /**
         * Assembles the animation set of this builder.
         *
         * <p>Named {@code toAnimationSet()} rather than {@code build()} because {@link #build()} of this
         * class finishes the whole library, and two methods called {@code build()} would make a chain
         * silently mean the wrong thing.
         */
        private AnimationSet toAnimationSet() {
            Map<String, AnimationClip> plain = new LinkedHashMap<>();
            Map<Direction, Map<String, AnimationClip>> directional = new EnumMap<>(Direction.class);
            for (ClipDefinition definition : clips) {
                String clip = "clip '" + definition.name + "' of animation set '" + spriteSetId + "'";
                if (definition.frames.isEmpty()) {
                    throw new IllegalStateException(clip + " has no frames; call frames(sheetId, start, count) "
                            + "or frame(sheetId, index)");
                }
                if (definition.ticksPerFrame <= 0) {
                    throw new IllegalStateException(clip + " has no frame duration; call ticksPerFrame(n)");
                }
                plain.put(definition.name, new AnimationClip(
                        definition.name, definition.frames, definition.ticksPerFrame, definition.loop));
                for (Map.Entry<Direction, ClipDefinition> entry : definition.directional.entrySet()) {
                    ClipDefinition variant = entry.getValue();
                    String variantName = "directional clip '" + definition.name + "' ("
                            + entry.getKey().id() + ") of animation set '" + spriteSetId + "'";
                    if (variant.frames.isEmpty()) {
                        throw new IllegalStateException(variantName + " has no frames; call "
                                + "frames(sheetId, start, count) or frame(sheetId, index) after direction(...)");
                    }
                    if (variant.ticksPerFrame <= 0) {
                        throw new IllegalStateException(variantName + " has no frame duration; call ticksPerFrame(n)");
                    }
                    directional.computeIfAbsent(entry.getKey(), key -> new LinkedHashMap<>())
                            .put(definition.name, new AnimationClip(definition.name,
                                    variant.frames, variant.ticksPerFrame, variant.loop));
                }
            }
            return new AnimationSet(spriteSetId, plain, directional);
        }

        /** Fluent definition of one clip and its directional variants. */
        public final class ClipBuilder {

            private final ClipDefinition definition;
            private ClipDefinition target;
            private boolean targetPrePopulated;

            private ClipBuilder(ClipDefinition definition) {
                this.definition = definition;
                this.target = definition;
                this.targetPrePopulated = !definition.frames.isEmpty();
            }

            public String name() {
                return definition.name;
            }

            /** Frames {@code startIndex} to {@code startIndex + count - 1} of the given sheet. */
            public ClipBuilder frames(String sheetId, int startIndex, int count) {
                String step = "frames(\"" + sheetId + "\", " + startIndex + ", " + count + ") of clip '"
                        + definition.name + "'";
                SheetBuilder sheet = owner.sheetDraft(sheetId, step);
                if (count <= 0) {
                    throw new IllegalStateException(step + " needs a positive frame count");
                }
                if (startIndex < 0) {
                    throw new IllegalStateException(step + " needs a non negative start index");
                }
                if (sheet.hasPinnedFrameCount() && startIndex + count > sheet.frameCount()) {
                    throw new IllegalStateException(step + " runs past the end of sheet '" + sheetId
                            + "', which holds " + sheet.frameCount() + " frames");
                }
                for (int index = startIndex; index < startIndex + count; index++) {
                    add(sheet.frame(index));
                }
                return this;
            }

            /** The single frame at the given index of the given sheet. */
            public ClipBuilder frame(String sheetId, int index) {
                String step = "frame(\"" + sheetId + "\", " + index + ") of clip '" + definition.name + "'";
                SheetBuilder sheet = owner.sheetDraft(sheetId, step);
                add(sheet.frame(index));
                return this;
            }

            /** Adds an already built frame, for clips that need a custom anchor or duration. */
            public ClipBuilder frame(SpriteFrame frame) {
                if (frame == null) {
                    throw new IllegalStateException("clip '" + definition.name + "' cannot add a null frame");
                }
                owner.sheetDraft(frame.sheetId(), "clip '" + definition.name + "'");
                add(frame);
                return this;
            }

            /** Default duration of every frame of the clip; must be positive before the library is built. */
            public ClipBuilder ticksPerFrame(int ticks) {
                if (ticks <= 0) {
                    throw new IllegalStateException("ticksPerFrame(" + ticks + ") of clip '" + definition.name
                            + "' must be positive");
                }
                target.ticksPerFrame = ticks;
                return this;
            }

            /** Makes the clip start over after its last frame. */
            public ClipBuilder loop() {
                target.loop = true;
                return this;
            }

            /** Makes the clip hold its last frame instead of repeating; this is the default. */
            public ClipBuilder once() {
                target.loop = false;
                return this;
            }

            /** Sets whether the clip repeats. */
            public ClipBuilder loop(boolean value) {
                target.loop = value;
                return this;
            }

            /**
             * Switches the following frame calls to a directional variant of this clip.
             *
             * <p>The variant inherits nothing: it starts with no frames, the default frame duration and no
             * looping, so every property is stated for it explicitly.
             */
            public ClipBuilder direction(Direction facing) {
                if (facing == null) {
                    throw new IllegalStateException("direction(null) is not a valid variant of clip '"
                            + definition.name + "'");
                }
                ClipDefinition existing = definition.directional.get(facing);
                target = existing != null ? existing : new ClipDefinition(definition.name);
                definition.directional.put(facing, target);
                targetPrePopulated = !target.frames.isEmpty();
                return this;
            }

            /** Switches back to the direction independent clip. */
            public ClipBuilder noDirection() {
                target = definition;
                targetPrePopulated = !definition.frames.isEmpty();
                return this;
            }

            /** Starts another clip of the same animation set. */
            public ClipBuilder clip(String name) {
                return owner.currentSet("clip(\"" + name + "\")").clip(name);
            }

            /** Starts another animation set. */
            public SetBuilder set(String spriteSetId) {
                return owner.set(spriteSetId);
            }

            /** Finishes the library, as {@link Builder#build()} does. */
            public VisualLibrary build() {
                return owner.build();
            }

            private void add(SpriteFrame frame) {
                if (targetPrePopulated) {
                    throw new IllegalStateException("the " + (target == definition
                            ? "clip '" + definition.name + "'"
                            : "'" + directionOf(target) + "' variant of clip '" + definition.name + "'")
                            + " of animation set '" + spriteSetId + "' already has frames; declare its frames "
                            + "in a single chain, or use direction(...) for another variant");
                }
                target.frames.add(frame);
            }

            private String directionOf(ClipDefinition variant) {
                for (Map.Entry<Direction, ClipDefinition> entry : definition.directional.entrySet()) {
                    if (entry.getValue() == variant) {
                        return entry.getKey().id();
                    }
                }
                return "unknown";
            }
        }
    }

    /** Mutable draft of one clip inside a {@link SetBuilder}. */
    private static final class ClipDefinition {

        private final String name;
        private final List<SpriteFrame> frames = new ArrayList<>();
        private final Map<Direction, ClipDefinition> directional = new EnumMap<>(Direction.class);
        private int ticksPerFrame = -1;
        private boolean loop;

        private ClipDefinition(String name) {
            this.name = name;
        }
    }
}
