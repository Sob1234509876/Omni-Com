package io.github.sob1234509876.oc.server.check;

import io.github.sob1234509876.oc.server.plugin.Plugin;
import io.github.sob1234509876.oc.server.plugin.PluginLoader;
import io.github.sob1234509876.oc.server.plugin.PluginDescriptor;
import io.github.sob1234509876.oc.server.ws.HttpWebServer;
import io.github.sob1234509876.oc.server.ws.WebSocketConfig;
import io.github.sob1234509876.oc.server.ws.WebSocketHandler;
import io.github.sob1234509876.oc.server.ws.WebSocketSession;

import javax.imageio.ImageIO;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/**
 * Self check for the plugin asset route: the merged manifest and the raw file service.
 *
 * <p>The fixture is the part of this check that matters. Five real plugin jars are compiled by the JDK's own
 * compiler, packed with their resources and loaded by a real {@link PluginLoader}, because the behaviour
 * under test - which plugin a resource belongs to - only exists at the level of real jars and real class
 * loaders. The five jars cover every branch of the merge and of the file route:
 *
 * <ol>
 *   <li>{@code a-alpha-plugin.jar} publishes a manifest and an atlas,</li>
 *   <li>{@code b-beta-plugin.jar} publishes a second manifest, so the array order can be asserted,</li>
 *   <li>{@code c-gamma-plugin.jar} has {@code assets/} but no manifest: it contributes nothing to the
 *       manifest while its files are still served, and its own {@code manifest.json} answers {@code 404}
 *       even though two other jars have one,</li>
 *   <li>{@code d-delta-plugin.jar} has no {@code assets/} at all, so every file of it is a {@code 404},</li>
 *   <li>{@code e-epsilon-plugin.jar} publishes a broken manifest: it is skipped, files still served.</li>
 * </ol>
 *
 * <p>Every assertion is made over a real socket against a real {@link HttpWebServer} on port {@code 0}.
 * The traversal probes assert the status code and that no plugin's bytes leaked into the answer; the
 * fixtures are real PNG files, so a byte exact response is a meaningful comparison rather than a string
 * equality of something the server could have generated.
 *
 * <p>Descriptions are Chinese, matching the rest of the repository's checks, while the code and Javadoc stay
 * English.
 */
public final class PluginAssetsCheck {

    /** The id, class and jar name of each fixture plugin, in the load order the jar names produce. */
    private static final List<Fixture> FIXTURES = List.of(
            new Fixture("oc-asset-alpha", "ocassets.alpha", "AlphaPlugin", "a-alpha-plugin.jar"),
            new Fixture("oc-asset-beta", "ocassets.beta", "BetaPlugin", "b-beta-plugin.jar"),
            new Fixture("oc-asset-gamma", "ocassets.gamma", "GammaPlugin", "c-gamma-plugin.jar"),
            new Fixture("oc-asset-delta", "ocassets.delta", "DeltaPlugin", "d-delta-plugin.jar"),
            new Fixture("oc-asset-epsilon", "ocassets.epsilon", "EpsilonPlugin", "e-epsilon-plugin.jar"));

    /** The manifest of the first fixture; compact on purpose, so its raw frame tables are predictable. */
    private static final String ALPHA_MANIFEST = "{\"image\":\"atlas.png\",\"tilePixels\":32,"
            + "\"tiles\":{\"grass\":[0,0,32,32],\"water\":[32,0,32,32]},"
            + "\"entities\":{\"worker\":{\"frames\":[[0,32,32,48],[32,32,32,48]],\"fps\":6,"
            + "\"anchor\":[0.5,0.75],\"scale\":1}}}";

    /** The manifest of the second fixture, which must follow the first in the merged array. */
    private static final String BETA_MANIFEST = "{\"image\":\"atlas.png\",\"tilePixels\":16,"
            + "\"tiles\":{\"sand\":[0,0,16,16]},"
            + "\"entities\":{\"soldier\":{\"frames\":[[0,16,16,24]]}}}";

    /** A manifest with a path in {@code image}, which the route must refuse to publish. */
    private static final String BROKEN_MANIFEST = "{\"image\":\"../secret.png\","
            + "\"tiles\":{\"grass\":[0,0,16,16]}}";

