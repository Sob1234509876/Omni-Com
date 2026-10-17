package io.github.sob1234509876.oc.server.hello;

import io.github.sob1234509876.oc.server.plugin.Plugin;
import io.github.sob1234509876.oc.server.plugin.PluginContext;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * The minimal Omni Com plugin: one read-only request that answers a greeting.
 *
 * <p><strong>What a plugin can do from outside the server.</strong> {@link PluginContext} hands a plugin a
 * folder, a version string, a logger and the request registry, and nothing else. There is no world, no
 * engine, no user record and no socket in it, so a plugin outside the server can only answer the requests
 * it registers. That is the whole plugin surface, and this example deliberately proves the smallest
 * possible case: it reads no game state, changes no game state and holds no reference that could outlive
 * the request.
 *
 * <p><strong>The request.</strong> {@value #REQUEST_NAME} takes the payload as a plain UTF-8 name, not an
 * envelope, because the transport already base64-encodes a {@code REQ} payload. A blank or empty payload
 * means {@code world}; a name longer than {@value #MAX_NAME_BYTES} UTF-8 bytes is cut on a character
 * boundary and followed by {@value #ELLIPSIS}. The answer is plain UTF-8 text, one greeting. The plugin
 * does not base64 anything itself: the transport does that on the way back.
 *
 * <p>The greeting is intentionally a constant plus the caller's name. It has no secret, no configuration
 * and no timestamp, so the check can assert it exactly and an operator can see the whole effect of the
 * plugin in one line of the browser log.
 */
public final class HelloPlugin implements Plugin {

    /** The name of the plugin, which is also the id in its descriptor. */
    public static final String NAME = "oc-hello";

    /** The version of the plugin, matching the descriptor. */
    public static final String VERSION = "1.0";

    /** The one request name this plugin registers. */
    public static final String REQUEST_NAME = "hello";

    /** Longest name this plugin echoes, in UTF-8 bytes, before it truncates. */
    public static final int MAX_NAME_BYTES = 64;

    /** The single character appended when a name is truncated. */
    public static final String ELLIPSIS = "\u2026";

    /** The name substituted for an empty or blank payload. */
    private static final String DEFAULT_NAME = "world";

    private PluginContext context;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public String description() {
        return "Minimal hello world plugin: answers the request 'hello' and never touches game state";
    }

    /**
     * Remembers the context and publishes the single request.
     *
     * <p>Registration happens at load time, like the debug plugin, so the very first client that connects
     * after startup can already reach it. {@link #onDisable()} removes exactly the name registered here, so
     * a disabled plugin leaves no dangling handler in a registry the server keeps using.
     *
     * @param context the plugin's folder, data root, logger and request registry
     */
    @Override
    public void onLoad(PluginContext context) {
        this.context = Objects.requireNonNull(context, "context");
        context.requests().register(REQUEST_NAME, this::handleHello);
    }

    /** Withdraws the request so a disabled plugin stops answering immediately. */
    @Override
    public void onDisable() {
        if (context != null) {
            context.requests().unregister(REQUEST_NAME);
        }
    }

    /**
     * Answers one {@code hello} request.
     *
     * <p>This method is the entire remote surface of the plugin, and it only reads its argument. It opens
     * no file, reads no field other than constants, writes no field, and returns a fresh byte array. A
     * request can therefore not reach anything but the name it sent.
     *
     * @param request the plain UTF-8 name the client sent, never {@code null}
     * @return the greeting as plain UTF-8 bytes, never {@code null}
     */
    private byte[] handleHello(byte[] request) {
        return greeting(nameOf(request)).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Turns the request bytes into the name to greet.
     *
     * <p>Surrounding whitespace is stripped so a terminal-minded client that sends {@code " Alice "} still
     * gets a sensible greeting; a name that is blank after stripping falls back to {@value #DEFAULT_NAME}.
     * Truncation happens on the byte length the protocol documents, and the cut is moved back to a
     * character boundary so a multi-byte character is never replaced by a broken sequence.
     */
    private static String nameOf(byte[] request) {
        String name = new String(request, StandardCharsets.UTF_8).strip();
        if (name.isEmpty()) {
            return DEFAULT_NAME;
        }
        byte[] encoded = name.getBytes(StandardCharsets.UTF_8);
        if (encoded.length <= MAX_NAME_BYTES) {
            return name;
        }
        int end = MAX_NAME_BYTES;
        while (end > 0 && (encoded[end] & 0xC0) == 0x80) {
            end--;
        }
        return new String(encoded, 0, end, StandardCharsets.UTF_8) + ELLIPSIS;
    }

    /** The exact greeting this plugin answers with; the check asserts the same text end to end. */
    private static String greeting(String name) {
        return "Hello, " + name + "! This is the Omni Com plugin system talking: request \""
                + REQUEST_NAME + "\", answered by " + NAME + " " + VERSION + ".";
    }
}
