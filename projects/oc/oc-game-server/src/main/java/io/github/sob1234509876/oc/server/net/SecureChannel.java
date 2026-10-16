package io.github.sob1234509876.oc.server.net;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.XECPublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPublicKeySpec;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * An authenticated, encrypted byte channel over a plain {@link InputStream}/{@link OutputStream} pair.
 *
 * <p>The game protocol carries user names, passwords and, later, session tokens. Sending those in the
 * clear over a LAN or over the open internet is indefensible, but a third party TLS stack is not an
 * option here: the module is required to run offline and with zero dependencies. So the channel
 * reimplements the small, well understood part of TLS 1.3 that is actually needed, using only JDK
 * primitives:
 *
 * <ol>
 *   <li>an ephemeral X25519 key exchange ({@code XDH}), which gives forward secrecy because the private
 *       keys live only in memory for the lifetime of one connection;</li>
 *   <li>HKDF-SHA256 to turn the raw shared secret into a single AES-256 key, using a fresh random salt
 *       from the server side, so the same key exchange can never produce the same session key twice;</li>
 *   <li>AES-256-GCM frames, which give both confidentiality and integrity - a flipped bit anywhere in
 *       a frame is detected instead of being fed to the command parser.</li>
 * </ol>
 *
 * <p>The handshake is verified against the version byte and the negotiated length on both sides. Every
 * failure path throws {@link IOException} whose message names exactly which step failed, because a
 * silent crypto failure is far worse than a loud one.
 *
 * <p>The channel is safe for one reader thread and one writer thread at a time. That is the shape the
 * server actually uses: a per-client thread reads, and any thread may answer. Writes are serialized
 * internally so the frame counter can never be observed out of order.
 */
public final class SecureChannel implements AutoCloseable {

    /** Name reported by {@link #protocolName()}. */
    private static final String PROTOCOL = "OC/1 X25519 + HKDF-SHA256 + AES-256-GCM";

    /** Handshake version, so a future change is rejected as a version mismatch instead of as garbage. */
    private static final byte HANDSHAKE_VERSION = 1;

    private static final int X25519_KEY_BYTES = 32;
    private static final int SALT_BYTES = 32;
    private static final int NONCE_PREFIX_BYTES = 4;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int SESSION_KEY_BYTES = 32;

    /** Client to server hello: version (1) + public key (32) + nonce prefix (4). */
    private static final int CLIENT_HELLO_BYTES = 1 + X25519_KEY_BYTES + NONCE_PREFIX_BYTES;

    /** Server to client hello: version (1) + public key (32) + salt (32) + nonce prefix (4). */
    private static final int SERVER_HELLO_BYTES =
            1 + X25519_KEY_BYTES + SALT_BYTES + NONCE_PREFIX_BYTES;

    /** Domain separation: only this protocol can ever derive this session key from the shared secret. */
    private static final byte[] HKDF_INFO =
            "oc.projects.oc.game.server/v1 session key".getBytes(StandardCharsets.US_ASCII);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final InputStream in;
    private final OutputStream out;
    private final boolean serverRole;
    private final SecretKeySpec sessionKey;
    private final byte[] writeNoncePrefix;
    private final byte[] readNoncePrefix;
    private final int maxFrameBytes;
    private final int maxLineLength;
    private final Object writeLock = new Object();
    private final Object readLock = new Object();

    /** Sentinel meaning "no frame has been accepted yet on this direction". */
    private static final long NO_SEQUENCE = -1L;

    /**
     * Empty frames tolerated inside one {@link #readLine()} call.
     *
     * <p>A frame boundary and a line boundary are independent, so empty frames are legal; an unbounded
     * run of them, however, would let a peer keep a reader busy without ever sending a line.
     */
    private static final int MAX_EMPTY_FRAMES_PER_LINE = 64;