    /** The exact merged manifest the two publishing fixtures must produce, order and rewrite included. */
    private static final String EXPECTED_MANIFEST = "["
            + "{\"plugin\":\"oc-asset-alpha\","
            + "\"atlasUrl\":\"/plugin-assets/oc-asset-alpha/atlas.png\","
            + "\"tilePixels\":32,"
            + "\"tiles\":{\"grass\":[0,0,32,32],\"water\":[32,0,32,32]},"
            + "\"entities\":{\"worker\":{\"frames\":[[0,32,32,48],[32,32,32,48]],\"fps\":6,"
            + "\"anchor\":[0.5,0.75],\"scale\":1}}}"
            + ","
            + "{\"plugin\":\"oc-asset-beta\","
            + "\"atlasUrl\":\"/plugin-assets/oc-asset-beta/atlas.png\","
            + "\"tilePixels\":16,"
            + "\"tiles\":{\"sand\":[0,0,16,16]},"
            + "\"entities\":{\"soldier\":{\"frames\":[[0,16,16,24]]}}}"
            + "]";

    /** The content type of the merged manifest, which differs from what {@code MimeTypes} gives a file. */
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    /** The cache policy every response of the route is required to send. */
    private static final String NO_CACHE = "no-cache";

    private PluginAssetsCheck() {
    }

    /**
     * Runs every plugin asset assertion.
     *
     * @param check the assertion sink, which reports the summary and the exit code
     */
    public static void run(Check check) {
        check.section("插件资源路由");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        check.that(compiler != null, "运行时能拿到 JDK 编译器（ToolProvider.getSystemJavaCompiler()）");
        String apiClasspath = pluginApiClasspath();
        if (compiler == null || apiClasspath.isEmpty()) {
            check.note("没有编译器或服务器 API，插件资源路由的检查被跳过");
            return;
        }
        Path root;
        try {
            root = Files.createTempDirectory("oc-plugin-assets-check-");
        } catch (IOException failure) {
            check.that(false, "无法创建临时目录: " + failure);
            return;
        }
        try {
            runAll(check, compiler, root, apiClasspath);
        } catch (IOException | RuntimeException failure) {
            check.that(false, "插件资源路由检查无法完成: " + failure);
        } finally {
            deleteRecursively(root);
        }
    }

    private static void runAll(Check check, JavaCompiler compiler, Path root, String apiClasspath)
            throws IOException {
        Path plugins = root.resolve("plugins");
        Path classes = compilePlugins(check, compiler, root, apiClasspath);
        byte[] alphaAtlas = png(64, 48, 0xFF2F5DA8);
        byte[] betaAtlas = png(32, 32, 0xFFD8C88A);
        byte[] gammaAtlas = png(8, 8, 0xFF5FA84F);
        byte[] epsilonAtlas = png(8, 8, 0xFF5A5A5A);
        byte[] alphaNote = "{\"note\":\"alpha fixture\"}".getBytes(StandardCharsets.UTF_8);
        byte[] rawBlob = new byte[] {0, 1, 2, 3, (byte) 0xFF, (byte) 0x80};

        writePluginJar(plugins, FIXTURES.get(0), classes,
                entry("assets/manifest.json", text(ALPHA_MANIFEST)),
                entry("assets/atlas.png", alphaAtlas),
                entry("assets/notes.json", alphaNote),
                entry("assets/raw.bin", rawBlob),
                entry("assets/sub/inside.txt", text("inside the assets directory")));
        writePluginJar(plugins, FIXTURES.get(1), classes,
                entry("assets/manifest.json", text(BETA_MANIFEST)),
                entry("assets/atlas.png", betaAtlas));
        writePluginJar(plugins, FIXTURES.get(2), classes,
                entry("assets/only.png", gammaAtlas));
        writePluginJar(plugins, FIXTURES.get(3), classes);
        writePluginJar(plugins, FIXTURES.get(4), classes,
                entry("assets/manifest.json", text(BROKEN_MANIFEST)),
                entry("assets/atlas.png", epsilonAtlas));

        PluginLoader loader = new PluginLoader(plugins, "9.9-assetcheck", root.resolve("data"));
        List<Plugin> loaded = loader.loadAll();
        check.equal(5, loaded.size(), "五个样例插件全部加载");
        check.equal(List.of("oc-asset-alpha", "oc-asset-beta", "oc-asset-gamma", "oc-asset-delta",
                "oc-asset-epsilon"), List.copyOf(loader.classLoadersByPlugin().keySet()),
                "classLoadersByPlugin() 按加载顺序给出每个插件的类加载器");
        check.that(loader.classLoadersByPlugin().get("oc-asset-alpha")
                        != loader.classLoadersByPlugin().get("oc-asset-beta"),
                "两个插件的类加载器不是同一个对象");
        check.that(loader.plugin("oc-asset-alpha")
                        .map(plugin -> plugin.getClass().getClassLoader()
                                == loader.classLoadersByPlugin().get("oc-asset-alpha"))
                        .orElse(false),
                "映射里的加载器就是该插件自己的加载器");
        check.that(throwsUnsupported(() -> loader.classLoadersByPlugin().put("x", null)),
                "classLoadersByPlugin() 是只读的");

        WebSocketConfig config = new WebSocketConfig(0,
                InetAddress.getLoopbackAddress().getHostAddress(), "/ws",
                root.resolve("web-client").toString(), 64 * 1024, 1024 * 1024, 10_000, List.of());
        HttpWebServer server = new HttpWebServer(config, NoopHandler.INSTANCE, loader);
        try {
            server.start();
            int port = server.port();
            check.note("插件资源检查的服务器绑定在 127.0.0.1:" + port);
            mergedManifest(check, port, alphaAtlas, betaAtlas);
            fileService(check, port, alphaAtlas, betaAtlas, gammaAtlas, alphaNote, rawBlob);
            missingResources(check, port);
            traversalAndIllegalPaths(check, port);
            methods(check, port);
        } finally {
            server.stop();
        }

        check.section("空插件目录");
        emptyManifest(check, root);
        loader.unloadAll();
        check.that(loader.classLoadersByPlugin().isEmpty(), "unloadAll() 之后映射被清空，插件资源不再可服务");
    }

