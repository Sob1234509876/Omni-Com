package io.github.sob1234509876.oc.server.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Objects;

/**
 * A minimal blocking client for the game protocol.
 *
 * <p>Its whole reason to exist is to be boring: open a socket, finish the encrypted handshake, and
 * expose exactly two operations - send one line and receive one line - so a scenario reads like the
 * conversation a player would have. Anything richer (reconnection, pipelining, a receive queue) belongs
 * in the client that ships with the game, not in a test helper.
 *
 * <p>One instance is meant to be used by one thread at a time, which matches how the scenarios drive it.
 * The socket has a connect timeout and a read timeout so that a broken build fails a test instead of
 * hanging the build.
 */
public final class GameClient implements AutoCloseable {

    /** Time allowed for the TCP connection to be established. */
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;

    /** Time allowed for one response line before the client gives up. */
    private static final int READ_TIMEOUT_MILLIS = 5_000;

    private final Socket socket;
    private final SecureChannel channel;
    private boolean closed;

    private GameClient(Socket socket, SecureChannel channel) {
        this.socket = socket;
        this.channel = channel;
    }

    /**
     * Connects to a server and completes the encrypted handshake.
     *
     * <p>The handshake runs here, not lazily on the first request, so a caller that cannot reach the
     * server or cannot agree on a protocol version learns that from {@code connect} rather than from a
     * confusing read error much later.
     *
     * @param host the server host
     * @param port the server port
     * @return a connected, encrypted client
     * @throws IOException if the connection or the handshake fails, with a message naming the step
     */
    public static GameClient connect(String host, int port) throws IOException {
        Objects.requireNonNull(host, "host");
        if (port < 1 || port > 65535) {
            throw new IOException("connect failed: port " + port + " is not in the range 1..65535");
        }
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);
            socket.setTcpNoDelay(true);
            SecureChannel channel = SecureChannel.clientSide(socket.getInputStream(),
                    socket.getOutputStream());
            return new GameClient(socket, channel);
        } catch (IOException failure) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // The connect already failed; a close error would only hide the real cause.
            }
            throw new IOException("connect failed for " + host + ":" + port + ": " + failure.getMessage(),
                    failure);
        }
    }

    /**
     * Sends one line and waits for the single line the server answers with.
     *
     * @param line the command, without a terminator
     * @return the response line, where an empty string means the server sent an empty line
     * @throws IOException if the line cannot be sent or the response cannot be read, for example because
     *                     the server disconnected this client
     */
    public String request(String line) throws IOException {
        Objects.requireNonNull(line, "line");
        synchronized (this) {
            if (closed) {
                throw new IOException("request failed: this client is already closed");
            }
            channel.writeLine(line);
            return channel.readLine();
        }
    }

    /**
     * Returns the encrypted channel, for scenarios that need frames or the negotiated protocol name.
     *
     * @return the channel, still owned by this client
     */
    public SecureChannel channel() {
        return channel;
    }

    /**
     * Returns whether this client is still open.
     *
     * @return {@code true} until {@link #close()} is called
     */
    public boolean isOpen() {
        synchronized (this) {
            return !closed;
        }
    }

    /** Closes the channel and the socket. Calling this twice is harmless. */
    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }
        channel.close();
        try {
            socket.close();
        } catch (IOException ignored) {
            // The channel is already closed; nothing can be recovered from a failed socket close here.
        }
    }
}
