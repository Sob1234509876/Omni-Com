package io.github.sob1234509876.oc.server.plugin;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/**
 * What {@link PluginLoader} made of one jar, kept even when the jar turned out to be useless.
 *
 * <p>The result exists for diagnosis. A server operator drops jars into a folder and expects either a plugin
 * or an explanation, so every jar the loader touched is reported with what was found in it and what went
 * wrong, instead of silently disappearing because a descriptor had a typo. {@link #usable()} is the one bit a
 * caller usually needs; the rest is there for the log line that says why a jar did nothing.
 *
 * @param jar the jar file that was inspected
 * @param descriptor the parsed descriptor, or empty when the jar has no readable
 *     {@link PluginDescriptor#RESOURCE} file
 * @param pluginClasses the distinct binary names of the loadable, concrete {@link Plugin} implementations
 *     found in the jar, sorted so two runs report the same thing
 * @param problems everything that went wrong for this jar, in the order it was noticed; empty when nothing
 *     did
 */
public record DiscoveryResult(Path jar, Optional<PluginDescriptor> descriptor, List<String> pluginClasses,
        List<String> problems) {

    /**
     * Copies the collections and puts the class names in order. The copies matter because the loader keeps
     * appending to the lists it builds while discovery is still running: without them a result could change
     * under the caller that is reading it, and the loader's own bookkeeping lists must not be mutable from
     * outside.
     */
    public DiscoveryResult {
        Objects.requireNonNull(jar, "jar");
        descriptor = descriptor == null ? Optional.empty() : descriptor;
        pluginClasses = List.copyOf(new TreeSet<>(pluginClasses));
        problems = List.copyOf(problems);
    }

    /**
     * Whether this jar contributed a plugin and raised no complaint.
     *
     * <p>A jar can hold plugin classes and still be unusable - a broken descriptor is reported without
     * preventing the scan from finding anything - so {@code pluginClasses} being non-empty is not the same
     * question, and both are asked separately when a caller wants to know whether the operator should be
     * warned.
     */
    public boolean usable() {
        return problems.isEmpty() && !pluginClasses.isEmpty();
    }
}
