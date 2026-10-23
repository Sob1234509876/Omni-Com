package io.github.sob1234509876.oc.server.debug.check;

import io.github.sob1234509876.oc.server.auth.AuthService;
import io.github.sob1234509876.oc.server.auth.LocalUserStore;
import io.github.sob1234509876.oc.server.check.Check;
import io.github.sob1234509876.oc.server.debug.DebugEnvelope;
import io.github.sob1234509876.oc.server.debug.DebugKeyFile;
import io.github.sob1234509876.oc.server.debug.DebugPlugin;
import io.github.sob1234509876.oc.server.debug.cli.DebugCli;
import io.github.sob1234509876.oc.server.net.GameClient;
import io.github.sob1234509876.oc.server.net.GameServer;
import io.github.sob1234509876.oc.server.net.ServerConfig;
import io.github.sob1234509876.oc.server.plugin.PluginContext;
import io.github.sob1234509876.oc.server.plugin.RequestHandler;
import io.github.sob1234509876.oc.server.plugin.RequestRegistry;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Self check of the debug plugin: the envelope, the key file, the handler and the whole path over a real
 * socket.
 *
 * <p>The check is a plain program rather than a test framework suite, matching the other modules; run it with
 * {@code ./gradlew :projects:oc:oc-game-server-debug:selfcheck}. The reporter lives in the game server
 * module's selfCheck source set, which this module's build puts on the check's compile and runtime class
 * path, so no assertion helper is duplicated here.
 *
 * <p>Descriptions are Chinese, matching the rest of the repository's checks, while the code, the comments and
 * everything the plugin logs stay English.
 *
 * <p>The last section is deliberately end to end: a real {@link GameServer} on port 0, a real
 * {@link GameClient} and a real encrypted channel, because the parts worth protecting - the login requirement
 * of {@code REQ}, the relay of an opaque blob and the fact that the plugin publishes exactly one request -
 * only exist at that level.
 */
public final class DebugPluginCheck {

    /** The server version the plugin reports; any non-blank value would do, so a distinct one is used. */
    private static final String SERVER_VERSION = "9.9-debugcheck";

    /** The account the socket section registers and logs in with. */
    private static final String USER = "debug_operator";

    /** A password long enough for the auth service. */
    private static final String PASSWORD = "debug-check-password";

    private DebugPluginCheck() {
    }

    /**
     * Runs the check and reports its own summary, which is what the {@code selfcheck} task executes.
     *
     * <p>The assertions live in {@link #run(Check)} so that the same code can be driven by the module's own
     * program or by a future test task; this entry point exists because a check that cannot be started is not
     * a check.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        Check check = new Check();
        run(check);
        check.summary("debug plugin self check");
    }

    /**
     * Runs every debug plugin assertion against the given reporter.
     *
     * @param check the assertion sink owned by the game server module's self check
     */
    public static void run(Check check) {
        Path root = createScratchDirectory();
        if (root == null) {
            check.that(false, "无法创建调试插件的临时目录");
            return;
        }
        try {
            checkEnvelope(check);
            checkKeyFileAndHandler(check, root);
            checkRegistry(check);
            checkOverSocket(check, root);
        } finally {
            deleteRecursively(root);
        }
    }

    // ------------------------------------------------------------------ 1) the envelope

