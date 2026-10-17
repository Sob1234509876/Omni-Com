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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Self check of the hello plugin: the plugin itself, its descriptor, its bundled 16x16 terrain atlas,
 * and the whole path over a real WebSocket.
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

    /** The 16 named tiles of the generated atlas, in the order the manifest lists them. */
    private static final String[] ATLAS_TILES = {
        "grass", "dirt", "stone", "cobblestone", "sand", "gravel", "water", "bedrock",
        "planks", "log", "leaves", "snow", "clay", "coal_ore", "iron_ore", "mossy_cobblestone"
    };

    /**
     * Terrain content path to atlas tile. The renderer resolves terrain by content path, so the manifest
     * has to publish these names next to the 16 art tiles, even where the names already coincide.
     */
    private static final String[][] TERRAIN_TILES = {
        {"grass", "grass"},
        {"water", "water"},
        {"sand", "sand"},
        {"forest", "leaves"},
        {"hill", "dirt"},
        {"rock", "stone"},
        {"void", "bedrock"}
    };

    /** Side of one atlas cell, in pixels. */
    private static final int TILE_PIXELS = 16;

    /**
     * The fewest distinct {@code 0xRRGGBB} values a tile must contain, counted literally.
     *
     * <p>The atlas draws every tile from a five shade palette (four for water), so a real tile has 4 to 7
     * distinct colours. Four therefore rejects a solid fill (one colour) and a two-tone gradient (two
     * colours) without pretending the palette is richer than it is. Distinct colours alone cannot catch a
     * two-tone block that covers half the tile each, which is why {@link #MAX_DOMINANT_SHARE} is checked
     * as well.
     */
    private static final int MIN_DISTINCT_COLOURS = 4;

    /**
     * The largest share of a tile's pixels that its single most common colour may cover.
     *
     * <p>This is the assertion that really catches "someone replaced the art with a solid block": a noisy
     * five shade tile spreads its pixels so that its most common colour stays well under four fifths,
     * while a flat fill covers 100% and a mostly flat tile is caught too. It complements rather than
     * replaces {@link #MIN_DISTINCT_COLOURS}: each number says exactly what it measures.
     */
    private static final double MAX_DOMINANT_SHARE = 0.80;

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
            checkAssets(check);
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

    // ------------------------------------------------------------------ 3) the bundled assets

    /**
     * The example plugin ships a real 16x16 terrain atlas, because the plugin asset route can only be
     * demonstrated end to end when a plugin jar actually contains a manifest and an image.
     *
     * <p>The resources are read through the plugin's own class loader - the same call the HTTP route makes -
     * so a file that is missing from the jar fails here instead of in a browser. The manifest is parsed as
     * JSON rather than compared as text, because the assertions are about the mapping and the rectangles,
     * not about how the file happens to be indented.
     */
    private static void checkAssets(Check check) {
        check.section("hello 插件：资源清单与 16x16 地形图集");
        String raw = resourceText("assets/manifest.json");
        check.that(raw != null, "插件自己的 classpath 上有 assets/manifest.json");
        Map<String, Object> manifest = null;
        Map<String, Object> tiles = null;
        if (raw != null) {
            try {
                manifest = asObject(Json.parse(raw), "manifest");
                check.that(true, "清单可以解析为 JSON");
            } catch (RuntimeException failure) {
                check.that(false, "清单可以解析为 JSON: " + failure.getMessage());
            }
        }
        if (manifest != null) {
            check.equal("atlas.png", manifest.get("image"), "清单的 image 指向同目录的 atlas.png");
            check.equal(16, integer(manifest.get("tilePixels")), "清单声明 tilePixels 为 16");
            check.that(!raw.replaceAll("\\s+", "").contains("atlasUrl"),
                    "插件清单里没有绝对 URL：atlasUrl 由服务器改写");
            try {
                tiles = asObject(manifest.get("tiles"), "tiles");
                check.that(true, "清单的 tiles 是 JSON 对象");
            } catch (RuntimeException failure) {
                check.that(false, "清单的 tiles 是 JSON 对象: " + failure.getMessage());
            }
            try {
                check.that(asObject(manifest.get("entities"), "entities").isEmpty(),
                        "清单的 entities 为空：单位回退到程序化形状");
            } catch (RuntimeException failure) {
                check.that(false, "清单的 entities 是对象: " + failure.getMessage());
            }
        }
        if (tiles != null) {
            for (String tile : ATLAS_TILES) {
                check.that(tiles.containsKey(tile), "清单保留了瓦片 " + tile);
            }
            for (String[] mapping : TERRAIN_TILES) {
                check.that(tiles.containsKey(mapping[0]), "清单映射了地形 " + mapping[0]);
            }
        }

        byte[] atlas = resourceBytes("assets/atlas.png");
        check.that(atlas != null, "插件自己的 classpath 上有 assets/atlas.png");
        if (atlas == null) {
            return;
        }
        check.that(atlas.length >= 2 * 1024,
                "atlas.png 至少 2 KiB，是真实纹理（实际 " + atlas.length + " 字节）");
        BufferedImage image = null;
        try {
            image = ImageIO.read(new ByteArrayInputStream(atlas));
        } catch (IOException failure) {
            check.that(false, "读取 assets/atlas.png 失败: " + failure);
        }
        check.that(image != null, "atlas.png 可以被 ImageIO 解码");
        if (image == null) {
            return;
        }
        check.equal(128, image.getWidth(), "图集宽度是 128 像素（8 列 16 像素）");
        check.equal(128, image.getHeight(), "图集高度是 128 像素（8 行 16 像素）");
        check.that(image.getType() == BufferedImage.TYPE_INT_ARGB || image.getColorModel().hasAlpha(),
                "图集带 alpha 通道（类型 " + image.getType() + "）");
        if (tiles != null) {
            checkTextures(check, image, tiles);
        }
    }

    /**
     * Checks that the atlas really is 16x16 pixel art and that every rectangle in the manifest is usable.
     *
     * <p>The geometry is checked before the pixels: a manifest whose rectangles leave the image would make
     * the colour assertions read whatever happens to be next to the image, so those checks only run on a
     * rectangle that is known to be inside it. Aliases (a terrain content path mapped onto a tile) share
     * their rectangle with the tile on purpose, which is why the overlap test compares distinct rectangles.
     */
    private static void checkTextures(Check check, BufferedImage image, Map<String, Object> tiles) {
        check.section("hello 插件：16x16 地形图集纹理");
        Map<String, int[]> rects = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : tiles.entrySet()) {
            int[] rect = rect(entry.getValue());
            if (rect == null) {
                check.that(false, "瓦片 " + entry.getKey() + " 的 rect 是四个整数");
                continue;
            }
            rects.put(entry.getKey(), rect);
            boolean inside = rect[0] >= 0 && rect[1] >= 0 && rect[0] + rect[2] <= image.getWidth()
                    && rect[1] + rect[3] <= image.getHeight();
            check.that(rect[2] == TILE_PIXELS && rect[3] == TILE_PIXELS && inside,
                    "瓦片 " + entry.getKey() + " 是 16x16 且落在 128x128 图集内");
        }
        check.that(rects.size() == tiles.size(), "清单里的每个 rect 都能读出四个整数");
        checkNoOverlap(check, rects);
        for (String[] mapping : TERRAIN_TILES) {
            int[] source = rects.get(mapping[1]);
            int[] mapped = rects.get(mapping[0]);
            check.that(source != null && Arrays.equals(source, mapped),
                    "地形 " + mapping[0] + " 映射到瓦片 " + mapping[1] + " 的 rect");
        }
        for (String name : ATLAS_TILES) {
            int[] rect = rects.get(name);
            if (rect == null) {
                continue;
            }
            Map<Integer, Integer> counts = new HashMap<>();
            for (int y = 0; y < rect[3]; y++) {
                for (int x = 0; x < rect[2]; x++) {
                    int rgb = image.getRGB(rect[0] + x, rect[1] + y) & 0xFFFFFF;
                    counts.merge(rgb, 1, Integer::sum);
                }
            }
            int pixels = rect[2] * rect[3];
            int dominant = 0;
            for (int count : counts.values()) {
                dominant = Math.max(dominant, count);
            }
            double dominantShare = pixels == 0 ? 1.0 : (double) dominant / pixels;
            String share = String.format(Locale.ROOT, "%.1f%%", dominantShare * 100.0);
            check.that(counts.size() >= MIN_DISTINCT_COLOURS,
                    "瓦片 " + name + " 至少有 " + MIN_DISTINCT_COLOURS + " 种颜色，不是纯色或双色块（实际 "
                            + counts.size() + " 种颜色，最常见的一种占 " + share + "）");
            check.that(dominantShare <= MAX_DOMINANT_SHARE,
                    "瓦片 " + name + " 最常见颜色不超过 80% 像素，不是被换成的色块（"
                            + dominant + "/" + pixels + " 像素，占 " + share + "，共 " + counts.size()
                            + " 种颜色）");
        }
        checkLeaves(check, image, rects);
        checkWater(check, image, rects);
        checkGrass(check, image, rects);
    }

    /** Asserts that two different tiles never share pixels; content path aliases may share a rectangle. */
    private static void checkNoOverlap(Check check, Map<String, int[]> rects) {
        List<String> names = new ArrayList<>(rects.keySet());
        for (int left = 0; left < names.size(); left++) {
            for (int right = left + 1; right < names.size(); right++) {
                int[] a = rects.get(names.get(left));
                int[] b = rects.get(names.get(right));
                if (Arrays.equals(a, b)) {
                    continue; // An alias maps onto its tile and shares the rectangle on purpose.
                }
                boolean overlap = a[0] < b[0] + b[2] && b[0] < a[0] + a[2]
                        && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
                if (overlap) {
                    check.that(false, "瓦片 " + names.get(left) + " 与 " + names.get(right)
                            + " 不重叠");
                    return;
                }
            }
        }
        check.that(true, "没有两个瓦片重叠（内容路径别名共享同一个 rect）");
    }

    /** Asserts the hole in the canopy: leaves is the one tile with a fully transparent pixel. */
    private static void checkLeaves(Check check, BufferedImage image, Map<String, int[]> rects) {
        int[] rect = rects.get("leaves");
        boolean hole = false;
        if (rect != null) {
            for (int y = 0; y < rect[3] && !hole; y++) {
                for (int x = 0; x < rect[2]; x++) {
                    if ((image.getRGB(rect[0] + x, rect[1] + y) >>> 24) == 0) {
                        hole = true;
                        break;
                    }
                }
            }
        }
        check.that(hole, "leaves 有完全透明的像素：树冠上的洞");
    }

    /** Asserts the wave dithering: at least four distinct blue dominant colours in water. */
    private static void checkWater(Check check, BufferedImage image, Map<String, int[]> rects) {
        int[] rect = rects.get("water");
        Set<Integer> blues = new HashSet<>();
        if (rect != null) {
            for (int y = 0; y < rect[3]; y++) {
                for (int x = 0; x < rect[2]; x++) {
                    int rgb = image.getRGB(rect[0] + x, rect[1] + y) & 0xFFFFFF;
                    int red = (rgb >>> 16) & 0xFF;
                    int green = (rgb >>> 8) & 0xFF;
                    int blue = rgb & 0xFF;
                    if (blue > red && blue > green) {
                        blues.add(rgb);
                    }
                }
            }
        }
        check.that(blues.size() >= 4,
                "water 至少有 4 种蓝色：波纹抖动（实际 " + blues.size() + " 种）");
    }

    /** Asserts the palette sanity check: grass is green, not grey or brown. */
    private static void checkGrass(Check check, BufferedImage image, Map<String, int[]> rects) {
        int[] rect = rects.get("grass");
        if (rect == null || rect[2] <= 0 || rect[3] <= 0) {
            check.that(false, "grass 的 rect 存在且非空，可以检查平均色");
            return;
        }
        long red = 0;
        long green = 0;
        long blue = 0;
        for (int y = 0; y < rect[3]; y++) {
            for (int x = 0; x < rect[2]; x++) {
                int rgb = image.getRGB(rect[0] + x, rect[1] + y);
                red += (rgb >>> 16) & 0xFF;
                green += (rgb >>> 8) & 0xFF;
                blue += rgb & 0xFF;
            }
        }
        int pixels = rect[2] * rect[3];
        check.that(green > red && green > blue,
                "grass 的平均绿色分量高于红、蓝（" + (green / pixels) + " > " + (red / pixels)
                        + "、" + (blue / pixels) + "）");
    }

    /** Reads a UTF-8 text resource of the plugin's own class loader, or returns null when absent. */
    private static String resourceText(String name) {
        try (InputStream in = HelloPlugin.class.getClassLoader().getResourceAsStream(name)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            return null;
        }
    }

    /** Reads a binary resource of the plugin's own class loader, or returns null when absent. */
    private static byte[] resourceBytes(String name) {
        try (InputStream in = HelloPlugin.class.getClassLoader().getResourceAsStream(name)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException failure) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 4) end to end over a socket

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

    /** Casts a parsed JSON value to a string keyed object, or fails with a readable reason. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value, String what) {
        if (value instanceof Map<?, ?>) {
            return (Map<String, Object>) value;
        }
        throw new IllegalArgumentException(what + " is not a JSON object");
    }

    /** The value of a parsed JSON number, or null when the member is absent or not a number. */
    private static Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    /** Reads a {@code [x, y, w, h]} array, or returns null when it is not four numbers. */
    private static int[] rect(Object value) {
        if (!(value instanceof List<?> values) || values.size() != 4) {
            return null;
        }
        int[] rect = new int[4];
        for (int index = 0; index < 4; index++) {
            if (!(values.get(index) instanceof Number number)) {
                return null;
            }
            rect[index] = number.intValue();
        }
        return rect;
    }

    /**
     * A minimal JSON reader, covering exactly the value shapes a manifest can contain.
     *
     * <p>The module builds offline with no third-party library, and the server's own manifest scanner is
     * package private, so the check parses the document itself: objects become {@link LinkedHashMap}s,
     * arrays {@link List}s and numbers {@link Long}s or {@link Double}s. A malformed document throws
     * {@link IllegalArgumentException} with a short reason, which the caller reports as one failed check.
     */
    private static final class Json {

        private final String text;
        private int index;

        private Json(String text) {
            this.text = text;
        }

        /** Parses one complete JSON value, rejecting trailing text. */
        static Object parse(String text) {
            Json reader = new Json(text);
            Object value = reader.readValue();
            reader.skipWhitespace();
            if (reader.index != text.length()) {
                throw new IllegalArgumentException("trailing text at position " + reader.index);
            }
            return value;
        }

        private Object readValue() {
            skipWhitespace();
            if (index >= text.length()) {
                throw new IllegalArgumentException("a value is missing");
            }
            return switch (text.charAt(index)) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> readNumber();
            };
        }

        private Object readLiteral(String literal, Object value) {
            if (!text.startsWith(literal, index)) {
                throw new IllegalArgumentException("expected '" + literal + "' at position " + index);
            }
            index += literal.length();
            return value;
        }

        private Map<String, Object> readObject() {
            Map<String, Object> members = new LinkedHashMap<>();
            index++;
            skipWhitespace();
            if (peek('}')) {
                index++;
                return members;
            }
            while (true) {
                skipWhitespace();
                if (!peek('"')) {
                    throw new IllegalArgumentException("a member name is not a string at position "
                            + index);
                }
                String name = readString();
                skipWhitespace();
                if (!peek(':')) {
                    throw new IllegalArgumentException("member '" + name + "' has no ':'");
                }
                index++;
                members.put(name, readValue());
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return members;
                }
                if (!peek(',')) {
                    throw new IllegalArgumentException("expected ',' or '}' at position " + index);
                }
                index++;
            }
        }

        private List<Object> readArray() {
            List<Object> values = new ArrayList<>();
            index++;
            skipWhitespace();
            if (peek(']')) {
                index++;
                return values;
            }
            while (true) {
                values.add(readValue());
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    return values;
                }
                if (!peek(',')) {
                    throw new IllegalArgumentException("expected ',' or ']' at position " + index);
                }
                index++;
            }
        }

        private String readString() {
            index++;
            StringBuilder value = new StringBuilder();
            while (index < text.length()) {
                char character = text.charAt(index++);
                if (character == '"') {
                    return value.toString();
                }
                if (character != '\\') {
                    value.append(character);
                    continue;
                }
                if (index >= text.length()) {
                    break;
                }
                char escape = text.charAt(index++);
                switch (escape) {
                    case '"', '\\', '/' -> value.append(escape);
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> value.append(readUnicode());
                    default -> throw new IllegalArgumentException("unknown escape '\\" + escape + "'");
                }
            }
            throw new IllegalArgumentException("a string is never closed");
        }

        private char readUnicode() {
            if (index + 4 > text.length()) {
                throw new IllegalArgumentException("a '\\u' escape is truncated");
            }
            int code = 0;
            for (int digit = 0; digit < 4; digit++) {
                int hex = Character.digit(text.charAt(index++), 16);
                if (hex < 0) {
                    throw new IllegalArgumentException("a '\\u' escape is not hexadecimal");
                }
                code = (code << 4) | hex;
            }
            return (char) code;
        }

        private Number readNumber() {
            int start = index;
            while (index < text.length() && "+-0123456789.eE".indexOf(text.charAt(index)) >= 0) {
                index++;
            }
            String number = text.substring(start, index);
            if (number.isEmpty()) {
                throw new IllegalArgumentException("a value is not JSON at position " + start);
            }
            try {
                boolean floating = number.indexOf('.') >= 0 || number.indexOf('e') >= 0
                        || number.indexOf('E') >= 0;
                return floating ? Double.valueOf(number) : Long.valueOf(number);
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("bad number '" + number + "'");
            }
        }

        private void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        private boolean peek(char expected) {
            return index < text.length() && text.charAt(index) == expected;
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
