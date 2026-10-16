package io.github.sob1234509876.oc.server.plugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * What the server hands to a plugin while it is loaded: where the plugins live, where this plugin may keep
 * its own files, the server version it was built against, and the server's logger.
 *
 * <p>The record is deliberately small. A plugin gets no reference to the server object graph, so the server
 * stays free to change internals without breaking plugins, and an unloaded plugin cannot keep the server
 * alive through a field of its own.
 *
 * @param pluginsDirectory the folder the jars were read from; a plugin may look at it, but the server owns
 *     it and may replace every file in it
 * @param dataDirectory the root of the per plugin data directories; plugin state belongs under it, never in
 *     the plugin's own jar, because a jar is replaced when the plugin is updated
 * @param serverVersion the version of the running server, so a plugin can refuse to run against an
 *     incompatible one
 * @param logger the logger to use; the server's log files and console are the only place a plugin's
 *     messages reach an operator
 */
public record PluginContext(Path pluginsDirectory, Path dataDirectory, String serverVersion,
        System.Logger logger) {

    /** Rejects a context that would leave a plugin without the directories it is told to use. */
    public PluginContext {
        Objects.requireNonNull(pluginsDirectory, "pluginsDirectory");
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Objects.requireNonNull(serverVersion, "serverVersion");
        Objects.requireNonNull(logger, "logger");
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
