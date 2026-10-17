package io.github.sob1234509876.oc.rts.content;

/**
 * One complete game catalogue behind a seam, so a host can run content it was never compiled against.
 *
 * <p>A world is nothing but a {@link ContentRegistry} plus a seed: every terrain, item, unit, building and
 * recipe the simulation may touch is data. {@code WorldContent} is the smallest possible description of
 * that data - an identity, a human readable summary, the namespace its keys live in, and the method that
 * fills a catalogue - which is what lets a server, a launcher or a test pick a game without importing the
 * module that defines it.
 *
 * <p><strong>How content registers.</strong> The engine's registration API is the fluent
 * {@link ContentRegistry.Builder}; a {@link ContentRegistry} is immutable once built. {@link #populate}
 * therefore receives a builder and appends to it, and whoever needs a registry creates the builder with
 * {@link #namespace()} and calls {@link ContentRegistry.Builder#build()} afterwards:
 *
 * <pre>{@code
 * ContentRegistry.Builder builder = ContentRegistry.builder(worldContent.namespace());
 * worldContent.populate(builder);
 * ContentRegistry content = builder.build();
 * }</pre>
 *
 * <p><strong>What a content is not.</strong> It holds no engine, no world and no state: the same instance
 * may be used to build any number of registries, and two hosts built from it are independent.
 *
 * @see ScenarioWorldContent the engine's default implementation
 */
public interface WorldContent {

    /**
     * Stable identity of this content, for logs, diagnostics and asset manifests; for example
     * {@code oc-classic}. It is not a {@link ResourceKey} namespace - see {@link #namespace()}.
     */
    String id();

    /** One line describing the game this content defines, shown in logs and diagnostics. */
    String description();

    /**
     * The {@link ResourceKey} namespace every definition of this content is registered under, for example
     * {@code oc}. It has to match the namespace pattern of {@link ResourceKey}: lower case letters, digits
     * and underscores.
     */
    String namespace();

    /**
     * Adds every terrain, item, unit, building and recipe of this content to the given catalogue builder.
     *
     * <p>The builder is created and built by the caller, which is also where a broken catalogue fails: an
     * unknown item reference or a duplicate definition is reported by {@link ContentRegistry.Builder#build()}
     * rather than silently ignored.
     *
     * @param registry the builder to append to; it must have been created with {@link #namespace()}
     */
    void populate(ContentRegistry.Builder registry);

    /**
     * Items a new player of this content starts with, addressed by item path inside {@link #namespace()}.
     *
     * <p>The host that creates the world deposits them into the player's warehouses once the starting scene
     * exists; see {@code WorldHost}. The default is {@link StartingResources#EMPTY}, which is what a game
     * that builds its economy from scratch wants.
     *
     * @return the starting stock, never {@code null}
     */
    default StartingResources startingResources() {
        return StartingResources.EMPTY;
    }
}