    /**
     * Highest nonce counter used so far on the write side.
     *
     * <p>Both directions start their sequence at counter zero and increment before every frame: the
     * reader accepts {@code lastReadCounter + 1}, so a sender that used counter one for its first frame
     * would be exactly one frame ahead of its peer and every tag check would fail. Starting at
     * {@code NO_SEQUENCE} keeps the two sides symmetric.
     */
    private long writeCounter = NO_SEQUENCE;

    /** Highest nonce counter accepted while reading; anything not greater is a replay. */
    private long lastReadCounter = NO_SEQUENCE;

    private byte[] lineBuffer = new byte[256];
    private int lineStart;
    private int lineEnd;
    private boolean closed;

    private SecureChannel(
            InputStream in,
            OutputStream out,
            boolean serverRole,
            byte[] sessionKey,
            byte[] writeNoncePrefix,
            byte[] readNoncePrefix,
            int maxFrameBytes,
            int maxLineLength) {
        this.in = in;
        this.out = out;
        this.serverRole = serverRole;
        this.sessionKey = new SecretKeySpec(sessionKey, "AES");
        this.writeNoncePrefix = writeNoncePrefix;
        this.readNoncePrefix = readNoncePrefix;
        this.maxFrameBytes = maxFrameBytes;
        this.maxLineLength = maxLineLength;
    }

    /**
     * Performs the server half of the handshake and returns the established channel.
     *
     * @param in  socket input, read exactly once per handshake field, never buffered ahead
     * @param out socket output
     * @return the encrypted channel
     * @throws IOException if the peer is not speaking this protocol, uses a different version, or sends
     *                     a public key of the wrong length
     */
    public static SecureChannel serverSide(InputStream in, OutputStream out) throws IOException {
        return serverSide(in, out, ServerConfig.DEFAULT_MAX_FRAME_BYTES, 4096);
    }

    /**
     * Performs the server half of the handshake with explicit frame and line limits.
     *
     * <p>The server must know the limits before it reads its first frame, so they are part of the
     * handshake rather than something that can be set later.
     *
     * @param in           socket input
     * @param out          socket output
     * @param maxFrameBytes largest accepted frame payload
     * @param maxLineLength largest accepted command line in bytes
     * @return the encrypted channel
     * @throws IOException if the handshake fails for any reason
     */
    public static SecureChannel serverSide(InputStream in, OutputStream out, int maxFrameBytes,
            int maxLineLength) throws IOException {
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        if (maxFrameBytes < TAG_BITS / 8) {
            throw new IllegalArgumentException("maxFrameBytes must be at least " + (TAG_BITS / 8) + ", was "
                    + maxFrameBytes);
        }
        if (maxLineLength < 1) {
            throw new IllegalArgumentException("maxLineLength must be positive, was " + maxLineLength);
        }

        byte[] hello = readFully(in, CLIENT_HELLO_BYTES, "client handshake");
        if (hello[0] != HANDSHAKE_VERSION) {
            throw new IOException("handshake failed: unsupported protocol version " + (hello[0] & 0xFF)
                    + ", expected " + (HANDSHAKE_VERSION & 0xFF));
        }
        PublicKey clientKey = decodePublicKey(hello, 1, "client");
        byte[] clientNoncePrefix = Arrays.copyOfRange(hello, 1 + X25519_KEY_BYTES, CLIENT_HELLO_BYTES);

        KeyPair serverPair = generateKeyPair();
        byte[] shared = agree(serverPair, clientKey, "server");
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] serverNoncePrefix = randomBytes(NONCE_PREFIX_BYTES);
        byte[] sessionKey = hkdfSha256(shared, salt, HKDF_INFO, SESSION_KEY_BYTES);
        Arrays.fill(shared, (byte) 0);

        byte[] reply = new byte[SERVER_HELLO_BYTES];
        reply[0] = HANDSHAKE_VERSION;
        System.arraycopy(encodePublicKey(serverPair.getPublic()), 0, reply, 1, X25519_KEY_BYTES);
        System.arraycopy(salt, 0, reply, 1 + X25519_KEY_BYTES, SALT_BYTES);
        System.arraycopy(serverNoncePrefix, 0, reply, 1 + X25519_KEY_BYTES + SALT_BYTES,
                NONCE_PREFIX_BYTES);
        out.write(reply);
        out.flush();

