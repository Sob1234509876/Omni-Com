package io.github.sob1234509876.oc.server.check;

import io.github.sob1234509876.oc.server.auth.AuthService;
import io.github.sob1234509876.oc.server.auth.LocalUserStore;
import io.github.sob1234509876.oc.server.net.GameClient;
import io.github.sob1234509876.oc.server.net.GameServer;
import io.github.sob1234509876.oc.server.net.ServerConfig;
import io.github.sob1234509876.oc.server.plugin.RequestRegistry;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Self check for the {@code REQ} protocol command: who may use it, what the server does with the bytes, and
 * what happens when a plugin handler fails.
 *
 * <p>{@code REQ} is the one command whose payload the server does not understand, so it is the one command
 * whose edges matter: a request before a login must never reach a handler, an unregistered name must not
 * guess, a handler that throws must cost exactly one reply, and the bytes must arrive and return unchanged.
 * All of that is checked over a real socket against a real {@link GameServer}, because the properties live in
 * the conversation, not in a mock.
 *
 * <p>Descriptions are Chinese, matching the rest of the repository's checks.
 */
public final class RequestCheck {

    /** The request name the check registers; the pattern allows exactly this shape. */
    private static final String ECHO_NAME = "selfcheck-echo";

    /** A handler that throws, to prove one failure costs one reply. */
    private static final String FAILING_NAME = "selfcheck-failing";

    /** A handler that counts its calls, to prove a refused request never reaches a handler. */
    private static final String COUNTING_NAME = "selfcheck-counting";

    /** What the counting handler answers; the reply is its base64, so both sides use this constant. */
    private static final String COUNTING_REPLY = "counted";

    /** Account used for the authenticated part of the check. */
    private static final String USER = "request.check.user";

    /** A second account, because the default-registry server reads the same scratch store back from disk. */
    private static final String DEFAULT_USER = "request.default.user";

    /** Password of those accounts. */
    private static final String PASSWORD = "request-check-password";

    private RequestCheck() {
    }

