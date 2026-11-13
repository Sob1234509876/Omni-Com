package io.github.sob1234509876.oc.server.ws;

import io.github.sob1234509876.oc.server.auth.AuthService;
import io.github.sob1234509876.oc.server.net.CommandDispatcher;
import io.github.sob1234509876.oc.server.plugin.RequestRegistry;
import io.github.sob1234509876.oc.server.world.NotificationBroadcaster;
import io.github.sob1234509876.oc.server.world.WorldHost;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * The game protocol over WebSocket: one text frame in, one text line out.
 *
 * <p>This is the only game aware class in the browser package, and it contains no game rules. Every message
 * is handed to the same {@link CommandDispatcher} the encrypted TCP channel uses, so a browser speaks
 * exactly the commands a TCP client speaks - {@code PING}, {@code REGISTER}, {@code LOGIN}, {@code COUNT},
 * {@code REQ}, {@code WHOAMI}, {@code ORDER}, {@code BUILD}, {@code TRAIN} and {@code QUIT} - with exactly
 * the same answers and the same "REQ and every gameplay command needs a successful LOGIN" rule. In
 * particular a browser client cannot reach a plugin request state that a TCP client could not, and its
 * gameplay commands are applied by the same world queue at the same step boundary.
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
 * <p><strong>Server initiated push frames.</strong> The command direction is one frame in, one line out -
 * but the server may also speak first: it implements {@link NotificationBroadcaster}, so
 * {@link WorldHost} can hand it a push frame ({@code NOTIFY {...}}) the moment rarely changing world state
 * changes, and every session that has completed the version handshake receives it as an ordinary text
 * frame. The frame is a wake up hint, never state: a session that connects afterwards simply reads the
 * current version from its next snapshot or bootstrap document, nothing is replayed, and correctness never
 * depends on the frame arriving. The audience is deliberately every greeted session, logged in or not:
 * {@code contentVersion} is not sensitive - the same value rides the unauthenticated snapshot route - and
 * requiring a login would only make the hint less useful. A session still inside the handshake is skipped:
 * its {@code pushSocket} is published only after the {@code OK OC/1} answer has been written, so the first
 * frame such a client ever sees stays the handshake answer and the server never pushes before the client
 * has agreed on OC/1.
 *
 * <p><strong>The push never touches the world thread.</strong> {@link WorldHost} broadcasts while it holds
 * the simulation lock, so {@link #broadcast(String)} only enqueues one task on a dedicated daemon executor
 * and returns; the socket writes happen on that thread. Each send is isolated in its own try/catch, so one
 * dead or slow session costs its own frame and nothing else, and a send failure just closes that session -
 * exactly like a failed heartbeat ping. The executor's single thread serialises the sends the same way the
 * endpoint's heartbeat scheduler does; the frames are tiny, and a peer that stops reading is eventually
 * unblocked because its own idle timeout closes the socket from the reader side. The thread times out when
 * idle, so an endpoint without pushes costs no thread at all.
 *
 * <p>No message, name or password from this class is logged. The dispatcher also logs no command line, so a
 * password sent over the browser endpoint never reaches a log file, exactly as on the TCP channel.
 */
public final class CommandWebSocketHandler implements WebSocketHandler, NotificationBroadcaster {

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
     * The one daemon thread the push frames are written on, shared by every session of this handler.
     *
     * <p>See the class comment on why the sends must not run on the world thread. The queue is unbounded
     * because the frames are rare and tiny, so {@link #broadcast(String)} enqueueing one from the world
     * thread can never block; {@code allowCoreThreadTimeOut} returns the thread to the void when no push
     * has happened for a while, which keeps a handler that is created per check from parking threads.
     */
    private final ThreadPoolExecutor pushExecutor = new ThreadPoolExecutor(0, 1, 30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "oc-ws-push");
                thread.setDaemon(true);
                return thread;
            });

    /**
     * Creates a handler over the services the game already uses.
     *
     * @param auth     the authentication service {@code REGISTER} and {@code LOGIN} go through
     * @param requests the plugin handlers {@code REQ} may reach
     */
    public CommandWebSocketHandler(AuthService auth, RequestRegistry requests) {
        this(auth, requests, () -> null);
    }

    /**
     * Creates a handler over the game services and the world gameplay commands are applied to.
     *
     * <p>The browser and the TCP channel therefore share both the grammar and the world queue; a
     * {@code BUILD} sent from a browser is applied by the same world step as one sent over TCP.
     *
     * @param auth     the authentication service {@code REGISTER} and {@code LOGIN} go through
     * @param requests the plugin handlers {@code REQ} may reach
     * @param worlds   supplies the live world, or {@code null} when the server hosts none
     */
    public CommandWebSocketHandler(AuthService auth, RequestRegistry requests, Supplier<WorldHost> worlds) {
        this(new CommandDispatcher(auth, requests, worlds));
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
        pushExecutor.allowCoreThreadTimeOut(true);
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
     * Pushes one server initiated frame to every greeted session, off the calling thread.
     *
     * <p>The caller is the {@link WorldHost} on the world thread holding the simulation lock, so this
     * method does nothing but enqueue one task and return - the socket writes happen on the push executor,
     * never on the world thread. The task walks the session map (a concurrent map, safe to iterate while
     * connections open and close), skips the sessions that have not published their handshake yet and
     * isolates every send: a closed session reports {@code IllegalStateException}, a dead socket reports
     * {@code UncheckedIOException} and closes itself, and either way the failure costs that one session its
     * frame and the rest of the audience still receives theirs. A frame lost this way is harmless by
     * design; the snapshot's {@code contentVersion} handshake is the fallback that catches the client up.
     */
    @Override
    public void broadcast(String frame) {
        pushExecutor.execute(() -> {
            for (ConnectionState state : sessions.values()) {
                WebSocketSession target = state.pushSocket;
                if (target == null || !target.open()) {
                    continue;
                }
                try {
                    target.sendText(frame);
                } catch (RuntimeException failure) {
                    LOG.log(System.Logger.Level.DEBUG, "push frame dropped for browser client "
                            + target.id() + ": " + failure.getMessage());
                }
            }
        });
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
                // Only now does the session join the push audience: the handshake answer is already on
                // the wire, so a NOTIFY frame can never overtake the OK OC/1 the client agreed to first.
                state.pushSocket = session;
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
     * The exception is {@code pushSocket}, which the push executor reads from another thread: it is
     * {@code volatile} and published exactly once, after the handshake answer has been written, so the
     * push task either does not see the session at all or sees it fully greeted.
     */
    private static final class ConnectionState {

        private final CommandDispatcher.Session session = new CommandDispatcher.Session();
        private boolean greeted;

        /**
         * The session as a push target, or {@code null} until the version handshake completed.
         *
         * <p>Set once, on the reader thread, right after the handshake answer has been written; never
         * cleared. {@code onClose} removes the whole state from the map instead, so a closed session
         * drops out of the audience together with its state.
         */
        private volatile WebSocketSession pushSocket;
    }
}
