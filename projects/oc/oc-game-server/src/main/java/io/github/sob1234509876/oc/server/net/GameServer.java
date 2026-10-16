package io.github.sob1234509876.oc.server.net;

import io.github.sob1234509876.oc.server.auth.AuthResult;
import io.github.sob1234509876.oc.server.auth.AuthService;
import io.github.sob1234509876.oc.server.plugin.RequestHandler;
import io.github.sob1234509876.oc.server.plugin.RequestRegistry;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The OMNI COM game server: a TCP listener that speaks one command per line over a
 * {@link SecureChannel}.
 *
 * <p>The design goals are deliberately modest, because a game server that is hard to debug is worse
 * than one that is small:
 *
 * <ul>
 *   <li><strong>One acceptor, one thread per client, all daemon.</strong> A blocking thread per client is
 *       the simplest correct model for a turn based game with a few hundred players. Daemon threads mean
 *       a scenario that forgets {@link #stop()} still lets the JVM exit, and the client pool is bounded
 *       so an attacker cannot create threads without limit.</li>
 *   <li><strong>The socket is never trusted.</strong> Every client runs behind a {@link SecureChannel},
 *       so a bad handshake, an oversized frame, a corrupted frame or an unparsable line costs exactly one
 *       connection. None of those paths may take the listener down, and each of them is logged at
 *       {@code WARNING} with the peer address.</li>
 *   <li><strong>Credentials are write only.</strong> Passwords travel inside the encrypted channel and
 *       are handed straight to {@link AuthService}. They are never echoed, never put into an error
 *       message and never logged.</li>
 * </ul>
 *
 * <p>The command grammar is intentionally tiny:
 *
 * <pre>
 * PING                              -&gt; PONG
 * REGISTER &lt;name&gt; &lt;password&gt;         -&gt; OK &lt;message&gt; | ERR &lt;message&gt;
 * LOGIN &lt;name&gt; &lt;password&gt;            -&gt; OK &lt;message&gt; | ERR &lt;message&gt;
 * COUNT                             -&gt; OK &lt;registered users&gt;
 * REQ &lt;name&gt; &lt;base64-payload&gt;        -&gt; OK &lt;base64-payload&gt; | ERR &lt;message&gt;
 * QUIT                              -&gt; the server closes the connection
 * anything else                     -&gt; ERR unknown command
 * </pre>
 *
 * <p>{@code REQ} is the one command whose payload the server does not understand: it base64 decodes the
 * argument, hands the bytes to the plugin handler registered under {@code name} and base64 encodes whatever
 * that handler returns. The server never parses, logs or stores either blob, and it requires an authenticated
 * session, so a client cannot use a plugin's request surface before the server knows who is asking.
 */
public final class GameServer {

    private static final System.Logger LOG = System.getLogger(GameServer.class.getName());

    private static final int CLIENT_QUEUE_CAPACITY = 128;

    /** Request name a plugin registers under: lower case letters, digits and dashes; see the registry. */
    private static final int MAX_REQUEST_NAME_LENGTH = 32;

    /** The single line a client gets when it sends a request without having logged in first. */
    private static final String ERR_LOGIN_FIRST = "ERR login first";

    /** The single line a client gets for a name nothing is registered under, or for a malformed request. */
    private static final String ERR_UNKNOWN_REQUEST = "ERR unknown request";

    /** The single line a client gets when a registered handler threw instead of answering. */
    private static final String ERR_REQUEST_FAILED = "ERR request failed";

    private final ServerConfig config;
    private final AuthService auth;

    /**
     * The plugin request handlers this server is willing to relay.
     *
     * <p>An empty registry is the default and the reason the constructors without a registry are still
     * correct: no plugin, no answer, and {@code REQ} reports an unknown request without the server needing
     * to know that plugins exist at all.
     */
    private final RequestRegistry requests;

    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicInteger clientCount = new AtomicInteger();
    private final Map<Socket, String> clients = new ConcurrentHashMap<>();

    private volatile ServerSocket serverSocket;
    private volatile ThreadPoolExecutor clientPool;
    private volatile Thread acceptorThread;
    private volatile ConnectionListener connectionListener;

    /**
     * Creates a server. Nothing binds until {@link #start()} is called, so a server can be constructed,
     * inspected and discarded without touching a port.
     *
     * <p>The registry is empty, so this server answers every {@code REQ} with an unknown request; that is
     * the right choice for a server without plugins and keeps its behaviour identical to what it was before
     * plugins could register handlers.
     *
     * @param config the socket and framing limits
     * @param auth   the authentication service every {@code REGISTER} and {@code LOGIN} goes through
     */
    public GameServer(ServerConfig config, AuthService auth) {
        this(config, auth, new RequestRegistry());
    }

    /**
     * Creates a server that relays plugin requests. Nothing binds until {@link #start()} is called.
     *
     * <p>A server with plugins passes the very registry the plugin loader handed to them, so a handler
     * registered at load time is reachable through {@code REQ} as soon as the socket accepts a client.
     *
     * @param config   the socket and framing limits
     * @param auth     the authentication service every {@code REGISTER} and {@code LOGIN} goes through
     * @param requests the handlers {@code REQ} may reach; never {@code null}
     */
    public GameServer(ServerConfig config, AuthService auth, RequestRegistry requests) {
        this.config = Objects.requireNonNull(config, "config");
        this.auth = Objects.requireNonNull(auth, "auth");
        this.requests = Objects.requireNonNull(requests, "requests");
    }

    /**
     * State of one connection that is not the socket: whether its client has logged in.
     *
     * <p>The game protocol has no session tokens yet, so "who is asking" is exactly "which connection is
     * this", and a field per connection is the smallest thing that can hold it. The object lives on the
     * client thread that reads the socket, so it needs no synchronisation.
     */
    private static final class ClientSession {

        private boolean authenticated;

        /** Whether this connection completed a successful {@code LOGIN}. */
        boolean authenticated() {
            return authenticated;
        }

        /** Records a successful login of the client on this connection. */
        void markAuthenticated() {
            this.authenticated = true;
        }
    }

    /**
     * Optional observer for connect and disconnect events.
     *
     * <p>This exists so a future lobby or metrics component can react to connections without the server
     * needing to know about it. It is deliberately a plain interface, not a plug-in framework, and the
     * callbacks run on the client thread, so an implementation must not block.
     */
    public interface ConnectionListener {

        /**
         * Called after a client completed the encrypted handshake.
         *
         * @param address the peer address, in {@code host:port} form
         */
        void connected(String address);

        /**
         * Called once when a client is removed, for any reason.
         *
         * @param address the peer address, in {@code host:port} form
         */
        void disconnected(String address);
    }

    /**
     * Binds the listener and starts accepting clients.
     *
     * <p>Binding happens on the calling thread so that a failure to bind - a busy port, a privileged port,
     * a wrong interface - is reported to the caller as an exception instead of disappearing into a
     * background thread.
     *
     * @throws IOException          if the socket cannot be bound
     * @throws IllegalStateException if the server is already running
     */
    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("server is already running on port " + port());
        }
        try {
            ServerSocket socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(config.bindAddress(), config.port()), config.backlog());
            serverSocket = socket;
        } catch (IOException failure) {
            running.set(false);
            throw new IOException("server failed to bind " + config.bindAddress() + ":" + config.port()
                    + ": " + failure.getMessage(), failure);
        }

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                Math.min(4, config.maxClients()),
                config.maxClients(),
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(CLIENT_QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "oc-game-client");
                    thread.setDaemon(true);
                    return thread;
                },
                (runnable, executor) -> LOG.log(System.Logger.Level.WARNING,
                        "refusing a client: all " + config.maxClients()
                                + " client threads are busy and the queue is full"));
        pool.allowCoreThreadTimeOut(true);
        clientPool = pool;

        Thread acceptor = new Thread(this::acceptLoop, "oc-game-acceptor");
        acceptor.setDaemon(true);
        acceptorThread = acceptor;
        acceptor.start();
        LOG.log(System.Logger.Level.INFO, "game server listening on " + config.bindAddress() + ":" + port()
                + " (" + config.describe() + ")");
    }

    /**
     * Returns the port the server is actually bound to.
     *
     * <p>This is the port configuration's value after binding, so a server configured with port {@code 0}
     * reports the port the operating system picked. Before {@link #start()} it reports the configured
     * value, which is the only thing that is known at that point.
     *
     * @return the bound port, or the configured port when not running
     */
    public int port() {
        ServerSocket socket = serverSocket;
        return socket == null ? config.port() : socket.getLocalPort();
    }

    /**
     * Returns whether this server is accepting or serving connections.
     *
     * @return {@code true} between a successful {@link #start()} and the end of {@link #stop()}
     */
    public boolean running() {
        return running.get();
    }

    /**
     * Returns the number of clients currently connected and past the handshake.
     *
     * @return the current client count
     */
    public int clientCount() {
        return clientCount.get();
    }

    /**
     * Returns the addresses of the connected clients, for logs and for the self check.
     *
     * @return a snapshot of peer addresses in {@code host:port} form, in no particular order
     */
    public List<String> clientAddresses() {
        return List.copyOf(clients.values());
    }

    /**
     * Registers an observer for connect and disconnect events.
     *
     * @param listener the listener, or {@code null} to remove the current one
     */
    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    /**
     * Stops the server and releases its port. Calling this more than once is harmless.
     *
     * <p>The order matters: the listening socket is closed first, which unblocks the acceptor's
     * {@code accept()}, then the client threads are interrupted, then the pool is awaited for a short
     * while so the port is free and the threads are gone by the time this method returns.
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        ServerSocket socket = serverSocket;
        serverSocket = null;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException failure) {
                LOG.log(System.Logger.Level.WARNING,
                        "failed to close the listening socket: " + failure.getMessage());
            }
        }

        for (Socket client : clients.keySet()) {
            closeClientSocket(client, "stop");
        }
        ThreadPoolExecutor pool = clientPool;
        clientPool = null;
        if (pool != null) {
            pool.shutdownNow();
            try {
                if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                    LOG.log(System.Logger.Level.WARNING,
                            "client threads did not stop within 2s; they are daemon and will not block"
                                    + " process exit");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        Thread acceptor = acceptorThread;
        acceptorThread = null;
        if (acceptor != null) {
            acceptor.interrupt();
            try {
                acceptor.join(TimeUnit.SECONDS.toMillis(2));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        clients.clear();
        clientCount.set(0);
        LOG.log(System.Logger.Level.INFO, "game server stopped; port " + config.port() + " released");
    }

    /** Accepts connections until the listening socket is closed. */
    private void acceptLoop() {
        ServerSocket socket = serverSocket;
        while (running.get() && socket != null) {
            Socket client;
            try {
                client = socket.accept();
            } catch (SocketException closed) {
                // Expected when stop() closes the socket under us; anything else is worth a log line.
                if (running.get()) {
                    LOG.log(System.Logger.Level.WARNING,
                            "accept failed, the listening socket is gone: " + closed.getMessage());
                }
                return;
            } catch (IOException failure) {
                LOG.log(System.Logger.Level.WARNING, "accept failed: " + failure.getMessage());
                continue;
            }
            try {
                configureAcceptedSocket(client);
            } catch (IOException failure) {
                LOG.log(System.Logger.Level.WARNING, "rejecting " + peerOf(client)
                        + ": the socket could not be configured: " + failure.getMessage());
                closeClientSocket(client, "configuration failed");
                continue;
            }
            ThreadPoolExecutor pool = clientPool;
            if (pool == null) {
                closeClientSocket(client, "server stopping");
                continue;
            }
            try {
                pool.execute(() -> serveClient(client));
            } catch (RejectedExecutionException rejected) {
                // The rejection handler above has already logged; the socket still has to be closed.
                closeClientSocket(client, "server overloaded");
            }
        }
    }

    /** Applies the read timeout and TCP tuning to a freshly accepted socket. */
    private void configureAcceptedSocket(Socket client) throws IOException {
        client.setSoTimeout(config.readTimeoutMillis());
        client.setTcpNoDelay(true);
        client.setKeepAlive(true);
    }

    /** Runs one client from handshake to disconnect. Never lets an exception escape. */
    private void serveClient(Socket socket) {
        String peer = peerOf(socket);
        clients.put(socket, peer);
        clientCount.incrementAndGet();
        SecureChannel channel = null;
        ConnectionListener listener = connectionListener;
        try {
            channel = SecureChannel.serverSide(socket.getInputStream(), socket.getOutputStream(),
                    config.maxFrameBytes(), config.maxLineLength());
            if (listener != null) {
                listener.connected(peer);
            }
            LOG.log(System.Logger.Level.INFO, "client " + peer + " connected using "
                    + channel.protocolName());
            commandLoop(socket, channel, peer);
        } catch (SocketTimeoutException idle) {
            LOG.log(System.Logger.Level.WARNING, "disconnecting idle client " + peer + " after "
                    + config.readTimeoutMillis() + "ms without a command");
        } catch (IOException failure) {
            // Handshake failures, oversized frames, tag mismatches and plain socket errors all land here.
            // The message is written by SecureChannel and always names what failed; the peer address is
            // added because that is the one thing the channel cannot know.
            LOG.log(System.Logger.Level.WARNING,
                    "disconnecting client " + peer + ": " + failure.getMessage());
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING,
                    "disconnecting client " + peer + " after an unexpected error: " + failure);
        } finally {
            if (channel != null) {
                channel.close();
            }
            closeClientSocket(socket, "client finished");
            clients.remove(socket);
            clientCount.decrementAndGet();
            if (listener != null) {
                listener.disconnected(peer);
            }
            LOG.log(System.Logger.Level.INFO, "client " + peer + " disconnected");
        }
    }

    /** Reads command lines until the client quits, closes or breaks the protocol. */
    private void commandLoop(Socket socket, SecureChannel channel, String peer) throws IOException {
        ClientSession session = new ClientSession();
        while (running.get() && !socket.isClosed()) {
            String line = channel.readLine();
            if (line == null) {
                return;
            }
            String response = execute(line, session);
            if (response == null) {
                channel.writeLine("OK bye");
                return;
            }
            channel.writeLine(response);
        }
    }

    /**
     * Executes one command and returns the single response line.
     *
     * <p>Command names are case insensitive, but names and passwords are compared exactly and are never
     * trimmed beyond the single separating whitespace, because a password may legitimately contain or end
     * with whitespace.
     *
     * @param line the raw command line, already validated to be at most the configured length
     * @param session the state of this connection, which {@code LOGIN} and {@code REQ} both consult
     * @return the response line, or {@code null} when the client asked to quit
     */
    private String execute(String line, ClientSession session) {
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
     * Serves one {@code REQ}: an authenticated session, a registered name and an opaque base64 payload.
     *
     * <p>The rule that a request needs a login is new and deliberate. {@code REQ} is the only command that
     * can make a plugin do work, so it is the only one that must know who is asking; {@code PING},
     * {@code REGISTER}, {@code LOGIN} and {@code COUNT} keep their old behaviour exactly.
     *
     * <p>Nothing in this method touches the payload or the response beyond base64 and the relay: the bytes
     * are neither parsed nor logged, and an unparsable payload is refused without ever reaching the handler.
     */
    private String request(String rest, ClientSession session) {
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
     * answers "are these credentials right", and only the server knows which client is speaking, so the
     * session is the server's business and the password hash never has to be consulted twice.
     */
    private String credentials(String command, String rest, ClientSession session, CredentialCall call) {
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

    /** Formats the peer address of a socket for logging and for {@link #clientAddresses()}. */
    private static String peerOf(Socket socket) {
        SocketAddress address = socket.getRemoteSocketAddress();
        return address == null ? "unknown peer" : address.toString();
    }

    /** Closes one client socket, logging - but never failing on - a close error. */
    private static void closeClientSocket(Socket socket, String reason) {
        try {
            socket.close();
        } catch (IOException failure) {
            LOG.log(System.Logger.Level.WARNING,
                    "failed to close a client socket (" + reason + "): " + failure.getMessage());
        }
    }
}
