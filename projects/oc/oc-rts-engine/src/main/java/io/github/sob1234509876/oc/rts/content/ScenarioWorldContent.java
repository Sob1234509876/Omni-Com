package io.github.sob1234509876.oc.rts.content;

import java.util.Objects;

/**
 * The engine's sample catalogue ({@link ScenarioContent}) exposed through the {@link WorldContent} seam.
 *
 * <p>This is the content a host serves when it was given none of its own, and the wrapper that makes the
 * old catalogue reachable from code that only knows {@link WorldContent}: the server, the world host and
 * any future launcher depend on the seam, never on {@link ScenarioContent} directly.
 *
 * <p>It adds no behaviour. {@link #populate} delegates to {@link ScenarioContent#populate} and hands out no
 * starting resources, so a world built from it is exactly the sample world the engine ships.
 */
public final class ScenarioWorldContent implements WorldContent {

    /** Stable identity of this content. */
    public static final String ID = "oc-scenario";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "The engine's sample catalogue: ore, goods, soldiers, workers and medics, warehouses, "
                + "barracks and a workshop";
    }

    @Override
    public String namespace() {
        return ScenarioContent.NAMESPACE;
    }

    @Override
    public void populate(ContentRegistry.Builder registry) {
        ScenarioContent.populate(Objects.requireNonNull(registry, "registry"));
    }

    @Override
    public String toString() {
        return "WorldContent{" + ID + "}";
    }
}