    // ------------------------------------------------------------------ 1) the merged manifest

    private static void mergedManifest(Check check, int port, byte[] alphaAtlas, byte[] betaAtlas)
            throws IOException {
        Response manifest = request(port, "GET", "/plugin-assets/manifest.json");
        check.equal(200, manifest.status(), "GET /plugin-assets/manifest.json 返回 200");
        check.equal(JSON_CONTENT_TYPE, manifest.header("content-type"), "合并清单的 MIME 类型带 charset");
        check.equal(NO_CACHE, manifest.header("cache-control"), "合并清单使用 no-cache");
        check.equal(Integer.toString(manifest.body().length), manifest.header("content-length"),
                "合并清单带正确的 Content-Length");
        check.equal(EXPECTED_MANIFEST, manifest.text(),
                "合并清单是数组：插件顺序、atlasUrl 改写与两个帧表都逐字节正确");
        check.that(manifest.text().startsWith("[") && manifest.text().endsWith("]"),
                "合并清单是 JSON 数组");
        check.that(!manifest.text().contains("\"image\""), "改写后的清单里不再有 image 字段");
        check.that(manifest.text().indexOf("oc-asset-alpha") < manifest.text().indexOf("oc-asset-beta"),
                "数组顺序就是插件加载顺序");
        check.that(!manifest.text().contains("oc-asset-gamma"),
                "没有清单的插件不贡献任何条目");
        check.that(!manifest.text().contains("oc-asset-delta"),
                "没有 assets/ 的插件不贡献任何条目");
        check.that(!manifest.text().contains("oc-asset-epsilon"),
                "清单不可用的插件被跳过");

        Response head = request(port, "HEAD", "/plugin-assets/manifest.json");
        check.equal(200, head.status(), "HEAD 清单返回 200");
        check.equal(manifest.header("content-length"), head.header("content-length"),
                "HEAD 清单的 Content-Length 与 GET 相同");
        check.equal(0, head.body().length, "HEAD 清单不返回响应体");

        check.that(!Arrays.equals(alphaAtlas, betaAtlas),
                "两个插件的图集内容不同，文件服务可以区分它们");
    }

    // ------------------------------------------------------------------ 2) raw files

