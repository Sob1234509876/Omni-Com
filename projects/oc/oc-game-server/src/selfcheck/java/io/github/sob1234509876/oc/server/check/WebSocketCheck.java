package io.github.sob1234509876.oc.server.check;

import io.github.sob1234509876.oc.server.auth.AuthService;
import io.github.sob1234509876.oc.server.auth.LocalUserStore;
import io.github.sob1234509876.oc.server.net.CommandDispatcher;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Self check for the browser facing WebSocket endpoint.
 *
 * <p>The endpoint is driven with a raw socket that writes the HTTP upgrade by hand and builds every frame
 * itself. That is the point: a client library would hide exactly the bytes this check exists to verify -
 * the {@code Sec-WebSocket-Accept} value, the mask bit, the fragmentation flags and the close codes. The
 * server is a real {@link HttpWebServer} on loopback with port {@code 0}, and every fixture is a temporary
 * file, so the check is deterministic and never touches a real server.
 *
 * <p>The RFC's own example key is used for the handshake assertion, so the expected accept value is not
 * produced by the code under test: {@code dGhlIHNhbXBsZSBub25jZQ==} must yield
 * {@code s3pPLMBiTxaQ9kYGzzhZRbK+xOo=}. A regression in the SHA-1/base64 step cannot hide behind a shared
 * helper.
 *
 * <p>Descriptions are Chinese, matching the rest of the repository's checks.
 */
public final class WebSocketCheck {

    /** Account registered over the WebSocket connection. */
    private static final String USER = "browser.check.user";

    /** Password of that account. */
    private static final String PASSWORD = "browser-check-password";

    /** Request name the stub plugin handler registers. */
    private static final String ENGINE_INFO = "engine-info";

    /** What the stub handler answers; the reply is its base64, so both sides share the constant. */
    private static final String ENGINE_ANSWER = "engine-ok";

    /** The example key from RFC 6455 section 1.3. */
    private static final String RFC_KEY = "dGhlIHNhbXBsZSBub25jZQ==";

    /** The accept value RFC 6455 says belongs to {@link #RFC_KEY}. */
    private static final String RFC_ACCEPT = "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=";

    /** Opcodes used by the check; the numbers are from RFC 6455 section 5.2. */
    private static final int OPCODE_CONTINUATION = 0x0;
    private static final int OPCODE_TEXT = 0x1;
    private static final int OPCODE_BINARY = 0x2;
    private static final int OPCODE_CLOSE = 0x8;
    private static final int OPCODE_PING = 0x9;
    private static final int OPCODE_PONG = 0xA;

    private WebSocketCheck() {
    }

