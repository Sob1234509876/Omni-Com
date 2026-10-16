package io.github.sob1234509876.oc.server.plugin;

import java.util.Optional;
import java.util.Properties;

/**
 * The metadata a plugin jar carries about itself, read from the small properties file named
 * {@link #RESOURCE} at the root of the jar.
 *
 * <p>A properties file is used rather than a manifest attribute or an annotation because it costs a plugin
 * author nothing: no extra dependency, no annotation processor, and the same four keys can be written by a
 * build script with one line. The remaining details of a plugin - the classes it contributes - are found by
 * {@link PluginLoader}, so the descriptor only has to name the entry point.
 *
 * <p>The file is read with {@link Properties#load(java.io.InputStream)}, which is the ISO-8859-1 properties
 * encoding, so non-ASCII text in {@code description} must be written as {@code \\uXXXX} escapes.
 *
 * @param id the identifier of the plugin, stable across versions because it names the plugin's data
 *     directory
 * @param version the version of the jar itself, not of the server
 * @param mainClass the binary name of the {@link Plugin} implementation to load first
 * @param description a one line summary for logs and listings; empty when absent
 */
public record PluginDescriptor(String id, String version, String mainClass, String description) {

    /** The name of the properties resource a loader looks for at the root of every plugin jar. */
    public static final String RESOURCE = "omnicom-plugin.properties";

    private static final String KEY_ID = "id";
    private static final String KEY_VERSION = "version";
    private static final String KEY_MAIN = "main";
    private static final String KEY_DESCRIPTION = "description";

    /**
     * Validates the three keys a loader cannot work without. A blank {@code id} would make the plugin's data
     * directory ambiguous, a blank {@code mainClass} would leave the jar with no entry point, and a blank
     * {@code version} would make log lines useless, so all three are refused here rather than at the point
     * of use, where the mistake is much harder to trace back to the jar.
     */
    public PluginDescriptor {
        id = requireText(id, KEY_ID);
        version = requireText(version, KEY_VERSION);
        mainClass = requireText(mainClass, KEY_MAIN);
        description = description == null ? "" : description.strip();
    }

    /**
     * Reads a descriptor out of already loaded properties, returning empty when the file is unusable.
     *
     * <p>Only {@code id}, {@code version} and {@code main} are required; a missing or blank one makes the
     * whole descriptor unusable, because a loader that guessed would eventually give two plugins the same
     * data directory. The caller decides what an empty result means: a jar without the resource at all is
     * usually just a library dropped into the folder, while a jar with a broken one is a packaging mistake
     * worth reporting.
     *
     * @param properties the properties of {@link #RESOURCE}, already loaded by the caller
     * @return the descriptor, or empty when a required key is missing or blank
     */
    public static Optional<PluginDescriptor> parse(Properties properties) {
        String id = value(properties, KEY_ID);
        String version = value(properties, KEY_VERSION);
        String mainClass = value(properties, KEY_MAIN);
        if (id == null || version == null || mainClass == null) {
            return Optional.empty();
        }
        return Optional.of(new PluginDescriptor(id, version, mainClass, value(properties, KEY_DESCRIPTION)));
    }

    private static String value(Properties properties, String key) {
        String text = properties.getProperty(key);
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String requireText(String text, String key) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("plugin descriptor key '" + key + "' must not be blank");
        }
        return text.strip();
    }
}