    private static void fileService(Check check, int port, byte[] alphaAtlas, byte[] betaAtlas,
            byte[] gammaAtlas, byte[] alphaNote, byte[] rawBlob) throws IOException {
        Response atlas = request(port, "GET", "/plugin-assets/oc-asset-alpha/atlas.png");
        check.equal(200, atlas.status(), "GET 插件的 atlas.png 返回 200");
        check.that(Arrays.equals(alphaAtlas, atlas.body()), "插件图片逐字节一致");
        check.equal("image/png", atlas.header("content-type"), "PNG 的 MIME 类型来自 MimeTypes");
        check.equal(NO_CACHE, atlas.header("cache-control"), "插件文件使用 no-cache");
        check.equal(Integer.toString(alphaAtlas.length), atlas.header("content-length"),
                "插件文件带精确的 Content-Length");

        Response beta = request(port, "GET", "/plugin-assets/oc-asset-beta/atlas.png");
        check.equal(200, beta.status(), "第二个插件的 atlas.png 返回 200");
        check.that(Arrays.equals(betaAtlas, beta.body()),
                "每个插件读到的是自己 jar 里的图片，不是邻居的");

        Response note = request(port, "GET", "/plugin-assets/oc-asset-alpha/notes.json");
        check.equal(200, note.status(), "插件里的 JSON 文件可以读取");
        check.that(Arrays.equals(alphaNote, note.body()), "JSON 文件逐字节一致");
        check.equal("application/json", note.header("content-type"), "JSON 的 MIME 类型来自 MimeTypes");

        Response blob = request(port, "GET", "/plugin-assets/oc-asset-alpha/raw.bin");
        check.equal(200, blob.status(), "未知扩展名的插件文件可以读取");
        check.that(Arrays.equals(rawBlob, blob.body()), "二进制文件逐字节一致");
        check.equal("application/octet-stream", blob.header("content-type"),
                "未知扩展名使用 application/octet-stream");

        Response nested = request(port, "GET", "/plugin-assets/oc-asset-alpha/sub/inside.txt");
        check.equal(200, nested.status(), "assets/ 子目录里的文件可以读取");
        check.equal("inside the assets directory", nested.text(), "子目录文件内容正确");
        check.equal("application/octet-stream", nested.header("content-type"),
                "MimeTypes 不认识的扩展名同样按 octet-stream 处理");

        Response head = request(port, "HEAD", "/plugin-assets/oc-asset-alpha/atlas.png");
        check.equal(200, head.status(), "HEAD 插件文件返回 200");
        check.equal(atlas.header("content-length"), head.header("content-length"),
                "HEAD 插件文件的 Content-Length 与 GET 相同");
        check.equal("image/png", head.header("content-type"), "HEAD 插件文件仍带 MIME 类型");
        check.equal(0, head.body().length, "HEAD 插件文件不返回响应体");

        Response gamma = request(port, "GET", "/plugin-assets/oc-asset-gamma/only.png");
        check.equal(200, gamma.status(), "没有清单但有 assets/ 的插件仍然可以服务文件");
        check.that(Arrays.equals(gammaAtlas, gamma.body()), "该插件的文件逐字节一致");

        Response gammaManifest = request(port, "GET", "/plugin-assets/oc-asset-gamma/manifest.json");
        check.equal(404, gammaManifest.status(),
                "没有清单的插件不会从邻居 jar 里借到 manifest.json");
        check.that(gammaManifest.header("content-length") != null, "404 也带 Content-Length");
    }

    // ------------------------------------------------------------------ 3) missing resources

    private static void missingResources(Check check, int port) throws IOException {
        Response unknownPlugin = request(port, "GET", "/plugin-assets/no-such-plugin/atlas.png");
        check.equal(404, unknownPlugin.status(), "未知插件返回 404");

        Response unknownFile = request(port, "GET", "/plugin-assets/oc-asset-alpha/missing.png");
        check.equal(404, unknownFile.status(), "插件的缺失文件返回 404");

        Response noAssets = request(port, "GET", "/plugin-assets/oc-asset-delta/anything.png");
        check.equal(404, noAssets.status(), "没有 assets/ 的插件返回 404");

        Response epsilonFile = request(port, "GET", "/plugin-assets/oc-asset-epsilon/atlas.png");
        check.equal(200, epsilonFile.status(), "清单坏掉的插件仍然可以服务自己的文件");

        Response bare = request(port, "GET", "/plugin-assets");
        check.equal(404, bare.status(), "只有前缀的路径返回 404");
        Response slash = request(port, "GET", "/plugin-assets/");
        check.equal(404, slash.status(), "只有前缀和斜杠的路径返回 404");
        Response noFile = request(port, "GET", "/plugin-assets/oc-asset-alpha");
        check.equal(404, noFile.status(), "只给插件 id 不给文件名返回 404");
    }

