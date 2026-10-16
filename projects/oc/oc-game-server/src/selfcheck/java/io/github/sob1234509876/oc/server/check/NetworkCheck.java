package io.github.sob1234509876.oc.server.check;

import io.github.sob1234509876.oc.server.auth.AuthResult;
import io.github.sob1234509876.oc.server.auth.AuthService;
import io.github.sob1234509876.oc.server.auth.LocalUserStore;
import io.github.sob1234509876.oc.server.auth.PasswordHasher;
import io.github.sob1234509876.oc.server.net.ChannelTestSupport;
import io.github.sob1234509876.oc.server.net.GameClient;
import io.github.sob1234509876.oc.server.net.GameServer;
import io.github.sob1234509876.oc.server.net.SecureChannel;
import io.github.sob1234509876.oc.server.net.ServerConfig;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Self check for the networking package: it drives a real {@link GameServer} over a real loopback socket.
 *
 * <p>Nothing here is mocked. The whole point of this check is to prove the parts that only a real socket
 * can prove - that the port is actually bound, that two threads can talk at once, that a broken client is
 * dropped without taking the server with it, and, most importantly, that the bytes on the wire are
 * ciphertext rather than the command text.
 */
public final class NetworkCheck {

    /** Handshake bytes the client writes; the tap excludes them so it sees transport frames only. */
    private static final int CLIENT_HANDSHAKE_BYTES = 1 + 32 + 4;

    private NetworkCheck() {
    }

    /**
     * Runs every networking check.
     *
     * @param check the assertion sink, which reports the summary and the exit code
     */
    public static void run(Check check) {
        Path store = null;
        try {
            store = Files.createTempFile("oc-game-server-users", ".tsv");
            Files.writeString(store, "", StandardCharsets.UTF_8);
            check.note("scratch user store: " + store);
            runAll(check, store);
        } catch (IOException failure) {
            check.that(false, "self check could not prepare its scratch store: " + failure);
        } finally {
            if (store != null) {
                try {
                    Files.deleteIfExists(store);
                } catch (IOException ignored) {
                    // A leftover temp file cannot fail the checks; the operating system cleans %TEMP%.
                }
            }
        }
    }