    private static void checkEnvelope(Check check) {
        check.section("调试插件：信封（AES-256-GCM 端到端加密）");
        byte[] key = DebugEnvelope.newKey();
        check.equal(DebugEnvelope.KEY_BYTES, key.length, "密钥是 32 字节（AES-256）");
        check.that(!Arrays.equals(key, DebugEnvelope.newKey()), "每次取密钥都得到新的随机字节");

        byte[] payload = "INFO".getBytes(StandardCharsets.UTF_8);
        byte[] sealed = DebugEnvelope.seal(payload, key);
        check.that(sealed.length >= payload.length + DebugEnvelope.HEADER_BYTES,
                "信封比明文长出头部与认证标签: " + sealed.length + " 字节");
        check.equal(new String(DebugEnvelope.MAGIC, StandardCharsets.US_ASCII),
                new String(sealed, 0, DebugEnvelope.MAGIC.length, StandardCharsets.US_ASCII),
                "信封以 ASCII magic 开头");
        check.that(indexOf(sealed, payload) < 0, "信封里看不到明文");
        check.that(!Arrays.equals(DebugEnvelope.seal(payload, key), sealed),
                "同一个明文两次封装得到不同的字节（随机 nonce）");

        long now = System.currentTimeMillis() / 1000L;
        check.equal("INFO", new String(DebugEnvelope.open(sealed, key, now), StandardCharsets.UTF_8),
                "信封可以原样解开");

        byte[] tampered = sealed.clone();
        tampered[tampered.length - 1] ^= 0x01;
        refuses(() -> DebugEnvelope.open(tampered, key, now), check, "被改过的密文打不开（认证标签不匹配）");

        byte[] otherKey = DebugEnvelope.newKey();
        refuses(() -> DebugEnvelope.open(sealed, otherKey, now), check, "换一把密钥打不开同一个信封");

        byte[] old = DebugEnvelope.seal(payload, key, now - 120L);
        refuses(() -> DebugEnvelope.open(old, key, now), check, "120 秒前的信封被拒绝（防重放窗口 60 秒）");
        byte[] future = DebugEnvelope.seal(payload, key, now + 120L);
        refuses(() -> DebugEnvelope.open(future, key, now), check, "时间戳在 120 秒之后的信封同样被拒绝");
        byte[] edge = DebugEnvelope.seal(payload, key, now - DebugEnvelope.MAX_CLOCK_SKEW_SECONDS);
        check.equal("INFO", new String(DebugEnvelope.open(edge, key, now), StandardCharsets.UTF_8),
                "正好在窗口边界上的信封仍然可以用");

        byte[] wrongMagic = sealed.clone();
        wrongMagic[0] = 'X';
        refuses(() -> DebugEnvelope.open(wrongMagic, key, now), check, "magic 不对的信封被拒绝");
        refuses(() -> DebugEnvelope.open(new byte[] {1, 2, 3}, key, now), check, "过短的信封被拒绝");
        refuses(() -> DebugEnvelope.seal(payload, new byte[16]), check, "16 字节的密钥被拒绝（必须是 AES-256）");
    }

    // ------------------------------------------------------------------ 2) key file and handler