    /**
     * Runs every browser endpoint check.
     *
     * @param check the assertion sink, which reports the summary and the exit code
     */
    public static void run(Check check) {
        Path store = null;
        try {
            store = Files.createTempFile("oc-websocket-check-users", ".tsv");
            Files.writeString(store, "", StandardCharsets.UTF_8);
            runAll(check, store);
        } catch (IOException | RuntimeException failure) {
            check.that(false, "WebSocket 检查无法准备它的临时环境: " + failure);
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
        check.section("WebSocket 端点");
        AuthService auth = new AuthService(LocalUserStore.at(store), CheckHashers.fast());
        RequestRegistry registry = new RequestRegistry();
        registry.register(ENGINE_INFO, request -> ENGINE_ANSWER.getBytes(StandardCharsets.UTF_8));

        WebSocketConfig config = new WebSocketConfig(0, InetAddress.getLoopbackAddress().getHostAddress(),
                "/ws", store + "-web", 256, 1024, 10_000, List.of());
        HttpWebServer server = new HttpWebServer(config, new CommandWebSocketHandler(auth, registry));
        try {
            server.start();
            int port = server.port();
            check.note("WebSocket 检查的服务器绑定在 127.0.0.1:" + port
                    + "（maxFrameBytes=256, maxMessageBytes=1024, 静态目录不存在）");
            check.that(server.running(), "start() 之后端点报告正在运行");
            check.that(port > 0 && port <= 65535, "端点绑定到可用端口: " + port);
            check.equal(0, config.port(), "配置里的 0 表示让操作系统选端口");

            probe(check, "有效的升级握手", () -> handshakeAccepted(check, port));
            probe(check, "文本版本握手", () -> helloHandshake(check, port));
            probe(check, "缺失的版本号", () -> versionRejected(check, port, null));
            probe(check, "错误的版本号", () -> versionRejected(check, port, "12"));
            probe(check, "缺失的 Sec-WebSocket-Key", () -> keyRejected(check, port));
            probe(check, "错误的升级路径", () -> wrongPath(check, port));
            probe(check, "未掩码的客户端帧", () -> unmaskedFrameRejected(check, port));
            probe(check, "文本帧命令", () -> textCommands(check, port));
            probe(check, "Ping/Pong", () -> pingPong(check, port));
            probe(check, "分片消息", () -> fragmentedMessage(check, port));
            probe(check, "超过帧上限的帧", () -> oversizedFrame(check, port));
            probe(check, "无效的 UTF-8 文本帧", () -> invalidUtf8(check, port));
            check.that(server.running(), "所有帧协议检查之后端点仍在运行");
            probe(check, "存活连接", () -> survivor(check, port));
            check.that(server.running(), "存活检查之后端点仍在运行");
        } finally {
            server.stop();
        }
        check.that(!server.running(), "stop() 之后端点不再运行");
        probe(check, "来源白名单", () -> originAllowlist(check, auth, registry, store));
        probe(check, "同源规则", () -> sameOrigin(check, auth, registry, store));
        check.note("WebSocket 检查完成；RFC 6455 示例键的期望 accept 值是 " + RFC_ACCEPT);
    }

    /** Runs one probe so a broken socket in it does not stop the remaining checks. */
    private static void probe(Check check, String description, Probe probe) {
        try {
            probe.run();
        } catch (IOException | RuntimeException failure) {
            check.that(false, description + " 检查中断: " + failure);
        }
    }

    /** A valid upgrade returns the RFC accept value and the resulting connection speaks commands. */
    private static void handshakeAccepted(Check check, int port) throws IOException {
        try (RawClient client = RawClient.connect(port)) {
            client.sendUpgrade("/ws", RFC_KEY, null);
            HttpAnswer answer = client.awaitHttpAnswer();
            check.equal(101, answer.status(), "有效的升级请求得到 101 Switching Protocols");
            check.equal(RFC_ACCEPT, answer.headers().get("sec-websocket-accept"),
                    "Sec-WebSocket-Accept 与 RFC 6455 的示例值一致");
            check.that("websocket".equalsIgnoreCase(answer.headers().get("upgrade")),
                    "101 响应带 Upgrade: websocket");
            check.that("upgrade".equalsIgnoreCase(answer.headers().get("connection")),
                    "101 响应带 Connection: Upgrade");

            client.sendText("HELLO 1");
            check.equal(CommandWebSocketHandler.HELLO_ANSWER, client.awaitText(),
                    "升级后的第一帧 HELLO 1 得到 OK OC/1");
            client.sendText("PING");
            check.equal("PONG", client.awaitText(), "升级后的同一连接上 PING 得到 PONG");
        }
    }

    /** The browser version handshake: {@code HELLO 1} first, then commands. */
    private static void helloHandshake(Check check, int port) throws IOException {
        // The accepted case: the exact line and the command that follows it.
        try (RawClient client = RawClient.connect(port)) {
            client.openWebSocket();
            client.sendText("hello 1");
            check.equal(CommandWebSocketHandler.HELLO_ANSWER, client.awaitText(),
                    "命令字大小写不敏感的 HELLO 1 得到 OK OC/1");
            check.equal("PONG", command(client, "PING"), "HELLO 之后 PING 仍然得到 PONG");
        }
        // A version this server does not speak: refused and closed, so a stale client cannot keep talking.
        try (RawClient client = RawClient.connect(port)) {
            client.openWebSocket();
            client.sendText("HELLO 2");
            check.equal(CommandWebSocketHandler.ERR_UNSUPPORTED_VERSION, client.awaitText(),
                    "HELLO 2 得到版本错误");
            check.equal(1008, RawClient.closeCode(client.awaitClose()),
                    "版本不匹配的连接以 1008 关闭");
        }
        // A command before the hello: refused and closed, so no command is ever parsed before the version.
        try (RawClient client = RawClient.connect(port)) {
            client.openWebSocket();
            client.sendText("PING");
            check.equal(CommandWebSocketHandler.ERR_HELLO_FIRST, client.awaitText(),
                    "HELLO 之前的命令得到 ERR HELLO first");
            check.equal(1008, RawClient.closeCode(client.awaitClose()),
                    "HELLO 之前的命令让连接以 1008 关闭");
        }
        // Malformed hello shapes: missing number, extra argument and a non numeric number.
        for (String malformed : List.of("HELLO", "HELLO 1 2", "HELLO abc")) {
            try (RawClient client = RawClient.connect(port)) {
                client.openWebSocket();
                client.sendText(malformed);
                check.equal(CommandWebSocketHandler.ERR_MALFORMED_HELLO, client.awaitText(),
                        "格式错误的 HELLO 得到 ERR malformed HELLO: " + malformed);
                check.equal(1002, RawClient.closeCode(client.awaitClose()),
                        "格式错误的 HELLO 以 1002 关闭: " + malformed);
            }
        }
    }

    /** A missing or wrong {@code Sec-WebSocket-Version} is a 400 with a one line reason. */
    private static void versionRejected(Check check, int port, String version) throws IOException {
        try (RawClient client = RawClient.connect(port)) {
            List<String> headers = new ArrayList<>();
            headers.add("Host: 127.0.0.1");
            headers.add("Upgrade: websocket");
            headers.add("Connection: Upgrade");
            if (version != null) {
                headers.add("Sec-WebSocket-Version: " + version);
            }
            headers.add("Sec-WebSocket-Key: " + RFC_KEY);
            client.sendHandshake("GET /ws HTTP/1.1", headers);
            HttpAnswer answer = client.awaitHttpAnswer();
            check.equal(400, answer.status(), (version == null ? "缺失版本号" : "版本号 " + version)
                    + " 被拒绝为 400");
            String body = client.readBodyText(answer);
            check.that(body.contains("Sec-WebSocket-Version"), "400 的原因提到版本号: " + body);
        }
    }

    /** A key that is absent is refused before anything is hashed. */
    private static void keyRejected(Check check, int port) throws IOException {
        try (RawClient client = RawClient.connect(port)) {
            List<String> headers = new ArrayList<>();
            headers.add("Host: 127.0.0.1");
            headers.add("Upgrade: websocket");
            headers.add("Connection: Upgrade");
            headers.add("Sec-WebSocket-Version: 13");
            client.sendHandshake("GET /ws HTTP/1.1", headers);
            HttpAnswer answer = client.awaitHttpAnswer();
            check.equal(400, answer.status(), "缺失 Sec-WebSocket-Key 被拒绝为 400");
            check.that(client.readBodyText(answer).contains("Sec-WebSocket-Key"),
                    "400 的原因提到 Sec-WebSocket-Key");
        }
    }

    /** An upgrade attempt at another path is a 404, never a static fallback. */
    private static void wrongPath(Check check, int port) throws IOException {
        try (RawClient client = RawClient.connect(port)) {
            client.sendUpgrade("/not-the-endpoint", RFC_KEY, null);
            HttpAnswer answer = client.awaitHttpAnswer();
            check.equal(404, answer.status(), "错误的升级路径得到 404");
            check.that(client.readBodyText(answer).contains("no WebSocket endpoint"),
                    "404 的原因说明这里没有 WebSocket 端点");
        }
    }

    /** A client frame without the mask bit closes the connection with 1002. */
    private static void unmaskedFrameRejected(Check check, int port) throws IOException {
        try (RawClient client = RawClient.connect(port)) {
            client.upgrade();
            client.sendFrame(OPCODE_TEXT, "PING".getBytes(StandardCharsets.US_ASCII), true, false);
            Frame close = client.awaitClose();
            check.equal(1002, RawClient.closeCode(close), "未掩码的客户端帧以 1002 关闭连接");
        }
    }

    /** The command surface is the TCP one: login rule, registration and an opaque REQ reply. */
    private static void textCommands(Check check, int port) throws IOException {
        try (RawClient client = RawClient.connect(port)) {
            client.upgrade();
            check.equal(CommandDispatcher.ERR_LOGIN_FIRST,
                    command(client, "REQ " + ENGINE_INFO + " " + base64("before-login")),
                    "登录之前的 REQ 得到 ERR login first");
            check.that(command(client, "REGISTER " + USER + " " + PASSWORD).startsWith("OK "),
                    "REGISTER 在 WebSocket 上成功");
            check.equal(CommandDispatcher.ERR_LOGIN_FIRST,
                    command(client, "REQ " + ENGINE_INFO + " " + base64("after-register")),
                    "注册之后、登录之前 REQ 仍然被拒绝");
            check.that(command(client, "LOGIN " + USER + " " + PASSWORD).startsWith("OK "),
                    "LOGIN 在 WebSocket 上成功");
            check.equal("OK " + base64(ENGINE_ANSWER),
                    command(client, "REQ " + ENGINE_INFO + " " + base64("after-login")),
                    "登录之后 REQ 得到插件的 OK 答复");
            check.equal("PONG", command(client, "ping"), "命令名大小写不敏感");
            check.equal("ERR unknown command", command(client, "WAT"), "未知命令仍然是 ERR unknown command");

            client.sendFrame(OPCODE_BINARY, new byte[] {0, 1, 2, 3}, true, true);
            check.equal("ERR binary frames are not commands", client.awaitText(),
                    "二进制帧得到一个文本错误而不是被当成命令");
            check.equal("PONG", command(client, "PING"), "二进制帧之后连接仍然可用");
        }
    }

    /** A ping frame is answered with a pong that carries exactly the same payload. */
    private static void pingPong(Check check, int port) throws IOException {
        try (RawClient client = RawClient.connect(port)) {
            client.upgrade();
            byte[] payload = new byte[] {0, 1, 2, (byte) 0xFF, 65};
            client.sendFrame(OPCODE_PING, payload, true, true);
            Frame pong = client.awaitFrame(OPCODE_PONG);
            check.that(Arrays.equals(payload, pong.payload()), "Pong 的负载与 Ping 的负载完全一致");
            check.equal("PONG", command(client, "PING"), "Ping 之后连接仍然可用");
        }
    }

    /** Two text fragments are reassembled into one command before it is executed. */
    private static void fragmentedMessage(Check check, int port) throws IOException {
        try (RawClient client = RawClient.connect(port)) {
            client.upgrade();
            client.sendFrame(OPCODE_TEXT, "PI".getBytes(StandardCharsets.US_ASCII), false, true);
            client.sendFrame(OPCODE_CONTINUATION, "NG".getBytes(StandardCharsets.US_ASCII), true, true);
            check.equal("PONG", client.awaitText(), "分片文本消息被重组为一条 PING 并得到 PONG");
        }
    }

    /** A frame above {@code maxFrameBytes} closes with 1009 before the payload is buffered. */
    private static void oversizedFrame(Check check, int port) throws IOException {
        try (RawClient client = RawClient.connect(port)) {
            client.upgrade();
            byte[] oversized = new byte[300];
            Arrays.fill(oversized, (byte) 'x');
            client.sendFrame(OPCODE_TEXT, oversized, true, true);
            Frame close = client.awaitClose();
            check.equal(1009, RawClient.closeCode(close), "超过帧上限的帧以 1009 关闭连接");
        }
    }

    /** An invalid UTF-8 text frame closes with 1007. */
    private static void invalidUtf8(Check check, int port) throws IOException {
        try (RawClient client = RawClient.connect(port)) {
            client.upgrade();
            client.sendFrame(OPCODE_TEXT, new byte[] {(byte) 0xC3, 0x28}, true, true);
            Frame close = client.awaitClose();
            check.equal(1007, RawClient.closeCode(close), "无效的 UTF-8 文本帧以 1007 关闭连接");
        }
    }

    /** After every hostile probe a fresh connection still works; QUIT is still the TCP goodbye. */
    private static void survivor(Check check, int port) throws IOException {
        try (RawClient client = RawClient.connect(port)) {
            client.upgrade();
            client.sendText("PING");
            check.equal("PONG", client.awaitText(), "所有检查之后新连接仍然得到 PONG");
            client.sendText("QUIT");
            check.equal(CommandDispatcher.BYE, client.awaitText(), "QUIT 先得到 OK bye");
            Frame close = client.awaitClose();
            check.equal(1000, RawClient.closeCode(close), "QUIT 之后以 1000 正常关闭");
        }
    }

    /** A configured allowlist accepts a matching origin and refuses a foreign or missing one. */
    private static void originAllowlist(Check check, AuthService auth, RequestRegistry registry, Path store)
            throws IOException {
        WebSocketConfig config = new WebSocketConfig(0, InetAddress.getLoopbackAddress().getHostAddress(),
                "/ws", store + "-web", 256, 1024, 10_000, List.of("http://localhost:*"));
        HttpWebServer server = new HttpWebServer(config, new CommandWebSocketHandler(auth, registry));
        try {
            server.start();
            int port = server.port();
            try (RawClient client = RawClient.connect(port)) {
                client.sendUpgrade("/ws", RFC_KEY, "http://localhost:5173");
                check.equal(101, client.awaitHttpAnswer().status(), "白名单里的 Origin 被接受");
            }
            try (RawClient client = RawClient.connect(port)) {
                client.sendUpgrade("/ws", RFC_KEY, "http://evil.example");
                check.equal(403, client.awaitHttpAnswer().status(), "白名单之外的 Origin 得到 403");
            }
            try (RawClient client = RawClient.connect(port)) {
                client.sendUpgrade("/ws", RFC_KEY, null);
                check.equal(403, client.awaitHttpAnswer().status(),
                        "配置白名单时缺失 Origin 也被拒绝");
            }
        } finally {
            server.stop();
        }
    }

    /**
     * The same origin rule runs before the allowlist: a page served by this server may connect from any
     * address, a foreign page is still refused, and only an allowlisted origin may cross origins.
     *
     * <p>The allowlist is deliberately non-empty here, because an empty one admits every origin and would
     * make the refusals indistinguishable from the development escape hatch. The loopback entry is the
     * default the server actually ships with.
     */
    private static void sameOrigin(Check check, AuthService auth, RequestRegistry registry, Path store)
            throws IOException {
        WebSocketConfig config = new WebSocketConfig(0, InetAddress.getLoopbackAddress().getHostAddress(),
                "/ws", store + "-web", 256, 1024, 10_000, List.of("http://localhost:*"));
        HttpWebServer server = new HttpWebServer(config, new CommandWebSocketHandler(auth, registry));
        try {
            server.start();
            int port = server.port();
            String lanHost = "192.168.1.7:" + port;

            try (RawClient client = RawClient.connect(port)) {
                client.sendUpgrade("/ws", RFC_KEY, "http://" + lanHost, lanHost);
                check.equal(101, client.awaitHttpAnswer().status(),
                        "同源请求被接受：Origin 的主机端口等于 Host（局域网地址无需加白名单）");
            }
            try (RawClient client = RawClient.connect(port)) {
                client.sendUpgrade("/ws", RFC_KEY, "https://" + lanHost, lanHost);
                check.equal(101, client.awaitHttpAnswer().status(),
                        "同源比较忽略 scheme：https Origin 与 http Host 的主机端口相同仍然接受");
            }
            try (RawClient client = RawClient.connect(port)) {
                client.sendUpgrade("/ws", RFC_KEY, "http://Example.Test:" + port,
                        "example.test:" + port);
                check.equal(101, client.awaitHttpAnswer().status(),
                        "同源比较不区分大小写：Example.Test 与 example.test 相同");
            }
            try (RawClient client = RawClient.connect(port)) {
                client.sendUpgrade("/ws", RFC_KEY, "http://192.168.1.7:1", lanHost);
                check.equal(403, client.awaitHttpAnswer().status(),
                        "同源但端口不同被拒绝为 403");
            }
            try (RawClient client = RawClient.connect(port)) {
                client.sendUpgrade("/ws", RFC_KEY, "http://192.168.1.8:" + port, lanHost);
                check.equal(403, client.awaitHttpAnswer().status(),
                        "同源但主机不同被拒绝为 403");
            }
            try (RawClient client = RawClient.connect(port)) {
                client.sendUpgrade("/ws", RFC_KEY, "http://evil.example", lanHost);
                check.equal(403, client.awaitHttpAnswer().status(),
                        "别的站点（http://evil.example）仍然被拒绝为 403");
            }
            try (RawClient client = RawClient.connect(port)) {
                client.sendUpgrade("/ws", RFC_KEY, "http://localhost:5173", lanHost);
                check.equal(101, client.awaitHttpAnswer().status(),
                        "Host 不同时，回环白名单条目仍然放行开发客户端");
            }
            try (RawClient client = RawClient.connect(port)) {
                client.sendUpgrade("/ws", RFC_KEY, "http://" + lanHost, null);
                check.equal(403, client.awaitHttpAnswer().status(),
                        "缺失 Host 不算同源，回退到白名单后被拒绝为 403");
            }
            try (RawClient client = RawClient.connect(port)) {
                client.sendUpgrade("/ws", RFC_KEY, "not-an-origin", lanHost);
                check.equal(400, client.awaitHttpAnswer().status(),
                        "格式错误的 Origin（没有 scheme）被拒绝为 400");
            }
            try (RawClient client = RawClient.connect(port)) {
                client.sendUpgrade("/ws", RFC_KEY, "http://192.168.1.7:not-a-port", lanHost);
                check.equal(400, client.awaitHttpAnswer().status(),
                        "格式错误的 Origin（端口不是数字）被拒绝为 400");
            }
        } finally {
            server.stop();
        }
    }

    /** Sends one text command and returns the text answer. */
    private static String command(RawClient client, String line) throws IOException {
        client.sendText(line);
        return client.awaitText();
    }

    /** Base64 of the UTF-8 bytes of a text, which is what {@code REQ} expects. */
    private static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /** One probe body; allowed to fail with an I/O error without aborting the whole check. */
    @FunctionalInterface
    private interface Probe {
        void run() throws IOException;
    }

    /** The parsed status line and headers of a non-101 HTTP answer. */
    private record HttpAnswer(int status, Map<String, String> headers) {
    }

    /** One parsed WebSocket frame, after unmasking. */
    private record Frame(int opcode, boolean fin, byte[] payload) {
    }

    /**
     * A raw socket client: it writes the HTTP upgrade and the frames itself, so the bytes are exactly what
     * the check intends to send.
     */
    private static final class RawClient implements AutoCloseable {

        private static final int TIMEOUT_MILLIS = 5_000;

        /** A fixed mask, so every probe sends the same deterministic bytes. */
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

        /** Writes a request line and the given header lines, terminated by the blank line. */
        void sendHandshake(String requestLine, List<String> headers) throws IOException {
            StringBuilder request = new StringBuilder(requestLine).append("\r\n");
            for (String header : headers) {
                request.append(header).append("\r\n");
            }
            request.append("\r\n");
            out.write(request.toString().getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }

        /** Writes a well formed upgrade request with the loopback Host the rest of the checks use. */
        void sendUpgrade(String path, String key, String origin) throws IOException {
            sendUpgrade(path, key, origin, "127.0.0.1");
        }

        /**
         * Writes an upgrade request with an explicit {@code Host}, or none at all.
         *
         * <p>The same origin rule compares {@code Origin} with {@code Host}, so a check that wants to
         * exercise it has to control the Host value rather than accept the fixed loopback one.
         *
         * @param host the Host header value, or {@code null} to omit the header
         */
        void sendUpgrade(String path, String key, String origin, String host) throws IOException {
            List<String> headers = new ArrayList<>();
            if (host != null) {
                headers.add("Host: " + host);
            }
            headers.add("Upgrade: websocket");
            // A deliberately padded Connection header: RFC 6455 defines it as a token list.
            headers.add("Connection: keep-alive, Upgrade");
            headers.add("Sec-WebSocket-Version: 13");
            headers.add("Sec-WebSocket-Key: " + key);
            if (origin != null) {
                headers.add("Origin: " + origin);
            }
            sendHandshake("GET " + path + " HTTP/1.1", headers);
        }

        /** Sends a valid upgrade and asserts it was accepted, without doing the text version handshake. */
        void openWebSocket() throws IOException {
            sendUpgrade("/ws", RFC_KEY, null);
            HttpAnswer answer = awaitHttpAnswer();
            if (answer.status() != 101) {
                throw new IOException("the upgrade was refused with " + answer.status());
            }
        }

        /** Performs the text version handshake every browser connection must start with. */
        void hello() throws IOException {
            sendText("HELLO 1");
            String answer = awaitText();
            if (!CommandWebSocketHandler.HELLO_ANSWER.equals(answer)) {
                throw new IOException("HELLO 1 was answered with: " + answer);
            }
        }

        /** Opens a working connection: upgrade, text version handshake, then commands are allowed. */
        void upgrade() throws IOException {
            openWebSocket();
            hello();
        }

        /** Reads the status line and the headers of the next HTTP answer. */
        HttpAnswer awaitHttpAnswer() throws IOException {
            String statusLine = readLine();
            String[] parts = statusLine.split(" ");
            if (parts.length < 2) {
                throw new IOException("the server sent a malformed status line: " + statusLine);
            }
            int status;
            try {
                status = Integer.parseInt(parts[1]);
            } catch (NumberFormatException failure) {
                throw new IOException("the status line has no numeric status: " + statusLine, failure);
            }
            Map<String, String> headers = new LinkedHashMap<>();
            while (true) {
                String line = readLine();
                if (line.isEmpty()) {
                    break;
                }
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).strip().toLowerCase(Locale.ROOT),
                            line.substring(colon + 1).strip());
                }
            }
            return new HttpAnswer(status, headers);
        }

