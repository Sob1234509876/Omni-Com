package io.github.sob1234509876.oc.server.check;

import io.github.sob1234509876.oc.server.OcGameServer;
import io.github.sob1234509876.oc.server.net.ServerConfig;
import io.github.sob1234509876.oc.server.world.WorldHost;
import io.github.sob1234509876.oc.server.ws.HttpWebServer;
import io.github.sob1234509876.oc.server.ws.WebSocketConfig;
import io.github.sob1234509876.oc.server.ws.WebSocketHandler;
import io.github.sob1234509876.oc.server.ws.WebSocketSession;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Self check for the server side world and its HTTP snapshot route.
 *
 * <p>The check covers the whole chain a browser depends on: the host builds and starts, its tick counter
 * advances on the daemon thread, its JSON document is well formed and complete, the same seed reproduces
 * the same world, a different seed does not, and a real {@link HttpWebServer} on loopback answers
 * {@code 200 application/json} while the host ticks and {@code 503} when it is stopped or absent (the
 * {@code --world off} case). The endpoint assertions go over a real socket, not a mocked dispatcher.
 *
 * <p>The JSON validator is deliberately hand written and tiny: this module has no test framework and no
 * JSON library, and a snapshot is a small, known document. It rejects unbalanced braces, unterminated
 * strings, malformed numbers and {@code NaN}/{@code Infinity}, then asserts the keys and tile count the
 * browser expects.
 *
 * <p>Descriptions are Chinese, matching the rest of the repository's checks.
 */
public final class WorldHostCheck {

    private static final long TICK_WAIT_MILLIS = 2_000L;

    private static final int TICK_TARGET = 2;

    private WorldHostCheck() {
    }

    /**
     * Runs every world host check.
     *
     * @param check the assertion sink, which reports the summary and the exit code
     */
    public static void run(Check check) {
        check.section("服务器世界主机");
        hostAndSnapshot(check);
        determinism(check);
        try {
            route(check);
            disabledServer(check);
        } catch (IOException | RuntimeException failure) {
            check.that(false, "世界快照路由检查无法完成: " + failure);
        }
    }

    /** Builds a host, validates its document, starts it and proves the tick thread really advances. */
    private static void hostAndSnapshot(Check check) {
        WorldHost host = new WorldHost(WorldHost.DEFAULT_SEED);
        try {
            check.equal(0L, host.tick(), "未启动时 tick() 为 0");
            check.that(!host.running(), "未启动时 running() 为 false");
            check.equal(WorldHost.MAP_SIZE, host.width(), "报出的地图宽度是 64");
            check.equal(WorldHost.MAP_SIZE, host.height(), "报出的地图高度是 64");
            check.equal(16, host.tilePixels(), "报出的 tilePixels 是 16");
            check.equal(20, host.ticksPerSecond(), "报出的速率是 20 ticks/s");

            String initial = host.snapshotJson();
            validateSnapshot(check, initial, "tick 0 快照");
            check.that(initial.contains("\"entities\":[{\"id\":1"), "快照从实体 1 开始，说明样例场景存在");
            check.that(initial.contains("\"kind\":\"worker\""), "快照里有 worker 内容路径");
            check.that(initial.contains("\"kind\":\"warehouse\""), "快照里有 warehouse 内容路径");

            host.start();
            check.that(host.running(), "start() 后 running() 为 true");
            long before = host.tick();
            boolean advanced = waitForTick(host, before + TICK_TARGET, TICK_WAIT_MILLIS);
            check.that(advanced, "启动后 tick 计数器前进（等待 " + TICK_WAIT_MILLIS + "ms）");
            host.stop();
            check.that(!host.running(), "stop() 后 running() 为 false");

            long stoppedAt = host.tick();
            sleep(120L);
            check.equal(stoppedAt, host.tick(), "stop() 之后 tick 不再变化");
            String afterStop = host.snapshotJson();
            validateSnapshot(check, afterStop, "停止后的快照");
            check.that(afterStop.contains("\"tick\":" + stoppedAt), "快照 tick 与 host.tick() 一致");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            check.that(false, "世界主机检查被中断");
        } finally {
            host.stop();
        }
    }

