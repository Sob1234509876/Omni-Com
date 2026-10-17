package io.github.sob1234509876.oc.server.ws;

import io.github.sob1234509876.oc.server.auth.AuthService;
import io.github.sob1234509876.oc.server.net.CommandDispatcher;
import io.github.sob1234509876.oc.server.plugin.RequestRegistry;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The game protocol over WebSocket: one text frame in, one text line out.
 *
 * <p>This is the only game aware class in the browser package, and it contains no game rules. Every message
 * is handed to the same {@link CommandDispatcher} the encrypted TCP channel uses, so a browser speaks
 * exactly the commands a TCP client speaks - {@code PING}, {@code REGISTER}, {@code LOGIN}, {@code COUNT},
 * {@code REQ} and {@code QUIT} - with exactly the same answers and the same "REQ needs a successful LOGIN"
 * rule. In particular a browser client cannot reach a plugin request state that a TCP client could not.
 *
 * <p><strong>Two version mechanisms, on purpose.</strong> The encrypted TCP channel tells the server what
 * protocol it speaks inside the binary handshake: {@code SecureChannel} writes a version byte before any
 * command is read, so a stale client is rejected before its first line is parsed. The browser channel has no
 * such slot. RFC 6455's {@code Sec-WebSocket-Version} negotiates the framing protocol, not the game protocol,
 * and after the HTTP upgrade every byte is a WebSocket frame. The first text frame is therefore the game
 * version line, {@code HELLO 1}, and it is required before any command: the server answers {@code OK OC/1},
 * or refuses a version it does not speak and closes, or refuses a connection that tried to send a command
 * first. Without that rule a stale browser bundle would be interpreted under the current grammar, which is
 * exactly the kind of silent misbehaviour a version handshake exists to prevent. The TCP channel is not
 * changed by this: it keeps its version byte and never sees {@code HELLO}.
 *
 * <p>Text frames are the commands. A frame is one command line, and the trailing {@code \r\n} a terminal
 * minded client may add is stripped because the TCP channel's {@code readLine} removes it too. Binary frames
 * never reach this handler: {@link WebSocketConnection} answers them with
 * {@code ERR binary frames are not commands} and keeps the connection open, because a client that sends
 * binary is confused rather than hostile.
 *
 * <p>Per connection state - the login flag and whether the version line was accepted - lives in a small
 * object keyed by the session id and is removed in {@link #onClose}, so a disconnected browser also stops
 * being authenticated. The map is a {@link ConcurrentHashMap} because connections open and close on different
 * threads, and a browser tab is not guaranteed to close politely. The state itself is only touched on the
 * connection's reader thread.
 *
 * <p>No message, name or password from this class is logged. The dispatcher also logs no command line, so a
 * password sent over the browser endpoint never reaches a log file, exactly as on the TCP channel.
 */
public final class CommandWebSocketHandler implements WebSocketHandler {

    private static final System.Logger LOG = System.getLogger(CommandWebSocketHandler.class.getName());

    /** Close code a client gets after {@code QUIT}. */
    private static final int CLOSE_NORMAL = 1000;

    /** Close code for a client that is not allowed to keep talking, e.g. a version mismatch. */
    private static final int CLOSE_POLICY_VIOLATION = 1008;

    /** Close code for a line that breaks the handshake grammar rather than its policy. */
    private static final int CLOSE_PROTOCOL_ERROR = 1002;

    /** Game protocol version the browser channel speaks, and the answer to a matching {@code HELLO}. */
    public static final int PROTOCOL_VERSION = 1;

    /** The one answer a matching {@code HELLO} gets. */
    public static final String HELLO_ANSWER = "OK OC/1";

    /** The answer to a first frame that is not a {@code HELLO} at all. */
    public static final String ERR_HELLO_FIRST = "ERR HELLO first";

    /** The answer to a {@code HELLO} with no number, extra arguments or a non numeric number. */
    public static final String ERR_MALFORMED_HELLO = "ERR malformed HELLO";

    /** The answer to a well formed {@code HELLO} naming a version this server does not speak. */
    public static final String ERR_UNSUPPORTED_VERSION = "ERR unsupported version, this server speaks OC/1";

    private final CommandDispatcher dispatcher;
    private final Map<String, ConnectionState> sessions = new ConcurrentHashMap<>();

    /**
     * Creates a handler over the services the game already uses.
     *
     * @param auth     the authentication service {@code REGISTER} and {@code LOGIN} go through
     * @param requests the plugin handlers {@code REQ} may reach
     */
    public CommandWebSocketHandler(AuthService auth, RequestRegistry requests) {
        this(new CommandDispatcher(auth, requests));
    }

    /**
     * Creates a handler that reuses an existing dispatcher.
     *
     * <p>Sharing the TCP server's dispatcher is safe because a dispatcher is stateless; it exists so a
     * future transport can be wired without duplicating the services it was built with.
     *
     * @param dispatcher the dispatcher every message is executed against
     */
    public CommandWebSocketHandler(CommandDispatcher dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    @Override
    public void onOpen(WebSocketSession session) {
        sessions.put(session.id(), new ConnectionState());
        LOG.log(System.Logger.Level.INFO,
                "browser client " + session.id() + " connected from " + session.remoteAddress());
    }

    @Override
    public void onMessage(WebSocketSession session, String text) {
        ConnectionState state = sessions.computeIfAbsent(session.id(), key -> new ConnectionState());
        String line = stripLineTerminator(text);
        if (!state.greeted) {
            greet(session, state, line);
            return;
        }
        String answer = dispatcher.execute(line, state.session);
        if (answer == null) {
            // QUIT: say goodbye exactly like the TCP channel, then close with a normal code so the browser
            // does not report the disconnect as an error.
            session.sendText(CommandDispatcher.BYE);
            session.close(CLOSE_NORMAL, "bye");
            return;
        }
        session.sendText(answer);
    }

    @Override
    public void onClose(WebSocketSession session, int code, String reason) {
        sessions.remove(session.id());
        LOG.log(System.Logger.Level.INFO, "browser client " + session.id() + " closed with " + code
                + (reason.isEmpty() ? "" : " (" + reason + ")"));
    }

    /**
     * Applies the text version handshake that must precede every browser command.
     *
     * <p>The three failure shapes are deliberately distinct so a client author can tell them apart: a first
     * frame that is not {@code HELLO} means the client did not implement the handshake at all; a
     * well formed {@code HELLO} with another number means the client speaks another game version; and a
     * malformed {@code HELLO} means the client tried but sent the wrong shape.
     */
    private void greet(WebSocketSession session, ConnectionState state, String line) {
        if (!line.isEmpty()) {
            String[] parts = line.strip().split("\\s+");
            boolean helloCommand = "HELLO".equalsIgnoreCase(parts[0]);
            if (helloCommand) {
                if (parts.length != 2 || !isDigits(parts[1])) {
                    session.sendText(ERR_MALFORMED_HELLO);
                    session.close(CLOSE_PROTOCOL_ERROR, "malformed HELLO");
                    return;
                }
                if (!Integer.toString(PROTOCOL_VERSION).equals(parts[1])) {
                    session.sendText(ERR_UNSUPPORTED_VERSION);
                    session.close(CLOSE_POLICY_VIOLATION, "unsupported version");
                    return;
                }
                state.greeted = true;
                session.sendText(HELLO_ANSWER);
                return;
            }
        }
        session.sendText(ERR_HELLO_FIRST);
        session.close(CLOSE_POLICY_VIOLATION, "HELLO first");
    }

    /** Whether a token consists only of decimal digits, so a version number never overflows an int. */
    private static boolean isDigits(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) < '0' || value.charAt(index) > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * Removes one line terminator from a message.
     *
     * <p>The WebSocket transport does not need the terminator that framed a TCP line; a browser client that
     * reuses a terminal command string sends one anyway. Removing exactly one {@code \r\n}, {@code \n} or
     * {@code \r} keeps the message comparable with what {@code readLine} would have produced, without
     * trimming meaningful whitespace inside a password.
     */
    private static String stripLineTerminator(String text) {
        if (text.endsWith("\r\n")) {
            return text.substring(0, text.length() - 2);
        }
        if (text.endsWith("\n") || text.endsWith("\r")) {
            return text.substring(0, text.length() - 1);
        }
        return text;
    }

    /**
     * Per connection state: the login flag the dispatcher owns, and whether the version line was accepted.
     *
     * <p>Both fields are read and written only on the connection's reader thread, so no synchronisation is
     * needed; the map needs a concurrent type only because connections come and go on different threads.
     */
    private static final class ConnectionState {

        private final CommandDispatcher.Session session = new CommandDispatcher.Session();
        private boolean greeted;
    }
}
