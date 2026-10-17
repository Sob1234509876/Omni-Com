package io.github.sob1234509876.oc.server.ws;

/**
 * The callbacks a WebSocket connection delivers to game logic.
 *
 * <p>Keeping the handler this small is what keeps the framing code free of game concepts: the transport
 * parses RFC 6455, enforces the limits and then hands over a finished message. A handler never sees a
 * fragment, a mask bit or a close frame, and the transport never sees a command.
 *
 * <p><strong>Calling convention.</strong> All three methods run on the connection's own reader thread, in
 * order, with at most one conversation per thread. An implementation may block briefly - the game protocol
 * answers in memory - but must not wait on another connection, because one stuck handler then costs one
 * thread rather than the whole endpoint. A runtime exception thrown by a handler closes that handler's
 * connection (with close code 1011) and nothing else.
 */
public interface WebSocketHandler {

    /**
     * Called once after the upgrade handshake succeeded and before the first message is read.
     *
     * @param session the new session, which the handler may keep for the lifetime of the connection
     */
    void onOpen(WebSocketSession session);

    /**
     * Called for every complete text message.
     *
     * <p>Fragmentation has already been reassembled and the bytes have already been validated as UTF-8, so
     * the argument is exactly what a client sent, even if it arrived in several frames. Binary messages are
     * not commands and are answered by the transport itself; they never reach this method.
     *
     * @param session the session the message arrived on
     * @param text    the complete message
     */
    void onMessage(WebSocketSession session, String text);

    /**
     * Called once when the connection ends, for any reason, after the close handshake.
     *
     * <p>The code is what the peer sent when it closed normally, or the transport's own code when the
     * transport had to end the conversation (for example {@code 1002} for a protocol error or {@code 1009}
     * for an oversized message). A handler uses this to drop per connection state; it must not send on the
     * session, which is already closed.
     *
     * @param session the session that ended
     * @param code    the close code, or {@code 1006} when the socket failed without a close frame
     * @param reason  a short reason, possibly empty
     */
    void onClose(WebSocketSession session, int code, String reason);
}