    /** The same seed must reproduce the same tiles and document; another seed must not. */
    private static void determinism(Check check) {
        WorldHost first = new WorldHost(1L);
        WorldHost second = new WorldHost(1L);
        WorldHost different = new WorldHost(2L);
        try {
            List<List<String>> firstTiles = tilesOf(first.snapshotJson());
            List<List<String>> secondTiles = tilesOf(second.snapshotJson());
            List<List<String>> differentTiles = tilesOf(different.snapshotJson());
            check.that(firstTiles.equals(secondTiles), "同一种子两次产生相同的地形矩阵");
            check.that(first.snapshotJson().equals(second.snapshotJson()),
                    "同一种子两次产生完全相同的快照");
            check.that(!firstTiles.equals(differentTiles), "不同种子产生不同的地形矩阵");
            check.that(!first.snapshotJson().equals(different.snapshotJson()), "不同种子的快照不同");
        } finally {
            first.stop();
            second.stop();
            different.stop();
        }
    }

    /** Starts a real endpoint and checks 200/503/405 over a socket. */
    private static void route(Check check) throws IOException {
        Path root = Files.createTempDirectory("oc-world-check");
        Files.writeString(root.resolve("index.html"), "<!doctype html><title>world check</title>",
                StandardCharsets.UTF_8);
        WebSocketConfig config = new WebSocketConfig(0, InetAddress.getLoopbackAddress().getHostAddress(),
                "/ws", root.toString(), 64 * 1024, 1024 * 1024, 10_000, List.of());
        WorldHost host = new WorldHost(1L);
        HttpWebServer server = new HttpWebServer(config, NoopHandler.INSTANCE, null, host);
        try {
            host.start();
            server.start();
            int port = server.port();
            check.section("世界快照路由");
            check.note("世界快照检查的服务器绑定在 127.0.0.1:" + port + "（静态根 " + root + "）");

            Response snapshot = request(port, "GET", "/world/snapshot");
            check.equal(200, snapshot.status(), "GET /world/snapshot 返回 200");
            check.equal("application/json; charset=utf-8", snapshot.header("content-type"),
                    "快照的 Content-Type 是 JSON");
            check.equal("no-store", snapshot.header("cache-control"), "快照使用 Cache-Control: no-store");
            String body = snapshot.text();
            check.that(body.startsWith("{") && body.endsWith("}"), "快照正文以 { 开头、以 } 结尾");
            check.that(snapshot.header("content-length") != null, "快照带 Content-Length");
            validateSnapshot(check, body, "HTTP 快照");

            host.stop();
            Response unavailable = request(port, "GET", "/world/snapshot");
            check.equal(503, unavailable.status(), "世界停止后 GET /world/snapshot 返回 503");
            check.that(unavailable.text().contains("world is not running"),
                    "503 的正文说明世界没有运行: " + unavailable.text());

            Response post = request(port, "POST", "/world/snapshot");
            check.equal(405, post.status(), "POST /world/snapshot 返回 405");
            check.equal("GET, HEAD", post.header("allow"), "405 说明只允许 GET 与 HEAD");
        } finally {
            host.stop();
            server.stop();
            deleteRecursively(root);
        }

        // --world off is represented by an endpoint with no host: the route must exist and answer 503
        // instead of falling through to the static SPA fallback.
        HttpWebServer noWorld = new HttpWebServer(config, NoopHandler.INSTANCE);
        try {
            noWorld.start();
            Response off = request(noWorld.port(), "GET", "/world/snapshot");
            check.equal(503, off.status(), "--world off 时 GET /world/snapshot 返回 503");
            check.that(off.header("content-type").startsWith("text/plain"),
                    "--world off 的 503 是短文本");
        } finally {
            noWorld.stop();
        }
    }

