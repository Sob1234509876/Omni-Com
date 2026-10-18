package io.github.sob1234509876.oc.server.ws;

import java.util.List;
import java.util.Objects;

/**
 * Immutable tuning knobs for the browser facing HTTP and WebSocket endpoint.
 *
 * <p>The endpoint is optional: the game itself still speaks its encrypted TCP protocol, and this
 * configuration only decides whether a browser can reach the same commands over HTTP and WebSocket.
 * Splitting the settings into their own record keeps {@code ServerConfig} - which is about the game socket
 * and its framing - untouched, so a server that does not want a browser route is not forced to carry its
 * limits around.
 *
 * <p>Validation happens in the compact constructor rather than at bind time. A bad port, a path without a
 * leading slash or a zero frame ceiling can only fail much later, as a bind error or as a client that is
 * silently dropped, and by then the mistake is far from the code that made it.
 *
 * <p><strong>Port 0.</strong> {@link HttpWebServer} treats port {@code 0} as "ask the operating system for
 * a free port", which is what every check in this module uses. {@code OcGameServer} deliberately gives the
 * same value a second meaning - "do not start the browser endpoint at all" - because a command line user
 * who writes {@code --http-port 0} means to turn the feature off, not to start a server on an unknown
 * port. Both meanings are documented where they are implemented.
 *
 * @param port             HTTP port to bind; {@code 0} means a free port to {@link HttpWebServer} and
 *                         "disabled" to {@code OcGameServer}
 * @param bindAddress      literal address or host name to bind, e.g. {@code 0.0.0.0} or {@code 127.0.0.1}
 * @param path             the exact request path that upgrades to a WebSocket, normally {@code /ws}
 * @param staticRoot       directory the static file route serves, normally {@code web-client/dist}
 * @param maxFrameBytes    largest accepted WebSocket frame payload, in bytes
 * @param maxMessageBytes  largest accepted reassembled WebSocket message, in bytes
 * @param idleTimeoutMillis socket read timeout, which doubles as the idle timeout, in milliseconds
 * @param allowedOrigins   glob patterns a foreign {@code Origin} must match; a request whose
 *                         {@code Origin} host and port equal its {@code Host} is accepted as same origin
 *                         before this list is consulted, and an empty list allows any origin as a
 *                         development default only, because a page on any site could then drive a server
 *                         that listens on a developer's machine
 * @param pingIntervalMillis how often the endpoint sends a server initiated WebSocket ping frame to every
 *                         open connection, in milliseconds; a peer that answers with a pong produces the
 *                         inbound traffic that keeps its read timeout from expiring
 */