    // ------------------------------------------------------------------ 4) traversal and illegal paths

    private static void traversalAndIllegalPaths(Check check, int port) throws IOException {
        List<String> illegal = List.of(
                "/plugin-assets/../secret.txt",
                "/plugin-assets/%2e%2e/secret.txt",
                "/plugin-assets/oc-asset-alpha/%2e%2e/%2e%2e/secret.txt",
                "/plugin-assets/oc-asset-alpha/..%5csecret.txt",
                "/plugin-assets/oc-asset-alpha/%00.png",
                "/plugin-assets/C:/Windows/win.ini",
                "/plugin-assets//etc/passwd",
                "/plugin-assets/oc-asset-alpha/a..b.png");
        for (String target : illegal) {
            Response response = request(port, "GET", target);
            check.equal(400, response.status(), "非法路径被拒绝: " + target);
            check.that(!response.text().contains("outside-the-plugin"),
                    "非法路径没有读到插件之外的字节: " + target);
        }

        Response directory = request(port, "GET", "/plugin-assets/oc-asset-alpha/.");
        check.equal(403, directory.status(),
                "规范化后落到 assets/ 目录本身的路径返回 403: /plugin-assets/oc-asset-alpha/.");
        Response emptyFile = request(port, "GET", "/plugin-assets/oc-asset-alpha/");
        check.equal(403, emptyFile.status(),
                "规范化后没有文件名的路径返回 403: /plugin-assets/oc-asset-alpha/");

        Response stillWorks = request(port, "GET", "/plugin-assets/oc-asset-alpha/atlas.png");
        check.equal(200, stillWorks.status(), "非法路径尝试之后正常插件文件仍然可读");
    }

    // ------------------------------------------------------------------ 5) methods

    private static void methods(Check check, int port) throws IOException {
        Response post = request(port, "POST", "/plugin-assets/manifest.json");
        check.equal(405, post.status(), "POST 插件资源得到 405");
        check.equal("GET, HEAD", post.header("allow"), "405 说明允许的方法");

        Response put = request(port, "PUT", "/plugin-assets/oc-asset-alpha/atlas.png");
        check.equal(405, put.status(), "PUT 插件资源得到 405");
    }

    // ------------------------------------------------------------------ 6) no plugins at all

    private static void emptyManifest(Check check, Path root) throws IOException {
        Path empty = root.resolve("empty-plugins");
        Files.createDirectories(empty);
        PluginLoader loader = new PluginLoader(empty, "9.9-assetcheck", root.resolve("empty-data"));
        loader.loadAll();
        WebSocketConfig config = new WebSocketConfig(0,
                InetAddress.getLoopbackAddress().getHostAddress(), "/ws",
                root.resolve("web-client").toString(), 64 * 1024, 1024 * 1024, 10_000, List.of());
        HttpWebServer server = new HttpWebServer(config, NoopHandler.INSTANCE, loader);
        try {
            server.start();
            Response manifest = request(server.port(), "GET", "/plugin-assets/manifest.json");
            check.equal(200, manifest.status(), "没有插件时清单仍然返回 200，而不是 404");
            check.equal("[]", manifest.text(), "没有插件时清单是空数组");
            check.equal(JSON_CONTENT_TYPE, manifest.header("content-type"), "空清单的 MIME 类型不变");
            check.equal(NO_CACHE, manifest.header("cache-control"), "空清单同样使用 no-cache");
        } finally {
            server.stop();
            loader.unloadAll();
        }
    }

    // ------------------------------------------------------------------ fixtures

