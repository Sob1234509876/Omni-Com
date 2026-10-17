package io.github.sob1234509876.oc.server.hello.check;

import io.github.sob1234509876.oc.server.auth.AuthService;
import io.github.sob1234509876.oc.server.auth.LocalUserStore;
import io.github.sob1234509876.oc.server.check.Check;
import io.github.sob1234509876.oc.server.hello.HelloPlugin;
import io.github.sob1234509876.oc.server.plugin.PluginContext;
import io.github.sob1234509876.oc.server.plugin.PluginDescriptor;
import io.github.sob1234509876.oc.server.plugin.RequestHandler;
import io.github.sob1234509876.oc.server.plugin.RequestRegistry;
import io.github.sob1234509876.oc.server.ws.CommandWebSocketHandler;
import io.github.sob1234509876.oc.server.ws.HttpWebServer;
import io.github.sob1234509876.oc.server.ws.WebSocketConfig;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Self check of the hello plugin: the plugin itself, its descriptor, and the whole path over a real
 * WebSocket.
 *
 * <p>The check is a plain program rather than a test framework suite, matching the other modules; run it
 * with {@code ./gradlew :projects:oc:oc-game-server-hello:selfcheck}. The reporter lives in the game server
 * module's selfCheck source set, which this module's build puts on the check's compile and runtime class
 * path, so no assertion helper is duplicated here.
 *
 * <p>The handler assertions are deliberately exact rather than "contains a greeting". The whole value of
 * the example is that a client can predict the answer byte for byte, so a changed wording should fail
 * here and not only in a browser. The last section is deliberately end to end: a real {@link HttpWebServer}
 * on port {@code 0}, a real user store, the WebSocket version handshake and an authenticated {@code REQ},
 * because that is the level on which the browser button actually works.
 *
 * <p>Descriptions are Chinese, matching the rest of the repository's checks, while the code and the
 * Javadoc stay English.
 */
public final class HelloPluginCheck {

    /** The account the socket section registers and logs in with. */
    private static final String USER = "hello_operator";

    /** A password long enough for the auth service. */
    private static final String PASSWORD = "hello-check-password";

    /** The server version reported to the plugin; any non-blank value would do. */
    private static final String SERVER_VERSION = "9.9-hellocheck";

    /** RFC 6455's example key, used so the handshake is the same one the WebSocket check asserts. */
    private static final String RFC_KEY = "dGhlIHNhbXBsZSBub25jZQ==";

    private HelloPluginCheck() {
    }

    /**
     * Runs the check and reports its own summary, which is what the {@code selfcheck} task executes.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        Check check = new Check();
        run(check);
        check.summary("hello plugin self check");
    }

    /**
     * Runs every hello plugin assertion against the given reporter.
     *
     * @param check the assertion sink owned by the game server module's self check
     */
    public static void run(Check check) {
        Path root = createScratchDirectory();
        if (root == null) {
            check.that(false, "无法创建 hello 插件的临时目录");
            return;
        }
        try {
            checkPlugin(check, root);
            checkDescriptor(check);
            checkOverWebSocket(check, root);
        } finally {
            deleteRecursively(root);
        }
    }

    // ------------------------------------------------------------------ 1) the plugin and its handler

    private static void checkPlugin(Check check, Path root) {
        check.section("hello 插件：注册与回答");
        PluginContext context = new PluginContext(root.resolve("plugins"), root.resolve("data"),
                SERVER_VERSION, System.getLogger("hellocheck"));
        HelloPlugin plugin = new HelloPlugin();
        check.equal("oc-hello", plugin.name(), "插件名");
        check.equal("1.0", plugin.version(), "插件版本");
        check.that(plugin.description().toLowerCase(Locale.ROOT).contains("minimal"),
                "插件说明写明它是最小示例: " + plugin.description());

        plugin.onLoad(context);
        plugin.onEnable();
        check.equal(1, context.requests().size(), "onLoad 之后插件只注册了一个请求");
        check.equal(List.of(HelloPlugin.REQUEST_NAME), context.requests().names(),
                "注册的请求名就是 hello");

        Optional<RequestHandler> found = context.requests().find(HelloPlugin.REQUEST_NAME);
        check.that(found.isPresent(), "按名字能查到注册的 hello 处理器");
        if (found.isEmpty()) {
            return;
        }
        RequestHandler handler = found.get();
        check.equal(greeting("browser"), answer(handler, "browser"),
                "带名字的 hello 请求得到完整问候");
        check.equal(greeting("world"), answer(handler, new byte[0]),
                "空载荷回答 Hello, world!");
        check.equal(greeting("world"), answer(handler, "   ".getBytes(StandardCharsets.UTF_8)),
                "只有空白的载荷同样回答 Hello, world!");

        byte[] longName = new byte[200];
        Arrays.fill(longName, (byte) 'x');
        String truncated = answer(handler, longName);
        check.equal(greeting("x".repeat(HelloPlugin.MAX_NAME_BYTES) + HelloPlugin.ELLIPSIS), truncated,
                "200 字节的名字被截成 64 字节并加上省略号");
        check.that(truncated.contains(HelloPlugin.ELLIPSIS), "被截断的回答里能看见省略号");

        plugin.onDisable();
        check.equal(0, context.requests().size(), "onDisable 撤下了自己注册的请求");
        check.that(context.requests().find(HelloPlugin.REQUEST_NAME).isEmpty(),
                "onDisable 之后按名字查不到 hello");
    }

