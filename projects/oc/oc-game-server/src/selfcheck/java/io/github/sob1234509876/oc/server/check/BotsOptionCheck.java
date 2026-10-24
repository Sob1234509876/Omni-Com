package io.github.sob1234509876.oc.server.check;

import io.github.sob1234509876.oc.server.OcGameServer;
import io.github.sob1234509876.oc.server.net.ServerConfig;
import io.github.sob1234509876.oc.server.plugin.Plugin;
import io.github.sob1234509876.oc.server.plugin.PluginContext;
import io.github.sob1234509876.oc.server.plugin.PluginDescriptor;
import io.github.sob1234509876.oc.server.plugin.PluginLoader;
import io.github.sob1234509876.oc.server.plugin.RequestRegistry;
import io.github.sob1234509876.oc.server.ws.WebSocketConfig;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/**
 * Self check of the {@code --bots} server option and the plugin option seam it travels through.
 *
 * <p>The classic module owns the bots themselves; this module owns only two things about them: the command
 * line value and the way a plugin learns it. Both are asserted here, on the real classes:
 *
 * <ol>
 *   <li>{@link OcGameServer#parseBotsOption(String)} accepts exactly {@code 0..6}, including the whitespace
 *       every other numeric server option accepts, and rejects everything else;</li>
 *   <li>the value reaches a plugin through {@link PluginContext#options()}: directly on a hand built
 *       context, and end to end through a real {@link PluginLoader} loading a jar compiled in this check and
 *       reading the map in {@link Plugin#onLoad(PluginContext)}.</li>
 * </ol>
 *
 * <p>Descriptions are Chinese, matching the rest of the repository's checks, while the code and its Javadoc
 * stay English. The generated plugin exists only to make the loader leg observable, exactly like the hook
 * plugin in {@link WorldTickHookCheck}.
 */
public final class BotsOptionCheck {

    private BotsOptionCheck() {
    }

    /**
     * Runs every {@code --bots} check.
     *
     * @param check the assertion sink, which reports the summary and the exit code
     */
    public static void run(Check check) {
        check.section("机器人选项");
        parsing(check);
        optionSeam(check);
        serverWiring(check);
        loaderSeam(check);
    }

    /** The documented range and its boundaries, plus the invalid values that must be refused. */
    private static void parsing(Check check) {
        check.equal("bots", OcGameServer.OPTION_BOTS, "插件选项名是 bots");
        check.equal(1, OcGameServer.DEFAULT_BOTS, "--bots 的默认值是 1（历史单机器人）");
        check.equal(0, OcGameServer.parseBotsOption("0"), "0 是合法值：没有机器人的世界");
        check.equal(1, OcGameServer.parseBotsOption("1"), "1 是合法值：历史单机器人");
        check.equal(6, OcGameServer.parseBotsOption("6"), "6 是合法上界");
        check.equal(4, OcGameServer.parseBotsOption(" 4 "), "--bots 接受前后空白");
        check.that(rejects(() -> OcGameServer.parseBotsOption("-1"))
                        && rejects(() -> OcGameServer.parseBotsOption("7"))
                        && rejects(() -> OcGameServer.parseBotsOption("many"))
                        && rejects(() -> OcGameServer.parseBotsOption("")),
                "--bots 拒绝负数、7、非数字与空串");
    }

    /** The context seam: a context without options reports an empty map, one with options reports them. */
    private static void optionSeam(Check check) {
        PluginContext empty = new PluginContext(Path.of("plugins"), Path.of("data"), "9.9-botscheck",
                System.getLogger("bots-check"));
        check.equal(Map.of(), empty.options(), "四参数上下文没有服务器选项");

        PluginContext configured = new PluginContext(Path.of("plugins"), Path.of("data"), "9.9-botscheck",
                System.getLogger("bots-check"), new RequestRegistry(), new ArrayList<>(), new ArrayList<>(),
                Map.of(OcGameServer.OPTION_BOTS, "3"));
        check.equal(Map.of(OcGameServer.OPTION_BOTS, "3"), configured.options(),
                "八参数上下文原样报告服务器选项");
        check.that(throwsUnsupported(() -> configured.options().put("x", "y")),
                "插件拿到的选项映射是只读的");
    }

    /** The server stores the validated count and the eight argument constructor keeps the old default. */
    private static void serverWiring(Check check) {
        WebSocketConfig web = WebSocketConfig.defaults().withPort(0);
        OcGameServer defaulted = new OcGameServer(ServerConfig.defaults(), Path.of("plugins"), Path.of("data"),
                60, web, false, 1L, 4096);
        check.equal(1, defaulted.bots(), "八参数服务器默认一个机器人");
        OcGameServer configured = new OcGameServer(ServerConfig.defaults(), Path.of("plugins"),
                Path.of("data"), 60, web, false, 1L, 4096, 3);
        check.equal(3, configured.bots(), "九参数服务器记住了 --bots 3");
        check.that(rejects(() -> new OcGameServer(ServerConfig.defaults(), Path.of("plugins"), Path.of("data"),
                        60, web, false, 1L, 4096, 7)),
                "服务器构造函数拒绝 7 个机器人");
    }

