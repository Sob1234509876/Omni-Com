package io.github.sob1234509876.oc.server.net;

/**
 * Immutable tuning knobs for the game server socket and for the framing on top of it.
 *
 * <p>The record validates in its compact constructor rather than at bind time. A misconfigured server
 * must fail at the moment the configuration object is built, because the alternative - a negative
 * backlog or a zero frame limit - only surfaces much later as a {@code SocketException} or as a
 * client that is silently dropped, and by then the cause is far away from the mistake.
 *
 * <p>{@code port} may be {@code 0}: that asks the operating system for any free port, which is what
 * every test in this module uses so that a busy machine cannot make the tests flaky.
 *
 * @param port             TCP port to bind, or {@code 0} to let the operating system choose one
 * @param bindAddress      literal address or host name to bind, e.g. {@code 0.0.0.0} or {@code 127.0.0.1}
 * @param backlog          maximum length of the operating system's accept queue
 * @param maxClients       maximum number of simultaneously connected clients
 * @param readTimeoutMillis socket read timeout per client, in milliseconds (also the idle timeout)
 * @param maxFrameBytes    largest accepted encrypted frame payload, in bytes
 * @param maxLineLength    largest accepted command line, in bytes, excluding the terminator
 */
public record ServerConfig(
        int port,
        String bindAddress,
        int backlog,
        int maxClients,
        int readTimeoutMillis,
        int maxFrameBytes,
        int maxLineLength) {

    /** Default port for the OMNI COM game protocol. */
    public static final int DEFAULT_PORT = 25566;

    /** One mebibyte, the default frame ceiling. */
    public static final int DEFAULT_MAX_FRAME_BYTES = 1024 * 1024;

    /**
     * Validates every field.
     *
     * @throws IllegalArgumentException if a value cannot produce a working server
     */
    public ServerConfig {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be 0..65535, was " + port
                    + " (0 asks the operating system for a free port)");
        }
        if (bindAddress == null || bindAddress.isBlank()) {
            throw new IllegalArgumentException("bindAddress must not be blank, was '" + bindAddress + "'");
        }
        if (backlog < 1) {
            throw new IllegalArgumentException("backlog must be positive, was " + backlog);
        }
        if (maxClients < 1) {
            throw new IllegalArgumentException("maxClients must be positive, was " + maxClients);
        }
        if (readTimeoutMillis < 1) {
            throw new IllegalArgumentException("readTimeoutMillis must be positive, was " + readTimeoutMillis
                    + " (0 would block an idle client thread forever)");
        }
        if (maxFrameBytes < 16) {
            throw new IllegalArgumentException("maxFrameBytes must be at least 16 (one AES-GCM tag), was "
                    + maxFrameBytes);
        }
        if (maxLineLength < 1) {
            throw new IllegalArgumentException("maxLineLength must be positive, was " + maxLineLength);
        }
    }

    /**
     * Returns a configuration that works out of the box on a developer machine.
     *
     * <p>The bind address is the wildcard address so a scenario can be driven from another host or a
     * container, and the thirty second read timeout doubles as an idle timeout: a client that stops
     * talking is reaped instead of pinning a thread until the process ends.
     *
     * @return the default configuration bound to port {@value #DEFAULT_PORT}
     */
    public static ServerConfig defaults() {
        return new ServerConfig(DEFAULT_PORT, "0.0.0.0", 64, 256, 30_000, DEFAULT_MAX_FRAME_BYTES, 4096);
    }

    /**
     * Returns a copy of this configuration that binds a different port.
     *
     * <p>Useful because the port is often only known after the server has bound port {@code 0} and
     * reported the port the operating system picked.
     *
     * @param port the new port, {@code 0} meaning "any free port"
     * @return a new configuration with {@code port} replaced
     */
    public ServerConfig withPort(int port) {
        return new ServerConfig(port, bindAddress, backlog, maxClients, readTimeoutMillis, maxFrameBytes,
                maxLineLength);
    }

    /**
     * Returns a copy of this configuration with a different frame ceiling.
     *
     * <p>Tests lower the ceiling to prove that an oversized frame only disconnects its own client;
     * lowering it is far cheaper than pushing one mebibyte through a loopback socket.
     *
     * @param maxFrameBytes the new frame ceiling in bytes
     * @return a new configuration with {@code maxFrameBytes} replaced
     */
    public ServerConfig withMaxFrameBytes(int maxFrameBytes) {
        return new ServerConfig(port, bindAddress, backlog, maxClients, readTimeoutMillis, maxFrameBytes,
                maxLineLength);
    }

    /**
     * Returns a short human readable summary for log lines.
     *
     * @return the bind target plus the limits that most often explain a disconnect
     */
    public String describe() {
        return bindAddress + ":" + port + " backlog=" + backlog + " maxClients=" + maxClients
                + " readTimeout=" + readTimeoutMillis + "ms maxFrame=" + maxFrameBytes
                + "B maxLine=" + maxLineLength + "B";
    }
}