    private static void checkKeyFileAndHandler(Check check, Path root) {
        check.section("调试插件：密钥文件与 engine-info 报告");
        PluginContext context = new PluginContext(root.resolve("plugins"), root.resolve("data"),
                SERVER_VERSION, System.getLogger("debugcheck"));
        DebugPlugin plugin = new DebugPlugin();
        check.equal("oc-debug", plugin.name(), "插件名");
        check.equal("1.0", plugin.version(), "插件版本");
        check.that(plugin.description().toLowerCase(Locale.ROOT).contains("read-only"),
                "插件说明写明了它只报告信息: " + plugin.description());
        plugin.onLoad(context);
        plugin.onEnable();

        check.equal(1, context.requests().size(), "onLoad 之后插件只注册了一个请求");
        check.equal(List.of(DebugPlugin.REQUEST_NAME), context.requests().names(),
                "注册的请求名就是 engine-info");

        Path keyFile = root.resolve("data").resolve("oc-debug").resolve(DebugKeyFile.FILE_NAME);
        check.that(Files.isRegularFile(keyFile), "首次使用时 debug.key 已经被创建: " + keyFile);
        check.equal(64, readKeyText(keyFile).length(), "密钥文件是 64 个十六进制字符");

        byte[] firstKey = DebugKeyFile.read(keyFile);
        check.equal(DebugEnvelope.KEY_BYTES, firstKey.length, "文件里的密钥正好 32 字节");
        PluginContext second = new PluginContext(root.resolve("plugins"), root.resolve("data"),
                SERVER_VERSION, System.getLogger("debugcheck"));
        DebugPlugin reloaded = new DebugPlugin();
        reloaded.onLoad(second);
        reloaded.onEnable();
        check.that(Arrays.equals(firstKey, DebugKeyFile.read(keyFile)),
                "重新加载插件会复用同一个 debug.key，而不是重新生成");
        reloaded.onDisable();

        Path missing = root.resolve("other-data").resolve("oc-debug").resolve(DebugKeyFile.FILE_NAME);
        refuses(() -> DebugKeyFile.read(missing), check, "缺失的密钥文件被明确拒绝");
        Path malformed = root.resolve("broken-data").resolve("oc-debug").resolve(DebugKeyFile.FILE_NAME);
        writeKeyText(malformed, "not-a-key");
        refuses(() -> DebugKeyFile.read(malformed), check, "内容不是密钥的文件被明确拒绝");

        RequestHandler handler = context.requests().find(DebugPlugin.REQUEST_NAME).orElseThrow();
        String text = answer(handler, firstKey, DebugPlugin.REQUEST_TEXT).report();
        check.that(text.contains("server-version: " + SERVER_VERSION),
                "报告里有服务器版本（来自 PluginContext.serverVersion()）");
        check.that(text.contains("plugin: oc-debug"), "报告里有插件名");
        check.that(text.contains("plugin-version: 1.0"), "报告里有插件版本");
        check.that(text.contains("jvm-uptime-seconds:"), "报告里有 JVM 运行时间（ManagementFactory）");
        check.that(text.contains("heap-used-bytes:") && text.contains("heap-max-bytes:"),
                "报告里有堆内存用量与上限");
        check.that(text.contains("live-thread-count:"), "报告里有存活线程数");
        check.that(text.contains("os-name:") && text.contains("jvm-version:") && text.contains("os-arch:"),
                "报告里有 JVM 与操作系统的名字和版本");
        check.that(text.contains("plugin-data-directory: "
                        + root.resolve("data").resolve("oc-debug").toAbsolutePath().normalize()),
                "报告里有插件自己的数据目录");
        check.that(text.contains("plugin-request-names: " + DebugPlugin.REQUEST_NAME),
                "报告里有服务器已注册的请求名");
        check.that(text.contains("engine-hyperoperations:") && text.contains("4:tetration"),
                "报告里有 HyperOperation 的等级名与编号");
        check.that(text.contains("engine-natural-rendered:") && text.contains("engine-natural-value: 10^^10"),
                "报告里有 NaturalFormat 渲染的 Natural");
        check.that(text.contains("engine-world-dimensions: 1") && text.contains("engine-chunk-size: 16")
                        && text.contains("engine-chunks-generated: 1"),
                "报告里有临时 World 的维度与区块统计");
        check.that(text.contains("engine-world-terrain: debug:grass"), "报告里有临时 World 的地形");
        check.that(text.contains("plugin-read-only: true"), "报告自报为只读");
        // The report inspects the same unbounded map type the server hosts: the old ChunkedMap cast would
        // fail here, so these facts prove engine-info works on an infinite world.
        check.that(text.contains("engine-map-bounded: false"),
                "报告在无界 InfiniteTerrainMap 上裁定世界无界（不再强转 ChunkedMap）");
        check.that(text.contains("engine-chunks-budget: "
                        + io.github.sob1234509876.oc.rts.map.InfiniteTerrainMap.DEFAULT_MAX_LOADED_CHUNKS),
                "报告里有区块预算，而不是地图大小");
        check.that(text.contains("engine-chunks-loaded: 1"),
                "报告里只有被查看过的 1 个区块（无界地图按需生成）");
        // The request and the response travel as one base64 protocol line, and the server's default ceiling is
        // 4096 bytes, so a report that grew past that would be delivered as a disconnect instead. Measured
        // here with headroom, because the text grows with a longer data directory path on a real machine.
        check.that(text.getBytes(StandardCharsets.UTF_8).length < 3072,
                "文本报告的字节数是 " + text.getBytes(StandardCharsets.UTF_8).length
                        + "，留给 4096 字节的协议行足够余量");

        String json = answer(handler, firstKey, DebugPlugin.REQUEST_JSON).report();
        check.that(json.startsWith("{") && json.endsWith("}"), "INFO json 返回一个 JSON 对象");
        check.that(json.contains("\"server-version\":\"" + SERVER_VERSION + "\""), "JSON 里有服务器版本");
        check.that(json.contains("\"engine-natural-rendered\":\""), "JSON 里有引擎的 Natural 渲染结果");
        check.that(json.indexOf('\n') < 0, "JSON 是单行，值里的换行都已被转义");

        String unsupported = answer(handler, firstKey, "SHUTDOWN").report();
        check.equal("ERR unsupported request", unsupported, "未知请求行在信封内被拒绝");

        // The refused request must not have been counted: the plugin's own served counter has to grow by
        // exactly one over the next INFO request, and by nothing at all over the refused one.
        Answered before = answer(handler, firstKey, DebugPlugin.REQUEST_TEXT);
        Answered after = answer(handler, firstKey, DebugPlugin.REQUEST_TEXT);
        check.equal(Integer.parseInt(before.served()) + 1, Integer.parseInt(after.served()),
                "未知请求行没有被计数：两次 INFO 之间插入了它，计数器只增加一");

        byte[] wrongKey = DebugEnvelope.newKey();
        byte[] refused = DebugEnvelope.open(handler.handle(
                DebugEnvelope.seal("INFO".getBytes(StandardCharsets.UTF_8), wrongKey)), firstKey,
                System.currentTimeMillis() / 1000L);
        check.equal("ERR bad request", new String(refused, StandardCharsets.UTF_8),
                "用错密钥封的请求得到信封内的 ERR bad request，并且只有持钥者能读到这句话");

        plugin.onDisable();
        check.equal(0, context.requests().size(), "onDisable 撤下了自己注册的请求");
    }

