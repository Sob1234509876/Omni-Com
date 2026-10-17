package io.github.sob1234509876.oc.server.net;

import io.github.sob1234509876.oc.server.auth.AuthService;
import io.github.sob1234509876.oc.server.plugin.RequestRegistry;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 *
 * <p>The grammar itself is executed by {@link CommandDispatcher}, which the browser facing WebSocket endpoint
 * uses as well. This class therefore owns sockets, framing and lifecycle only; a rule such as "REQ needs a
 * successful LOGIN" has exactly one implementation for both transports.
 */
public final class GameServer {

    private static final System.Logger LOG = System.getLogger(GameServer.class.getName());

    private static final int CLIENT_QUEUE_CAPACITY = 128;

    private final ServerConfig config;

    /**
     * The command grammar both this transport and the browser facing one execute.
     *
     * <p>It is stateless, so the TCP channel and the WebSocket channel can each hold their own instance over
     * the same services and still answer identically. Keeping the call behind this field is also what keeps
     * {@link #serveClient(Socket)} about sockets: it frames lines, and the dispatcher decides what a line
     * means.
     */
    private final CommandDispatcher commands;

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
     * An empty registry is equally valid: no plugin, no answer, and {@code REQ} reports an unknown request
     * without the server needing to know that plugins exist at all.
     *
     * @param config   the socket and framing limits
     * @param auth     the authentication service every {@code REGISTER} and {@code LOGIN} goes through
     * @param requests the handlers {@code REQ} may reach; never {@code null}
     */
    public GameServer(ServerConfig config, AuthService auth, RequestRegistry requests) {
        this.config = Objects.requireNonNull(config, "config");
        this.commands = new CommandDispatcher(
                Objects.requireNonNull(auth, "auth"),
                Objects.requireNonNull(requests, "requests"));
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
        CommandDispatcher.Session session = new CommandDispatcher.Session();
        while (running.get() && !socket.isClosed()) {
            String line = channel.readLine();
            if (line == null) {
                return;
            }
            String response = commands.execute(line, session);
            if (response == null) {
                channel.writeLine(CommandDispatcher.BYE);
                return;
            }
            channel.writeLine(response);
        }
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