    /**
     * Starts a real {@link OcGameServer} with {@code worldEnabled == false} (what {@code --world off}
     * passes) and queries its browser endpoint. This proves the CLI switch is wired through the server
     * lifecycle, not just through a hand built endpoint with no world.
     */
    private static void disabledServer(Check check) throws IOException {
        Path root = Files.createTempDirectory("oc-world-off-check");
        Path plugins = Files.createDirectories(root.resolve("plugins"));
        Path data = Files.createDirectories(root.resolve("data"));
        Path staticRoot = Files.createDirectories(root.resolve("web-client"));
        int httpPort = freePort();
        // A port of 0 means "disabled" to OcGameServer, so a free concrete port is reserved here. The
        // window between closing the probe and binding is small and a clash only fails this check.
        WebSocketConfig config = new WebSocketConfig(httpPort, InetAddress.getLoopbackAddress().getHostAddress(),
                "/ws", staticRoot.toString(), 64 * 1024, 1024 * 1024, 10_000, List.of());
        OcGameServer server = new OcGameServer(ServerConfig.defaults().withPort(0), plugins, data, 60,
                config, false, 1L);
        try {
            server.start();
            check.equal(httpPort, server.httpPort(), "--world off 时浏览器端点仍然启动");
            check.that(server.worldHost() == null, "--world off 时服务器没有 WorldHost");
            Response off = request(server.httpPort(), "GET", "/world/snapshot");
            check.equal(503, off.status(), "--world off 的服务器对 /world/snapshot 回答 503");
            check.that(off.header("content-type").startsWith("text/plain"),
                    "--world off 的 503 是短文本");
            check.that(off.text().contains("world is not running"),
                    "--world off 的 503 说明世界没有运行: " + off.text());
        } finally {
            server.stop();
            deleteRecursively(root);
        }
    }

