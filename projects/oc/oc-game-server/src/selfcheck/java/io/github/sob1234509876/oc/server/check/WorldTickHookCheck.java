package io.github.sob1234509876.oc.server.check;

import io.github.sob1234509876.oc.rts.content.ScenarioWorldContent;
import io.github.sob1234509876.oc.server.OcGameServer;
import io.github.sob1234509876.oc.server.plugin.Plugin;
import io.github.sob1234509876.oc.server.plugin.PluginDescriptor;
import io.github.sob1234509876.oc.server.plugin.PluginLoader;
import io.github.sob1234509876.oc.server.world.WorldHost;
import io.github.sob1234509876.oc.server.world.WorldTickHook;

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
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/**
 * Self check of the plugin world tick hook: when a hook runs, in which order, and what {@code unloadAll}
 * does to the hooks a plugin registered.
 *
 * <p>The three properties worth protecting cannot be observed from a plugin's own code - the hook only
 * sees a world and a number - so they are asserted from the outside, on the host that runs the hooks:
 *
 * <ol>
 *   <li>a hook runs <strong>after</strong> the engine tick, so the world's own clock already reads the
 *       tick number the hook is handed;</li>
 *   <li>two hooks run in <strong>registration order</strong>, every step, because both must see the same
 *       world in a known sequence;</li>
 *   <li>{@link PluginLoader#unloadAll()} <strong>clears</strong> the hooks as well as the content, so a
 *       disabled plugin cannot keep ticking.</li>
 * </ol>
 *
 * <p>The last one needs a real plugin: only a real {@link Plugin#onLoad} call can register a hook with the
 * loader. The check therefore compiles a tiny plugin with the JDK's own compiler and packs it into a jar,
 * exactly the way {@link PluginCheck} builds its fixtures - no test framework and no fake loader.
 *
 * <p>Descriptions are Chinese, matching the rest of the repository's checks, while the code and its
 * Javadoc stay English.
 */
public final class WorldTickHookCheck {

    private WorldTickHookCheck() {
    }

    /**
     * Runs every world tick hook check.
     *
     * @param check the assertion sink, which reports the summary and the exit code
     */
    public static void run(Check check) {
        check.section("插件世界 tick 钩子");
        hostOrder(check);
        loaderSeam(check);
    }

    /**
     * Two hand written hooks on a hand built host: they run after the tick with the world's number, and in
     * registration order.
     */
    private static void hostOrder(Check check) {
        List<String> order = new ArrayList<>();
        List<Long> ticks = new ArrayList<>();
        List<Long> worldClock = new ArrayList<>();
        WorldTickHook first = (world, tick) -> {
            order.add("first");
            ticks.add(tick);
            worldClock.add(world.tickNumber());
        };
        WorldTickHook second = (world, tick) -> {
            order.add("second");
            ticks.add(tick);
            worldClock.add(world.tickNumber());
        };
        List<WorldTickHook> registered = List.of(first, second);
        List<WorldTickHook> selected = OcGameServer.selectWorldTickHooks(registered);
        check.equal(2, selected.size(), "服务器接受全部钩子（不是像世界内容那样只取第一份）");
        check.that(selected.get(0) == first && selected.get(1) == second, "选中的钩子保持注册顺序");
        check.equal(List.of(), OcGameServer.selectWorldTickHooks(List.of()), "没有插件注册钩子时是空列表");

        WorldHost host = new WorldHost(1L, new ScenarioWorldContent(), registered);
        try {
            check.equal(0L, host.tick(), "未 step() 时世界还在 tick 0");
            check.equal(0, order.size(), "未 step() 时没有钩子运行");
            check.equal(2, host.worldTickHooks().size(), "主机保存了两个钩子");
            check.that(host.worldTickHooks().get(0) == first && host.worldTickHooks().get(1) == second,
                    "主机按注册顺序保存两个钩子");

            host.step();
            check.equal(1L, host.tick(), "step() 让世界前进一步");
            check.equal(List.of(1L, 1L), ticks, "引擎 tick 之后两个钩子都拿到 tick 1");
            check.equal(List.of(1L, 1L), worldClock, "钩子看到的 world.tickNumber() 已经是本步的 tick");
            check.equal(List.of("first", "second"), order, "两个钩子按注册顺序运行");

            host.step();
            check.equal(2L, host.tick(), "第二次 step() 后世界在 tick 2");
            check.equal(List.of(1L, 1L, 2L, 2L), ticks, "每一步两个钩子各跑一次，tick 递增");
            check.equal(List.of("first", "second", "first", "second"), order,
                    "每一步都保持注册顺序（first 在 second 之前）");
        } finally {
            host.stop();
        }
    }

