package io.github.sob1234509876.oc.server.check;

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
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Self check for the static file route of the browser endpoint.
 *
 * <p>Every assertion is made over a real socket against a real {@link HttpWebServer} on loopback port
 * {@code 0}, using a temporary static root written by the check. The fixture deliberately puts a secret file
 * next to that root: the traversal probes assert both the status code and that the secret text never appears
 * in a response, so a future change that "fixes" a traversal by serving the wrong file cannot pass.
 *
 * <p>The handler is a no-op because this check is only about files; the WebSocket half of the endpoint is
 * covered by {@link WebSocketCheck}. The temporary tree is removed afterwards, so repeated runs on one
 * machine do not accumulate fixtures.
 *
 * <p>Descriptions are Chinese, matching the rest of the repository's checks.
 */
public final class HttpCheck {

    /** Content of the fixture {@code index.html}; distinctive enough to prove which file was served. */
    private static final String INDEX_HTML = "<!doctype html><title>Omni Com HTTP check</title>";

    /** Content of the fixture {@code app.js}. */
    private static final String APP_JS = "console.log('app fixture');\n";

    /** Content of the fixture {@code assets/x.js}. */
    private static final String ASSET_JS = "console.log('asset fixture');\n";

    /** Text of the file outside the static root; it must never appear in a response. */
    private static final String OUTSIDE_SECRET = "outside-the-static-root-secret";

    /** The HTML cache policy the route is required to send. */
    private static final String NO_CACHE = "no-cache";

    /** The cache policy hashed assets are required to send. */
    private static final String IMMUTABLE = "public, max-age=31536000, immutable";

    private HttpCheck() {
    }

    /**
     * Runs every static route check.
     *
     * @param check the assertion sink, which reports the summary and the exit code
     */
    public static void run(Check check) {
        Path base = null;
        try {
            base = createFixture();
            runAll(check, base);
        } catch (IOException | RuntimeException failure) {
            check.that(false, "HTTP 检查无法准备它的临时目录: " + failure);
        } finally {
            deleteRecursively(base);
        }
    }

    private static void runAll(Check check, Path base) throws IOException {
        Path root = base.resolve("web-client");
        WebSocketConfig config = new WebSocketConfig(0, InetAddress.getLoopbackAddress().getHostAddress(),
                "/ws", root.toString(), 64 * 1024, 1024 * 1024, 10_000, List.of());
        HttpWebServer server = new HttpWebServer(config, NoopHandler.INSTANCE);
        try {
            server.start();
            int port = server.port();
            check.section("静态文件路由");
            check.note("HTTP 检查的服务器绑定在 127.0.0.1:" + port + "（静态根 " + root + "）");

            knownFiles(check, port);
            spaFallback(check, port);
            traversalRefused(check, port);
            methodsAndMissing(check, port);
        } finally {
            server.stop();
        }

        check.section("缺失的静态根");
        missingRoot(check, base);
    }

    /** Known files carry their bytes, their MIME type and the right cache policy. */
    private static void knownFiles(Check check, int port) throws IOException {
        Response app = request(port, "GET", "/app.js");
        check.equal(200, app.status(), "GET /app.js 返回 200");
        check.equal(APP_JS, app.text(), "GET /app.js 返回文件内容");
        check.that(app.header("content-type").startsWith("text/javascript; charset=utf-8"),
                "JS 的 MIME 类型正确: " + app.header("content-type"));
        check.equal(Integer.toString(APP_JS.getBytes(StandardCharsets.UTF_8).length),
                app.header("content-length"), "GET 总是带 Content-Length");

        Response index = request(port, "GET", "/");
        check.equal(200, index.status(), "GET / 返回 200");
        check.equal(INDEX_HTML, index.text(), "GET / 服务 index.html");
        check.that(index.header("content-type").startsWith("text/html; charset=utf-8"),
                "HTML 的 MIME 类型正确: " + index.header("content-type"));
        check.equal(NO_CACHE, index.header("cache-control"), "HTML 使用 no-cache");

        Response asset = request(port, "GET", "/assets/x.js");
        check.equal(200, asset.status(), "GET /assets/x.js 返回 200");
        check.equal(ASSET_JS, asset.text(), "assets 下的文件内容正确");
        check.equal(IMMUTABLE, asset.header("cache-control"),
                "assets 下的文件使用一年不变的缓存策略");

        Response query = request(port, "GET", "/app.js?v=2");
        check.equal(200, query.status(), "查询字符串不影响文件查找");
        check.equal(APP_JS, query.text(), "带查询字符串时仍返回文件内容");

        Response head = request(port, "HEAD", "/app.js");
        check.equal(200, head.status(), "HEAD /app.js 返回 200");
        check.equal(app.header("content-length"), head.header("content-length"),
                "HEAD 的 Content-Length 与 GET 相同");
        check.equal(0, head.body().length, "HEAD 不返回响应体");

        Response binary = request(port, "GET", "/blob.bin");
        check.equal(200, binary.status(), "未知扩展名的文件仍然可以读取");
        check.equal("application/octet-stream", binary.header("content-type"),
                "未知扩展名使用 application/octet-stream");
    }