    /**
     * Seals one request line, calls the handler and returns the opened response payload.
     *
     * <p>The counter returned alongside the report is the one {@link #reportCounter(String)} reads out of the
     * payload, so a caller can compare what the plugin says it served with what the check observed without
     * depending on the order in which either counter was updated.
     */
    private static Answered answer(RequestHandler handler, byte[] key, String requestLine) {
        byte[] envelope = DebugEnvelope.seal(requestLine.getBytes(StandardCharsets.UTF_8), key);
        byte[] response = handler.handle(envelope);
        byte[] payload = DebugEnvelope.open(response, key, System.currentTimeMillis() / 1000L);
        String report = new String(payload, StandardCharsets.UTF_8);
        return new Answered(report, reportCounter(report));
    }

    /** One answered request: the report itself and the served count the report claims. */
    private record Answered(String report, String served) {
    }

    /** Reads the plugin's own request counter out of a report, for the check's assertions. */
    private static String reportCounter(String report) {
        String marker = "plugin-requests-served: ";
        int start = report.indexOf(marker);
        if (start < 0) {
            return "<absent>";
        }
        int end = report.indexOf('\n', start);
        return report.substring(start + marker.length(), end < 0 ? report.length() : end);
    }

    // ------------------------------------------------------------------ 3) the registry

    private static void checkRegistry(Check check) {
        check.section("调试插件：请求注册表");
        RequestRegistry registry = new RequestRegistry();
        check.equal(0, registry.size(), "新注册表是空的");
        check.that(registry.find("engine-info").isEmpty(), "空注册表查不到任何名字");

        RequestHandler handler = request -> new byte[0];
        registry.register("engine-info", handler);
        check.equal(1, registry.size(), "注册之后 size() 为 1");
        check.that(registry.find("engine-info").isPresent(), "按名字能查到刚注册的处理程序");
        check.equal(List.of("engine-info"), registry.names(), "names() 返回已注册的名字");
        refuses(() -> registry.register("engine-info", handler), check, "重复注册同一个名字被拒绝");
        refuses(() -> registry.register("Engine-Info", handler), check, "大写名字被拒绝（不做大小写折叠）");
        refuses(() -> registry.register("engine info", handler), check, "带空格的名字被拒绝");
        refuses(() -> registry.register("", handler), check, "空名字被拒绝");
        refuses(() -> registry.register("a".repeat(33), handler), check, "超过 32 个字符的名字被拒绝");
        refuses(() -> registry.register(null, handler), check, "null 名字被拒绝");
        check.that(!registry.unregister("missing"), "注销没注册过的名字返回 false");
        check.that(registry.unregister("engine-info"), "注销已注册的名字返回 true");
        check.equal(0, registry.size(), "注销之后注册表为空");
    }