    /** A real plugin jar registers a hook through the loader, and {@code unloadAll} takes it away again. */
    private static void loaderSeam(Check check) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        check.that(compiler != null, "自检运行时带有 JDK 编译器（用于生成注册钩子的插件）");
        if (compiler == null) {
            return;
        }
        Path root = null;
        try {
            root = Files.createTempDirectory("oc-world-hook-check");
            Path plugins = Files.createDirectories(root.resolve("plugins"));
            Path classes = compile(check, compiler, root);
            if (classes == null) {
                return;
            }
            writePluginJar(plugins.resolve("hook-plugin.jar"), classes);

            PluginLoader loader = new PluginLoader(plugins, "9.9-hookcheck", root.resolve("data"));
            List<Plugin> loaded = loader.loadAll();
            check.equal(1, loaded.size(), "钩子插件被真实类加载器加载");
            check.that(loader.problems().isEmpty(), "钩子插件没有报告问题: " + loader.problems());
            check.equal(1, loader.worldTickHooks().size(), "插件在 onLoad 里注册的钩子出现在 worldTickHooks() 里");

            WorldHost host = new WorldHost(1L, new ScenarioWorldContent(), loader.worldTickHooks());
            try {
                host.step();
                check.equal(1L, host.tick(), "用插件注册的钩子建起的世界也能 step()");
                if (!loaded.isEmpty()) {
                    check.equal(List.of("1@1"), recordedTicks(loaded.get(0)),
                            "插件钩子真的在 tick 1 之后运行了一次（记录 tick@world.tickNumber()）");
                }
            } finally {
                host.stop();
            }

            loader.unloadAll();
            check.equal(0, loader.worldTickHooks().size(), "unloadAll() 清空了世界 tick 钩子");
            check.equal(0, loader.plugins().size(), "unloadAll() 同时也清空了插件列表");
        } catch (IOException failure) {
            check.that(false, "世界 tick 钩子检查无法建立临时目录或 jar: " + failure);
        } finally {
            deleteRecursively(root);
        }
    }

    /** Compiles the generated hook plugin and returns its output directory, or {@code null} on failure. */
    private static Path compile(Check check, JavaCompiler compiler, Path root) throws IOException {
        Path source = root.resolve("src/ocplugin/hook/HookPlugin.java");
        Path classes = root.resolve("classes");
        Files.createDirectories(source.getParent());
        Files.writeString(source, HOOK_PLUGIN_SOURCE, StandardCharsets.UTF_8);
        Files.createDirectories(classes);
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        boolean compiled;
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(collector, null,
                StandardCharsets.UTF_8)) {
            // The JVM already has the server, the engine and the googology module on its class path, which
            // is exactly the API the generated plugin compiles against; no extra class path is assembled.
            List<String> options = List.of("-classpath", System.getProperty("java.class.path", ""),
                    "-d", classes.toString(), "-encoding", "UTF-8", "-proc:none");
            compiled = Boolean.TRUE.equals(compiler.getTask(null, manager, collector, options, null,
                    manager.getJavaFileObjects(source.toFile())).call());
        }
        check.that(compiled, "生成的钩子插件可以编译");
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
            out.putNextEntry(new JarEntry("ocplugin/hook/HookPlugin.class"));
            out.write(Files.readAllBytes(classes.resolve("ocplugin/hook/HookPlugin.class")));
            out.closeEntry();
            out.putNextEntry(new JarEntry(PluginDescriptor.RESOURCE));
            out.write(("id=hook-plugin\nversion=1.0\nmain=ocplugin.hook.HookPlugin\n"
                    + "description=registers a world tick hook\n").getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }

    /**
     * Reads the ticks the generated plugin recorded. Reflection is needed because its class lives in a
     * loader of its own, which is the point of the exercise.
     */
    private static List<String> recordedTicks(Plugin plugin) {
        try {
            Object value = plugin.getClass().getField("TICKS").get(null);
            if (value instanceof List<?> recorded) {
                List<String> ticks = new ArrayList<>();
                for (Object entry : recorded) {
                    ticks.add(String.valueOf(entry));
                }
                return ticks;
            }
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            return List.of("<unreadable: " + unreadable + ">");
        }
        return List.of();
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
     * Source of the generated plugin: it registers one hook that records {@code tick@world.tickNumber()}.
     * The static list is public so the check can read it through the plugin's own loader.
     */
    private static final String HOOK_PLUGIN_SOURCE = """
            package ocplugin.hook;

            import io.github.sob1234509876.oc.server.plugin.Plugin;
            import io.github.sob1234509876.oc.server.plugin.PluginContext;

            import java.util.ArrayList;
            import java.util.List;

            /** Generated by the self check; it is not part of the repository. */
            public final class HookPlugin implements Plugin {

                /** One entry per hook call, as tick@worldTick. */
                public static final List<String> TICKS = new ArrayList<>();

                @Override
                public String name() {
                    return "hook-plugin";
                }

                @Override
                public String version() {
                    return "1.0";
                }

                @Override
                public void onLoad(PluginContext context) {
                    context.registerWorldTickHook(
                            (world, tick) -> TICKS.add(tick + "@" + world.tickNumber()));
                }
            }
            """;
}