    /**
     * Runs every {@code REQ} assertion against the given reporter.
     *
     * @param check the assertion sink, which reports the summary and the exit code
     */
    public static void run(Check check) {
        Path store = null;
        try {
            store = Files.createTempFile("oc-request-check-users", ".tsv");
            Files.writeString(store, "", StandardCharsets.UTF_8);
            new RequestCheck().runAll(check, store);
        } catch (IOException failure) {
            check.that(false, "REQ 检查无法准备它的临时用户文件: " + failure);
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

    private void runAll(Check check, Path store) throws IOException {
        check.section("REQ 协议命令");
        checkRegistryShape(check);

        AuthService auth = new AuthService(LocalUserStore.at(store), CheckHashers.fast());
        RequestRegistry registry = new RequestRegistry();
        registry.register(ECHO_NAME, request -> request);
        registry.register(FAILING_NAME, request -> {
            throw new IllegalStateException("deliberate handler failure");
        });
        AtomicInteger served = new AtomicInteger();
        registry.register(COUNTING_NAME, request -> {
            served.incrementAndGet();
            return COUNTING_REPLY.getBytes(StandardCharsets.UTF_8);
        });
        check.equal(3, registry.size(), "三个处理程序都注册成功");
        check.equal(List.of(COUNTING_NAME, ECHO_NAME, FAILING_NAME), registry.names(),
                "names() 按名字排序");

        GameServer server = null;
        try {
            server = new GameServer(loopback(ServerConfig.defaults()), auth, registry);
            server.start();
            check.note("REQ 检查的服务器绑定在 127.0.0.1:" + server.port());

            try (GameClient client = GameClient.connect("127.0.0.1", server.port())) {
                authenticationRules(check, client, served);
                opaqueRelay(check, client);
                handlerFailures(check, client);
            }
            oversizedAnswer(check, auth);
            check.that(server.running(), "所有 REQ 检查之后原来的服务器仍在运行");
        } finally {
            if (server != null) {
                server.stop();
            }
        }
        checkEmptyRegistryServer(check, store);
    }

    /** Name validation is part of the protocol surface, so it is asserted here as well as in the plugin. */
    private static void checkRegistryShape(Check check) {
        RequestRegistry registry = new RequestRegistry();
        check.equal(0, registry.size(), "新注册表为空");
        check.that(registry.find("anything").isEmpty(), "空注册表里查不到名字");
        registry.register("a-b9", request -> request);
        check.that(registry.find("a-b9").isPresent(), "符合 [a-z0-9-] 的名字可以注册");
        check.that(registry.unregister("a-b9"), "注销已注册的名字返回 true");
        check.that(!registry.unregister("a-b9"), "再次注销返回 false");
        check.that(throwsIllegalArgument(() -> registry.register("Upper", request -> request)),
                "大写名字被拒绝");
        check.that(throwsIllegalArgument(() -> registry.register("a_b", request -> request)),
                "带下划线的名字被拒绝（模式是 [a-z0-9-]）");
        check.that(throwsIllegalArgument(() -> registry.register("", request -> request)),
                "空名字被拒绝");
        check.that(throwsIllegalArgument(() -> registry.register("a".repeat(33), request -> request)),
                "超过 32 个字符的名字被拒绝");
        RequestRegistry duplicates = new RequestRegistry();
        duplicates.register("twice", request -> request);
        check.that(throwsIllegalArgument(() -> duplicates.register("twice", request -> request)),
                "重复注册被拒绝");
    }

    /** The new rule, and the old commands it must not disturb. */
    private static void authenticationRules(Check check, GameClient client, AtomicInteger served)
            throws IOException {
        check.equal("ERR login first", client.request("REQ " + ECHO_NAME + " " + base64("before-login")),
                "登录之前 REQ 被拒绝");
        check.equal(0, served.get(), "被拒绝的请求从来没有交给处理程序");
        check.equal("PONG", client.request("PING"), "PING 不受新规则影响");
        check.equal("ERR unknown command", client.request("WAT"), "未知命令仍然是 ERR unknown command");
        check.equal("ERR REGISTER needs a name and a password", client.request("REGISTER " + USER),
                "REGISTER 的形状检查没有改变");
        check.that(client.request("REGISTER " + USER + " " + PASSWORD).startsWith("OK "), "注册账号成功");
        check.equal("ERR login first", client.request("REQ " + ECHO_NAME + " " + base64("after-register")),
                "注册之后、登录之前 REQ 仍然被拒绝");

        check.that(client.request("LOGIN " + USER + " wrong-password").startsWith("ERR "),
                "错误密码登录失败");
        check.equal("ERR login first", client.request("REQ " + ECHO_NAME + " " + base64("after-failure")),
                "登录失败不会让会话变成已认证");
        check.that(client.request("LOGIN " + USER + " " + PASSWORD).startsWith("OK "), "正确密码登录成功");
        check.equal("OK " + base64("after-login"),
                client.request("REQ " + ECHO_NAME + " " + base64("after-login")),
                "登录之后 REQ 把负载原样交给处理程序并原样送回");

        check.equal("ERR unknown request", client.request("REQ not-registered " + base64("x")),
                "未注册的名字得到 ERR unknown request");
        check.equal("ERR unknown request", client.request("REQ " + ECHO_NAME), "缺少负载的 REQ 被拒绝");
        check.equal("ERR unknown request", client.request("REQ " + ECHO_NAME + " not-base64!!"),
                "负载不是 base64 时被拒绝，且不交给处理程序");
        check.equal("ERR unknown request", client.request("REQ " + "a".repeat(40) + " " + base64("x")),
                "过长的名字不查注册表，直接按未知请求拒绝");
        // An empty payload is not the same as an absent one: the decoder still has to see something. This
        // pins the documented behaviour down, so a future change to the parser cannot silently accept a
        // frame that carries no payload at all.
        check.equal("ERR unknown request", client.request("REQ " + ECHO_NAME + " "),
                "只有分隔空白的负载不是合法 base64，按未知请求拒绝");
    }

    /** The server must relay bytes without looking at them, in both directions. */
    private static void opaqueRelay(Check check, GameClient client) throws IOException {
        byte[] payload = new byte[] {0, 1, 2, (byte) 0xFF, 10, 13, (byte) 0x80};
        check.equal("OK " + Base64.getEncoder().encodeToString(payload),
                client.request("REQ " + ECHO_NAME + " " + Base64.getEncoder().encodeToString(payload)),
                "任意字节的负载被原样送回（服务器不解释也不改写）");
        check.equal("OK " + base64("unicode \u4e2d\u6587"),
                client.request("REQ " + ECHO_NAME + " " + base64("unicode \u4e2d\u6587")),
                "非 ASCII 的负载同样原样往返");
        check.equal("OK " + base64(COUNTING_REPLY),
                client.request("REQ " + COUNTING_NAME + " " + base64("go")),
                "计数器处理程序被调用一次");
        check.equal("OK " + base64(COUNTING_REPLY),
                client.request("REQ " + COUNTING_NAME + " " + base64("go")),
                "计数器处理程序可以被再次调用");
    }

    /** A handler that throws costs one reply, not the connection and not the listener. */
    private static void handlerFailures(Check check, GameClient client) throws IOException {
        check.equal("ERR request failed", client.request("REQ " + FAILING_NAME + " " + base64("boom")),
                "处理程序抛异常时得到 ERR request failed");
        check.equal("PONG", client.request("PING"), "抛过异常之后这条连接仍然可用");
        check.equal("OK " + base64("still here"),
                client.request("REQ " + ECHO_NAME + " " + base64("still here")),
                "抛过异常之后别的处理程序仍然可用");
    }

    /**
     * A response larger than the protocol line is refused exactly like an over-long line written by hand: the
     * client loses its connection and the server keeps serving.
     *
     * <p>This is the one {@code REQ} failure that is not answered with an error line, and it is worth pinning
     * down: a plugin must not be able to write a partial line and leave the framing of the connection in a
     * state the next command trips over.
     */
    private static void oversizedAnswer(Check check, AuthService auth) throws IOException {
        RequestRegistry small = new RequestRegistry();
        small.register("too-big", request -> new byte[8192]);
        GameServer limited = null;
        try {
            limited = new GameServer(loopback(ServerConfig.defaults()), auth, small);
            limited.start();
            boolean disconnected = false;
            try (GameClient client = GameClient.connect("127.0.0.1", limited.port())) {
                check.that(client.request("LOGIN " + USER + " " + PASSWORD).startsWith("OK "),
                        "超大响应的检查先登录成功");
                try {
                    String reply = client.request("REQ too-big " + base64("x"));
                    check.note("超大响应竟然被回答了: " + reply);
                } catch (IOException expected) {
                    disconnected = true;
                    check.note("超大响应让客户端断开: " + expected.getMessage());
                }
            }
            check.that(disconnected, "超过行长度上限的响应让客户端断开，而不是写坏帧");
            check.that(limited.running(), "超大响应之后服务器仍在运行");
            try (GameClient survivor = GameClient.connect("127.0.0.1", limited.port())) {
                check.equal("PONG", survivor.request("PING"), "超大响应之后新客户端仍然得到 PONG");
            }
        } finally {
            if (limited != null) {
                limited.stop();
            }
        }
    }

    /** Without plugins the registry is empty and every request is unknown; nothing else changes. */
    private static void checkEmptyRegistryServer(Check check, Path store) throws IOException {
        check.section("没有插件处理程序的服务器（两参数构造器）");
        GameServer server = null;
        try {
            AuthService auth = new AuthService(LocalUserStore.at(store), CheckHashers.fast());
            server = new GameServer(loopback(ServerConfig.defaults()), auth);
            server.start();
            try (GameClient client = GameClient.connect("127.0.0.1", server.port())) {
                check.that(client.request("REGISTER " + DEFAULT_USER + " " + PASSWORD).startsWith("OK "),
                        "默认构造器的服务器仍然可以注册");
                check.that(client.request("LOGIN " + DEFAULT_USER + " " + PASSWORD).startsWith("OK "),
                        "默认构造器的服务器仍然可以登录");
                check.equal("ERR unknown request",
                        client.request("REQ " + ECHO_NAME + " " + base64("x")),
                        "没有插件的服务器对每一个 REQ 都回答未知请求");
            }
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    /** Base64 of the UTF-8 bytes of a text, which is what {@code REQ} expects. */
    private static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Restricts a configuration to loopback so the check never exposes a port to the network. */
    private static ServerConfig loopback(ServerConfig config) {
        return new ServerConfig(0, InetAddress.getLoopbackAddress().getHostAddress(), config.backlog(),
                config.maxClients(), config.readTimeoutMillis(), config.maxFrameBytes(),
                config.maxLineLength());
    }

    private static boolean throwsIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }
}