    /** Compiles one tiny plugin class per fixture id, in one compiler call. */
    private static Path compilePlugins(Check check, JavaCompiler compiler, Path root, String apiClasspath)
            throws IOException {
        Path sourceRoot = root.resolve("sources");
        Path classesRoot = root.resolve("classes");
        Map<String, String> sources = new LinkedHashMap<>();
        for (Fixture fixture : FIXTURES) {
            sources.put(fixture.packageName().replace('.', '/') + "/" + fixture.className() + ".java",
                    PLUGIN_SOURCE.formatted(fixture.packageName(), fixture.className(), fixture.id()));
        }
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = sourceRoot.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue(), StandardCharsets.UTF_8);
        }
        List<File> files;
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            files = walk.filter(path -> path.toString().endsWith(".java")).map(Path::toFile).toList();
        }
        Files.createDirectories(classesRoot);
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(collector, null,
                StandardCharsets.UTF_8)) {
            List<String> options = List.of("-classpath", apiClasspath, "-d", classesRoot.toString(),
                    "-encoding", "UTF-8", "-proc:none");
            Boolean compiled = compiler.getTask(null, manager, collector, options, null,
                    manager.getJavaFileObjectsFromFiles(files)).call();
            boolean ok = Boolean.TRUE.equals(compiled);
            check.that(ok, "样例插件源码可以编译");
            if (!ok) {
                for (Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
                    check.note(diagnostic.getKind() + ": " + diagnostic.getMessage(Locale.ROOT));
                }
            }
            return classesRoot;
        }
    }

    /** Writes one fixture jar: its class, its descriptor and whatever resources the fixture declares. */
    private static void writePluginJar(Path plugins, Fixture fixture, Path classes, Entry... resources)
            throws IOException {
        Path jar = plugins.resolve(fixture.jarName());
        Files.createDirectories(plugins);
        List<Entry> entries = new ArrayList<>();
        String mainClass = fixture.packageName() + "." + fixture.className();
        entries.add(classEntry(classes, mainClass));
        entries.add(descriptor(fixture.id(), mainClass));
        entries.addAll(List.of(resources));
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Entry entry : entries) {
                out.putNextEntry(new JarEntry(entry.name()));
                out.write(entry.bytes());
                out.closeEntry();
            }
        }
    }

    /** A real PNG of one solid colour, so the file route serves decodable image bytes. */
    private static byte[] png(int width, int height, int argb) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, argb);
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    private static Entry descriptor(String id, String mainClass) {
        return entry(PluginDescriptor.RESOURCE,
                text("id=" + id + "\nversion=1.0\nmain=" + mainClass + "\n"
                        + "description=asset route fixture\n"));
    }

    private static Entry classEntry(Path classesRoot, String binaryName) throws IOException {
        String resource = binaryName.replace('.', '/') + ".class";
        return entry(resource, Files.readAllBytes(classesRoot.resolve(resource)));
    }

    private static Entry entry(String name, byte[] bytes) {
        return new Entry(name, bytes);
    }

    private static byte[] text(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private record Entry(String name, byte[] bytes) {
    }

    private record Fixture(String id, String packageName, String className, String jarName) {
    }

    /** The classpath that holds the plugin API, so the generated fixture can be compiled against it. */
    private static String pluginApiClasspath() {
        try {
            CodeSource codeSource = Plugin.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                return Path.of(codeSource.getLocation().toURI()).toString();
            }
        } catch (URISyntaxException | RuntimeException unusable) {
            // Fall back to the runtime class path below, which is what a modular run reports.
        }
        return System.getProperty("java.class.path", "");
    }

    private static boolean throwsUnsupported(Runnable action) {
        try {
            action.run();
            return false;
        } catch (UnsupportedOperationException expected) {
            return true;
        }
    }

    // ------------------------------------------------------------------ HTTP client

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
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // A leftover temp file cannot fail the checks; the operating system cleans %TEMP%.
                }
            }
        } catch (IOException ignored) {
            // Cleanup is best effort and never an assertion.
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

    /** The asset route never upgrades; this makes that explicit. */
    private static final class NoopHandler implements WebSocketHandler {

        private static final NoopHandler INSTANCE = new NoopHandler();

        @Override
        public void onOpen(WebSocketSession session) {
            // No WebSocket is opened by this check.
        }

        @Override
        public void onMessage(WebSocketSession session, String text) {
            // No WebSocket is opened by this check.
        }

        @Override
        public void onClose(WebSocketSession session, int code, String reason) {
            // No WebSocket is opened by this check.
        }
    }

    private static final String PLUGIN_SOURCE = """
            package %s;

            import io.github.sob1234509876.oc.server.plugin.Plugin;

            /** Generated by the self check; its only job is to give the asset route a plugin id. */
            public class %s implements Plugin {

                @Override
                public String name() {
                    return "%s";
                }

                @Override
                public String version() {
                    return "1.0";
                }
            }
            """;
}