        /** Reads the body of an HTTP answer, using its {@code Content-Length}. */
        String readBodyText(HttpAnswer answer) throws IOException {
            String length = answer.headers().get("content-length");
            int bytes = length == null ? 0 : Integer.parseInt(length.strip());
            return new String(readFully(bytes), StandardCharsets.UTF_8);
        }

        /** Sends one text message as a masked text frame with FIN set. */
        void sendText(String text) throws IOException {
            sendFrame(OPCODE_TEXT, text.getBytes(StandardCharsets.UTF_8), true, true);
        }

        /**
         * Writes one frame with the given flags.
         *
         * <p>The length is encoded with the 7, 16 or 64 bit form exactly as the RFC requires, and the mask
         * is the fixed constant above, so a failure names the server's behaviour rather than a random mask.
         */
        void sendFrame(int opcode, byte[] payload, boolean fin, boolean masked) throws IOException {
            ByteArrayOutputStream frame = new ByteArrayOutputStream(payload.length + 14);
            frame.write((fin ? 0x80 : 0) | opcode);
            int maskBit = masked ? 0x80 : 0;
            if (payload.length <= 125) {
                frame.write(maskBit | payload.length);
            } else if (payload.length <= 0xFFFF) {
                frame.write(maskBit | 126);
                frame.write(payload.length >>> 8);
                frame.write(payload.length);
            } else {
                frame.write(maskBit | 127);
                for (int shift = 56; shift >= 0; shift -= 8) {
                    frame.write((int) ((long) payload.length >>> shift) & 0xFF);
                }
            }
            if (masked) {
                frame.writeBytes(MASK);
                byte[] maskedPayload = payload.clone();
                for (int index = 0; index < maskedPayload.length; index++) {
                    maskedPayload[index] ^= MASK[index & 3];
                }
                frame.writeBytes(maskedPayload);
            } else {
                frame.writeBytes(payload);
            }
            out.write(frame.toByteArray());
            out.flush();
        }