    /** Sends one request through the handler and decodes the plain UTF-8 answer. */
    private static String answer(RequestHandler handler, String request) {
        return answer(handler, request.getBytes(StandardCharsets.UTF_8));
    }

    /** Sends one request through the handler and decodes the plain UTF-8 answer. */
    private static String answer(RequestHandler handler, byte[] request) {
        return new String(handler.handle(request), StandardCharsets.UTF_8);
    }

    /** The exact greeting the plugin is specified to answer; kept independent of the plugin's code. */
    private static String greeting(String name) {
        return "Hello, " + name + "! This is the Omni Com plugin system talking: request \"hello\","
                + " answered by oc-hello 1.0.";
    }

    // ------------------------------------------------------------------ 2) the jar descriptor

    private static void checkDescriptor(Check check) {
        check.section("hello 插件：jar 描述文件");
        try (InputStream in = HelloPlugin.class.getClassLoader()
                .getResourceAsStream(PluginDescriptor.RESOURCE)) {
            check.that(in != null, "插件自己的 classpath 上有 " + PluginDescriptor.RESOURCE);
            if (in == null) {
                return;
            }
            Properties properties = new Properties();
            properties.load(in);
            Optional<PluginDescriptor> parsed = PluginDescriptor.parse(properties);
            check.that(parsed.isPresent(), "描述文件可以解析为 PluginDescriptor");
            if (parsed.isEmpty()) {
                return;
            }
            PluginDescriptor descriptor = parsed.get();
            check.equal("oc-hello", descriptor.id(), "描述文件的 id");
            check.equal("1.0", descriptor.version(), "描述文件的 version");
            check.equal(HelloPlugin.class.getName(), descriptor.mainClass(),
                    "描述文件的 main 指向 HelloPlugin");
            check.equal("Minimal hello world plugin", descriptor.description(),
                    "描述文件的 description");
        } catch (IOException | RuntimeException failure) {
            check.that(false, "读取 " + PluginDescriptor.RESOURCE + " 失败: " + failure);
        }
    }

    // ------------------------------------------------------------------ 3) end to end over a socket

    private static void checkOverWebSocket(Check check, Path root) {
        check.section("hello 插件：真实 WebSocket 端到端");
        RequestRegistry registry = new RequestRegistry();
        PluginContext live = new PluginContext(root.resolve("plugins"), root.resolve("data"),
                SERVER_VERSION, System.getLogger("hellocheck"), registry);
        HelloPlugin plugin = new HelloPlugin();
        plugin.onLoad(live);
        plugin.onEnable();

        AuthService auth = new AuthService(LocalUserStore.at(root.resolve("users.txt")));
        WebSocketConfig config = new WebSocketConfig(0, InetAddress.getLoopbackAddress().getHostAddress(),
                "/ws", root.resolve("web-client").toString(), 64 * 1024, 1024 * 1024, 10_000, List.of());
        HttpWebServer server = new HttpWebServer(config, new CommandWebSocketHandler(auth, registry));
        try {
            server.start();
            int port = server.port();
            check.note("hello 检查的 WebSocket 服务器绑定在 127.0.0.1:" + port);
            check.that(server.running(), "WebSocket 端点正在运行");

            try (RawClient client = RawClient.connect(port)) {
                int status = client.upgrade();
                check.equal(101, status, "hello 的 WebSocket 握手得到 101");
                if (status != 101) {
                    return;
                }
                check.equal("OK OC/1", client.command("HELLO 1"), "HELLO 1 得到 OK OC/1");
                check.that(client.command("REGISTER " + USER + " " + PASSWORD).startsWith("OK "),
                        "在 WebSocket 上 REGISTER 成功");
                check.that(client.command("LOGIN " + USER + " " + PASSWORD).startsWith("OK "),
                        "在 WebSocket 上 LOGIN 成功");

                String reply = client.command("REQ hello " + base64("browser"));
                check.that(reply.startsWith("OK "), "REQ hello 得到 OK 答复");
                if (!reply.startsWith("OK ")) {
                    return;
                }
                byte[] decoded = Base64.getDecoder().decode(reply.substring("OK ".length()).strip());
                check.equal(greeting("browser"), new String(decoded, StandardCharsets.UTF_8),
                        "端到端回答解码后就是问候语（传输层只做 base64）");
            }
        } catch (IOException | RuntimeException failure) {
            check.that(false, "端到端检查失败: " + failure);
        } finally {
            server.stop();
            plugin.onDisable();
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Base64 of the UTF-8 bytes of a text, which is what {@code REQ} expects. */
    private static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Creates the scratch directory the whole check works in. */
    private static Path createScratchDirectory() {
        try {
            return Files.createTempDirectory("oc-hello-check-");
        } catch (IOException failure) {
            return null;
        }
    }

    /** Removes the scratch directory; a leftover temp directory must not fail an otherwise good run. */
    private static void deleteRecursively(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // The operating system cleans %TEMP%; a stray directory is not a check failure.
        }
    }

    /**
     * A raw WebSocket client, small enough to be read in one sitting.
     *
     * <p>It writes the HTTP upgrade and the client frames itself because the check is about the exact
     * bytes of the hello round trip; a library would hide the base64 payload and the framing this example
     * is meant to demonstrate. Only the text frame path is implemented, which is all OC/1 uses.
     */
    private static final class RawClient implements AutoCloseable {

        private static final int TIMEOUT_MILLIS = 5_000;

        /** A fixed mask, so every run sends the same deterministic bytes. */
        private static final byte[] MASK = new byte[] {0x37, (byte) 0xFA, 0x21, 0x3D};

        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;

        private RawClient(Socket socket) throws IOException {
            this.socket = socket;
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
        }

        static RawClient connect(int port) throws IOException {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), TIMEOUT_MILLIS);
            socket.setSoTimeout(TIMEOUT_MILLIS);
            socket.setTcpNoDelay(true);
            return new RawClient(socket);
        }

