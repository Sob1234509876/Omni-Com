package io.github.sob1234509876.oc.server.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The named {@link RequestHandler}s the server is willing to relay to plugins.
 *
 * <p>The registry is the only bridge between the network layer and a plugin's own request surface, and it
 * is deliberately narrow: a name and a handler, nothing else. It knows no payload, no response and no game
 * concept, so the server keeps compiling and behaving the same whether or not any plugin registered
 * anything. A server that owns an empty registry - which is what the {@code GameServer} constructors
 * without a registry do - answers every {@code REQ} with an unknown request, which is byte for byte what it
 * did before this class existed.
 *
 * <p><strong>Why a name pattern.</strong> The name becomes a field of a command line, so it must survive a
 * round trip through a whitespace separated protocol without quoting rules. The pattern
 * {@code [a-z0-9-]{1,32}} is the smallest set that satisfies that and still leaves room for the readable
 * names a plugin author wants ({@code engine-info}, {@code map-stats}). Upper case is refused rather than
 * folded, so two spellings can never name, or fail to find, the same handler.
 *
 * <p><strong>Thread safety.</strong> Registration and unregistration happen on the plugin lifecycle thread,
 * which is startup or shutdown, and are not meant to race with a running server; the storage is
 * nevertheless a {@link ConcurrentHashMap}, so a handler registered by a plugin that loads its features
 * lazily is visible to client threads immediately. {@link #find(String)} and {@link #names()} are safe to
 * call from client threads at any time: {@code find} is a map read, and {@code names} sorts a snapshot of
 * the keys, so it never iterates a collection that another thread could be modifying.
 */
public final class RequestRegistry {

    /**
     * The exact name shape a plugin may register.
     *
     * <p>Anchored and fully matched rather than searched inside the name, because a name that merely
     * contains a legal fragment would put a space or a control character on the wire.
     */
    private static final Pattern NAME = Pattern.compile("[a-z0-9-]{1,32}");

    /** Shortest and longest legal request name, kept as constants for the error message. */
    private static final int MIN_NAME_LENGTH = 1;
    private static final int MAX_NAME_LENGTH = 32;

    private final Map<String, RequestHandler> handlers = new ConcurrentHashMap<>();

    /**
     * Publishes one handler under a name.
     *
     * <p>A duplicate name is refused instead of replacing the previous handler: two plugins claiming
     * {@code engine-info} is a configuration mistake, and silently letting the second one win would turn it
     * into a bug that only shows up as "the wrong plugin answered" much later.
     *
     * @param name the request name, matching {@code [a-z0-9-]{1,32}}
     * @param handler the handler called for that name
     * @throws IllegalArgumentException if the name is {@code null} or does not match the pattern, or if the
     *     name is already registered
     * @throws NullPointerException if the handler is {@code null}
     */
    public void register(String name, RequestHandler handler) {
        requireValidName(name);
        Objects.requireNonNull(handler, "handler");
        RequestHandler previous = handlers.putIfAbsent(name, handler);
        if (previous != null) {
            throw new IllegalArgumentException("request name '" + name + "' is already registered");
        }
    }

    /**
     * Removes one handler. A plugin calls this from {@link Plugin#onDisable()} so that a disabled plugin
     * stops answering without the server having to know which handlers were ever registered.
     *
     * @param name the request name to remove
     * @return whether a handler was registered under that name
     */
    public boolean unregister(String name) {
        return name != null && handlers.remove(name) != null;
    }

    /**
     * Looks a handler up by name, from any thread.
     *
     * @param name the request name, normally already upper or lower case folded by the caller
     * @return the handler, or empty when nothing is registered under that name
     */
    public Optional<RequestHandler> find(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(handlers.get(name));
    }

    /**
     * The registered names, sorted alphabetically.
     *
     * <p>Sorted rather than in registration order because this list is shown to an operator (a plugin may
     * report its own request surface through it) and two runs of the same server must produce the same
     * text.
     *
     * @return a fresh, mutable list of names; never a view of the live registry
     */
    public List<String> names() {
        List<String> sorted = new ArrayList<>(handlers.keySet());
        Collections.sort(sorted);
        return sorted;
    }

    /**
     * How many handlers are registered.
     *
     * @return the number of registered names
     */
    public int size() {
        return handlers.size();
    }

    /** Rejects a name that could not survive the protocol line or the lookup rules. */
    private static void requireValidName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("request name must not be null");
        }
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("request name '" + name + "' must match "
                    + NAME.pattern() + " (" + MIN_NAME_LENGTH + " to " + MAX_NAME_LENGTH
                    + " lower case letters, digits or dashes)");
        }
    }
}