    /** Reserves a free loopback port for the one check that needs a concrete HTTP port. */
    private static int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return probe.getLocalPort();
        }
    }

    private static boolean waitForTick(WorldHost host, long target, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (host.tick() >= target) {
                return true;
            }
            sleep(10L);
        }
        return host.tick() >= target;
    }

    private static void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    // ---------------------------------------------------------------------------------------------
    // Snapshot assertions
    // ---------------------------------------------------------------------------------------------

    private static void validateSnapshot(Check check, String json, String label) {
        check.that(balanced(json), label + "：花括号与方括号配对，字符串闭合");
        check.that(!json.contains("NaN") && !json.contains("Infinity"),
                label + "：没有 NaN 或 Infinity");
        Map<String, Object> document;
        try {
            document = MiniJson.parseObject(json);
        } catch (RuntimeException failure) {
            check.that(false, label + "：可由手写解析器读取（" + failure.getMessage() + "）");
            return;
        }
        check.that(true, label + "：可由手写解析器读取");

        for (String key : List.of("tick", "width", "height", "tilePixels", "tiles", "entities")) {
            check.that(document.containsKey(key), label + "：包含字段 " + key);
        }
        long tick = number(document, "tick");
        int width = (int) number(document, "width");
        int height = (int) number(document, "height");
        check.that(tick >= 0, label + "：tick 不是负数");
        check.that(width > 0 && height > 0, label + "：宽高是正数");

        Object tilesValue = document.get("tiles");
        int tileCount = 0;
        boolean tilesShaped = tilesValue instanceof List<?>;
        if (tilesValue instanceof List<?> rows) {
            tilesShaped = rows.size() == height;
            for (Object rowValue : rows) {
                if (!(rowValue instanceof List<?> row) || row.size() != width) {
                    tilesShaped = false;
                    break;
                }
                for (Object tile : row) {
                    if (!(tile instanceof String text) || text.isEmpty()) {
                        tilesShaped = false;
                        break;
                    }
                    tileCount++;
                }
            }
        }
        check.that(tilesShaped, label + "：tiles 是 height 行、每行 width 个字符串");
        check.equal(width * height, tileCount, label + "：tiles 恰好有 w*h 个条目");

        Object entitiesValue = document.get("entities");
        boolean entitiesShaped = entitiesValue instanceof List<?>;
        boolean everyEntityValid = entitiesShaped;
        int entityCount = 0;
        if (entitiesValue instanceof List<?> entities) {
            entityCount = entities.size();
            for (Object raw : entities) {
                if (!(raw instanceof Map<?, ?> entity)) {
                    everyEntityValid = false;
                    break;
                }
                Object id = entity.get("id");
                Object kind = entity.get("kind");
                Object x = entity.get("x");
                Object y = entity.get("y");
                if (!(id instanceof Number) || !(kind instanceof String kindText) || kindText.isEmpty()
                        || !(x instanceof Number xNumber) || !(y instanceof Number yNumber)) {
                    everyEntityValid = false;
                    break;
                }
                double xValue = xNumber.doubleValue();
                double yValue = yNumber.doubleValue();
                if (!Double.isFinite(xValue) || !Double.isFinite(yValue)
                        || xValue < 0 || xValue >= width || yValue < 0 || yValue >= height) {
                    everyEntityValid = false;
                    break;
                }
            }
        }
        check.that(entitiesShaped, label + "：entities 是数组");
        check.that(entityCount > 0, label + "：样例场景至少有一个实体");
        check.that(everyEntityValid, label + "：每个实体都有 id/kind/x/y 且位于地图内");
    }

    private static long number(Map<String, Object> document, String key) {
        Object value = document.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("field '" + key + "' is not a number");
        }
        return number.longValue();
    }

    private static List<List<String>> tilesOf(String json) {
        Map<String, Object> document = MiniJson.parseObject(json);
        List<List<String>> tiles = new ArrayList<>();
        if (document.get("tiles") instanceof List<?> rows) {
            for (Object rowValue : rows) {
                if (!(rowValue instanceof List<?> row)) {
                    continue;
                }
                List<String> copy = new ArrayList<>(row.size());
                for (Object tile : row) {
                    copy.add(String.valueOf(tile));
                }
                tiles.add(copy);
            }
        }
        return tiles;
    }

    /** A cheap structural check: balanced braces/brackets and closed strings. */
    private static boolean balanced(String json) {
        List<Character> expected = new ArrayList<>();
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < json.length(); index++) {
            char character = json.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
                continue;
            }
            if (character == '"') {
                inString = true;
            } else if (character == '{' || character == '[') {
                expected.add(character == '{' ? '}' : ']');
            } else if (character == '}' || character == ']') {
                if (expected.isEmpty() || expected.remove(expected.size() - 1) != character) {
                    return false;
                }
            }
        }
        return !inString && expected.isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // HTTP client
    // ---------------------------------------------------------------------------------------------

    private static Response request(int port, String method, String target) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 5_000);
            socket.setSoTimeout(5_000);
            socket.setTcpNoDelay(true);
            String head = method + " " + target + " HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            String statusLine = readLine(in);
            String[] parts = statusLine.split(" ");
            int status = Integer.parseInt(parts[1]);
            Map<String, String> headers = new LinkedHashMap<>();
            while (true) {
                String line = readLine(in);
                if (line.isEmpty()) {
                    break;
                }
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).strip().toLowerCase(Locale.ROOT),
                            line.substring(colon + 1).strip());
                }
            }
            byte[] body = new byte[0];
            if (!"HEAD".equals(method)) {
                String length = headers.get("content-length");
                int bytes = length == null ? 0 : Integer.parseInt(length);
                body = readFully(in, bytes);
            }
            return new Response(status, headers, body);
        }
    }

    private static String readLine(InputStream in) throws IOException {
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

    private static byte[] readFully(InputStream in, int length) throws IOException {
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

    private static void deleteRecursively(Path root) {
        if (root == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort; the operating system cleans its temporary directory.
                }
            });
        } catch (IOException ignored) {
            // Same as above: cleanup is never an assertion.
        }
    }

    /** One HTTP answer, with the body decoded only when a check needs it. */
    private record Response(int status, Map<String, String> headers, byte[] body) {

        String header(String name) {
            return headers.get(name);
        }

        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    /** The snapshot route is checked without a WebSocket conversation. */
    private static final class NoopHandler implements WebSocketHandler {

        private static final NoopHandler INSTANCE = new NoopHandler();

        @Override
        public void onOpen(WebSocketSession session) {
            // Not reached: this check never upgrades a connection.
        }

        @Override
        public void onMessage(WebSocketSession session, String text) {
            // Not reached: this check never upgrades a connection.
        }

        @Override
        public void onClose(WebSocketSession session, int code, String reason) {
            // Not reached: this check never upgrades a connection.
        }
    }

    // ---------------------------------------------------------------------------------------------
    // A very small JSON reader: objects, arrays, strings, numbers, true/false/null
    // ---------------------------------------------------------------------------------------------

    private static final class MiniJson {

        private final String text;
        private int index;

        private MiniJson(String text) {
            this.text = text;
        }

        static Map<String, Object> parseObject(String text) {
            MiniJson reader = new MiniJson(text);
            reader.skipWhitespace();
            Map<String, Object> value = reader.object();
            reader.skipWhitespace();
            if (reader.index != text.length()) {
                throw reader.error("trailing characters after the top level object");
            }
            return value;
        }

        private Map<String, Object> object() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                index++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = string();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                result.put(key, value());
                skipWhitespace();
                char next = next();
                if (next == '}') {
                    return result;
                }
                if (next != ',') {
                    throw error("expected ',' or '}' in object");
                }
            }
        }

        private List<Object> array() {
            expect('[');
            List<Object> result = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                index++;
                return result;
            }
            while (true) {
                skipWhitespace();
                result.add(value());
                skipWhitespace();
                char next = next();
                if (next == ']') {
                    return result;
                }
                if (next != ',') {
                    throw error("expected ',' or ']' in array");
                }
            }
        }

        private Object value() {
            skipWhitespace();
            if (index >= text.length()) {
                throw error("value expected at end of document");
            }
            char character = peek();
            return switch (character) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Object literal(String word, Object value) {
            if (!text.startsWith(word, index)) {
                throw error("expected " + word);
            }
            index += word.length();
            return value;
        }

        private String string() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (index < text.length()) {
                char character = text.charAt(index++);
                if (character == '"') {
                    return value.toString();
                }
                if (character != '\\') {
                    if (character < 0x20) {
                        throw error("raw control character in a string");
                    }
                    value.append(character);
                    continue;
                }
                if (index >= text.length()) {
                    throw error("unfinished escape sequence");
                }
                char escape = text.charAt(index++);
                switch (escape) {
                    case '"' -> value.append('"');
                    case '\\' -> value.append('\\');
                    case '/' -> value.append('/');
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> {
                        if (index + 4 > text.length()) {
                            throw error("unfinished unicode escape");
                        }
                        String hex = text.substring(index, index + 4);
                        try {
                            value.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException failure) {
                            throw error("malformed unicode escape '\\u" + hex + "'");
                        }
                        index += 4;
                    }
                    default -> throw error("unknown escape '\\" + escape + "'");
                }
            }
            throw error("unterminated string");
        }

        private Double number() {
            int start = index;
            while (index < text.length()) {
                char character = text.charAt(index);
                boolean part = (character >= '0' && character <= '9')
                        || character == '-' || character == '+' || character == '.'
                        || character == 'e' || character == 'E';
                if (!part) {
                    break;
                }
                index++;
            }
            if (start == index) {
                throw error("number expected");
            }
            double value;
            try {
                value = Double.parseDouble(text.substring(start, index));
            } catch (NumberFormatException failure) {
                throw error("malformed number");
            }
            if (!Double.isFinite(value)) {
                throw error("number is not finite");
            }
            return value;
        }

        private void skipWhitespace() {
            while (index < text.length()) {
                char character = text.charAt(index);
                if (character == ' ' || character == '\t' || character == '\n' || character == '\r') {
                    index++;
                } else {
                    return;
                }
            }
        }

        private char peek() {
            if (index >= text.length()) {
                throw error("unexpected end of document");
            }
            return text.charAt(index);
        }

        private char next() {
            char character = peek();
            index++;
            return character;
        }

        private void expect(char expected) {
            char actual = next();
            if (actual != expected) {
                throw error("expected '" + expected + "', was '" + actual + "'");
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at offset " + index);
        }
    }
}