    /** Routes without an extension are the client side router's; missing assets are not. */
    private static void spaFallback(Check check, int port) throws IOException {
        Response route = request(port, "GET", "/lobby/deck/42");
        check.equal(200, route.status(), "没有扩展名的未知路径回退到 index.html");
        check.equal(INDEX_HTML, route.text(), "回退返回的是 index.html 的内容");
        check.equal(NO_CACHE, route.header("cache-control"), "回退的 HTML 仍然使用 no-cache");

        Response directory = request(port, "GET", "/assets/");
        check.equal(200, directory.status(), "目录不会被列出，而是回退到 index.html");
        check.equal(INDEX_HTML, directory.text(), "目录请求返回 index.html");

        Response missing = request(port, "GET", "/missing.js");
        check.equal(404, missing.status(), "有扩展名的未知路径返回 404");
        check.that(missing.header("content-length") != null, "404 也带 Content-Length");
    }

    /** Path traversal is refused and never leaks the file next to the root. */
    private static void traversalRefused(Check check, int port) throws IOException {
        List<String> attempts = List.of(
                "/../secret.txt",
                "/%2e%2e/secret.txt",
                "/assets/../../secret.txt",
                "/%2e%2e%2fsecret.txt",
                "/..%5csecret.txt",
                "/..%20/secret.txt",
                "/C:/Windows/win.ini");
        for (String attempt : attempts) {
            Response response = request(port, "GET", attempt);
            check.that(response.status() == 400 || response.status() == 403,
                    "路径穿越被拒绝: " + attempt + " 得到 " + response.status());
            check.that(!response.text().contains(OUTSIDE_SECRET),
                    "路径穿越没有返回静态根之外的文件: " + attempt);
        }
        Response stillWorks = request(port, "GET", "/app.js");
        check.equal(200, stillWorks.status(), "穿越尝试之后正常文件仍然可读");
    }

    /** Only GET and HEAD are served, and a missing root is an ordinary 404. */
    private static void methodsAndMissing(Check check, int port) throws IOException {
        Response post = request(port, "POST", "/");
        check.equal(405, post.status(), "POST 得到 405");
        check.equal("GET, HEAD", post.header("allow"), "405 说明允许的方法");

        Response options = request(port, "OPTIONS", "/app.js");
        check.equal(405, options.status(), "GET/HEAD 之外的方法一律 405");
    }

    /** A missing static root is not an error: it is a 404 that says the client is not built. */
    private static void missingRoot(Check check, Path base) throws IOException {
        Path absent = base.resolve("no-such-web-client");
        WebSocketConfig config = new WebSocketConfig(0, InetAddress.getLoopbackAddress().getHostAddress(),
                "/ws", absent.toString(), 64 * 1024, 1024 * 1024, 10_000, List.of());
        HttpWebServer server = new HttpWebServer(config, NoopHandler.INSTANCE);
        try {
            server.start();
            int port = server.port();
            Response root = request(port, "GET", "/");
            check.equal(404, root.status(), "缺失的静态根让 GET / 返回 404");
            check.that(root.text().contains("web client is not built"),
                    "404 说明 web client 还没有构建: " + root.text());
            Response asset = request(port, "GET", "/assets/x.js");
            check.equal(404, asset.status(), "缺失的静态根也让资源返回 404");
            check.that(asset.text().contains("web client is not built"),
                    "资源 404 同样说明 web client 还没有构建");
        } finally {
            server.stop();
        }
    }

    /** Writes the fixture tree and returns the directory that contains the root and the secret. */
    private static Path createFixture() throws IOException {
        Path base = Files.createTempDirectory("oc-http-check");
        Path root = Files.createDirectories(base.resolve("web-client"));
        Files.createDirectories(root.resolve("assets"));
        Files.writeString(root.resolve("index.html"), INDEX_HTML, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("app.js"), APP_JS, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("assets/x.js"), ASSET_JS, StandardCharsets.UTF_8);
        Files.write(root.resolve("blob.bin"), new byte[] {0, 1, 2, 3});
        Files.writeString(base.resolve("secret.txt"), OUTSIDE_SECRET, StandardCharsets.UTF_8);
        return base;
    }

    /** Sends one request and reads the whole answer, using {@code Content-Length} for the body. */
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
                    // A leftover temp file cannot fail the checks; the operating system cleans %TEMP%.
                }
            });
        } catch (IOException ignored) {
            // Same as above: cleanup is best effort and never an assertion.
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

    /** The static route never calls a handler; this makes that explicit. */
    private static final class NoopHandler implements WebSocketHandler {

        private static final NoopHandler INSTANCE = new NoopHandler();

        @Override
        public void onOpen(WebSocketSession session) {
            // The static route does not upgrade, so this is never called in this check.
        }

        @Override
        public void onMessage(WebSocketSession session, String text) {
            // The static route does not upgrade, so this is never called in this check.
        }

        @Override
        public void onClose(WebSocketSession session, int code, String reason) {
            // The static route does not upgrade, so this is never called in this check.
        }
    }
}
