package io.github.sob1234509876.oc.server.ws;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One RFC 6455 connection: frame parsing, message assembly and close handling.
 *
 * <p>This is the only class in the module that interprets WebSocket bytes. It produces finished text
 * messages for a {@link WebSocketHandler} and never lets a handler see a fragment, a mask or a control
 * frame, which is the separation that keeps game logic out of the framing code.
 *
 * <p>The implementation follows the RFC where it matters for safety and picks the strict option wherever
 * the RFC allows one:
 *
 * <ul>
 *   <li>A client frame that is not masked is a protocol error ({@code 1002}). The RFC requires masking from
 *       clients precisely to stop a cache or proxy from replaying a frame as if the server had sent it.</li>
 *   <li>Reserved bits, unknown opcodes, fragmented control frames, oversized control frames, a continuation
 *       without a message and a new data frame inside a fragmented message all close with {@code 1002}.</li>
 *   <li>A frame larger than {@code maxFrameBytes}, or a reassembled message larger than
 *       {@code maxMessageBytes}, closes with {@code 1009} before the oversized payload is buffered.</li>
 *   <li>Text is validated with a reporting {@link java.nio.charset.CharsetDecoder} after reassembly, so a
 *       fragmented multi-byte character is accepted and an invalid one closes with {@code 1007}. Close
 *       reasons are validated the same way.</li>
 *   <li>Binary messages are not commands. The transport itself answers them with the text line
 *       {@code ERR binary frames are not commands} and keeps the connection open, because a client that
 *       sends one is confused rather than hostile.</li>
 * </ul>
 *
 * <p><strong>Idle timeout and heartbeat.</strong> The socket read timeout is set to
 * {@code idleTimeoutMillis} and doubles as the idle timeout, exactly like the TCP channel's read timeout: a
 * browser tab that disappears without a close frame costs its thread for one timeout period and then a
 * close frame with code {@code 1001}. The endpoint additionally calls {@link #ping()} every
 * {@code pingIntervalMillis} ms, and a conforming peer answers each ping with a pong. Those pongs are
 * inbound bytes, so every read resets the socket timeout and a session that is only being kept alive by the
 * server's own pings stays open; a peer that never answers still times out as before. The heartbeat timer
 * itself lives in {@link HttpWebServer} - one daemon scheduler for every connection, not one timer per
 * socket - so this class only has to write the frame safely.
 *
 * <p><strong>Lingering close.</strong> After a protocol error the server has usually not read the payload
 * that the offending frame announced. Closing a socket with unread data makes TCP send a reset, which can
 * destroy the close frame before the peer reads it. Before closing, this class therefore drains whatever is
 * already waiting for a short, bounded while. That makes the error observable to the client without letting
 * a peer keep a thread alive by sending an endless payload.
 *
 * <p>Server frames are never masked, as required. The frame writer is synchronised so a handler thread and
 * the reader thread (which answers pings) cannot interleave halves of two frames.
 */
final class WebSocketConnection implements WebSocketSession {

    private static final System.Logger LOG = System.getLogger(WebSocketConnection.class.getName());

    private static final int OPCODE_CONTINUATION = 0x0;
    private static final int OPCODE_TEXT = 0x1;
    private static final int OPCODE_BINARY = 0x2;
    private static final int OPCODE_CLOSE = 0x8;
    private static final int OPCODE_PING = 0x9;
    private static final int OPCODE_PONG = 0xA;

    /** A control frame may carry at most 125 bytes, which leaves 123 for a close reason. */
    private static final int MAX_CONTROL_PAYLOAD = 125;
    private static final int MAX_CLOSE_REASON_BYTES = MAX_CONTROL_PAYLOAD - 2;

    /** Close codes this implementation sends or recognises; the numbers are from RFC 6455 section 7.4. */
    private static final int CLOSE_GOING_AWAY = 1001;
    private static final int CLOSE_PROTOCOL_ERROR = 1002;
    private static final int CLOSE_NO_STATUS = 1005;
    private static final int CLOSE_ABNORMAL = 1006;
    private static final int CLOSE_INVALID_PAYLOAD = 1007;
    private static final int CLOSE_MESSAGE_TOO_BIG = 1009;
    private static final int CLOSE_INTERNAL_ERROR = 1011;

    /** The one line a binary frame is answered with; it is a command protocol error, not a close reason. */
    private static final String BINARY_NOT_A_COMMAND = "ERR binary frames are not commands";

    /** How long the lingering close waits for bytes that are already in flight. */
    private static final int DRAIN_SILENCE_MILLIS = 100;

    /** How many bytes the lingering close may discard, so an endless payload cannot pin a thread. */
    private static final int DRAIN_LIMIT_BYTES = 64 * 1024;

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final WebSocketHandler handler;
    private final WebSocketConfig config;
    private final String id;
    private final String remoteAddress;
    private final Object writeLock = new Object();
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final AtomicBoolean closeNotified = new AtomicBoolean();

    /** Set when this side closes first, so the handler hears the code this side chose, not {@code 1006}. */
    private volatile CloseInfo requestedClose;

    WebSocketConnection(Socket socket, WebSocketHandler handler, WebSocketConfig config, String id,
            String remoteAddress) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
        this.handler = handler;
        this.config = config;
        this.id = id;
        this.remoteAddress = remoteAddress;
    }

    /**
     * Runs the connection until it ends and notifies the handler exactly once.
     *
     * <p>Every failure is contained here: a malformed peer, an idle peer or a handler that throws costs
     * this connection and nothing else. The listener and every other browser client keep working.
     */
    void run() {
        CloseInfo outcome;
        try {
            handler.onOpen(this);
            outcome = readLoop();
        } catch (WebSocketProtocolException failure) {
            LOG.log(System.Logger.Level.WARNING, "closing browser client " + id + " (" + remoteAddress
                    + ") with " + failure.code() + ": " + failure.getMessage());
            outcome = failClosed(failure.code(), failure.getMessage());
        } catch (SocketTimeoutException idle) {
            LOG.log(System.Logger.Level.DEBUG, "browser client " + id + " was idle for "
                    + config.idleTimeoutMillis() + "ms");
            outcome = failClosed(CLOSE_GOING_AWAY, "idle for " + config.idleTimeoutMillis() + "ms");
        } catch (IOException failure) {
            // A browser that navigates away, or a peer that simply drops the socket, lands here. It is a
            // normal end of a browser connection and needs no warning.
            LOG.log(System.Logger.Level.DEBUG, "browser client " + id + " disconnected: "
                    + describe(failure));
            outcome = new CloseInfo(CLOSE_ABNORMAL, "connection ended without a close frame");
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING,
                    "browser client " + id + " failed while handling a message: " + failure);
            outcome = failClosed(CLOSE_INTERNAL_ERROR, "handler failed");
        } finally {
            open.set(false);
            closeQuietly(socket);
        }

        CloseInfo chosen = requestedClose;
        CloseInfo reported = chosen != null ? chosen : outcome;
        if (closeNotified.compareAndSet(false, true)) {
            try {
                handler.onClose(this, reported.code(), reported.reason());
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING,
                        "onClose of browser client " + id + " failed: " + failure);
            }
        }
    }

    /**
     * Reads frames and dispatches them until the peer closes or the protocol is broken.
     *
     * <p>Fragmentation is tracked with two locals - the opcode of the message being assembled and its bytes -
     * so a continuation can never be mistaken for a new message.
     */
    private CloseInfo readLoop() throws IOException {
        int fragmentedOpcode = -1;
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        while (open.get()) {
            Frame frame = readFrame();
            switch (frame.opcode()) {
                case OPCODE_CLOSE:
                    return handleCloseFrame(frame);
                case OPCODE_PING:
                    writeFrame(OPCODE_PONG, frame.payload(), true);
                    break;
                case OPCODE_PONG:
                    break;
                case OPCODE_TEXT:
                case OPCODE_BINARY:
                    if (fragmentedOpcode != -1) {
                        return fail(CLOSE_PROTOCOL_ERROR,
                                "a new data frame started before the fragmented message finished");
                    }
                    if (frame.fin()) {
                        CloseInfo failure = deliver(frame.opcode(), frame.payload());
                        if (failure != null) {
                            return failure;
                        }
                        break;
                    }
                    fragmentedOpcode = frame.opcode();
                    message.reset();
                    message.writeBytes(frame.payload());
                    if (message.size() > config.maxMessageBytes()) {
                        return fail(CLOSE_MESSAGE_TOO_BIG, "a message exceeds the configured maximum of "
                                + config.maxMessageBytes() + " bytes");
                    }
                    break;
                case OPCODE_CONTINUATION:
                    if (fragmentedOpcode == -1) {
                        return fail(CLOSE_PROTOCOL_ERROR,
                                "a continuation frame arrived without an unfinished message");
                    }
                    if ((long) message.size() + frame.payload().length > config.maxMessageBytes()) {
                        return fail(CLOSE_MESSAGE_TOO_BIG, "a message exceeds the configured maximum of "
                                + config.maxMessageBytes() + " bytes");
                    }
                    message.writeBytes(frame.payload());
                    if (frame.fin()) {
                        int opcode = fragmentedOpcode;
                        fragmentedOpcode = -1;
                        byte[] complete = message.toByteArray();
                        message.reset();
                        CloseInfo failure = deliver(opcode, complete);
                        if (failure != null) {
                            return failure;
                        }
                    }
                    break;
                default:
                    return fail(CLOSE_PROTOCOL_ERROR, "unknown opcode " + frame.opcode());
            }
        }
        return new CloseInfo(CLOSE_ABNORMAL, "the connection was closed by this side");
    }

    /**
     * Hands one complete message to the handler.
     *
     * @return {@code null} on success, or the outcome that already ended the connection
     */
    private CloseInfo deliver(int opcode, byte[] payload) throws IOException {
        if (payload.length > config.maxMessageBytes()) {
            return fail(CLOSE_MESSAGE_TOO_BIG, "a message exceeds the configured maximum of "
                    + config.maxMessageBytes() + " bytes");
        }
        if (opcode == OPCODE_BINARY) {
            // A confused client, not a hostile one: answer in the protocol's own language and stay open.
            sendText(BINARY_NOT_A_COMMAND);
            return null;
        }
        String text;
        try {
            text = decodeUtf8(payload);
        } catch (CharacterCodingException failure) {
            return fail(CLOSE_INVALID_PAYLOAD, "a text message is not valid UTF-8");
        }
        handler.onMessage(this, text);
        return null;
    }

    /** Answers a close frame: echoes its code, closes the socket and reports what the peer said. */
    private CloseInfo handleCloseFrame(Frame frame) throws IOException {
        byte[] payload = frame.payload();
        if (payload.length == 1) {
            return fail(CLOSE_PROTOCOL_ERROR, "a close frame with a one byte payload is invalid");
        }
        int code = CLOSE_NO_STATUS;
        String reason = "";
        if (payload.length >= 2) {
            code = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
            if (!isValidCloseCode(code)) {
                return fail(CLOSE_PROTOCOL_ERROR, "close code " + code + " is not valid");
            }
            try {
                reason = decodeUtf8(Arrays.copyOfRange(payload, 2, payload.length));
            } catch (CharacterCodingException failure) {
                return fail(CLOSE_INVALID_PAYLOAD, "the close reason is not valid UTF-8");
            }
            sendCloseQuietly(code, "");
        } else {
            sendEmptyCloseQuietly();
        }
        open.set(false);
        closeQuietly(socket);
        return new CloseInfo(code, reason);
    }

    /**
     * Reads and validates one frame.
     *
     * <p>The payload length is validated against the configured ceiling before the payload is allocated, so
     * the only thing a peer can achieve with a huge declared length is the loss of its own connection.
     */
    private Frame readFrame() throws IOException {
        int first = readByte();
        int second = readByte();
        if ((first & 0x70) != 0) {
            throw new WebSocketProtocolException(CLOSE_PROTOCOL_ERROR, "a reserved bit is set");
        }
        boolean fin = (first & 0x80) != 0;
        int opcode = first & 0x0F;
        if (opcode != OPCODE_CONTINUATION && opcode != OPCODE_TEXT && opcode != OPCODE_BINARY
                && opcode != OPCODE_CLOSE && opcode != OPCODE_PING && opcode != OPCODE_PONG) {
            throw new WebSocketProtocolException(CLOSE_PROTOCOL_ERROR, "unknown opcode " + opcode);
        }
        if ((second & 0x80) == 0) {
            throw new WebSocketProtocolException(CLOSE_PROTOCOL_ERROR, "a client frame must be masked");
        }
        long length = second & 0x7F;
        if (length == 126) {
            length = readUnsigned16();
        } else if (length == 127) {
            length = readUnsigned64();
        }
        if (opcode >= 0x8) {
            if (!fin) {
                throw new WebSocketProtocolException(CLOSE_PROTOCOL_ERROR,
                        "a control frame must not be fragmented");
            }
            if (length > MAX_CONTROL_PAYLOAD) {
                throw new WebSocketProtocolException(CLOSE_PROTOCOL_ERROR,
                        "a control frame payload exceeds " + MAX_CONTROL_PAYLOAD + " bytes");
            }
        }
        if (length > config.maxFrameBytes()) {
            throw new WebSocketProtocolException(CLOSE_MESSAGE_TOO_BIG, "a frame of " + length
                    + " bytes exceeds the configured maximum of " + config.maxFrameBytes() + " bytes");
        }
        if (length > Integer.MAX_VALUE) {
            throw new WebSocketProtocolException(CLOSE_MESSAGE_TOO_BIG,
                    "a frame of " + length + " bytes cannot be handled by this server");
        }
        byte[] mask = readFully(4);
        byte[] payload = readFully((int) length);
        for (int index = 0; index < payload.length; index++) {
            payload[index] ^= mask[index & 3];
        }
        return new Frame(opcode, fin, payload);
    }

    /** Sends a close frame, drains what is in flight and closes, returning the outcome for the handler. */
    private CloseInfo fail(int code, String reason) {
        sendCloseQuietly(code, reason);
        open.set(false);
        drainBeforeClose();
        closeQuietly(socket);
        return new CloseInfo(code, reason);
    }

    /** The same sequence for a failure that was thrown rather than detected inline. */
    private CloseInfo failClosed(int code, String reason) {
        return fail(code, reason);
    }

    /** Writes one close frame with a code and a reason, swallowing a socket failure. */
    private void sendCloseQuietly(int code, String reason) {
        try {
            byte[] text = truncateUtf8(reason, MAX_CLOSE_REASON_BYTES);
            byte[] payload = new byte[2 + text.length];
            payload[0] = (byte) (code >>> 8);
            payload[1] = (byte) code;
            System.arraycopy(text, 0, payload, 2, text.length);
            writeFrame(OPCODE_CLOSE, payload, true);
        } catch (IOException ignored) {
            // The socket is already failing; there is no second chance and no action for the caller.
        }
    }

    /** Writes the empty close frame that answers a close frame without a status code. */
    private void sendEmptyCloseQuietly() {
        try {
            writeFrame(OPCODE_CLOSE, new byte[0], true);
        } catch (IOException ignored) {
            // Same as above: the connection is going away regardless.
        }
    }

    /** Discards the bytes of an offending frame so the close frame is not destroyed by a TCP reset. */
    private void drainBeforeClose() {
        try {
            socket.setSoTimeout(DRAIN_SILENCE_MILLIS);
            byte[] buffer = new byte[1024];
            int drained = 0;
            while (drained < DRAIN_LIMIT_BYTES) {
                int read = in.read(buffer);
                if (read < 0) {
                    return;
                }
                drained += read;
            }
        } catch (IOException ignored) {
            // Nothing else arrived within the short window; closing is the right answer either way.
        }
    }

    /** Writes one unmasked frame under the write lock, so concurrent sends cannot interleave. */
    private void writeFrame(int opcode, byte[] payload, boolean fin) throws IOException {
        synchronized (writeLock) {
            out.write(frameHeader(opcode, payload.length, fin));
            out.write(payload);
            out.flush();
        }
    }

    /** Builds the two to ten byte server frame header; no mask bit is ever set here. */
    private static byte[] frameHeader(int opcode, int length, boolean fin) {
        ByteArrayOutputStream header = new ByteArrayOutputStream(10);
        header.write((fin ? 0x80 : 0) | opcode);
        if (length <= 125) {
            header.write(length);
        } else if (length <= 0xFFFF) {
            header.write(126);
            header.write(length >>> 8);
            header.write(length);
        } else {
            header.write(127);
            for (int shift = 56; shift >= 0; shift -= 8) {
                header.write((int) ((long) length >>> shift) & 0xFF);
            }
        }
        return header.toByteArray();
    }

    /** Reads one byte or reports the peer's end of stream. */
    private int readByte() throws IOException {
        int value = in.read();
        if (value < 0) {
            throw new EOFException("the peer closed the connection");
        }
        return value;
    }

    /** Reads exactly {@code length} bytes, or reports how far it got. */
    private byte[] readFully(int length) throws IOException {
        byte[] buffer = new byte[length];
        int filled = 0;
        while (filled < length) {
            int read = in.read(buffer, filled, length - filled);
            if (read < 0) {
                throw new EOFException("the peer closed after " + filled + " of " + length + " bytes");
            }
            filled += read;
        }
        return buffer;
    }

    /** Reads a 16 bit big-endian payload length. */
    private long readUnsigned16() throws IOException {
        return ((long) readByte() << 8) | readByte();
    }

    /** Reads a 64 bit big-endian payload length, refusing the negative range. */
    private long readUnsigned64() throws IOException {
        long value = 0;
        for (int index = 0; index < 8; index++) {
            value = (value << 8) | readByte();
        }
        if (value < 0) {
            throw new WebSocketProtocolException(CLOSE_PROTOCOL_ERROR,
                    "the 64 bit payload length has its high bit set");
        }
        return value;
    }

    /** Decodes UTF-8 strictly, so malformed or unmappable input is an error rather than a replacement. */
    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    /** Truncates a close reason to what a control frame can carry, without splitting a character. */
    private static byte[] truncateUtf8(String text, int maxBytes) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return bytes;
        }
        int end = maxBytes;
        while (end > 0 && (bytes[end] & 0xC0) == 0x80) {
            end--;
        }
        return Arrays.copyOf(bytes, end);
    }

    /** Whether a close code may appear on the wire. 1005 and 1006 are local sentinels, not codes. */
    private static boolean isValidCloseCode(int code) {
        if (code >= 3000 && code <= 4999) {
            return true;
        }
        return code >= 1000 && code <= 1014 && code != 1004 && code != 1005 && code != 1006;
    }

    /** Returns a short, non-null description of an I/O failure for a debug log line. */
    private static String describe(IOException failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Closing an already failed socket cannot fail in a way the caller could act on.
        }
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String remoteAddress() {
        return remoteAddress;
    }

    /**
     * Sends one empty server initiated ping frame, from the endpoint's heartbeat scheduler.
     *
     * <p>The write goes through the same lock as every other server frame, so a ping can never interleave
     * with half a command answer. A write failure means the peer is gone: the connection is marked closed
     * and its socket is closed, which also unblocks the reader thread. A missing pong is not an error here;
     * the ordinary socket read timeout still decides when a silent peer is closed.
     */
    void ping() {
        if (!open.get()) {
            return;
        }
        try {
            writeFrame(OPCODE_PING, new byte[0], true);
        } catch (IOException failure) {
            open.set(false);
            closeQuietly(socket);
        }
    }

    @Override
    public void sendText(String text) {
        Objects.requireNonNull(text, "text");
        if (!open.get()) {
            throw new IllegalStateException("browser client " + id + " is closed");
        }
        try {
            writeFrame(OPCODE_TEXT, text.getBytes(StandardCharsets.UTF_8), true);
        } catch (IOException failure) {
            open.set(false);
            closeQuietly(socket);
            throw new UncheckedIOException("failed to send a text frame to browser client " + id,
                    failure);
        }
    }

    @Override
    public void close(int code, String reason) {
        Objects.requireNonNull(reason, "reason");
        if (!open.compareAndSet(true, false)) {
            return;
        }
        requestedClose = new CloseInfo(code, reason);
        sendCloseQuietly(code, reason);
        closeQuietly(socket);
    }

    @Override
    public boolean open() {
        return open.get();
    }

    /** One parsed frame, after unmasking. */
    private record Frame(int opcode, boolean fin, byte[] payload) {
    }

    /** What the handler is told when the connection ends. */
    private record CloseInfo(int code, String reason) {
    }

    /**
     * A protocol violation that already knows which close code it deserves.
     *
     * <p>Throwing it aborts the read loop; {@link #run()} turns it into a close frame and one log line, so
     * every violation has exactly one place that writes to the wire.
     */
    private static final class WebSocketProtocolException extends IOException {

        private final int code;

        WebSocketProtocolException(int code, String reason) {
            super(reason);
            this.code = code;
        }

        int code() {
            return code;
        }
    }
}
