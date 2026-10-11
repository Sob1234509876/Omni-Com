package io.github.sob1234509876.oc.rts.content;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Namespaced identifier of a piece of game content, for example {@code oc:iron_ore}.
 *
 * <p>Content is addressed by key instead of by Java type, which is what allows a scenario to contain
 * an arbitrary number of items, resources, terrain kinds, units, buildings and recipes: the engine
 * never names a concrete item itself, it only ever handles the keys and definitions a scenario
 * registered.
 */
public record ResourceKey(String namespace, String path) implements Comparable<ResourceKey> {

    private static final Pattern VALID_SEGMENT = Pattern.compile("[a-z0-9_]+");

    public ResourceKey {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!VALID_SEGMENT.matcher(namespace).matches()) {
            throw new IllegalArgumentException("invalid namespace '" + namespace + "', expected [a-z0-9_]+");
        }
        if (!VALID_SEGMENT.matcher(path).matches()) {
            throw new IllegalArgumentException("invalid path '" + path + "', expected [a-z0-9_]+");
        }
    }

    /** Parses {@code namespace:path}. */
    public static ResourceKey of(String key) {
        int separator = key.indexOf(':');
        if (separator < 0) {
            throw new IllegalArgumentException("key '" + key + "' must be of the form namespace:path");
        }
        return new ResourceKey(key.substring(0, separator), key.substring(separator + 1));
    }

    public static ResourceKey of(String namespace, String path) {
        return new ResourceKey(namespace, path);
    }

    @Override
    public int compareTo(ResourceKey other) {
        int byNamespace = namespace.compareTo(other.namespace);
        return byNamespace != 0 ? byNamespace : path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
