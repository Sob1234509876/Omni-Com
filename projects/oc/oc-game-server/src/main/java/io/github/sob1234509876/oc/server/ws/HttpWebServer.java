package io.github.sob1234509876.oc.server.ws;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The browser facing endpoint: one HTTP listener that serves the static web client and upgrades
 * {@code /ws} to an RFC 6455 WebSocket.
 *
 * <p>The endpoint is an adapter, not a second game server. Its only job is to put browser bytes into the
 * shape the game already understands - one text frame is one command line - and to put answers back. The
 * command grammar itself lives in {@code CommandDispatcher}, shared with the encrypted TCP channel, so both
 * transports answer {@code PING}, {@code REGISTER}, {@code LOGIN}, {@code COUNT}, {@code REQ} and
 * {@code QUIT} with exactly the same lines and the same login rule.
 *
 * <p><strong>Why plain sockets.</strong> The module must build offline with zero third-party dependencies,
 * and {@code com.sun.net.httpserver} has no WebSocket support. Implementing the small, stable part of
 * RFC 6455 that a game client needs - text messages, ping and close - is therefore less risky than
 * pretending a JDK HTTP server can upgrade a connection it does not expose.
 *
 * <p><strong>Threading.</strong> One acceptor thread hands connections to a bounded pool of daemon threads.
 * A WebSocket connection then owns its thread until it closes, which is the same simple model the TCP
 * channel uses: a browser tab that stops reading costs one thread, not the listener, and a daemon thread
 * cannot keep the JVM alive after {@link #stop()}. Static requests are short and release their thread
 * immediately.
 *
 * <p><strong>Strictness.</strong> The handshake is validated field by field and any deviation answers
 * {@code 400} (or {@code 403} for a refused origin) with a one line reason, then closes. In particular the
 * {@code Sec-WebSocket-Key} must decode to exactly sixteen bytes. The {@code Origin} is accepted first when
 * it is the same origin as the request's {@code Host}, and otherwise has to match the configured allowlist
 * when one is configured. An empty allowlist permits any origin and is documented in
 * {@link WebSocketConfig} as a development default only.
 *
 * <p>Port {@code 0} asks the operating system for a free port, which is what every check in this module
 * uses; callers that want to know the port read {@link #port()} after {@link #start()}.
 */
public final class HttpWebServer implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(HttpWebServer.class.getName());

    /** The magic value RFC 6455 appends to the client key before hashing it. */
    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    /** Accept queue for the HTTP listener; the same order of magnitude as the game socket's. */
    private static final int BACKLOG = 64;

    /** Upper bound on connection threads, so a browser cannot create them without limit. */
    private static final int MAX_CONNECTION_THREADS = 64;

    /** Requests waiting for a thread; beyond this the endpoint refuses the connection. */
    private static final int CONNECTION_QUEUE_CAPACITY = 128;

    private final WebSocketConfig config;
    private final WebSocketHandler handler;
    private final StaticFiles staticFiles;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong connectionIds = new AtomicLong();

    /** Every socket accepted but not finished, so {@link #stop()} can close them all. */
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();

    /** Live WebSocket connections, so {@link #stop()} can send them a close frame. */
    private final Set<WebSocketConnection> connections = ConcurrentHashMap.newKeySet();

    private volatile ServerSocket serverSocket;
    private volatile ThreadPoolExecutor connectionPool;
    private volatile Thread acceptorThread;

    /**
     * Creates an endpoint. Nothing binds until {@link #start()} is called, so a server can be configured,
     * inspected and discarded without touching a port.
     *
     * @param config  the port, path, static root and limits
     * @param handler the callbacks every upgraded connection delivers its messages to
     */
    public HttpWebServer(WebSocketConfig config, WebSocketHandler handler) {
        this.config = Objects.requireNonNull(config, "config");
        this.handler = Objects.requireNonNull(handler, "handler");
        this.staticFiles = new StaticFiles(config.staticRoot());
    }

    /**
     * Binds the listener and starts accepting browser connections.
     *
     * <p>Binding happens on the calling thread so a busy or privileged port is reported as an exception
     * instead of disappearing into a background thread.
     *
     * @throws IOException           if the socket cannot be bound
     * @throws IllegalStateException if this endpoint is already running
     */
    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("the browser endpoint is already running on port " + port());
        }
        try {
            ServerSocket socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(config.bindAddress(), config.port()), BACKLOG);
            serverSocket = socket;
        } catch (IOException failure) {
            running.set(false);
            throw new IOException("the browser endpoint failed to bind " + config.bindAddress() + ":"
                    + config.port() + ": " + failure.getMessage(), failure);
        }

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                Math.min(4, MAX_CONNECTION_THREADS),
                MAX_CONNECTION_THREADS,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(CONNECTION_QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "oc-web-client");
                    thread.setDaemon(true);
                    return thread;
                },
                (runnable, executor) -> LOG.log(System.Logger.Level.WARNING,
                        "refusing a browser connection: all " + MAX_CONNECTION_THREADS
                                + " connection threads are busy and the queue is full"));
        pool.allowCoreThreadTimeOut(true);
        connectionPool = pool;

        Thread acceptor = new Thread(this::acceptLoop, "oc-web-acceptor");
        acceptor.setDaemon(true);
        acceptorThread = acceptor;
        acceptor.start();
        LOG.log(System.Logger.Level.INFO, "browser endpoint listening on " + config.bindAddress() + ":"
                + port() + " (" + config.describe() + ")");
    }

    /**
     * Returns the port the endpoint is actually bound to.
     *
     * <p>After binding this is the port the operating system chose when the configuration asked for
     * {@code 0}; before {@link #start()} it is the configured value, which is the only thing known then.
     *
     * @return the bound port, or the configured port when not running
     */
    public int port() {
        ServerSocket socket = serverSocket;
        return socket == null ? config.port() : socket.getLocalPort();
    }

    /**
     * Returns whether the endpoint is accepting connections.
     *
     * @return {@code true} between a successful {@link #start()} and the end of {@link #stop()}
     */
    public boolean running() {
        return running.get();
    }

    /**
     * Stops the endpoint and releases its port. Calling this more than once is harmless.
     *
     * <p>The order mirrors the game socket: close the listener first so no new connection slips in, then
     * close the live connections (with a close frame so a browser sees a normal shutdown), then await the
     * pool and the acceptor. Everything is daemon, so even a connection that ignores the close cannot keep
     * the process alive.
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
                        "failed to close the browser listener: " + failure.getMessage());
            }
        }
        for (WebSocketConnection connection : connections) {
            connection.close(1001, "server stopping");
        }
        for (Socket client : sockets) {
            closeQuietly(client);
        }
        ThreadPoolExecutor pool = connectionPool;
        connectionPool = null;
        if (pool != null) {
            pool.shutdownNow();
            try {
                if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                    LOG.log(System.Logger.Level.WARNING,
                            "browser connection threads did not stop within 2s; they are daemon and will"
                                    + " not block process exit");
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
        connections.clear();
        sockets.clear();
        LOG.log(System.Logger.Level.INFO, "browser endpoint stopped; port " + config.port() + " released");
    }

    /** Stops the endpoint; present so a try-with-resources block can own one. */
    @Override
    public void close() {
        stop();
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
                            "browser accept failed, the listening socket is gone: " + closed.getMessage());
                }
                return;
            } catch (IOException failure) {
                LOG.log(System.Logger.Level.WARNING, "browser accept failed: " + failure.getMessage());
                continue;
            }
            sockets.add(client);
            ThreadPoolExecutor pool = connectionPool;
            if (pool == null) {
                sockets.remove(client);
                closeQuietly(client);
                continue;
            }
            try {
                pool.execute(() -> serve(client));
            } catch (RejectedExecutionException rejected) {
                // The rejection handler above has already logged; the socket still has to be closed.
                sockets.remove(client);
                closeQuietly(client);
            }
        }
    }

    /** Runs one HTTP request, or one whole WebSocket conversation, on its own thread. */
    private void serve(Socket socket) {
        String peer = peerOf(socket);
        try {
            socket.setSoTimeout(config.idleTimeoutMillis());
            socket.setTcpNoDelay(true);
            HttpRequest request = HttpRequest.read(socket.getInputStream());
            if (request == null) {
                return;
            }
            if (request.isWebSocketAttempt() || request.path().equals(config.path())) {
                serveWebSocket(socket, request, peer);
            } else {
                serveStatic(socket, request);
            }
        } catch (HttpError refusal) {
            writeErrorQuietly(socket, refusal.status(), refusal.getMessage());
        } catch (SocketTimeoutException idle) {
            LOG.log(System.Logger.Level.DEBUG, "browser client " + peer + " sent no request within "
                    + config.idleTimeoutMillis() + "ms");
        } catch (IOException failure) {
            LOG.log(System.Logger.Level.DEBUG,
                    "browser client " + peer + " disconnected: " + failure.getMessage());
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING,
                    "browser request from " + peer + " failed unexpectedly: " + failure);
            writeErrorQuietly(socket, 500, "the request failed inside the server");
        } finally {
            closeQuietly(socket);
            sockets.remove(socket);
        }
    }

    /** Serves one static request, after rejecting methods the file route does not have. */
    private void serveStatic(Socket socket, HttpRequest request) throws IOException {
        OutputStream out = socket.getOutputStream();
        if (!"GET".equals(request.method()) && !"HEAD".equals(request.method())) {
            HttpResponse.text(405, "Method Not Allowed", "only GET and HEAD are served by the static route")
                    .withHeader("Allow", "GET, HEAD")
                    .writeTo(out, false);
            return;
        }
        HttpResponse response = staticFiles.get(request.path());
        response.writeTo(out, "HEAD".equals(request.method()));
    }

    /** Validates and performs the upgrade, then hands the socket to a connection until it closes. */
    private void serveWebSocket(Socket socket, HttpRequest request, String peer) throws IOException {
        OutputStream out = socket.getOutputStream();
        if (!request.path().equals(config.path())) {
            HttpResponse.text(404, "Not Found", "no WebSocket endpoint at " + request.path()
                    + ", the endpoint is " + config.path()).writeTo(out, false);
            return;
        }
        Refusal refusal = validateUpgrade(request);
        if (refusal != null) {
            writeError(out, refusal.status(), refusal.reason());
            return;
        }
        String key = request.header("sec-websocket-key").strip();
        String accept = acceptKey(key);
        String handshake = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n"
                + "\r\n";
        out.write(handshake.getBytes(StandardCharsets.US_ASCII));
        out.flush();

        String id = "ws-" + Long.toHexString(connectionIds.incrementAndGet());
        WebSocketConnection connection = new WebSocketConnection(socket, handler, config, id, peer);
        connections.add(connection);
        try {
            connection.run();
        } finally {
            connections.remove(connection);
        }
    }

    /**
     * Applies every rule of the opening handshake, in the order a diagnostic reader expects.
     *
     * @return the refusal to send, or {@code null} when the handshake is acceptable
     */
    private Refusal validateUpgrade(HttpRequest request) {
        if (!"GET".equals(request.method())) {
            return new Refusal(400, "the WebSocket handshake requires GET");
        }
        if (!"HTTP/1.1".equals(request.version())) {
            return new Refusal(400, "the WebSocket handshake requires HTTP/1.1");
        }
        if (!request.headerContainsToken("upgrade", "websocket")) {
            return new Refusal(400, "the WebSocket handshake requires 'Upgrade: websocket'");
        }
        if (!request.headerContainsToken("connection", "upgrade")) {
            return new Refusal(400, "the WebSocket handshake requires 'Connection: Upgrade'");
        }
        String version = request.header("sec-websocket-version");
        if (version == null) {
            return new Refusal(400, "the WebSocket handshake requires 'Sec-WebSocket-Version: 13'");
        }
        if (!"13".equals(version.strip())) {
            return new Refusal(400, "Sec-WebSocket-Version must be 13, was '" + version.strip() + "'");
        }
        String key = request.header("sec-websocket-key");
        if (key == null) {
            return new Refusal(400, "the WebSocket handshake requires 'Sec-WebSocket-Key'");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(key.strip());
        } catch (IllegalArgumentException malformed) {
            return new Refusal(400, "Sec-WebSocket-Key is not valid base64");
        }
        if (decoded.length != 16) {
            return new Refusal(400, "Sec-WebSocket-Key must decode to 16 bytes, decoded to "
                    + decoded.length);
        }
        return originRefusal(request);
    }

    /**
     * Applies the same origin rule first, then the configured allowlist.
     *
     * <p><strong>Why same origin is safe.</strong> A browser sets {@code Origin} to the page that opened
     * the socket and {@code Host} to the server the socket was opened to, and the page cannot forge either
     * value. When the two host and port values are equal, the page was served by this very server, so the
     * browser already applied our own origin policy to it. Accepting that upgrade therefore does not trust
     * a foreign page; it trusts a page we handed out ourselves. That is what lets a LAN client work when
     * the page is opened through {@code http://192.168.1.7:25567/} without adding every possible LAN
     * address to the loopback allowlist.
     *
     * <p><strong>Why the allowlist stays.</strong> A WebSocket handshake is not protected by the browser's
     * same origin policy, so a page served from another site can still open a socket to this server. The
     * allowlist is the second, explicit rule for exactly that case: it names the origins - by default the
     * bundled development client on localhost - that may talk to the server even though they are not same
     * origin. Keeping it means a page from {@code http://evil.example} is refused unless an operator
     * deliberately allows it, while same origin needs no configuration at all.
     *
     * <p>The scheme is ignored because a page may be served over HTTPS through a proxy while the socket is
     * plain {@code ws}; the host and port, which are the origin's identity, are compared case-insensitively.
     * A missing {@code Host} or a missing {@code Origin} is never same origin, because without both there
     * is nothing to compare. A malformed {@code Origin} answers {@code 400}: it is a broken request, not an
     * origin this server could safely decide about.
     *
     * <p>When the allowlist is empty, any syntactically valid origin is still allowed. That development
     * default is unchanged; a malformed header is refused rather than handed to the wildcard, because it
     * cannot be an origin a browser sent.
     *
     * @param request the upgrade request whose {@code Origin} and {@code Host} are compared
     * @return the refusal to send, or {@code null} when the request may upgrade
     */
    private Refusal originRefusal(HttpRequest request) {
        String origin = request.header("origin");
        String host = request.header("host");
        if (origin != null && !"null".equalsIgnoreCase(origin)) {
            String authority = authorityOf(origin);
            if (authority == null) {
                return new Refusal(400, "the Origin header is malformed: '" + origin + "'");
            }
            if (host != null && authority.equalsIgnoreCase(host.strip())) {
                return null;
            }
        }
        List<String> allowed = config.allowedOrigins();
        if (allowed.isEmpty()) {
            return null;
        }
        if (origin == null) {
            return new Refusal(403, "the WebSocket handshake requires an Origin header when an origin"
                    + " allowlist is configured");
        }
        for (String pattern : allowed) {
            if (wildcardMatch(pattern, origin)) {
                return null;
            }
        }
        return new Refusal(403, "origin '" + origin + "' is neither the same origin as the request nor in"
                + " the WebSocket origin allowlist");
    }

    /**
     * Extracts the {@code host[:port]} authority of a serialized origin.
     *
     * <p>The check is structural rather than a full URL parse. An origin is exactly
     * {@code scheme://host[:port]} with no path, query, fragment, user information or whitespace; a value
     * that has any of those, or a scheme that is not a legal scheme token, is malformed for this header
     * rather than a URL this server should normalize. Keeping the comparison to the authority is what makes
     * the rule independent of the scheme and of any default port the client chose to omit.
     *
     * @param origin the raw {@code Origin} header value
     * @return the authority exactly as written, or {@code null} when the value is not a serialized origin
     */
    private static String authorityOf(String origin) {
        int separator = origin.indexOf("://");
        if (separator <= 0 || !isScheme(origin.substring(0, separator))) {
            return null;
        }
        String authority = origin.substring(separator + 3);
        if (authority.isEmpty()) {
            return null;
        }
        for (int index = 0; index < authority.length(); index++) {
            char character = authority.charAt(index);
            if (character <= 0x20 || character >= 0x7F || "/?#@\\".indexOf(character) >= 0) {
                return null;
            }
        }
        return isAuthority(authority) ? authority : null;
    }

    /** Whether a string is an RFC 3986 scheme, which is what precedes {@code ://} in an origin. */
    private static boolean isScheme(String scheme) {
        if (scheme.isEmpty() || !isAsciiLetter(scheme.charAt(0))) {
            return false;
        }
        for (int index = 1; index < scheme.length(); index++) {
            char character = scheme.charAt(index);
            boolean legal = isAsciiLetter(character) || (character >= '0' && character <= '9')
                    || character == '+' || character == '-' || character == '.';
            if (!legal) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether an authority is a host with an optional valid port.
     *
     * <p>An IPv6 literal must be bracketed, as it is in every serialized origin; an unbracketed value with
     * more than one colon is refused instead of being split at an arbitrary colon. An empty host and a
     * port outside {@code 1..65535} are refused as well, because neither can name the server a browser
     * opened a socket to.
     */
    private static boolean isAuthority(String authority) {
        if (authority.startsWith("[")) {
            int close = authority.indexOf(']');
            if (close < 0) {
                return false;
            }
            String literal = authority.substring(1, close);
            if (literal.isEmpty()) {
                return false;
            }
            for (int index = 0; index < literal.length(); index++) {
                char character = literal.charAt(index);
                if (Character.digit(character, 16) < 0 && character != ':' && character != '.') {
                    return false;
                }
            }
            String rest = authority.substring(close + 1);
            return rest.isEmpty() || (rest.startsWith(":") && isPort(rest.substring(1)));
        }
        int colon = authority.indexOf(':');
        if (colon >= 0) {
            if (authority.indexOf(':', colon + 1) >= 0) {
                return false;
            }
            return isHost(authority.substring(0, colon)) && isPort(authority.substring(colon + 1));
        }
        return isHost(authority);
    }

    /**
     * Whether a host is a DNS name or an IPv4 literal.
     *
     * <p>The character set is intentionally small: letters, digits, {@code -}, {@code .} and {@code _}.
     * International names reach this layer as punycode, so a non-ASCII character here means the header is
     * malformed rather than a name this server should try to normalize.
     */
    private static boolean isHost(String host) {
        if (host.isEmpty()) {
            return false;
        }
        for (int index = 0; index < host.length(); index++) {
            char character = host.charAt(index);
            boolean legal = isAsciiLetter(character) || (character >= '0' && character <= '9')
                    || character == '-' || character == '.' || character == '_';
            if (!legal) {
                return false;
            }
        }
        return true;
    }

    /** Whether a port is decimal digits naming a usable TCP port. */
    private static boolean isPort(String text) {
        if (text.isEmpty() || text.length() > 5) {
            return false;
        }
        int port = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
            port = port * 10 + (character - '0');
        }
        return port >= 1 && port <= 65535;
    }

    /** Whether a character is an ASCII letter; host names and schemes are ASCII on the wire. */
    private static boolean isAsciiLetter(char character) {
        return (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z');
    }

    /**
     * Matches a glob pattern where {@code *} stands for any run of characters, ignoring case.
     *
     * <p>Written by hand instead of building a regular expression from configuration: the pattern comes from
     * a config file, and turning configuration into a regex is how a harmless allowlist entry becomes a
     * regex denial of service. Origins are ASCII, so a per-character walk is enough.
     *
     * @param pattern the configured pattern, for example {@code http://localhost:*}
     * @param value   the {@code Origin} header value
     * @return whether the value matches the whole pattern
     */
    private static boolean wildcardMatch(String pattern, String value) {
        int patternIndex = 0;
        int valueIndex = 0;
        int starIndex = -1;
        int starValueIndex = 0;
        while (valueIndex < value.length()) {
            if (patternIndex < pattern.length()
                    && pattern.charAt(patternIndex) == '*') {
                starIndex = patternIndex++;
                starValueIndex = valueIndex;
            } else if (patternIndex < pattern.length()
                    && Character.toLowerCase(pattern.charAt(patternIndex))
                            == Character.toLowerCase(value.charAt(valueIndex))) {
                patternIndex++;
                valueIndex++;
            } else if (starIndex >= 0) {
                patternIndex = starIndex + 1;
                valueIndex = ++starValueIndex;
            } else {
                return false;
            }
        }
        while (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
            patternIndex++;
        }
        return patternIndex == pattern.length();
    }

    /**
     * Computes the {@code Sec-WebSocket-Accept} value for a client key.
     *
     * <p>SHA-1 is not used here as a security primitive: RFC 6455 defines the value as
     * {@code base64(SHA-1(key + GUID))}, and a browser rejects anything else. It proves that the server read
     * the handshake, not that the connection is secret - the transport is plain text and is intended for a
     * local development client.
     */
    private static String acceptKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest((key + WEBSOCKET_GUID).getBytes(StandardCharsets.ISO_8859_1));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-1 is required by RFC 6455 but missing from this JVM",
                    failure);
        }
    }

    /** Writes an error response, letting the socket failure propagate to the caller. */
    private static void writeError(OutputStream out, int status, String reason) throws IOException {
        HttpResponse.text(status, HttpResponse.reasonFor(status), reason).writeTo(out, false);
    }

    /** Writes an error response while a connection is being torn down, swallowing secondary failures. */
    private static void writeErrorQuietly(Socket socket, int status, String reason) {
        try {
            writeError(socket.getOutputStream(), status, reason);
        } catch (IOException ignored) {
            // The peer is already gone; a response it cannot read is not worth a second failure.
        }
    }

    /** Formats the peer address of a socket for logging and for {@link WebSocketSession#remoteAddress()}. */
    private static String peerOf(Socket socket) {
        SocketAddress address = socket.getRemoteSocketAddress();
        return address == null ? "unknown peer" : address.toString();
    }

    /** Closes a socket, logging nothing: the caller is already handling the reason it is going away. */
    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Closing an already failed socket cannot fail in a way the caller could act on.
        }
    }

    /** One refused handshake: the status and the single line of explanation. */
    private record Refusal(int status, String reason) {
    }
}