    private static void runAll(Check check, Path store) throws IOException {
        AuthService auth = new AuthService(LocalUserStore.at(store), new FastHasher());

        check.section("Server startup on port 0");
        GameServer server = null;
        int boundPort = -1;
        List<String> listened = new ArrayList<>();
        try {
            ServerConfig config = loopback(ServerConfig.defaults());
            server = new GameServer(config, auth);
            server.setConnectionListener(new GameServer.ConnectionListener() {
                @Override
                public void connected(String address) {
                    listened.add("+" + address);
                }

                @Override
                public void disconnected(String address) {
                    listened.add("-" + address);
                }
            });
            check.that(!server.running(), "a freshly constructed server is not running");
            check.equal(0, portOf(config), "the requested port is 0 before binding");
            server.start();
            boundPort = server.port();
            check.that(server.running(), "start() makes the server report running");
            check.that(boundPort > 0 && boundPort <= 65535, "the bound port is usable: " + boundPort);
            check.that(boundPort != 0, "port() reports the port the operating system actually bound");
            check.note("server bound to 127.0.0.1:" + boundPort);
            boolean secondStartRefused = false;
            try {
                server.start();
            } catch (IllegalStateException expected) {
                secondStartRefused = true;
            }
            check.that(secondStartRefused, "starting the same server twice is refused");

            check.section("Handshake and the PING command");
            GameClient first = GameClient.connect("127.0.0.1", boundPort);
            try {
                check.that(first.channel() != null, "the client got an encrypted channel");
                check.that(first.channel().protocolName().startsWith("OC/1"),
                        "the negotiated protocol is named: " + first.channel().protocolName());
                check.equal("PONG", first.request("PING"), "PING is answered with PONG");
                check.equal("PONG", first.request("ping"), "commands are case insensitive");
                check.equal(1, server.clientCount(), "the server counts the connected client");
                check.that(server.clientAddresses().size() == 1,
                        "the server can list the peer address: " + server.clientAddresses());

                check.section("Registration and login through the encrypted channel");
                check.equal(0, auth.userCount(), "the scratch store starts empty");
                String registerReply = first.request("REGISTER alice S3cret-Passw0rd!");
                check.equal("OK " + AuthResult.SUCCESS.message(), registerReply,
                        "REGISTER succeeds with the message from the auth service");
                check.equal(1, auth.userCount(), "the registration reached the auth service");
                String loginReply = first.request("LOGIN alice S3cret-Passw0rd!");
                check.equal("OK " + AuthResult.SUCCESS.message(), loginReply,
                        "LOGIN with the right password succeeds");
                String wrongReply = first.request("LOGIN alice not-the-password");
                check.equal("ERR " + AuthResult.WRONG_PASSWORD.message(), wrongReply,
                        "a wrong password is rejected with the auth service's own result");
                check.that(!wrongReply.contains("S3cret-Passw0rd!"),
                        "the rejection does not echo any password");
                check.that(!registerReply.contains("S3cret-Passw0rd!"),
                        "the success reply does not echo the password either");
                check.equal("ERR " + AuthResult.INVALID_CREDENTIALS.message(),
                        first.request("REGISTER bob 12345"),
                        "a password below the auth service minimum is refused");

                String countReply = first.request("COUNT");
                check.equal("OK 1", countReply, "COUNT reports one registered user");
                check.equal("ERR unknown command", first.request("WAT"),
                        "an unknown command gets the documented error");
                check.equal("ERR REGISTER needs a name and a password",
                        first.request("REGISTER alice"),
                        "a REGISTER without a password is refused");
                check.that(first.request("REGISTER bob hunter2").startsWith("OK "),
                        "a second registration succeeds");

                check.section("A second client sees the shared user count");
                GameClient second = GameClient.connect("127.0.0.1", boundPort);
                try {
                    check.equal("OK 2", second.request("COUNT"),
                            "COUNT is shared across connections: " + auth.userCount());
                    check.equal(2, server.clientCount(), "two clients are connected at the same time");
                } finally {
                    second.close();
                }

                check.section("QUIT ends a connection");
                GameClient quitter = GameClient.connect("127.0.0.1", boundPort);
                try {
                    check.equal("OK bye", quitter.request("QUIT"), "QUIT is acknowledged before the close");
                    // The server closes the socket from its own thread, so the count drops a moment later.
                    check.equal(1, awaitClientCount(server, 1, 2_000),
                            "the server counted the quitter out without disturbing the other client");
                } finally {
                    quitter.close();
                }

                check.section("Two concurrent clients both get answers");
                // Captured by the lambdas below, which is why this is a separate final local: the port is
                // only known after binding, so it cannot be a constant of the check.
                final int concurrentPort = boundPort;
                ExecutorService executor = Executors.newFixedThreadPool(2);
                try {
                    Callable<String> conversation = () -> {
                        try (GameClient concurrent = GameClient.connect("127.0.0.1", concurrentPort)) {
                            String pong = concurrent.request("PING");
                            String count = concurrent.request("COUNT");
                            return pong + "|" + count;
                        }
                    };
                    Future<String> one = executor.submit(conversation);
                    Future<String> two = executor.submit(conversation);
                    check.equal("PONG|OK 2", one.get(20, TimeUnit.SECONDS),
                            "the first concurrent client got both answers");
                    check.equal("PONG|OK 2", two.get(20, TimeUnit.SECONDS),
                            "the second concurrent client got both answers");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    check.that(false, "the concurrency check was interrupted");
                } catch (ExecutionException | TimeoutException failure) {
                    check.that(false, "the concurrency check failed: " + failure);
                } finally {
                    executor.shutdownNow();
                }

                check.section("The wire really is encrypted");
                checkWireIsEncrypted(check, boundPort);
            } finally {
                first.close();
            }

            check.section("Stopping the server");
            server.stop();
            check.that(!server.running(), "stop() makes the server report not running");
            check.equal(0, server.clientCount(), "stop() leaves no counted clients");
            server.stop();
            check.that(!server.running(), "stop() is idempotent");
            check.that(portIsFree(boundPort), "the port is released and can be bound again: " + boundPort);
        } finally {
            if (server != null) {
                server.stop();
            }
        }

        check.section("A broken or hostile client cannot stop the server");
        checkBrokenClients(check, auth, listened);

        check.section("Protocol notes");
        check.note("commands: PING, REGISTER <name> <password>, LOGIN <name> <password>, COUNT, QUIT");
        check.note("responses: PONG, OK <message>, ERR <message>, OK <count>");
        check.note("connection listener callbacks observed: " + listened.size() + " " + listened);
    }

