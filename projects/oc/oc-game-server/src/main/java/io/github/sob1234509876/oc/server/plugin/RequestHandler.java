package io.github.sob1234509876.oc.server.plugin;

/**
 * One named request a plugin makes available to clients, as an opaque byte blob in and an opaque byte blob
 * out.
 *
 * <p><strong>Contract.</strong> The server hands the handler exactly the bytes a client sent for the
 * registered name and relays exactly the bytes the handler returns. It never interprets the request, never
 * parses the response and never feeds either of them into a game engine, a command parser or a world. That
 * is deliberate: the server stays free of game shaped knowledge, and the "return information only" promise
 * belongs to the plugin that registers the handler, not to this interface.
 *
 * <p>Because the server does not inspect what it relays, a handler that answers is by definition a way to
 * make the server do work for a client. A plugin must therefore treat its own handler as a public entry
 * point: authenticate the request with something the server does not carry (a plugin owned key is the
 * usual answer), accept only the request shapes it documents, and never mutate engine state from here. A
 * handler that can change state is a remote control for the plugin, and no interface can prevent that -
 * only the plugin's own code can.
 *
 * <p><strong>Threads.</strong> A handler runs on the client thread that sent the request, so several calls
 * may be in flight at once and an implementation must be safe for concurrent use. It must not block the
 * connection indefinitely and must not throw for a reason it can report itself; an exception is caught by
 * the server, reported to the client as a failed request, and never reaches the engine.
 *
 * <p><strong>Size.</strong> The request and the response travel as one protocol line inside the encrypted
 * channel, so they are bounded by the server's line and frame limits ({@code maxFrameBytes} and
 * {@code maxLineLength} of {@code ServerConfig}). A response that is too large is not delivered: the client
 * is disconnected, exactly as it would be for a hand written over-long line.
 */
@FunctionalInterface
public interface RequestHandler {

    /**
     * Handles one request and returns the response to send back to the client.
     *
     * <p>The bytes are opaque to the server: it does not log them, does not decode them and does not keep
     * them after the reply has been written. The handler owns their meaning, including any encryption of its
     * own; a handler that wants confidentiality beyond the channel must encrypt in here.
     *
     * @param request the exact bytes the client sent for this request, never {@code null} and never
     *     interpreted by the server
     * @return the bytes to relay to the client, never {@code null}
     * @throws RuntimeException to report a failure the handler cannot answer; the server turns it into a
     *     generic protocol error and keeps serving other clients
     */
    byte[] handle(byte[] request);
}