        /** Writes the upgrade and returns the HTTP status of the answer. */
        int upgrade() throws IOException {
            String host = InetAddress.getLoopbackAddress().getHostAddress() + ":" + socket.getPort();
            String request = "GET /ws HTTP/1.1\r\n"
                    + "Host: " + host + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "Sec-WebSocket-Key: " + RFC_KEY + "\r\n"
                    + "\r\n";
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String statusLine = readLine();
            String[] parts = statusLine.split(" ");
            int status = parts.length >= 2 ? Integer.parseInt(parts[1]) : -1;
            while (!readLine().isEmpty()) {
                // Headers are irrelevant to the assertions; the blank line ends the answer.
            }
            return status;
        }

        /** Sends one text command and returns the next text answer. */
        String command(String line) throws IOException {
            sendText(line);
            return awaitText();
        }

        /** Sends one masked text frame with FIN set. */
        void sendText(String text) throws IOException {
            byte[] payload = text.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream frame = new ByteArrayOutputStream(payload.length + 14);
            frame.write(0x81);
            if (payload.length <= 125) {
                frame.write(0x80 | payload.length);
            } else if (payload.length <= 0xFFFF) {
                frame.write(0x80 | 126);
                frame.write(payload.length >>> 8);
                frame.write(payload.length);
            } else {
                throw new IOException("this check only sends short commands, was " + payload.length
                        + " bytes");
            }
            frame.writeBytes(MASK);
            for (int index = 0; index < payload.length; index++) {
                frame.write(payload[index] ^ MASK[index & 3]);
            }
            out.write(frame.toByteArray());
            out.flush();
        }

        /** Reads frames until a text frame arrives; a close frame while waiting is an error. */
        String awaitText() throws IOException {
            while (true) {
                int first = readByte();
                int second = readByte();
                int opcode = first & 0x0F;
                boolean masked = (second & 0x80) != 0;
                long length = second & 0x7F;
                if (length == 126) {
                    length = ((long) readByte() << 8) | readByte();
                } else if (length == 127) {
                    length = 0;
                    for (int index = 0; index < 8; index++) {
                        length = (length << 8) | readByte();
                    }
                }
                if (length > Integer.MAX_VALUE) {
                    throw new IOException("the server announced an impossible frame length " + length);
                }
                byte[] mask = masked ? readFully(4) : null;
                byte[] payload = readFully((int) length);
                if (mask != null) {
                    for (int index = 0; index < payload.length; index++) {
                        payload[index] ^= mask[index & 3];
                    }
                }
                if (opcode == 0x8) {
                    int code = payload.length >= 2 ? ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF) : 1005;
                    throw new IOException("the server closed with " + code + " while waiting for text");
                }
                if (opcode == 0x1) {
                    return new String(payload, StandardCharsets.UTF_8);
                }
                // Ping, pong and continuation frames are not part of this short exchange.
            }
        }

        private int readByte() throws IOException {
            int value = in.read();
            if (value < 0) {
                throw new EOFException("the server closed the connection");
            }
            return value;
        }

        private byte[] readFully(int length) throws IOException {
            byte[] buffer = new byte[length];
            int filled = 0;
            while (filled < length) {
                int read = in.read(buffer, filled, length - filled);
                if (read < 0) {
                    throw new EOFException("the server closed after " + filled + " of " + length + " bytes");
                }
                filled += read;
            }
            return buffer;
        }

        private String readLine() throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream(64);
            int value;
            while ((value = in.read()) >= 0) {
                if (value == '\n') {
                    return line.toString(StandardCharsets.US_ASCII);
                }
                if (value != '\r') {
                    line.write(value);
                }
            }
            throw new EOFException("the server closed before finishing an HTTP header line");
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // The check is done with this socket; a close error cannot change any assertion.
            }
        }
    }
}