public record WebSocketConfig(
        int port,
        String bindAddress,
        String path,
        String staticRoot,
        int maxFrameBytes,
        int maxMessageBytes,
        int idleTimeoutMillis,
        List<String> allowedOrigins,
        int pingIntervalMillis) {

    /** Default port for the browser facing endpoint. */
    public static final int DEFAULT_PORT = 25567;

    /** Default WebSocket path. */
    public static final String DEFAULT_PATH = "/ws";

    /** Default directory of the built web client, relative to the working directory. */
    public static final String DEFAULT_STATIC_ROOT = "web-client/dist";

    /** 64 KiB: large enough for a command line, small enough that one peer cannot pin much memory. */
    public static final int DEFAULT_MAX_FRAME_BYTES = 64 * 1024;

    /** One MiB: a fragmented message may be larger than a single frame, but not unbounded. */
    public static final int DEFAULT_MAX_MESSAGE_BYTES = 1024 * 1024;

    /**
     * Five minutes: long enough that an open page is not disconnected just for being idle, while still
     * bounded so a vanished browser eventually releases its connection thread.
     *
     * <p>This is the default {@code --ws-idle 300}. The endpoint additionally sends a ping frame every
     * {@value #DEFAULT_PING_INTERVAL_MILLIS} ms, so a live browser - which answers pings automatically -
     * produces inbound pongs well inside this window and is not closed by the read timeout.
     */
    public static final int DEFAULT_IDLE_TIMEOUT_MILLIS = 300_000;

    /**
     * Default interval of the server initiated WebSocket ping frames: 25 seconds.
     *
     * <p>It is comfortably shorter than {@link #DEFAULT_IDLE_TIMEOUT_MILLIS}, so a browser that is only
     * being kept alive by the server's own pings still sends a pong between two read timeouts. Checks lower
     * it through {@link #withPingIntervalMillis(int)} so the heartbeat is observable without a 25 second
     * wait; the command line does not expose it because the default is the tuning that matters.
     */
    public static final int DEFAULT_PING_INTERVAL_MILLIS = 25_000;

    /**
     * The origins a browser client normally has during development.
     *
     * <p>A WebSocket handshake is not protected by the same origin policy: a page served from anywhere can
     * open a socket to {@code localhost}. Restricting the default to the loopback origins is what stops a
     * random website from driving a developer's game server, while still allowing the bundled web client.
     * A page served by the server itself is accepted by the same origin rule before this list is
     * consulted, so the list only has to name the cross-origin development client.
     */
    private static final List<String> DEFAULT_ORIGINS = List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "https://localhost:*",
            "https://127.0.0.1:*");

    /**
     * Validates every field.
     *
     * @throws IllegalArgumentException if a value cannot produce a working endpoint
     * @throws NullPointerException     if {@code bindAddress}, {@code path}, {@code staticRoot} or
     *                                  {@code allowedOrigins} is {@code null}
     */
    public WebSocketConfig {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be 0..65535, was " + port
                    + " (0 asks the operating system for a free port, or disables the endpoint)");
        }
        Objects.requireNonNull(bindAddress, "bindAddress");
        if (bindAddress.isBlank()) {
            throw new IllegalArgumentException("bindAddress must not be blank");
        }
        Objects.requireNonNull(path, "path");
        if (!path.startsWith("/") || path.length() < 2) {
            throw new IllegalArgumentException("path must start with '/' and name a route, was '" + path
                    + "'");
        }
        if (path.indexOf('?') >= 0 || path.indexOf('#') >= 0 || path.indexOf(' ') >= 0) {
            throw new IllegalArgumentException("path must not contain '?', '#' or spaces, was '" + path
                    + "'");
        }
        Objects.requireNonNull(staticRoot, "staticRoot");
        if (staticRoot.isBlank()) {
            throw new IllegalArgumentException("staticRoot must not be blank");
        }
        if (maxFrameBytes < 1) {
            throw new IllegalArgumentException("maxFrameBytes must be positive, was " + maxFrameBytes);
        }
        if (maxMessageBytes < 1) {
            throw new IllegalArgumentException("maxMessageBytes must be positive, was " + maxMessageBytes);
        }
        if (idleTimeoutMillis < 1) {
            throw new IllegalArgumentException("idleTimeoutMillis must be positive, was " + idleTimeoutMillis
                    + " (0 would block an idle connection thread forever)");
        }
        if (pingIntervalMillis < 1) {
            throw new IllegalArgumentException("pingIntervalMillis must be positive, was "
                    + pingIntervalMillis + " (0 would schedule a ping storm)");
        }
        Objects.requireNonNull(allowedOrigins, "allowedOrigins");
        for (String origin : allowedOrigins) {
            if (origin == null || origin.isBlank()) {
                throw new IllegalArgumentException("allowedOrigins must not contain null or blank"
                        + " entries, was " + allowedOrigins);
            }
        }
        allowedOrigins = List.copyOf(allowedOrigins);
    }

    /**
     * Creates a configuration with the default server ping interval.
     *
     * <p>This overload is the historical eight argument shape; it keeps every caller written before server
     * initiated pings existed compiling unchanged and gives it {@value #DEFAULT_PING_INTERVAL_MILLIS} ms.
     *
     * @param port              HTTP port to bind; {@code 0} asks for a free port
     * @param bindAddress       literal address or host name to bind
     * @param path              the exact request path that upgrades to a WebSocket
     * @param staticRoot        directory the static file route serves
     * @param maxFrameBytes     largest accepted WebSocket frame payload, in bytes
     * @param maxMessageBytes   largest accepted reassembled WebSocket message, in bytes
     * @param idleTimeoutMillis socket read timeout, which doubles as the idle timeout, in milliseconds
     * @param allowedOrigins    glob patterns a foreign {@code Origin} must match
     */
    public WebSocketConfig(int port, String bindAddress, String path, String staticRoot,
            int maxFrameBytes, int maxMessageBytes, int idleTimeoutMillis, List<String> allowedOrigins) {
        this(port, bindAddress, path, staticRoot, maxFrameBytes, maxMessageBytes, idleTimeoutMillis,
                allowedOrigins, DEFAULT_PING_INTERVAL_MILLIS);
    }

    /**
     * Returns the configuration a development server starts with.
     *
     * <p>The bind address is the wildcard address, so the bundled client may be opened from another host or
     * a container, and the origin list permits the loopback origins a local browser uses. Set the list to
     * {@code List.of()} to allow every origin; that is a development convenience and not a safe default,
     * because it lets any website reach the endpoint.
     *
     * @return the default configuration bound to port {@value #DEFAULT_PORT}
     */
    public static WebSocketConfig defaults() {
        return new WebSocketConfig(DEFAULT_PORT, "0.0.0.0", DEFAULT_PATH, DEFAULT_STATIC_ROOT,
                DEFAULT_MAX_FRAME_BYTES, DEFAULT_MAX_MESSAGE_BYTES, DEFAULT_IDLE_TIMEOUT_MILLIS,
                DEFAULT_ORIGINS, DEFAULT_PING_INTERVAL_MILLIS);
    }

    /**
     * Returns a copy of this configuration that binds a different port.
     *
     * @param port the new port, {@code 0} meaning "any free port" or "disabled" depending on the caller
     * @return a new configuration with {@code port} replaced
     */
    public WebSocketConfig withPort(int port) {
        return new WebSocketConfig(port, bindAddress, path, staticRoot, maxFrameBytes, maxMessageBytes,
                idleTimeoutMillis, allowedOrigins, pingIntervalMillis);
    }

    /**
     * Returns a copy of this configuration that binds a different interface.
     *
     * @param bindAddress the new bind address
     * @return a new configuration with {@code bindAddress} replaced
     */
    public WebSocketConfig withBindAddress(String bindAddress) {
        return new WebSocketConfig(port, bindAddress, path, staticRoot, maxFrameBytes, maxMessageBytes,
                idleTimeoutMillis, allowedOrigins, pingIntervalMillis);
    }

    /**
     * Returns a copy of this configuration that serves a different static directory.
     *
     * @param staticRoot the new static root
     * @return a new configuration with {@code staticRoot} replaced
     */
    public WebSocketConfig withStaticRoot(String staticRoot) {
        return new WebSocketConfig(port, bindAddress, path, staticRoot, maxFrameBytes, maxMessageBytes,
                idleTimeoutMillis, allowedOrigins, pingIntervalMillis);
    }

    /**
     * Returns a copy of this configuration that accepts a different frame ceiling.
     *
     * <p>Checks lower it so an oversized frame can be proven without pushing sixty-four kibibytes through a
     * loopback socket.
     *
     * @param maxFrameBytes the new frame ceiling in bytes
     * @return a new configuration with {@code maxFrameBytes} replaced
     */
    public WebSocketConfig withMaxFrameBytes(int maxFrameBytes) {
        return new WebSocketConfig(port, bindAddress, path, staticRoot, maxFrameBytes, maxMessageBytes,
                idleTimeoutMillis, allowedOrigins, pingIntervalMillis);
    }

    /**
     * Returns a copy of this configuration with a different idle timeout.
     *
     * <p>{@code OcGameServer} uses this to apply {@code --ws-idle <seconds>}; a check uses it to prove the
     * value really reaches the socket read timeout without waiting five minutes.
     *
     * @param idleTimeoutMillis the new read timeout, in milliseconds; must be positive
     * @return a new configuration with {@code idleTimeoutMillis} replaced
     */
    public WebSocketConfig withIdleTimeoutMillis(int idleTimeoutMillis) {
        return new WebSocketConfig(port, bindAddress, path, staticRoot, maxFrameBytes, maxMessageBytes,
                idleTimeoutMillis, allowedOrigins, pingIntervalMillis);
    }

    /**
     * Returns a copy of this configuration with a different server ping interval.
     *
     * <p>Checks lower it so the heartbeat arrives in a fraction of a second; production uses
     * {@value #DEFAULT_PING_INTERVAL_MILLIS} ms.
     *
     * @param pingIntervalMillis the new ping interval, in milliseconds; must be positive
     * @return a new configuration with {@code pingIntervalMillis} replaced
     */
    public WebSocketConfig withPingIntervalMillis(int pingIntervalMillis) {
        return new WebSocketConfig(port, bindAddress, path, staticRoot, maxFrameBytes, maxMessageBytes,
                idleTimeoutMillis, allowedOrigins, pingIntervalMillis);
    }

    /**
     * Returns a copy of this configuration with a different origin allowlist.
     *
     * @param allowedOrigins the new patterns, empty to allow any origin
     * @return a new configuration with {@code allowedOrigins} replaced
     */
    public WebSocketConfig withAllowedOrigins(List<String> allowedOrigins) {
        return new WebSocketConfig(port, bindAddress, path, staticRoot, maxFrameBytes, maxMessageBytes,
                idleTimeoutMillis, allowedOrigins, pingIntervalMillis);
    }

    /**
     * Returns a short human readable summary for log lines.
     *
     * @return the bind target, the WebSocket path and the limits that most often explain a refusal
     */
    public String describe() {
        return bindAddress + ":" + port + " path=" + path + " staticRoot=" + staticRoot
                + " maxFrame=" + maxFrameBytes + "B maxMessage=" + maxMessageBytes
                + "B idleTimeout=" + idleTimeoutMillis + "ms pingInterval=" + pingIntervalMillis
                + "ms origins="
                + (allowedOrigins.isEmpty() ? "any (development only)" : allowedOrigins);
    }
}