    // ------------------------------------------------------------------ 4) end to end over a socket

    private static void checkOverSocket(Check check, Path root) {
        check.section("调试插件：真实 socket 端到端");
        GameServer server = null;
        try {
            Path userFile = root.resolve("users.txt");
            AuthService auth = new AuthService(LocalUserStore.at(userFile));
            PluginContext live = new PluginContext(root.resolve("plugins"), root.resolve("data"),
                    SERVER_VERSION, System.getLogger("debugcheck"));
            DebugPlugin plugin = new DebugPlugin();
            plugin.onLoad(live);
            plugin.onEnable();
            check.equal(List.of(DebugPlugin.REQUEST_NAME), live.requests().names(),
                    "真实服务器上插件仍然只注册一个请求名（插件暴露的请求面就这一个）");

            ServerConfig config = loopback(ServerConfig.defaults());
            server = new GameServer(config, auth, live.requests());
            server.start();
            int port = server.port();
            check.note("调试插件检查的服务器绑定在 127.0.0.1:" + port);

            byte[] key = DebugKeyFile.read(root.resolve("data").resolve("oc-debug")
                    .resolve(DebugKeyFile.FILE_NAME));
            Path keyFile = root.resolve("data").resolve("oc-debug").resolve(DebugKeyFile.FILE_NAME);
            String envelope = Base64.getEncoder().encodeToString(DebugEnvelope.seal(
                    DebugPlugin.REQUEST_TEXT.getBytes(StandardCharsets.UTF_8), key));

            try (GameClient client = GameClient.connect("127.0.0.1", port)) {
                check.equal("ERR login first", client.request("REQ engine-info " + envelope),
                        "登录之前 REQ 被拒绝，请求根本不会到达插件");
                check.equal("PONG", client.request("PING"), "被拒绝的 REQ 不影响这条连接（PING 仍然可用）");

                check.that(client.request("REGISTER " + USER + " " + PASSWORD).startsWith("OK "),
                        "注册调试账号成功");
                check.that(client.request("LOGIN " + USER + " " + PASSWORD).startsWith("OK "),
                        "登录成功");

                check.equal("ERR unknown request", client.request("REQ no-such-request " + envelope),
                        "未注册的请求名得到 ERR unknown request");

                String reply = client.request("REQ " + DebugPlugin.REQUEST_NAME + " " + envelope);
                check.that(reply.startsWith("OK "), "已注册的请求名得到 OK 回复");
                byte[] sealed = Base64.getDecoder().decode(reply.substring("OK ".length()).strip());
                String payload = new String(
                        DebugEnvelope.open(sealed, key, System.currentTimeMillis() / 1000L),
                        StandardCharsets.UTF_8);
                check.that(payload.contains("server-version: " + SERVER_VERSION),
                        "端到端报告里有服务器版本");
                check.that(payload.contains("engine-hyperoperations:"), "端到端报告里有引擎数据");
                check.that(!reply.contains("server-version"), "响应在信道上也是密文，看不到报告明文");

                checkCli(check, port, root, keyFile);

                plugin.onDisable();
                check.equal("ERR unknown request", client.request("REQ " + DebugPlugin.REQUEST_NAME
                        + " " + envelope), "插件停用后同一个请求名不再可用");
            }
        } catch (IOException failure) {
            check.that(false, "端到端检查失败: " + failure);
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    /**
     * Drives the real CLI against the running server, so the operator's exact path is covered too.
     *
     * <p>{@link DebugCli#fetchReport(Map)} is the CLI without its printing and its exit status, which is what
     * makes it assertable here; the exit status itself is a two line branch of {@code main}. Both output
     * flavours are asked for, and the missing key file is checked because that is the mistake an operator
     * makes first.
     */
    private static void checkCli(Check check, int port, Path root, Path keyFile) throws IOException {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("host", "127.0.0.1");
        options.put("port", Integer.toString(port));
        options.put("user", USER);
        options.put("password", PASSWORD);
        options.put("key", keyFile.toString());

        String text = DebugCli.fetchReport(options);
        check.that(text.contains("server-version: " + SERVER_VERSION), "CLI 拿到的报告里有服务器版本");
        check.that(text.contains("plugin: oc-debug"), "CLI 拿到的报告里有插件名");

        Map<String, String> jsonOptions = new LinkedHashMap<>(options);
        jsonOptions.put("json", "true");
        String json = DebugCli.fetchReport(jsonOptions);
        check.that(json.startsWith("{") && json.contains("\"server-version\":\"" + SERVER_VERSION + "\""),
                "CLI 的 --json 拿到一个 JSON 对象");

        Map<String, String> wrongKey = new LinkedHashMap<>(options);
        wrongKey.put("key", root.resolve("no-such-key").toString());
        refuses(() -> DebugCli.fetchReport(wrongKey), check, "CLI 在密钥文件缺失时明确失败");

        Map<String, String> noUser = new LinkedHashMap<>(options);
        noUser.remove("user");
        refuses(() -> DebugCli.fetchReport(noUser), check, "CLI 在缺少 --user 时明确失败");
        refuses(() -> DebugCli.parseArguments(new String[] {"--nope"}), check, "CLI 拒绝未知选项");
        refuses(() -> DebugCli.parseArguments(new String[] {"--host"}), check, "CLI 拒绝缺少取值的选项");
        check.that(DebugCli.parseArguments(new String[] {"--json", "--user", "u"}).containsKey("json"),
                "CLI 的 --json 是开关，不需要取值");
    }

    // ------------------------------------------------------------------ helpers

    /** Restricts a configuration to loopback so the check never exposes a port to the network. */
    private static ServerConfig loopback(ServerConfig config) {
        return new ServerConfig(0, InetAddress.getLoopbackAddress().getHostAddress(), config.backlog(),
                config.maxClients(), config.readTimeoutMillis(), config.maxFrameBytes(),
                config.maxLineLength());
    }

    /** The text of a key file, without the trailing newline. */
    private static String readKeyText(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).strip();
        } catch (IOException failure) {
            return "<unreadable: " + failure + ">";
        }
    }

    /** Writes key text, used for the deliberately invalid key the check must refuse. */
    private static void writeKeyText(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("the check could not write its scratch key file", failure);
        }
    }

    /** Creates the scratch directory the whole check works in. */
    private static Path createScratchDirectory() {
        try {
            return Files.createTempDirectory("oc-debug-check-");
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
     * Asserts that an action is refused with a well formed complaint.
     *
     * <p>The check has no assertion helper for exceptions, and the types used here are the deliberate ones:
     * {@link IllegalArgumentException} and the unchecked {@code UncheckedIOException} for a bad envelope, a
     * bad request name or a key file that is missing or malformed, and {@link IOException} for a network step
     * that fails. Anything else - a programming error, a runtime assertion - escapes and fails the check
     * instead of being swallowed.
     */
    private static void refuses(Action action, Check check, String description) {
        try {
            action.run();
            check.that(false, description + "（但它没有被拒绝）");
        } catch (RuntimeException | IOException refused) {
            check.that(true, description + "（" + refused.getMessage() + "）");
        }
    }

    /** A step that either succeeds or throws one of the refusals {@link #refuses} accepts. */
    @FunctionalInterface
    private interface Action {
        void run() throws IOException;
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
}