    /** A real plugin jar reads the option map in onLoad through a real loader. */
    private static void loaderSeam(Check check) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        check.that(compiler != null, "自检运行时带有 JDK 编译器（用于生成读取选项的插件）");
        if (compiler == null) {
            return;
        }
        Path root = null;
        try {
            root = Files.createTempDirectory("oc-bots-option-check");
            Path plugins = Files.createDirectories(root.resolve("plugins"));
            Path classes = compile(check, compiler, root);
            if (classes == null) {
                return;
            }
            writePluginJar(plugins.resolve("options-plugin.jar"), classes);

            Map<String, String> options = Map.of(OcGameServer.OPTION_BOTS, "3");
            PluginLoader loader = new PluginLoader(plugins, "9.9-botscheck", root.resolve("data"),
                    new RequestRegistry(), options);
            check.equal(options, loader.options(), "加载器保存了服务器选项");
            List<Plugin> loaded = loader.loadAll();
            check.equal(1, loaded.size(), "选项插件被真实类加载器加载");
            check.that(loader.problems().isEmpty(), "选项插件没有报告问题: " + loader.problems());
            check.equal(options, recordedOptions(loaded.isEmpty() ? null : loaded.get(0)),
                    "插件在 onLoad 里通过 PluginContext.options() 读到了 bots=3");

            loader.unloadAll();
            check.equal(0, loader.plugins().size(), "unloadAll() 清空了选项插件");
        } catch (IOException failure) {
            check.that(false, "机器人选项检查无法建立临时目录或 jar: " + failure);
        } finally {
            deleteRecursively(root);
        }
    }

    /** Compiles the generated options plugin and returns its output directory, or {@code null} on failure. */
    private static Path compile(Check check, JavaCompiler compiler, Path root) throws IOException {
        Path source = root.resolve("src/ocplugin/options/OptionsPlugin.java");
        Path classes = root.resolve("classes");
        Files.createDirectories(source.getParent());
        Files.writeString(source, OPTIONS_PLUGIN_SOURCE, StandardCharsets.UTF_8);
        Files.createDirectories(classes);
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        boolean compiled;
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(collector, null,
                StandardCharsets.UTF_8)) {
            List<String> options = List.of("-classpath", System.getProperty("java.class.path", ""),
                    "-d", classes.toString(), "-encoding", "UTF-8", "-proc:none");
            compiled = Boolean.TRUE.equals(compiler.getTask(null, manager, collector, options, null,
                    manager.getJavaFileObjects(source.toFile())).call());
        }
        check.that(compiled, "生成的选项插件可以编译");
        if (!compiled) {
            for (Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
                check.note(diagnostic.getKind() + ": " + diagnostic.getMessage(Locale.ROOT));
            }
            return null;
        }
        return classes;
    }

    /** Packs the compiled plugin plus its descriptor into a jar the loader accepts. */
    private static void writePluginJar(Path jar, Path classes) throws IOException {
        Files.createDirectories(jar.getParent());
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("ocplugin/options/OptionsPlugin.class"));
            out.write(Files.readAllBytes(classes.resolve("ocplugin/options/OptionsPlugin.class")));
            out.closeEntry();
            out.putNextEntry(new JarEntry(PluginDescriptor.RESOURCE));
            out.write(("id=options-plugin\nversion=1.0\nmain=ocplugin.options.OptionsPlugin\n"
                    + "description=reads the plugin options\n").getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }

    /**
     * Reads the generated plugin's recorded options. Reflection is needed because its class lives in a
     * loader of its own, which is the point of the exercise.
     */
    private static Map<String, String> recordedOptions(Plugin plugin) {
        if (plugin == null) {
            return Map.of();
        }
        try {
            Object value = plugin.getClass().getField("OPTIONS").get(null);
            if (value instanceof Map<?, ?> recorded) {
                Map<String, String> options = new LinkedHashMap<>();
                recorded.forEach((key, member) -> options.put(String.valueOf(key), String.valueOf(member)));
                return options;
            }
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            return Map.of("<unreadable>", String.valueOf(unreadable));
        }
        return Map.of();
    }

    private static boolean rejects(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static boolean throwsUnsupported(Runnable action) {
        try {
            action.run();
            return false;
        } catch (UnsupportedOperationException expected) {
            return true;
        }
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

    /**
     * Source of the generated plugin: it copies the option map in {@code onLoad}. The static map is public
     * so the check can read it through the plugin's own loader.
     */
    private static final String OPTIONS_PLUGIN_SOURCE = """
            package ocplugin.options;

            import io.github.sob1234509876.oc.server.plugin.Plugin;
            import io.github.sob1234509876.oc.server.plugin.PluginContext;

            import java.util.LinkedHashMap;
            import java.util.Map;

            /** Generated by the self check; it is not part of the repository. */
            public final class OptionsPlugin implements Plugin {

                /** What the server passed through PluginContext.options(). */
                public static final Map<String, String> OPTIONS = new LinkedHashMap<>();

                @Override
                public String name() {
                    return "options-plugin";
                }

                @Override
                public String version() {
                    return "1.0";
                }

                @Override
                public void onLoad(PluginContext context) {
                    OPTIONS.putAll(context.options());
                }
            }
            """;
}
