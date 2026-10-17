package io.github.sob1234509876.oc.server.ws;

/**
 * One live WebSocket conversation, as seen by a {@link WebSocketHandler}.
 *
 * <p>The handler is deliberately not given the socket, the streams or the frame codec. A game handler only
 * needs to know who is talking, how to answer and how to hang up; everything else - masking, fragmentation,
 * close codes - belongs to the transport and must not be re-implemented by every handler. The narrow
 * surface is also what lets a handler be tested with a stub session: there is nothing else to fake.
 *
 * <p>All methods may be called from the connection's reader thread (which is where the handler runs) or
 * from another thread that owns the same session. {@link #sendText(String)} and {@link #close(int, String)}
 * are safe for that, because the frame writer serialises itself. An implementation is expected to be
 * usable after the underlying socket is gone, in the sense that a send fails loudly or a close is a no-op;
 * a handler should not depend either way.
 */
public interface WebSocketSession {

    /**
     * Returns a stable identifier of this connection.
     *
     * <p>The identifier is unique within one server run and is used by handlers that keep per connection
     * state in a map. It is not a credential and must not be sent to a client.
     *
     * @return the identifier, never {@code null} or blank
     */
    String id();

    /**
     * Returns the peer address in {@code host:port} form, for logs and diagnostics.
     *
     * @return the peer address, never {@code null}
     */
    String remoteAddress();

    /**
     * Sends one text message as a single unmasked WebSocket text frame.
     *
     * <p>Text is the only message type a handler can send, because every answer of the game protocol is one
     * line of text. The frame is written and flushed before this method returns, so a caller may close the
     * connection immediately afterwards and the message still reaches the peer.
     *
     * <p>The interface has no checked exception because a handler is game logic, not transport logic: a
     * failed write means the connection is gone, which an implementation reports by closing the session and
     * throwing an unchecked exception. That keeps every handler free of {@code try}/{@code catch} around a
     * call that cannot be retried anyway.
     *
     * @param text the message; may be empty but not {@code null}
     * @throws IllegalStateException    if the session is already closed
     * @throws java.io.UncheckedIOException if the frame cannot be written
     */
    void sendText(String text);

    /**
     * Sends a close frame and closes the connection. Calling this twice is harmless.
     *
     * <p>The code is echoed to the peer as required by RFC 6455, and the reason is truncated to the 123
     * payload bytes a control frame may carry. A handler that answers {@code QUIT} uses code {@code 1000};
     * a transport failure uses its own code.
     *
     * @param code   the close code, normally {@code 1000}
     * @param reason a short human readable reason; may be empty but not {@code null}
     */
    void close(int code, String reason);

    /**
     * Returns whether the connection can still carry messages.
     *
     * @return {@code true} until a close frame was sent, received or the socket failed
     */
    boolean open();
}
