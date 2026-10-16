package io.github.sob1234509876.oc.server.plugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * What the server hands to a plugin while it is loaded: where the plugins live, where this plugin may keep
 * its own files, the server version it was built against, the server's logger and the request surface the
 * plugin may publish into.
 *
 * <p>The context is deliberately small. A plugin gets no reference to the server object graph, so the server
 * stays free to change internals without breaking plugins, and an unloaded plugin cannot keep the server
 * alive through a field of its own. {@link #requests()} is the one exception, and it is a narrow one: the
 * registry holds opaque byte-in/byte-out handlers and gives no route from a request to a socket, a user
 * record or a running game.
 *
 * <p>A plugin <em>receives</em> a context and never builds one itself; the constructor exists for the loader
 * and for the self check, which assembles a context to drive a plugin directly over no socket at all.
 */
public final class PluginContext {

    private final Path pluginsDirectory;
    private final Path dataDirectory;
    private final String serverVersion;
    private final System.Logger logger;
    private final RequestRegistry requests;

    /**
     * Creates a context for a server that publishes no requests.
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
     * Creates a context whose requests reach the given registry.
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
        this.pluginsDirectory = Objects.requireNonNull(pluginsDirectory, "pluginsDirectory");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.serverVersion = Objects.requireNonNull(serverVersion, "serverVersion");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.requests = Objects.requireNonNull(requests, "requests");
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
