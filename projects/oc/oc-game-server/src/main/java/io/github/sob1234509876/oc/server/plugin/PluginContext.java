package io.github.sob1234509876.oc.server.plugin;

import io.github.sob1234509876.oc.rts.content.WorldContent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What the server hands to a plugin while it is loaded: where the plugins live, where this plugin may keep
 * its own files, the server version it was built against, the server's logger, the request surface the
 * plugin may publish into and the content seam it may claim the world with.
 *
 * <p>The context is deliberately small. A plugin gets no reference to the server object graph, so the server
 * stays free to change internals without breaking plugins, and an unloaded plugin cannot keep the server
 * alive through a field of its own. {@link #requests()} is the one exception, and it is a narrow one: the
 * registry holds opaque byte-in/byte-out handlers and gives no route from a request to a socket, a user
 * record or a running game. {@link #registerWorldContent(WorldContent)} is the other: it hands the server a
 * catalogue to serve, which is data the engine already knows how to run, not a way into the running world.
 *
 * <p>A plugin <em>receives</em> a context and never builds one itself; the constructors exist for the loader
 * and for the self check, which assembles a context to drive a plugin directly over no socket at all.
 */
public final class PluginContext {

    private final Path pluginsDirectory;
    private final Path dataDirectory;
    private final String serverVersion;
    private final System.Logger logger;
    private final RequestRegistry requests;

    /**
     * The world content registered so far, in registration order.
     *
     * <p>The list is owned by whoever created the context - the {@link PluginLoader} passes the one list it
     * exposes through {@link PluginLoader#worldContents()} to every context of one startup - so a plugin can
     * only ever append, and the server reads the same order the plugins were loaded in. A context built by a
     * test gets a list of its own, which is what makes the seam testable without a jar.
     */
    private final List<WorldContent> worldContents;

    /**
     * Creates a context for a server that publishes no requests and serves no registered content.
     *
     * <p>This is the four argument form the plugin loader and the self check were written against. It
     * behaves exactly as it always did and reports an empty {@link #requests()}, so a plugin that wants to
     * be reachable must be loaded by a server that passed a registry to it.
     *
     * @param pluginsDirectory the folder the jars were read from
     * @param dataDirectory the root of the per plugin data directories
     * @param serverVersion the version of the running server
     * @param logger the logger to use
     */
    public PluginContext(Path pluginsDirectory, Path dataDirectory, String serverVersion,
            System.Logger logger) {
        this(pluginsDirectory, dataDirectory, serverVersion, logger, new RequestRegistry());
    }

    /**
     * Creates a context whose requests reach the given registry and whose content registrations are its own.
     *
     * @param pluginsDirectory the folder the jars were read from; a plugin may look at it, but the server
     *     owns it and may replace every file in it
     * @param dataDirectory the root of the per plugin data directories; plugin state belongs under it, never
     *     in the plugin's own jar, because a jar is replaced when the plugin is updated
     * @param serverVersion the version of the running server, so a plugin can refuse to run against an
     *     incompatible one
     * @param logger the logger to use; the server's log files and console are the only place a plugin's
     *     messages reach an operator
     * @param requests the registry a plugin publishes its read-only request handlers into
     */
    public PluginContext(Path pluginsDirectory, Path dataDirectory, String serverVersion,
            System.Logger logger, RequestRegistry requests) {
        this(pluginsDirectory, dataDirectory, serverVersion, logger, requests, new ArrayList<>());
    }

    /**
     * Creates a context that appends content registrations to a list the caller also owns.
     *
     * <p>This is the form the {@link PluginLoader} uses: it keeps one list for the whole startup, hands it
     * to every plugin's context, and exposes it afterwards through {@link PluginLoader#worldContents()}, so
     * the load order of the plugins is the order of the content they registered.
     *
     * @param pluginsDirectory the folder the jars were read from
     * @param dataDirectory the root of the per plugin data directories
     * @param serverVersion the version of the running server
     * @param logger the logger to use
     * @param requests the registry a plugin publishes its read-only request handlers into
     * @param worldContents the live list registrations are appended to; the caller keeps ownership of it
     */
    public PluginContext(Path pluginsDirectory, Path dataDirectory, String serverVersion,
            System.Logger logger, RequestRegistry requests, List<WorldContent> worldContents) {
        this.pluginsDirectory = Objects.requireNonNull(pluginsDirectory, "pluginsDirectory");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.serverVersion = Objects.requireNonNull(serverVersion, "serverVersion");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.requests = Objects.requireNonNull(requests, "requests");
        this.worldContents = Objects.requireNonNull(worldContents, "worldContents");
    }

    /**
     * The folder the jars were read from.
     *
     * @return the plugin directory, absolute and normalised
     */
    public Path pluginsDirectory() {
        return pluginsDirectory;
    }

    /**
     * The root of the per plugin data directories.
     *
     * @return the data root, absolute and normalised; this plugin's own directory is
     *     {@link #dataDirectoryOf(String)}
     */
    public Path dataDirectory() {
        return dataDirectory;
    }

    /**
     * The version of the server this plugin was loaded by.
     *
     * @return the version string, for example {@code 2.0a}
     */
    public String serverVersion() {
        return serverVersion;
    }

    /**
     * The logger of the server.
     *
     * @return the logger a plugin should use instead of writing to the console itself
     */
    public System.Logger logger() {
        return logger;
    }

    /**
     * The request surface a plugin may publish into.
     *
     * <p>A plugin registers the names it answers under with {@link RequestRegistry#register(String,
     * RequestHandler)} and removes them again in {@link Plugin#onDisable()}. The server relays an
     * authenticated client's payload to the matching handler and sends the handler's bytes back without
     * looking at them, so what a plugin exposes here is its own public protocol: keep it read-only and, if
     * the information is sensitive, encrypt the payload with a key of the plugin's own.
     *
     * @return the live registry, shared by every plugin of this server
     */
    public RequestRegistry requests() {
        return requests;
    }

    /**
     * Claims the game world for a catalogue this plugin provides.
     *
     * <p>This is how a content plugin turns the server into <em>its</em> game: the catalogue says which
     * terrains, items, units, buildings and recipes exist, and the server builds its world from the one
     * content it selected. Registering is not starting: the server decides at startup which registration it
     * serves; see {@code OcGameServer} for the rule and why it is the rule.
     *
     * <p>Registration belongs in {@link Plugin#onLoad(PluginContext)}. A plugin may register more than one
     * catalogue, and a second plugin may register one as well; both are kept in load order so the server -
     * and an operator reading the log - can see exactly what was offered.
     *
     * @param content the catalogue to offer, never {@code null}
     * @throws NullPointerException when the content is {@code null}
     */
    public void registerWorldContent(WorldContent content) {
        worldContents.add(Objects.requireNonNull(content, "content"));
    }

    /**
     * The catalogues registered through this context so far, in registration order.
     *
     * <p>An unmodifiable snapshot of the live list: the loader exposes the same registrations through
     * {@link PluginLoader#worldContents()} after the whole folder was loaded. A plugin should treat this as
     * read-only information - what other plugins offered - and never as a way to pick the world.
     *
     * @return the registered content, in registration order; empty when none was registered
     */
    public List<WorldContent> worldContents() {
        return List.copyOf(worldContents);
    }

    /**
     * Returns this plugin's own storage directory and creates it (and its parents) when it is not there yet.
     *
     * <p>A plugin is given a directory of its own rather than the data root, so two plugins cannot overwrite
     * each other's files by accident and removing a plugin is removing one directory.
     *
     * <p>The name is validated because it becomes a path element: it must be a single, harmless name, so the
     * {@code /}, {@code \} and {@code ..} that could climb out of the data root are rejected. The colon is
     * rejected as well, and on Windows that is not theoretical: {@code resolve("C:evil")} yields the
     * drive relative path {@code C:evil}, which leaves the data directory entirely.
     *
     * @param pluginName the name of the plugin, normally {@link Plugin#name()}
     * @return the existing directory {@code dataDirectory/<pluginName>/}
     * @throws IllegalArgumentException when the name is blank or contains a path separator, {@code ..} or a
     *     colon
     * @throws UncheckedIOException when the directory cannot be created
     */
    public Path dataDirectoryOf(String pluginName) {
        String name = requireSafeName(pluginName);
        Path root = dataDirectory.normalize();
        Path directory = root.resolve(name).normalize();
        // The character checks above should already make an escape impossible; this is the cheap second
        // opinion, so a platform whose separators differ from the ones we know cannot turn a name into a path
        // out of the data directory.
        if (!directory.startsWith(root)) {
            throw new IllegalArgumentException("plugin name escapes the data directory: " + pluginName);
        }
        try {
            return Files.createDirectories(directory);
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot create the data directory of plugin " + pluginName
                    + ": " + directory, failure);
        }
    }

    private static String requireSafeName(String pluginName) {
        String name = Objects.requireNonNull(pluginName, "pluginName");
        if (name.isBlank()) {
            throw new IllegalArgumentException("plugin name must not be blank");
        }
        if (name.contains("/") || name.contains("\\") || name.contains("..") || name.contains(":")) {
            throw new IllegalArgumentException(
                    "plugin name must be a single name without '/', '\\', '..' or ':': " + pluginName);
        }
        return name;
    }
}
