package io.github.sob1234509876.oc.rts.content;

import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.map.NoiseSettings;

import java.util.Objects;

/**
 * One extra dimension a {@link WorldContent} declares, so a game can ship with more than one world
 * without the host knowing anything about it. The content names the dimension, gives it a display name
 * and tunes the noise terrain with a {@link NoiseSettings} whose six content paths point at the
 * terrain types {@link #populate(ContentRegistry.Builder)} registers - lava as the water of hell, for
 * example.
 *
 * <p>The host hands the spec to the engine builder, which derives the dimension's own seed from the
 * world seed and the dimension key: the same world seed always produces the same hell, and no two
 * dimensions ever share terrain.
 *
 * @param id          stable dimension key, for example {@code hell}
 * @param displayName the name players see, for example {@code Hell}
 * @param settings    noise terrain tuned with this content's own terrain paths
 */
public record DimensionContent(DimensionId id, String displayName, NoiseSettings settings) {

    public DimensionContent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("the display name of a dimension must not be blank");
        }
        Objects.requireNonNull(settings, "settings");
    }
}
