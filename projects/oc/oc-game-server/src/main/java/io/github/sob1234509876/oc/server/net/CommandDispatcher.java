package io.github.sob1234509876.oc.server.net;

import io.github.sob1234509876.oc.server.auth.AuthResult;
import io.github.sob1234509876.oc.server.auth.AuthService;
import io.github.sob1234509876.oc.server.plugin.RequestHandler;
import io.github.sob1234509876.oc.server.plugin.RequestRegistry;

import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The OMNI COM command grammar, independent of the transport that carries it.
 *
 * <p>The grammar is tiny and deliberately has nothing to do with sockets:
 *
 * <pre>
 * PING                              -&gt; PONG
 * REGISTER &lt;name&gt; &lt;password&gt;         -&gt; OK &lt;message&gt; | ERR &lt;message&gt;
 * LOGIN &lt;name&gt; &lt;password&gt;            -&gt; OK &lt;message&gt; | ERR &lt;message&gt;
 * COUNT                             -&gt; OK &lt;registered users&gt;
 * REQ &lt;name&gt; &lt;base64-payload&gt;        -&gt; OK &lt;base64-payload&gt; | ERR &lt;message&gt;
 * QUIT                              -&gt; {@link #BYE}, then the caller closes
 * anything else                     -&gt; ERR unknown command
 * </pre>
 *
 * <p>This class exists so the encrypted TCP channel and the browser facing WebSocket channel cannot drift
 * apart. Both call {@link #execute(String, Session)} with one line and write back exactly one answer, so a
 * command added or corrected here is available on both transports without a second implementation of the
 * same rules - and, just as important, a rule that guards credentials or plugin requests can only be
 * forgotten once.
 *
 * <p><strong>What belongs here and what does not.</strong> Parsing, authentication and plugin relay live
 * here because they are policy. Framing, timeouts, logging of peers and connection lifecycle live in the
 * transports because they differ. In particular this class never logs a line: a line may contain a
 * password, so the transport, which knows the peer address, is the only place allowed to log, and it logs
 * peer addresses rather than payloads.
 *
 * <p><strong>Thread safety.</strong> A dispatcher is stateless apart from the services it was built with,
 * and both {@link AuthService} and {@link RequestRegistry} are safe to use from several client threads. One
 * dispatcher may therefore be shared by every connection of every transport. The per connection state lives
 * in {@link Session}, which is deliberately <em>not</em> synchronised: it belongs to exactly one transport
 * connection and is touched only by that connection's reader thread, so a lock would only add cost.
 */
public final class CommandDispatcher {

    private static final System.Logger LOG = System.getLogger(CommandDispatcher.class.getName());

    /** The single line a client gets when it sends a request without having logged in first. */
    public static final String ERR_LOGIN_FIRST = "ERR login first";

    /** The single line a client gets for a name nothing is registered under, or for a malformed request. */
    public static final String ERR_UNKNOWN_REQUEST = "ERR unknown request";

    /** The single line a client gets when a registered handler threw instead of answering. */
    public static final String ERR_REQUEST_FAILED = "ERR request failed";

    /**
     * The line written before a connection that sent {@code QUIT} is closed.
     *
     * <p>It is a public constant rather than a literal in each transport so the last words a client hears are
     * spelled the same over TCP and over WebSocket.
     */
    public static final String BYE = "OK bye";

    /** Request name a plugin registers under: lower case letters, digits and dashes; see the registry. */
    private static final int MAX_REQUEST_NAME_LENGTH = 32;

    private final AuthService auth;
    private final RequestRegistry requests;

    /**
     * Creates a dispatcher over the given services.
     *
     * @param auth     the authentication service every {@code REGISTER} and {@code LOGIN} goes through
     * @param requests the handlers {@code REQ} may reach; never {@code null}
     */
    public CommandDispatcher(AuthService auth, RequestRegistry requests) {
        this.auth = Objects.requireNonNull(auth, "auth");
        this.requests = Objects.requireNonNull(requests, "requests");
    }

    /**
     * Executes one command line and returns the single line to answer with.
     *
     * <p>Command names are case insensitive, but names and passwords are compared exactly and are never
     * trimmed beyond the single separating whitespace, because a password may legitimately contain or end
     * with whitespace.
     *
     * @param line    the raw command line, already validated by the transport to be at most its line limit
     * @param session the state of this connection, which {@code LOGIN} and {@code REQ} both consult
     * @return the response line, or {@code null} when the client asked to quit; the caller then writes
     *         {@link #BYE} and closes its connection
     */
    public String execute(String line, Session session) {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(session, "session");
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            return "ERR unknown command";
        }
        int firstSpace = indexOfWhitespace(trimmed, 0);
        String command = (firstSpace < 0 ? trimmed : trimmed.substring(0, firstSpace))
                .toUpperCase(Locale.ROOT);
        String rest = firstSpace < 0 ? "" : trimmed.substring(firstSpace + 1).strip();

        switch (command) {
            case "PING":
                return "PONG";
            case "COUNT":
                return "OK " + auth.userCount();
            case "QUIT":
                return null;
            case "REGISTER":
                return credentials("REGISTER", rest, session, auth::register);
            case "LOGIN":
                return credentials("LOGIN", rest, session, auth::authenticate);
            case "REQ":
                return request(rest, session);
            default:
                return "ERR unknown command";
        }
    }

    /**
     * State of one connection that is not the socket: whether its client has logged in.
     *
     * <p>The game protocol has no session tokens yet, so "who is asking" is exactly "which connection is
     * this", and a field per connection is the smallest thing that can hold it. Keeping the flag here, next
     * to the command that reads it, means a transport cannot accidentally implement {@code REQ} against a
     * different notion of "logged in" than the one the TCP channel uses.
     */
    public static final class Session {

        private boolean authenticated;

        /** Whether this connection completed a successful {@code LOGIN}. */
        public boolean authenticated() {
            return authenticated;
        }

        /** Records a successful login of the client on this connection. */
        public void markAuthenticated() {
            this.authenticated = true;
        }
    }

    /**
     * Serves one {@code REQ}: an authenticated session, a registered name and an opaque base64 payload.
     *
     * <p>The rule that a request needs a login is deliberate. {@code REQ} is the only command that can make
     * a plugin do work, so it is the only one that must know who is asking; {@code PING}, {@code REGISTER},
     * {@code LOGIN} and {@code COUNT} keep their old behaviour exactly.
     *
     * <p>Nothing in this method touches the payload or the response beyond base64 and the relay: the bytes
     * are neither parsed nor logged, and an unparsable payload is refused without ever reaching the handler.
     */
    private String request(String rest, Session session) {
        if (!session.authenticated()) {
            return ERR_LOGIN_FIRST;
        }
        int separator = indexOfWhitespace(rest, 0);
        if (separator < 0) {
            return ERR_UNKNOWN_REQUEST;
        }
        String name = rest.substring(0, separator);
        String encoded = rest.substring(separator + 1).strip();
        if (name.length() > MAX_REQUEST_NAME_LENGTH) {
            // A name that long cannot be registered, so there is no point decoding a payload for it.
            return ERR_UNKNOWN_REQUEST;
        }
        Optional<RequestHandler> handler = requests.find(name);
        if (handler.isEmpty()) {
            return ERR_UNKNOWN_REQUEST;
        }
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException malformed) {
            // The message from the decoder describes the base64 text, never the plaintext it would have
            // carried, so recording the reason cannot leak what a plugin seals into its envelope.
            LOG.log(System.Logger.Level.WARNING,
                    "refusing request '" + name + "': the payload is not base64 ("
                            + malformed.getMessage() + ")");
            return ERR_UNKNOWN_REQUEST;
        }
        byte[] response;
        try {
            response = handler.get().handle(payload);
        } catch (RuntimeException failure) {
            // The server keeps serving: one bad handler costs one reply, not the connection and not the
            // listener. Only the exception type and message are logged; the payload stays opaque.
            LOG.log(System.Logger.Level.WARNING, "request '" + name + "' failed in the plugin: " + failure);
            return ERR_REQUEST_FAILED;
        }
        if (response == null) {
            // A handler that returns null has nothing to say; treat it as a failed request rather than
            // letting a NullPointerException escape into the client loop.
            LOG.log(System.Logger.Level.WARNING, "request '" + name + "' answered with null");
            return ERR_REQUEST_FAILED;
        }
        return "OK " + Base64.getEncoder().encodeToString(response);
    }

    /** Functional shape shared by {@code REGISTER} and {@code LOGIN}. */
    @FunctionalInterface
    private interface CredentialCall {
        AuthResult apply(String name, String password);
    }

    /**
     * Parses exactly {@code <name> <password>} and runs one authentication call.
     *
     * <p>The reply never contains the password: success replies carry the message from {@link AuthResult},
     * which the auth package controls, and every failure reply constructed here is a fixed string about
     * the shape of the command or about the name, not about the secret.
     *
     * <p>A successful {@code LOGIN} also marks the connection authenticated, which is what {@code REQ}
     * checks. The flag is deliberately owned by the connection rather than by the auth service: the service
     * answers "are these credentials right", and only the transport knows which client is speaking, so the
     * session is the transport's business and the password hash never has to be consulted twice.
     */
    private String credentials(String command, String rest, Session session, CredentialCall call) {
        int separator = indexOfWhitespace(rest, 0);
        if (separator < 0) {
            return "ERR " + command + " needs a name and a password";
        }
        String name = rest.substring(0, separator);
        String password = rest.substring(separator + 1);
        if (name.isEmpty()) {
            return "ERR " + command + " needs a non-empty name";
        }
        AuthResult result;
        try {
            result = call.apply(name, password);
        } catch (RuntimeException failure) {
            // An auth failure must not close the connection, but it must not leak a stack trace either.
            LOG.log(System.Logger.Level.WARNING,
                    command + " for '" + name + "' failed in the auth service: " + failure);
            return "ERR internal authentication error for '" + name + "'";
        }
        if (result.ok() && "LOGIN".equals(command)) {
            session.markAuthenticated();
        }
        return (result.ok() ? "OK " : "ERR ") + result.message();
    }

    /** Returns the index of the first whitespace character at or after {@code from}, or {@code -1}. */
    private static int indexOfWhitespace(String value, int from) {
        for (int index = from; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return index;
            }
        }
        return -1;
    }
}
