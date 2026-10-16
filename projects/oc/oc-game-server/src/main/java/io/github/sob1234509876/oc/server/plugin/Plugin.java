package io.github.sob1234509876.oc.server.plugin;

/**
 * One plugin of the Omni Com server.
 *
 * <p>A plugin is an ordinary user supplied jar: the server knows nothing about it beyond this interface and
 * the entry in {@link PluginLoader}. Everything a plugin owns - game content, commands, the libraries it
 * dragged in - stays inside its own files, which is why the server never compiles against a plugin and why
 * an unwanted plugin can be deleted from the folder without touching the server.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>The loader drives a plugin through exactly three calls, in this order:
 *
 * <ol>
 *   <li>{@link #onLoad(PluginContext)} - called once, immediately after the no-argument constructor returns.
 *       The plugin should read its configuration, create its data directory and remember what the context
 *       hands it. The server is not listening yet, so a plugin that fails here cannot leave clients talking
 *       to a half loaded server: it is reported as a problem and never enabled.</li>
 *   <li>{@link #onEnable()} - called once, after {@code onLoad} succeeded, when the server treats the plugin
 *       as part of itself. Anything the plugin wants to publish while the game runs belongs here.</li>
 *   <li>{@link #onDisable()} - called once at shutdown, in load order, by
 *       {@link PluginLoader#unloadAll()}. It must release what the plugin holds (files, daemon threads,
 *       network connections); the server calls nothing on the plugin afterwards.</li>
 * </ol>
 *
 * <p>The three lifecycle methods have empty default bodies on purpose: a plugin that only contributes
 * static content implements nothing but {@link #name()} and {@link #version()}, and the loader never has to
 * special case it.
 *
 * <p>The loader instantiates a plugin with {@code getDeclaredConstructor().newInstance()}, so an
 * implementation needs a public no-argument constructor.
 *
 * <h2>Threads</h2>
 *
 * <p>A plugin must not start non-daemon threads. Such a thread keeps the JVM alive after the server has
 * stopped, and the symptom is a process that appears to hang at shutdown with no window and no log left to
 * explain it. Start a daemon thread ({@code thread.setDaemon(true)} before {@code start()}) or stop your
 * thread in {@link #onDisable()}.
 */
public interface Plugin {

    /**
     * The stable name of the plugin, used to look the plugin up and to name its data directory, so it must
     * not contain {@code /}, {@code \} or {@code ..} (see {@link PluginContext#dataDirectoryOf(String)}).
     */
    String name();

    /** The version of the plugin, in whatever format the plugin's author uses. */
    String version();

    /** A human readable one line summary; empty when the plugin has nothing to say. */
    default String description() {
        return "";
    }

    /**
     * Called once, right after construction, with everything the server offers a plugin. The plugin is not
     * enabled yet, and an exception thrown here disables it for good.
     */
    default void onLoad(PluginContext context) {
    }

    /** Called once after {@link #onLoad(PluginContext)} succeeded; the plugin is now part of the server. */
    default void onEnable() {
    }

    /** Called once at shutdown so the plugin can release what it holds. */
    default void onDisable() {
    }
}