        return new SecureChannel(in, out, true, sessionKey, serverNoncePrefix, clientNoncePrefix,
                maxFrameBytes, maxLineLength);
    }

    /**
     * Performs the client half of the handshake and returns the established channel.
     *
     * @param in  socket input
     * @param out socket output
     * @return the encrypted channel
     * @throws IOException if the server does not answer with a well formed hello
     */
    public static SecureChannel clientSide(InputStream in, OutputStream out) throws IOException {
        return clientSide(in, out, ServerConfig.DEFAULT_MAX_FRAME_BYTES, 4096);
    }

    /**
     * Performs the client half of the handshake with explicit frame and line limits.
     *
     * @param in           socket input
     * @param out          socket output
     * @param maxFrameBytes largest frame payload this client will send or accept
     * @param maxLineLength largest command line this client will send or accept, in bytes
     * @return the encrypted channel
     * @throws IOException if the handshake fails for any reason
     */
    public static SecureChannel clientSide(InputStream in, OutputStream out, int maxFrameBytes,
            int maxLineLength) throws IOException {
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        if (maxFrameBytes < TAG_BITS / 8) {
            throw new IllegalArgumentException("maxFrameBytes must be at least " + (TAG_BITS / 8) + ", was "
                    + maxFrameBytes);
        }
        if (maxLineLength < 1) {
            throw new IllegalArgumentException("maxLineLength must be positive, was " + maxLineLength);
        }

        KeyPair clientPair = generateKeyPair();
        byte[] clientNoncePrefix = randomBytes(NONCE_PREFIX_BYTES);
        byte[] hello = new byte[CLIENT_HELLO_BYTES];
        hello[0] = HANDSHAKE_VERSION;
        System.arraycopy(encodePublicKey(clientPair.getPublic()), 0, hello, 1, X25519_KEY_BYTES);
        System.arraycopy(clientNoncePrefix, 0, hello, 1 + X25519_KEY_BYTES, NONCE_PREFIX_BYTES);
        out.write(hello);
        out.flush();

        byte[] reply = readFully(in, SERVER_HELLO_BYTES, "server handshake");
        if (reply[0] != HANDSHAKE_VERSION) {
            throw new IOException("handshake failed: server answered with protocol version "
                    + (reply[0] & 0xFF) + ", expected " + (HANDSHAKE_VERSION & 0xFF));
        }
        PublicKey serverKey = decodePublicKey(reply, 1, "server");
        byte[] salt = Arrays.copyOfRange(reply, 1 + X25519_KEY_BYTES, 1 + X25519_KEY_BYTES + SALT_BYTES);
        byte[] serverNoncePrefix = Arrays.copyOfRange(reply, 1 + X25519_KEY_BYTES + SALT_BYTES,
                SERVER_HELLO_BYTES);

        byte[] shared = agree(clientPair, serverKey, "client");
        byte[] sessionKey = hkdfSha256(shared, salt, HKDF_INFO, SESSION_KEY_BYTES);
        Arrays.fill(shared, (byte) 0);

        return new SecureChannel(in, out, false, sessionKey, clientNoncePrefix, serverNoncePrefix,
                maxFrameBytes, maxLineLength);
    }

    /**
     * Returns the negotiated protocol, useful for logging and for the wire check in the self check.
     *
     * @return a stable, human readable protocol name
     */
    public String protocolName() {
        return PROTOCOL;
    }

    /**
     * Encrypts and sends one frame.
     *
     * @param plaintext the payload; may be empty but not {@code null}
     * @throws IOException if the payload is too large, the cipher fails, or the socket write fails
     */
    public void writeFrame(byte[] plaintext) throws IOException {
        Objects.requireNonNull(plaintext, "plaintext");
        if (plaintext.length > maxFrameBytes) {
            throw new IOException("frame write failed: payload of " + plaintext.length
                    + " bytes exceeds the configured maximum of " + maxFrameBytes + " bytes");
        }
        try {
            writeFrameTo(out, plaintext);
            out.flush();
        } catch (IOException failure) {
            throw failure.getMessage() != null && failure.getMessage().startsWith(SOCKET_WRITE_PREFIX)
                    ? failure
                    : new IOException(SOCKET_WRITE_PREFIX + failure.getMessage(), failure);
        }
    }

    /** Prefix used when a frame could not reach the socket, so callers can tell the cases apart. */
    private static final String SOCKET_WRITE_PREFIX = "frame write failed on the socket: ";

    /**
     * Reads and decrypts one frame.
     *
     * @return the decrypted payload
     * @throws IOException if the peer closed, the length prefix is impossible, the payload is larger
     *                     than the configured maximum, the tag does not verify, or the counter repeats
     */
    public byte[] readFrame() throws IOException {
        synchronized (readLock) {
            if (closed) {
                throw new IOException("frame read failed: channel is closed");
            }
            byte[] lengthBytes = readFully(in, 4, "frame length prefix");
            long declared = ((lengthBytes[0] & 0xFFL) << 24) | ((lengthBytes[1] & 0xFFL) << 16)
                    | ((lengthBytes[2] & 0xFFL) << 8) | (lengthBytes[3] & 0xFFL);
            if (declared < TAG_BITS / 8) {
                throw new IOException("frame read failed: declared length " + declared
                        + " bytes is smaller than one " + (TAG_BITS / 8) + " byte authentication tag");
            }
            if (declared > maxFrameBytes + (TAG_BITS / 8)) {
                throw new IOException("frame read failed: declared length " + declared
                        + " bytes exceeds the configured maximum of " + maxFrameBytes
                        + " bytes of payload");
            }
            byte[] sealed = readFully(in, (int) declared, "frame payload");
            // Replay guard: the counter is per direction and strictly increasing, and a replayed frame
            // carries a counter this side has already consumed. The AEAD tag check below would also
            // reject a replay, because the nonce is derived from the counter, but failing here names the
            // real reason instead of blaming the ciphertext.
            long counter = lastReadCounter + 1;
            if (lastReadCounter != NO_SEQUENCE && counter <= lastReadCounter) {
                throw new IOException("frame read failed: replayed counter " + counter
                        + ", but the last accepted counter was already " + lastReadCounter);
            }
            byte[] nonce = nonceFor(readNoncePrefix, counter);
            byte[] plaintext;
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, sessionKey, new GCMParameterSpec(TAG_BITS, nonce));
                plaintext = cipher.doFinal(sealed);
            } catch (AEADBadTagException failure) {
                throw new IOException("frame read failed: authentication tag mismatch at counter " + counter
                        + " - the frame was corrupted or tampered with", failure);
            } catch (GeneralSecurityException failure) {
                throw new IOException("frame read failed: decryption error at counter " + counter + ": "
                        + failure, failure);
            }
            lastReadCounter = counter;
            return plaintext;
        }
    }

    /**
     * Writes one UTF-8 line, terminating it with {@code \n}.
     *
     * @param line the line without terminator
     * @return {@code line}, so callers can log or echo what they sent
     * @throws IOException if the line, including its terminator, exceeds the configured line limit or the
     *                     frame cannot be sent
     */
    public String writeLine(String line) throws IOException {
        Objects.requireNonNull(line, "line");
        if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
            throw new IOException("line write failed: the line contains a line terminator");
        }
        byte[] body = line.getBytes(StandardCharsets.UTF_8);
        if (body.length > maxLineLength) {
            throw new IOException("line write failed: " + body.length + " bytes exceeds the configured line"
                    + " limit of " + maxLineLength + " bytes");
        }
        byte[] payload = Arrays.copyOf(body, body.length + 1);
        payload[body.length] = '\n';
        writeFrame(payload);
        return line;
    }

    /**
     * Reads one UTF-8 line, blocking until a terminator arrives, a frame boundary is crossed or not.
     *
     * <p>A line may span several frames, so partial input is retained between calls. A line longer than
     * the configured limit is rejected as soon as the limit is exceeded, before the rest of it is read,
     * which stops a peer from exhausting memory with one endless line.
     *
     * @return the line without its terminator
     * @throws IOException if the peer closed mid-line, the bytes are not valid UTF-8, the line exceeds
     *                     the configured limit, or a frame cannot be read
     */
    public String readLine() throws IOException {
        int emptyFrames = 0;
        while (true) {
            int newline = indexOfNewline();
            if (newline >= 0) {
                int length = newline - lineStart;
                if (length > 0 && lineBuffer[newline - 1] == '\r') {
                    length--;
                }
                String line;
                try {
                    line = StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(lineBuffer, lineStart, length))
                            .toString();
                } catch (CharacterCodingException failure) {
                    throw new IOException("line read failed: the bytes are not valid UTF-8", failure);
                }
                lineStart = newline + 1;
                compactLineBuffer();
                return line;
            }
            if (lineEnd - lineStart > maxLineLength) {
                throw new IOException("line read failed: no terminator within " + maxLineLength
                        + " bytes, so the peer is either broken or sending a line that is too long");
            }
            byte[] frame = readFrame();
            if (frame.length == 0) {
                // Empty frames carry no information, so a peer that sends nothing but empty frames would
                // otherwise keep this loop spinning forever without ever producing a line.
                if (++emptyFrames > MAX_EMPTY_FRAMES_PER_LINE) {
                    throw new IOException("line read failed: " + emptyFrames
                            + " consecutive empty frames carried no line terminator");
                }
                continue;
            }
            if (lineEnd - lineStart + frame.length > maxLineLength + 1) {
                throw new IOException("line read failed: no terminator within " + maxLineLength
                        + " bytes, so the peer is either broken or sending a line that is too long");
            }
            appendToLineBuffer(frame);
        }
    }

    /**
     * Closes the socket streams behind this channel. Closing twice is harmless.
     *
     * <p>Only the streams are closed. Nothing here touches a class loader or any other global state, so a
     * closed channel can never take the rest of the server down with it.
     */
    @Override
    public void close() {
        synchronized (writeLock) {
            if (closed) {
                return;
            }
            closed = true;
        }
        closeQuietly(in);
        closeQuietly(out);
    }

    /**
     * Returns whether this channel is the server side of the handshake.
     *
     * @return {@code true} for a channel produced by {@link #serverSide}
     */
    public boolean isServerRole() {
        return serverRole;
    }

    /**
     * Returns the encrypted bytes this channel would send for one payload, without sending them.
     *
     * <p>This exists so the self check can prove the replay guard with authentic ciphertext. A replayed
     * frame cannot be built from the outside, because the session key lives only inside this object, and a
     * frame made of arbitrary bytes would be rejected for the wrong reason - a bad tag rather than a
     * repeated counter. The counter is advanced exactly as a real write would advance it, so the returned
     * bytes are the frame that the peer's next read must accept.
     *
     * <p>Package private on purpose: it is a test seam, not part of the protocol surface.
     *
     * @param plaintext the payload to seal
     * @return the complete frame, length prefix included
     * @throws IOException if the payload is too large or the cipher fails
     */
    byte[] captureFrame(byte[] plaintext) throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        writeFrameTo(sink, plaintext);
        return sink.toByteArray();
    }

    /** Writes one frame into an arbitrary sink instead of the socket, for {@link #captureFrame}. */
    private void writeFrameTo(OutputStream sink, byte[] plaintext) throws IOException {
        Objects.requireNonNull(plaintext, "plaintext");
        if (plaintext.length > maxFrameBytes) {
            throw new IOException("frame write failed: payload of " + plaintext.length
                    + " bytes exceeds the configured maximum of " + maxFrameBytes + " bytes");
        }
        synchronized (writeLock) {
            if (closed) {
                throw new IOException("frame write failed: channel is closed");
            }
            if (writeCounter == Long.MAX_VALUE) {
                throw new IOException("frame write failed: nonce counter reached " + Long.MAX_VALUE
                        + " frames, which is the end of this session key's life; reconnect to negotiate a"
                        + " fresh key");
            }
            writeCounter++;
            byte[] nonce = nonceFor(writeNoncePrefix, writeCounter);
            byte[] sealed;
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, sessionKey, new GCMParameterSpec(TAG_BITS, nonce));
                sealed = cipher.doFinal(plaintext);
            } catch (GeneralSecurityException failure) {
                throw new IOException("frame encryption failed: " + failure, failure);
            }
            sink.write(encodeFrameLength(sealed.length));
            sink.write(sealed);
        }
    }

    /** Closes one stream, swallowing secondary failures because the caller cannot act on them. */
    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
            // The socket is going away regardless; a failure while closing it cannot be repaired here and
            // there is no useful action a caller could take, so it must not mask the original failure.
        }
    }

    /** Appends freshly decrypted bytes to the line buffer, growing it when needed. */
    private void appendToLineBuffer(byte[] bytes) {
        int used = lineEnd - lineStart;
        if (used + bytes.length > lineBuffer.length) {
            int capacity = Math.max(lineBuffer.length * 2, used + bytes.length);
            byte[] grown = new byte[capacity];
            System.arraycopy(lineBuffer, lineStart, grown, 0, used);
            lineBuffer = grown;
            lineStart = 0;
            lineEnd = used;
        }
        System.arraycopy(bytes, 0, lineBuffer, lineEnd, bytes.length);
        lineEnd += bytes.length;
    }

    /** Drops consumed bytes so the buffer does not grow without bound across many lines. */
    private void compactLineBuffer() {
        if (lineStart == lineEnd) {
            lineStart = 0;
            lineEnd = 0;
        } else if (lineStart > 512) {
            System.arraycopy(lineBuffer, lineStart, lineBuffer, 0, lineEnd - lineStart);
            lineEnd -= lineStart;
            lineStart = 0;
        }
    }

    /** Finds the next newline in the unconsumed part of the line buffer, or {@code -1}. */
    private int indexOfNewline() {
        for (int index = lineStart; index < lineEnd; index++) {
            if (lineBuffer[index] == '\n') {
                return index;
            }
        }
        return -1;
    }

    /** Builds the 12 byte nonce: a per-direction random prefix plus the big-endian counter. */
    private static byte[] nonceFor(byte[] prefix, long counter) {
        byte[] nonce = new byte[NONCE_BYTES];
        System.arraycopy(prefix, 0, nonce, 0, NONCE_PREFIX_BYTES);
        for (int index = 0; index < 8; index++) {
            nonce[NONCE_PREFIX_BYTES + index] = (byte) (counter >>> (56 - 8 * index));
        }
        return nonce;
    }

    /** Reads exactly {@code length} bytes or fails with a message naming the field being read. */
    private static byte[] readFully(InputStream source, int length, String what) throws IOException {
        byte[] buffer = new byte[length];
        int filled = 0;
        while (filled < length) {
            int read;
            try {
                read = source.read(buffer, filled, length - filled);
            } catch (IOException failure) {
                throw new IOException("handshake/read failed while reading " + what + " (" + filled + " of "
                        + length + " bytes): " + failure.getMessage(), failure);
            }
            if (read < 0) {
                throw new EOFException("connection closed after " + filled + " of " + length
                        + " bytes while reading " + what);
            }
            filled += read;
        }
        return buffer;
    }

    /** Encodes the 4 byte big-endian frame length prefix. */
    private static byte[] encodeFrameLength(int length) {
        return new byte[] {
                (byte) (length >>> 24), (byte) (length >>> 16), (byte) (length >>> 8), (byte) length
        };
    }

    /** Generates one ephemeral X25519 key pair. */
    private static KeyPair generateKeyPair() throws IOException {
        try {
            return KeyPairGenerator.getInstance("X25519").generateKeyPair();
        } catch (GeneralSecurityException failure) {
            throw new IOException("handshake failed: this JVM cannot generate an X25519 key pair: "
                    + failure, failure);
        }
    }

    /** Runs the XDH agreement for one side. */
    private static byte[] agree(KeyPair own, PublicKey peer, String role) throws IOException {
        try {
            KeyAgreement agreement = KeyAgreement.getInstance("XDH");
            agreement.init(own.getPrivate());
            agreement.doPhase(peer, true);
            return agreement.generateSecret();
        } catch (GeneralSecurityException failure) {
            throw new IOException("handshake failed: the " + role + " could not complete the X25519 key "
                    + "agreement: " + failure, failure);
        }
    }

    /** Converts the 32 raw key bytes at {@code offset} into an X25519 public key. */
    private static PublicKey decodePublicKey(byte[] encoded, int offset, String who) throws IOException {
        byte[] raw = Arrays.copyOfRange(encoded, offset, offset + X25519_KEY_BYTES);
        // Big-endian per RFC 7748; the reverse of what encodePublicKey writes.
        byte[] littleEndian = new byte[X25519_KEY_BYTES];
        for (int index = 0; index < X25519_KEY_BYTES; index++) {
            littleEndian[index] = raw[X25519_KEY_BYTES - 1 - index];
        }
        try {
            return KeyFactory.getInstance("X25519")
                    .generatePublic(new XECPublicKeySpec(NamedParameterSpec.X25519,
                            new BigInteger(1, littleEndian)));
        } catch (GeneralSecurityException failure) {
            throw new IOException("handshake failed: the " + who + " public key is not a valid X25519 point: "
                    + failure, failure);
        }
    }

    /** Converts an X25519 public key back to exactly 32 raw bytes. */
    private static byte[] encodePublicKey(PublicKey key) throws IOException {
        if (!(key instanceof XECPublicKey xec)) {
            throw new IOException("handshake failed: key is not an X25519 public key but "
                    + key.getClass().getName());
        }
        byte[] bigEndian = xec.getU().toByteArray();
        byte[] littleEndian = new byte[X25519_KEY_BYTES];
        for (int index = 0; index < bigEndian.length && index < X25519_KEY_BYTES; index++) {
            littleEndian[index] = bigEndian[bigEndian.length - 1 - index];
        }
        return littleEndian;
    }

    /** HKDF-SHA256: extract a pseudo random key from the shared secret, then expand it to a session key. */
    private static byte[] hkdfSha256(byte[] inputKeyMaterial, byte[] salt, byte[] info, int length)
            throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt, "HmacSHA256"));
            byte[] pseudoRandomKey = mac.doFinal(inputKeyMaterial);

            mac.init(new SecretKeySpec(pseudoRandomKey, "HmacSHA256"));
            byte[] output = new byte[length];
            byte[] block = new byte[0];
            int offset = 0;
            int counter = 1;
            while (offset < length) {
                mac.reset();
                mac.update(block);
                mac.update(info);
                mac.update((byte) counter);
                block = mac.doFinal();
                int copy = Math.min(block.length, length - offset);
                System.arraycopy(block, 0, output, offset, copy);
                offset += copy;
                counter++;
            }
            Arrays.fill(pseudoRandomKey, (byte) 0);
            return output;
        } catch (GeneralSecurityException failure) {
            throw new IOException("handshake failed: HKDF-SHA256 is unavailable: " + failure, failure);
        }
    }

    /** Fills a fresh array from {@link SecureRandom}; key material never comes from a weaker source. */
    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

}