    /**
     * Proves that the transport frames are ciphertext.
     *
     * <p>The client writes through a tap that keeps every byte after the handshake. A known command is
     * sent, and the captured bytes must contain neither the response text nor the password. If the
     * channel were a plain pass through, this is the check that would fail.
     */
    private static void checkWireIsEncrypted(Check check, int port) throws IOException {
        String password = "wire-probe-password-4711";
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 5_000);
            socket.setSoTimeout(10_000);
            WireTap tap = new WireTap(socket.getOutputStream(), CLIENT_HANDSHAKE_BYTES);
            try (PrintStream out = new PrintStream(tap, false, StandardCharsets.UTF_8)) {
                SecureChannel channel = SecureChannel.clientSide(socket.getInputStream(), out);
                check.equal(0, tap.captured(), "the handshake bytes are not part of the transport capture");
                channel.writeLine("REGISTER carol " + password);
                check.equal("OK " + AuthResult.SUCCESS.message(), channel.readLine(),
                        "the encrypted channel delivered the registration reply");
                channel.writeLine("PING");
                check.equal("PONG", channel.readLine(), "PING over the tapped channel still answers PONG");
                out.flush();

                byte[] bytes = tap.capturedBytes();
                check.that(bytes.length > 0, "the tap captured " + bytes.length
                        + " transport bytes after the handshake");
                check.that(indexOf(bytes, "PONG".getBytes(StandardCharsets.UTF_8)) < 0,
                        "the plaintext response PONG does not appear on the wire");
                check.that(indexOf(bytes, password.getBytes(StandardCharsets.UTF_8)) < 0,
                        "the plaintext password does not appear on the wire");
                check.that(indexOf(bytes, "REGISTER".getBytes(StandardCharsets.UTF_8)) < 0,
                        "the plaintext command does not appear on the wire");
                check.that(indexOf(bytes, "carol".getBytes(StandardCharsets.UTF_8)) < 0,
                        "the plaintext user name does not appear on the wire");
                check.note("first transport frame on the wire: " + hex(bytes, 12));
            }
        }
    }

    /**
     * Proves that a malformed peer only loses its own connection.
     *
     * <p>Each broken client is built by hand, because the point is to send things a well behaved client
     * would never send. After each one the check drives a normal client, which is the actual assertion
     * that the listener survived.
     */
    private static void checkBrokenClients(Check check, AuthService auth, List<String> listened)
            throws IOException {
        GameServer server = null;
        try {
            ServerConfig config = loopback(ServerConfig.defaults()).withMaxFrameBytes(64);
            server = new GameServer(config, auth);
            server.start();
            int port = server.port();
            check.note("hardened server bound to 127.0.0.1:" + port + " with maxFrameBytes=64");

            // A peer that is not speaking this protocol at all, for example a port scanner or an HTTP
            // client: its first byte is not the version byte, so the server refuses it immediately.
            garbageHandshakeIsRefused(check, port, "a peer that is not our protocol at all",
                    "GET / HTTP/1.1\r\nHost: x\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            // The right version byte, then a hello that is the wrong length: the server cannot tell where
            // the key ends, so it must refuse the connection instead of guessing.
            byte[] shortHello = new byte[CLIENT_HANDSHAKE_BYTES - 5];
            shortHello[0] = 1;
            Arrays.fill(shortHello, 1, shortHello.length, (byte) 0x5A);
            garbageHandshakeIsRefused(check, port, "a hello whose public key is the wrong length",
                    shortHello);
            // The right length and a usable key, but not the version this server speaks.
            byte[] wrongVersion = new byte[CLIENT_HANDSHAKE_BYTES];
            wrongVersion[0] = 9;
            Arrays.fill(wrongVersion, 1, wrongVersion.length, (byte) 0x7F);
            garbageHandshakeIsRefused(check, port, "a peer with an unknown handshake version", wrongVersion);
            // A truncated hello: the server has to see the close rather than block forever on the rest.
            byte[] truncated = new byte[CLIENT_HANDSHAKE_BYTES / 2];
            truncated[0] = 1;
            garbageHandshakeIsRefused(check, port, "a peer that stops in the middle of its hello",
                    truncated);

            check.that(oversizedFrameIsRefused(check, port), "an oversized frame is refused");
            check.that(corruptedFrameIsRefused(check, port), "a corrupted frame is refused");
            check.that(overlongLineIsRefused(check, port), "a line longer than the limit is refused");
            check.that(replayedFrameIsRefused(check, port), "a replayed frame is refused");

            check.that(server.running(), "the server is still running after seven hostile clients");
            try (GameClient survivor = GameClient.connect("127.0.0.1", port)) {
                check.equal("PONG", survivor.request("PING"),
                        "a normal client still works after the broken ones");
            }
            check.that(server.running(), "the server is still running after that normal client left");
        } finally {
            if (server != null) {
                server.stop();
            }
            check.note("connection listener callbacks: " + listened.size());
        }
    }

    /**
     * Sends pre-handshake bytes that cannot be a handshake and asserts the server drops that socket.
     *
     * <p>The point is not that the bytes are random - it is that a peer which has not completed the key
     * exchange is never allowed to reach the command parser, and that dropping it costs only its own
     * connection. The check therefore counts both a clean end of stream and a reset as "the server
     * disconnected this client".
     */
    private static boolean garbageHandshakeIsRefused(Check check, int port, String what, byte[] bytes)
            throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 5_000);
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(bytes);
            out.flush();
            boolean closed = serverClosed(socket);
            check.that(closed, what + " is disconnected by the server");
            return closed;
        }
    }

    /**
     * Builds a valid handshake and then sends an encrypted frame far beyond the configured ceiling.
     *
     * <p>The oversized frame is sent through a real {@link SecureChannel} with a deliberately generous
     * limit on the client side, so the server is the side that has to reject it. The client is expected to
     * see the connection die, not a protocol error, because the server must not spend bandwidth answering
     * a peer that already broke the frame contract.
     */
    private static boolean oversizedFrameIsRefused(Check check, int port) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 5_000);
            socket.setSoTimeout(10_000);
            SecureChannel channel = SecureChannel.clientSide(socket.getInputStream(),
                    socket.getOutputStream(), 8192, 8192);
            channel.writeFrame(new byte[4096]);
            try {
                channel.readLine();
                check.that(false, "a 4096 byte frame must not be answered when the server accepts 64");
                return false;
            } catch (IOException expected) {
                check.note("the oversized frame cost this client its connection: " + expected.getMessage());
                return true;
            }
        }
    }

    /**
     * Completes a real handshake and then writes bytes that are not a valid frame.
     *
     * <p>This is the tampering case: an attacker who can rewrite bytes in flight must not be able to
     * smuggle anything into the command parser. The server has to notice and drop the connection, and the
     * length prefix of the bytes below (four kilobytes) is what it rejects first.
     */
    private static boolean corruptedFrameIsRefused(Check check, int port) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 5_000);
            socket.setSoTimeout(10_000);
            SecureChannel handshake = SecureChannel.clientSide(socket.getInputStream(),
                    socket.getOutputStream(), 8192, 8192);
            check.that(handshake != null, "the corrupted frame probe completed its handshake");
            OutputStream out = socket.getOutputStream();
            out.write(new byte[] {
                    0, 0, 16, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16
            });
            out.flush();
            boolean closed = serverClosed(socket);
            check.that(closed, "the server disconnected the client that sent the corrupt frame");
            return closed;
        }
    }

    /**
     * Sends one line that is longer than the configured line limit and asserts it never becomes a command.
     *
     * <p>A regression here would be invisible without this probe: an unbounded read loop would happily
     * accept a multi-megabyte "line" and hand it to the parser. The expected outcome is that the server
     * drops the connection, and the assertion that actually matters is that no reply ever arrives.
     */
    private static boolean overlongLineIsRefused(Check check, int port) throws IOException {
        // The client side deliberately allows a larger frame than the server does, so the over-long line
        // is really transmitted instead of being refused before it leaves this process.
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 5_000);
            socket.setSoTimeout(10_000);
            SecureChannel channel = SecureChannel.clientSide(socket.getInputStream(),
                    socket.getOutputStream(), 16 * 1024, 16 * 1024);
            StringBuilder longLine = new StringBuilder("PING ");
            while (longLine.length() <= 8192) {
                longLine.append('x');
            }
            channel.writeLine(longLine.toString());
            try {
                String reply = channel.readLine();
                check.that(false, "an over-long line must not be answered, but the server said: " + reply);
                return false;
            } catch (IOException expected) {
                // The message is one of the frame limit errors, or a plain end of stream when the server
                // closed the socket before this side finished reading. Both mean the same thing here.
                check.note("the over-long line cost this client its connection: " + expected.getMessage());
                return true;
            }
        }
    }

    /**
     * Sends the same encrypted frame twice and asserts the server does not accept the second copy.
     *
     * <p>This is the replay case: a peer records a frame and sends it again to make a command happen
     * twice. The nonce is derived from a per-direction counter, so the second copy is decrypted with the
     * next nonce and cannot pass its tag check. The two frames below are built by hand because a real
     * client would never emit the same frame twice - that is the whole point of the probe.
     */
    private static boolean replayedFrameIsRefused(Check check, int port) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 5_000);
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            SecureChannel channel = SecureChannel.clientSide(socket.getInputStream(), out, 8192, 8192);

            // A real frame, produced by the channel itself so the ciphertext and the counter are genuine,
            // but captured instead of sent. A frame the server can accept is what makes the second copy a
            // replay rather than ordinary corruption.
            byte[] frame = ChannelTestSupport.captureLine(channel, "PING");
            check.that(frame.length == 4 + 5 + 16,
                    "the captured frame is a length prefix plus a sealed PING (" + frame.length + " bytes)");

            out.write(frame);
            out.flush();
            check.equal("PONG", channel.readLine(), "the first copy of the frame is accepted");

            out.write(frame);
            out.flush();
            try {
                String second = channel.readLine();
                check.that(false, "the replayed frame must not be answered, but the server said: " + second);
                return false;
            } catch (IOException rejected) {
                check.note("the replayed frame cost this client its connection: " + rejected.getMessage());
                return true;
            }
        }
    }

    /** Waits for the peer to close: either an end of stream or a reset counts. */
    private static boolean serverClosed(Socket socket) {
        try {
            return socket.getInputStream().read() < 0;
        } catch (IOException closed) {
            return true;
        }
    }

    /** Returns the configured port of a configuration, so the check can assert on the pre-bind value. */
    private static int portOf(ServerConfig config) {
        return config.port();
    }

    /**
     * Waits for the server's client count to reach an expected value.
     *
     * <p>A disconnect is observed by the client thread, which runs a moment after the reply that caused
     * it, so a bare read of the count would be racy. The wait is bounded and returns whatever it last saw,
     * which lets the caller report the real value in a failure message.
     */
    private static int awaitClientCount(GameServer server, int expected, long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        int seen = server.clientCount();
        while (seen != expected && System.nanoTime() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
            seen = server.clientCount();
        }
        return seen;
    }

    /** Restricts a configuration to loopback so the check never exposes a port to the network. */
    private static ServerConfig loopback(ServerConfig config) {
        return new ServerConfig(0, InetAddress.getLoopbackAddress().getHostAddress(), config.backlog(),
                config.maxClients(), config.readTimeoutMillis(), config.maxFrameBytes(),
                config.maxLineLength());
    }

    /** Returns whether a fresh listening socket can still take the port, which proves it was released. */
    private static boolean portIsFree(int port) {
        try (ServerSocket probe = new ServerSocket()) {
            probe.setReuseAddress(true);
            probe.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1);
            return true;
        } catch (IOException stillBound) {
            return false;
        }
    }

    /** Finds the first occurrence of a byte sequence, or {@code -1}. */
    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int start = 0; start + needle.length <= haystack.length; start++) {
            for (int index = 0; index < needle.length; index++) {
                if (haystack[start + index] != needle[index]) {
                    continue outer;
                }
            }
            return start;
        }
        return -1;
    }

    /** Formats the first bytes of a capture as hex, for a human readable note in the report. */
    private static String hex(byte[] bytes, int limit) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < Math.min(limit, bytes.length); index++) {
            if (index > 0) {
                text.append(' ');
            }
            text.append(Character.forDigit((bytes[index] >> 4) & 0xF, 16));
            text.append(Character.forDigit(bytes[index] & 0xF, 16));
        }
        return text.length() == 0 ? "<empty>" : text.toString();
    }

    /**
     * A deliberately cheap hasher for this check.
     *
     * <p>The production hasher is PBKDF2 with a high iteration count, which is exactly right for accounts
     * and exactly wrong for a self check that registers a handful of users on every run: it would add
     * seconds for no extra coverage, because password stretching is the auth module's subject, not this
     * one's. The iteration count still satisfies the record's own floor, so what this check exercises is
     * the wire protocol, not the hashing.
     */
    private static final class FastHasher implements PasswordHasher {

        private static final int ITERATIONS = 10_000;

        private final SecureRandom random = new SecureRandom();

        @Override
        public String newSalt() {
            byte[] salt = new byte[16];
            random.nextBytes(salt);
            return HexFormat.of().formatHex(salt);
        }

        @Override
        public String hash(String password, String salt, int iterations) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest((salt + ':' + password).getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(digest);
            } catch (NoSuchAlgorithmException failure) {
                throw new IllegalStateException("SHA-256 is unavailable in this JVM", failure);
            }
        }

        @Override
        public int defaultIterations() {
            return ITERATIONS;
        }
    }

    /**
     * An output stream that forwards everything to the socket and keeps a copy of the bytes written after
     * the handshake.
     *
     * <p>It is wrapped in a {@link PrintStream} by the caller precisely because a {@code PrintStream}
     * swallows {@link IOException}. That is what lets the capture happen without changing the transport:
     * the channel writes, flushes and reads as usual while this stream records what actually left the
     * process.
     */
    private static final class WireTap extends FilterOutputStream {

        private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        private int handshakeBytes;

        WireTap(OutputStream out, int handshakeBytes) {
            super(out);
            this.handshakeBytes = handshakeBytes;
        }

        @Override
        public void write(int value) throws IOException {
            out.write(value);
            record(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length);
            for (int index = 0; index < length; index++) {
                record(bytes[offset + index]);
            }
        }

        private void record(int value) {
            if (handshakeBytes > 0) {
                handshakeBytes--;
                return;
            }
            captured.write(value);
        }

        int captured() {
            return captured.size();
        }

        byte[] capturedBytes() {
            return captured.toByteArray();
        }
    }
}