        /** Reads the next frame, whatever its opcode. */
        Frame readFrame() throws IOException {
            int first = readByte();
            int second = readByte();
            boolean fin = (first & 0x80) != 0;
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
            return new Frame(opcode, fin, payload);
        }

        /** Reads frames until one has the wanted opcode; a close frame while waiting is an error. */
        Frame awaitFrame(int wantedOpcode) throws IOException {
            while (true) {
                Frame frame = readFrame();
                if (frame.opcode() == wantedOpcode) {
                    return frame;
                }
                if (frame.opcode() == OPCODE_CLOSE) {
                    throw new IOException("the server closed with " + closeCode(frame)
                            + " while the check waited for opcode " + wantedOpcode);
                }
            }
        }

        /** Reads frames until a text frame arrives. */
        String awaitText() throws IOException {
            return new String(awaitFrame(OPCODE_TEXT).payload(), StandardCharsets.UTF_8);
        }

        /** Reads frames until a close frame arrives. */
        Frame awaitClose() throws IOException {
            while (true) {
                Frame frame = readFrame();
                if (frame.opcode() == OPCODE_CLOSE) {
                    return frame;
                }
            }
        }

        /** The status code of a close frame, or 1005 when it carried none. */
        static int closeCode(Frame frame) {
            byte[] payload = frame.payload();
            return payload.length >= 2 ? ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF) : 1005;
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
                    return line.toString(StandardCharsets.UTF_8);
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
