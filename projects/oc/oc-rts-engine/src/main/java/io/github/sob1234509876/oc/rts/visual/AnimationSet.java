package io.github.sob1234509876.oc.rts.visual;

import io.github.sob1234509876.oc.rts.core.Direction;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Every animation of one sprite set, i.e. of one {@code spriteSetId} of a unit or building type.
 *
 * <p>An animation set holds a flat set of named clips ({@code idle}, {@code move}, {@code attack},
 * {@code death}, ...) and, optionally, a directional variant per compass direction. Directional clips
 * live in their own map rather than behind a generated name such as {@code move_north} so that a set
 * can mix both styles: a hovering drone uses one clip for every direction, a soldier uses eight.
 *
 * <p>Lookups never fail silently for the caller that needs a clip: {@link #clip(String)} throws, while
 * {@link #findClip(String)} returns an {@link Optional} for the tolerant code paths of the simulation.
 * Both fall back to the non directional clip when a direction was asked for and that direction has no
 * variant, which is what makes an eight directional set and a single sprite set interchangeable.
 *
 * <p>The builder example of {@link VisualLibrary} shows how instances are created; they are immutable
 * once built.
 */
public final class AnimationSet {

    private final String spriteSetId;
    private final Map<String, AnimationClip> clips;
    private final Map<Direction, Map<String, AnimationClip>> directionalClips;

    public AnimationSet(String spriteSetId, Map<String, AnimationClip> clips) {
        this(spriteSetId, clips, Map.of());
    }

    public AnimationSet(
            String spriteSetId,
            Map<String, AnimationClip> clips,
            Map<Direction, Map<String, AnimationClip>> directionalClips) {
        if (spriteSetId == null || spriteSetId.isBlank()) {
            throw new IllegalArgumentException("spriteSetId must not be blank");
        }
        this.spriteSetId = spriteSetId;
        this.clips = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(clips, "clips")));
        this.directionalClips = copyDirectional(directionalClips);
    }

    private static Map<Direction, Map<String, AnimationClip>> copyDirectional(
            Map<Direction, Map<String, AnimationClip>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<Direction, Map<String, AnimationClip>> copy = new EnumMap<>(Direction.class);
        source.forEach((direction, byName) -> {
            if (direction != null && byName != null && !byName.isEmpty()) {
                copy.put(direction, Collections.unmodifiableMap(new LinkedHashMap<>(byName)));
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    /** An empty set, used when content declares no animations at all. */
    public static AnimationSet empty(String spriteSetId) {
        return new AnimationSet(spriteSetId, Map.of());
    }

    public String spriteSetId() {
        return spriteSetId;
    }

    /** Direction independent clips by name, in declaration order. */
    public Map<String, AnimationClip> clips() {
        return clips;
    }

    public boolean hasClip(String name) {
        return name != null && clips.containsKey(name);
    }

    /**
     * The named clip.
     *
     * @throws NoSuchElementException when the set has no such clip
     */
    public AnimationClip clip(String name) {
        AnimationClip clip = clips.get(name);
        if (clip == null) {
            throw new NoSuchElementException("animation set '" + spriteSetId
                    + "' has no clip '" + name + "'; available: " + clips.keySet());
        }
        return clip;
    }

    public Optional<AnimationClip> findClip(String name) {
        return Optional.ofNullable(name == null ? null : clips.get(name));
    }

    /** Directional variants of every clip, empty when the set has none. */
    public Map<Direction, Map<String, AnimationClip>> directionalClips() {
        return directionalClips;
    }

    public boolean hasDirectionalClips() {
        return !directionalClips.isEmpty();
    }

    public boolean hasDirectionalClip(Direction facing, String name) {
        if (facing == null || name == null) {
            return false;
        }
        Map<String, AnimationClip> byName = directionalClips.get(facing);
        return byName != null && byName.containsKey(name);
    }

    /**
     * Clip to play for a facing: the directional variant when there is one, the plain clip otherwise.
     *
     * @throws NoSuchElementException when neither the direction nor the set has that clip
     */
    public AnimationClip clip(String name, Direction facing) {
        return findClip(name, facing).orElseThrow(() -> new NoSuchElementException(
                "animation set '" + spriteSetId + "' has no clip '" + name + "'"
                        + (facing == null ? "" : " and no '" + facing.id() + "' variant of it")
                        + "; available: " + clips.keySet()));
    }

    public Optional<AnimationClip> findClip(String name, Direction facing) {
        if (name == null) {
            return Optional.empty();
        }
        if (facing != null) {
            Map<String, AnimationClip> byName = directionalClips.get(facing);
            if (byName != null) {
                AnimationClip directional = byName.get(name);
                if (directional != null) {
                    return Optional.of(directional);
                }
            }
        }
        return findClip(name);
    }

    /** Names of all clips of this set, directional ones included, without duplicates. */
    public Set<String> clipNames() {
        Set<String> names = new LinkedHashSet<>(clips.keySet());
        for (Map<String, AnimationClip> byName : directionalClips.values()) {
            names.addAll(byName.keySet());
        }
        return Collections.unmodifiableSet(names);
    }

    @Override
    public String toString() {
        return "AnimationSet{" + spriteSetId + ", clips=" + clips.keySet()
                + ", directional=" + directionalClips.size() + " directions}";
    }
}
