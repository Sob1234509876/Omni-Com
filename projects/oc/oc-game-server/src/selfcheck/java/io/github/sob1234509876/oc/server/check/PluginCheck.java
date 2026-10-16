package io.github.sob1234509876.oc.server.check;

import io.github.sob1234509876.oc.server.plugin.DiscoveryResult;
import io.github.sob1234509876.oc.server.plugin.Plugin;
import io.github.sob1234509876.oc.server.plugin.PluginContext;
import io.github.sob1234509876.oc.server.plugin.PluginDescriptor;
import io.github.sob1234509876.oc.server.plugin.PluginLoader;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/**
 * Self check of the plugin loader. The module has no test framework and no network, so the check builds real
 * plugin jars with the JDK's own compiler and jar writer, drops them into a temporary plugin folder and then
 * behaves exactly like the server: it loads them, calls them and shuts them down.
 *
 * <p>That is deliberately not a mock. The behaviour worth protecting - one class loader per jar, the folder
 * as the dependency mechanism, a loader that is never closed so a plugin may load a class later, a broken jar
 * that costs only itself - only exists at the level of real jars and real class loaders, and all of it would
 * disappear behind a fake.
 */
public final class PluginCheck {

    private static final String PLUGIN_API = "io.github.sob1234509876.oc.server.plugin.Plugin";

    private PluginCheck() {
    }

    /** Runs every plugin loader assertion against the given reporter. */
    public static void run(Check check) {
        check.section("插件加载器");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        check.that(compiler != null, "运行时能拿到 JDK 编译器（ToolProvider.getSystemJavaCompiler()）");
        if (compiler == null) {
            check.note("没有编译器就无法在运行时生成插件 jar，插件加载器的检查被跳过");
            return;
        }
        String apiClasspath = pluginApiClasspath();
        check.that(!apiClasspath.isEmpty(), "找得到 Plugin 接口所在的 classpath: " + apiClasspath);
        if (apiClasspath.isEmpty()) {
            check.note("找不到服务器 API 就无法编译插件，插件加载器的检查被跳过");
            return;
        }
        Path root;
        try {
            root = Files.createTempDirectory("oc-plugin-check-");
        } catch (IOException failure) {
            check.that(false, "无法创建临时目录: " + failure);
            return;
        }
        check.note("临时目录（故意保留下来，可以查看生成的源码和 jar）: " + root);
        try {
            Path demoJar = checkSinglePlugin(check, compiler, root, apiClasspath);
            checkDroppedDependency(check, compiler, root, apiClasspath);
            checkSameClassName(check, compiler, root, apiClasspath);
            checkBrokenJars(check, compiler, root, apiClasspath);
            checkExplicitDataDirectory(check, root, demoJar);
        } catch (IOException failure) {
            check.that(false, "构建插件 jar 时出错: " + failure);
        }
    }

    /**
     * One well formed jar: descriptor, service file, lifecycle, data directory and late class loading. Uses
     * the two argument constructor, so the data directory must land in the plugin folder.
     *
     * @return the jar that was built, so the explicit data directory case can reuse it
     */
    private static Path checkSinglePlugin(Check check, JavaCompiler compiler, Path root, String apiClasspath)
            throws IOException {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("ocplugin/demo/DemoPlugin.java", DEMO_SOURCE);
        sources.put("ocplugin/demo/LateHelper.java", LATE_HELPER_SOURCE);
        sources.put("ocplugin/demo/BytesHelper.java", BYTES_HELPER_SOURCE);
        Path classes = compile(check, compiler, root.resolve("single"), "示例插件的源码", apiClasspath, sources);

        Path plugins = root.resolve("single-plugins");
        Path jar = plugins.resolve("demo-plugin.jar");
        writeJar(jar,
                classEntry(classes, "ocplugin.demo.DemoPlugin"),
                classEntry(classes, "ocplugin.demo.LateHelper"),
                // The class the plugin defines itself travels as raw bytes under a name no scan looks at, so
                // it cannot have been loaded before the plugin asks for it.
                new Entry("ocplugin/demo/BytesHelper.class.bin",
                        Files.readAllBytes(classes.resolve("ocplugin/demo/BytesHelper.class"))),
                // Nothing reads this one until after unloadAll.
                text("ocplugin/demo/still-open.txt", "still open"),
                descriptor("demo", "1.2.3", "ocplugin.demo.DemoPlugin", "self check plugin"),
                service("ocplugin.demo.DemoPlugin", "ocplugin.demo.DemoPlugin"));

        PluginLoader loader = new PluginLoader(plugins, "9.9-selfcheck");
        List<Plugin> loaded = loader.loadAll();

        check.equal(1, loader.discoveries().size(), "插件目录里的 jar 被发现");
        DiscoveryResult discovery = loader.discoveries().get(0);
        check.equal(jar.toAbsolutePath().normalize(), discovery.jar(), "DiscoveryResult 指向被发现的 jar");
        check.that(discovery.usable(), "这个 jar 报告为可用");
        check.equal(List.of("ocplugin.demo.DemoPlugin"), discovery.pluginClasses(),
                "服务文件与 main 指向同一个类时只报告一次（去重且排序）");
        check.that(loader.problems().isEmpty(), "没有 jar 报告问题: " + loader.problems());

        PluginDescriptor descriptor = discovery.descriptor().orElse(null);
        check.that(descriptor != null, "jar 里的 " + PluginDescriptor.RESOURCE + " 被解析");
        if (descriptor != null) {
            check.equal("demo", descriptor.id(), "描述文件的 id");
            check.equal("1.2.3", descriptor.version(), "描述文件的 version");
            check.equal("ocplugin.demo.DemoPlugin", descriptor.mainClass(), "描述文件的 main");
            check.equal("self check plugin", descriptor.description(), "描述文件的 description");
        }

        check.equal(1, loaded.size(), "插件被实例化");
        check.that(loader.plugin("demo-plugin").isPresent(), "plugin(String) 按插件名找到实例");
        check.that(loader.plugin("demo").isEmpty(), "plugin(String) 找的是插件名而不是描述文件里的 id");
        if (loaded.isEmpty()) {
            return jar;
        }
        Plugin plugin = loaded.get(0);
        check.equal("demo-plugin", plugin.name(), "插件实例报告的名字");
        check.equal("1.2.3", plugin.version(), "插件实例报告的版本");
        check.equal("self check plugin", plugin.description(), "插件实例报告的说明");
        check.equal(List.of("onLoad:9.9-selfcheck", "onEnable"), eventsOf(plugin),
                "onLoad 先执行（拿到服务器版本），onEnable 后执行");

        Path dataDirectory = plugins.resolve("data").resolve("demo-plugin");
        check.that(Files.isDirectory(dataDirectory), "插件的数据目录被创建: " + dataDirectory);
        check.equal(plugins.resolve("data").toString(), staticText(plugin, "dataRoot"),
                "两参数构造器把插件数据根放在 plugins/data 下（方便但会往插件目录写状态）");

        check.equal("ocplugin.demo.LateHelper", call(plugin, "lateClassName"),
                "loadAll() 之后按名字加载同一个 jar 里的另一个类");
        ClassLoader pluginLoader = plugin.getClass().getClassLoader();
        check.that(pluginLoader instanceof URLClassLoader, "插件类由 URLClassLoader 定义");
        check.that(pluginLoader != Plugin.class.getClassLoader(), "插件有独立的加载器，不是服务器那个");
        check.that(loader.classLoaders().contains(pluginLoader), "这个加载器被 loader 记录下来");
        check.equal("bytes", call(plugin, "defineLateClass"),
                "loadAll() 之后读取 jar 里的字节并定义类（加载器没有被关闭）");
        check.that(readResource(pluginLoader, "ocplugin/demo/still-open.txt") != null,
                "加载器能读出此前从未读过的 jar 资源");

        loader.unloadAll();
        check.that(loader.plugins().isEmpty(), "unloadAll() 清空了插件列表");
        check.equal(List.of("onLoad:9.9-selfcheck", "onEnable", "onDisable"), eventsOf(plugin),
                "unloadAll() 调用了 onDisable");
        check.equal(1, loader.classLoaders().size(), "unloadAll() 之后类加载器仍然被保留");
        check.that(readResource(pluginLoader, "ocplugin/demo/still-open.txt") != null,
                "unloadAll() 之后加载器仍能读出 jar 里的资源，说明它没有被 close");
        check.that(loadClassName("ocplugin.demo.LateHelper", pluginLoader) != null,
                "unloadAll() 之后插件的类依然是活的（JVM 不会卸载它们，这是动态加载的代价）");

        checkContext(check, plugins);
        return jar;
    }

    /**
     * The three argument constructor keeps the jars and the generated state apart: plugin data goes under the
     * server's data root, and the plugin folder stays exactly what the operator dropped into it.
     */
    private static void checkExplicitDataDirectory(Check check, Path root, Path demoJar) throws IOException {
        Path plugins = root.resolve("explicit-plugins");
        Path serverData = root.resolve("server-data");
        Files.createDirectories(plugins);
        Files.copy(demoJar, plugins.resolve("demo-plugin.jar"));

        PluginLoader loader = new PluginLoader(plugins, "9.9-selfcheck", serverData);
        List<Plugin> loaded = loader.loadAll();

        check.equal(1, loaded.size(), "带显式数据根目录的 loader 同样加载了这个插件");
        Path dataDirectory = serverData.resolve("demo-plugin");
        check.that(Files.isDirectory(dataDirectory), "插件的数据目录建在传入的数据根目录下: " + dataDirectory);
        check.that(!Files.exists(plugins.resolve("data")),
                "有显式数据根目录时，插件目录（用户的拖放区）里不会生成任何状态: " + plugins);
        if (!loaded.isEmpty()) {
            check.equal(serverData.toString(), staticText(loaded.get(0), "dataRoot"),
                    "PluginContext 拿到的是构造器配置的数据根目录");
        }
    }

    /** The correction that matters: a plugin whose library is a second jar dropped into the same folder. */
    private static void checkDroppedDependency(Check check, JavaCompiler compiler, Path root,
            String apiClasspath) throws IOException {
        Path libraryClasses = compile(check, compiler, root.resolve("dep-library"), "依赖库的源码",
                apiClasspath, Map.of("ocplugin/lib/Library.java", LIBRARY_SOURCE));
        String pluginClasspath = apiClasspath + File.pathSeparator + libraryClasses;
        Path pluginClasses = compile(check, compiler, root.resolve("dep-plugin"), "依赖该库的插件源码",
                pluginClasspath, Map.of("ocplugin/dep/DepPlugin.java", DEPENDENCY_SOURCE));

        Path plugins = root.resolve("dep-plugins");
        writeJar(plugins.resolve("dep-a-plugin.jar"),
                classEntry(pluginClasses, "ocplugin.dep.DepPlugin"),
                descriptor("dep", "1.0", "ocplugin.dep.DepPlugin", "needs a dropped dependency"));
        writeJar(plugins.resolve("dep-b-library.jar"),
                classEntry(libraryClasses, "ocplugin.lib.Library"));

        PluginLoader loader = new PluginLoader(plugins, "9.9-selfcheck");
        List<Plugin> loaded = loader.loadAll();

        check.equal(1, loaded.size(), "依赖 jar 里没有插件，只有插件 jar 被加载");
        check.that(loader.plugin("dep-plugin").isPresent(), "插件 A 被加载");
        check.equal(2, loader.classLoaders().size(), "目录里两个 jar 各有一个类加载器");
        check.that(loader.classLoaders().get(0) != loader.classLoaders().get(1),
                "两个 jar 的类加载器不是同一个对象");
        check.that(loader.problems().isEmpty(), "作为依赖的 jar 没有描述文件也不算问题: " + loader.problems());
        DiscoveryResult library = discoveryOf(loader, "dep-b-library.jar");
        check.that(library != null && library.descriptor().isEmpty() && !library.usable(),
                "依赖 jar 既没有描述文件也不贡献插件");

        if (loaded.isEmpty()) {
            return;
        }
        Plugin plugin = loaded.get(0);
        check.equal(List.of("onLoad", "onEnable:library"), eventsOf(plugin),
                "插件 A 在 onEnable 里用到了只存在于 jar B 里的类（依赖靠同目录的 jar 解析）");
        check.equal("library", call(plugin, "dependency"),
                "loadAll() 之后插件 A 仍能按需解析 jar B 里的类");
        check.equal("ocplugin.lib.Library",
                loadClassName("ocplugin.lib.Library", plugin.getClass().getClassLoader()),
                "插件 A 的加载器能看到同目录的 jar B");
    }

    /** Two jars that ship the same class name with different implementations must not clash. */
    private static void checkSameClassName(Check check, JavaCompiler compiler, Path root, String apiClasspath)
            throws IOException {
        Path classesA = compile(check, compiler, root.resolve("clash-a"), "同名类的插件 A",
                apiClasspath, Map.of("ocplugin/dup/ClashingPlugin.java", clashingSource("clash-a")));
        Path classesB = compile(check, compiler, root.resolve("clash-b"), "同名类的插件 B",
                apiClasspath, Map.of("ocplugin/dup/ClashingPlugin.java", clashingSource("clash-b")));

        Path plugins = root.resolve("clash-plugins");
        writeJar(plugins.resolve("clash-a.jar"),
                classEntry(classesA, "ocplugin.dup.ClashingPlugin"),
                descriptor("clash-a", "1.0", "ocplugin.dup.ClashingPlugin", "same name as B"));
        writeJar(plugins.resolve("clash-b.jar"),
                classEntry(classesB, "ocplugin.dup.ClashingPlugin"),
                descriptor("clash-b", "1.0", "ocplugin.dup.ClashingPlugin", "same name as A"));

        PluginLoader loader = new PluginLoader(plugins, "9.9-selfcheck");
        List<Plugin> loaded = loader.loadAll();

        check.equal(2, loaded.size(), "两个 jar 里的同名类各自成为一个插件");
        check.that(loader.problems().isEmpty(), "同名类不产生问题: " + loader.problems());
        Plugin first = loader.plugin("clash-a").orElse(null);
        Plugin second = loader.plugin("clash-b").orElse(null);
        check.that(first != null && second != null, "两个同名类互不覆盖，各自报告自己的名字");
        if (first != null && second != null) {
            check.that(first.getClass() != second.getClass(), "同名类由两个不同的类加载器定义");
            check.equal(List.of("clash-a"), eventsOf(first), "A 实例执行的是 A 的 jar 里的实现");
            check.equal(List.of("clash-b"), eventsOf(second), "B 实例执行的是 B 的 jar 里的实现");
        }
    }

    /** A broken descriptor and a plugin that throws must cost only themselves. */
    private static void checkBrokenJars(Check check, JavaCompiler compiler, Path root, String apiClasspath)
            throws IOException {
        Path goodClasses = compile(check, compiler, root.resolve("mixed-good"), "好插件的源码",
                apiClasspath, Map.of("ocplugin/good/GoodPlugin.java",
                        plainSource("ocplugin.good", "GoodPlugin", "good")));
        Path brokenClasses = compile(check, compiler, root.resolve("mixed-broken"), "坏描述文件插件的源码",
                apiClasspath, Map.of("ocplugin/broken/BrokenPlugin.java",
                        plainSource("ocplugin.broken", "BrokenPlugin", "broken")));
        Path throwingClasses = compile(check, compiler, root.resolve("mixed-throwing"), "会抛异常的插件源码",
                apiClasspath, Map.of("ocplugin/throwing/ThrowingPlugin.java", THROWING_SOURCE));

        Path plugins = root.resolve("mixed-plugins");
        writeJar(plugins.resolve("good-plugin.jar"),
                classEntry(goodClasses, "ocplugin.good.GoodPlugin"),
                descriptor("good", "2.0", "ocplugin.good.GoodPlugin", "loads fine"),
                service("ocplugin.good.GoodPlugin"));
        writeJar(plugins.resolve("broken-plugin.jar"),
                classEntry(brokenClasses, "ocplugin.broken.BrokenPlugin"),
                // id is there, version and main are not: the descriptor is unusable, not absent.
                text(PluginDescriptor.RESOURCE, "id=broken\n"));
        writeJar(plugins.resolve("throwing-plugin.jar"),
                classEntry(throwingClasses, "ocplugin.throwing.ThrowingPlugin"),
                descriptor("throwing", "1.0", "ocplugin.throwing.ThrowingPlugin", "throws in onLoad"));

        PluginLoader loader = new PluginLoader(plugins, "9.9-selfcheck");
        List<Plugin> loaded = loader.loadAll();

        check.that(loader.plugin("good").isPresent(), "好 jar 正常加载");
        check.that(loader.plugin("broken").isPresent(), "坏描述文件的 jar 仍被扫描发现（扫描不依赖描述文件）");
        check.that(loader.plugin("throwing").isEmpty(), "onLoad 抛异常的插件不会被启用");
        check.equal(List.of("broken", "good"), loaded.stream().map(Plugin::name).toList(),
                "按 jar 文件名排序加载，坏 jar 不影响它后面的 jar");
        check.that(loader.problems().stream()
                        .anyMatch(problem -> problem.contains(PluginDescriptor.RESOURCE)),
                "损坏的描述文件被记为问题: " + loader.problems());
        check.that(loader.problems().stream().anyMatch(problem -> problem.contains("deliberate failure")),
                "插件抛出的异常被记为问题，并且能看到真正的原因: " + loader.problems());

        DiscoveryResult broken = discoveryOf(loader, "broken-plugin.jar");
        check.that(broken != null && !broken.usable() && broken.descriptor().isEmpty(),
                "坏描述文件的 jar 报告为不可用且没有描述文件");
        check.that(broken != null && broken.pluginClasses().contains("ocplugin.broken.BrokenPlugin"),
                "坏描述文件的 jar 仍然报告它找到的插件类");
        DiscoveryResult good = discoveryOf(loader, "good-plugin.jar");
        check.that(good != null && good.usable(), "好 jar 报告为可用");
    }

    /** Name validation and directory creation of {@link PluginContext#dataDirectoryOf(String)}. */
    private static void checkContext(Check check, Path plugins) {
        Path dataDirectory = plugins.resolve("data");
        PluginContext context = new PluginContext(plugins, dataDirectory, "9.9-selfcheck",
                System.getLogger("selfcheck"));
        check.that(throwsIllegalArgument(() -> context.dataDirectoryOf("../escape")),
                "插件数据目录拒绝含有 .. 的名字");
        check.that(throwsIllegalArgument(() -> context.dataDirectoryOf("a/b")), "拒绝含有 / 的名字");
        check.that(throwsIllegalArgument(() -> context.dataDirectoryOf("a\\b")), "拒绝含有 \\ 的名字");
        check.that(throwsIllegalArgument(() -> context.dataDirectoryOf("C:evil")),
                "拒绝含有 : 的名字（Windows 上 C:evil 会直接跳出数据目录）");
        check.equal(dataDirectory.resolve("demo-plugin"), context.dataDirectoryOf("demo-plugin"),
                "已经存在的插件数据目录原样返回");
        check.that(Files.isDirectory(context.dataDirectoryOf("fresh-plugin")),
                "首次调用会创建插件数据目录");
        Properties incomplete = new Properties();
        incomplete.setProperty("id", "demo");
        check.that(PluginDescriptor.parse(incomplete).isEmpty(), "缺少 version 与 main 的描述文件不可用");
        Properties complete = new Properties();
        complete.setProperty("id", "demo");
        complete.setProperty("version", "1.2.3");
        complete.setProperty("main", "ocplugin.demo.DemoPlugin");
        check.equal(Optional.of(new PluginDescriptor("demo", "1.2.3", "ocplugin.demo.DemoPlugin", "")),
                PluginDescriptor.parse(complete), "齐全的描述文件被解析，缺失的描述为默认空串");
    }

    /**
     * Writes the given sources into {@code base/src} and compiles them into {@code base/classes} with the
     * runtime JDK compiler. The diagnostics are reported through the check, because a compile failure in the
     * check itself is the hardest kind to diagnose from a stack trace.
     */
    private static Path compile(Check check, JavaCompiler compiler, Path base, String label, String classpath,
            Map<String, String> sources) throws IOException {
        Path sourceRoot = base.resolve("src");
        Path classesRoot = base.resolve("classes");
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
            List<String> options = List.of("-classpath", classpath, "-d", classesRoot.toString(),
                    "-encoding", "UTF-8", "-proc:none");
            Boolean compiled = compiler.getTask(null, manager, collector, options, null,
                    manager.getJavaFileObjectsFromFiles(files)).call();
            boolean ok = Boolean.TRUE.equals(compiled);
            check.that(ok, label + " 可以编译");
            if (!ok) {
                for (Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
                    check.note(diagnostic.getKind() + ": " + diagnostic.getMessage(Locale.ROOT));
                }
            }
            return classesRoot;
        }
    }

    /** The classpath that holds the plugin API, so the generated plugin source can be compiled against it. */
    private static String pluginApiClasspath() {
        try {
            CodeSource codeSource = Plugin.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                return Path.of(codeSource.getLocation().toURI()).toString();
            }
        } catch (URISyntaxException | RuntimeException unusable) {
            // Fall back to the runtime class path below; a modular run reports the module in the code source,
            // which is not a directory the compiler can read, and the class path is then the better answer.
        }
        return System.getProperty("java.class.path", "");
    }

    private static DiscoveryResult discoveryOf(PluginLoader loader, String fileName) {
        for (DiscoveryResult result : loader.discoveries()) {
            if (result.jar().getFileName().toString().equals(fileName)) {
                return result;
            }
        }
        return null;
    }

    /**
     * The lifecycle calls a plugin recorded, read out of the plugin's own static list. Reflection is needed
     * because the class lives in a loader of its own: the check cannot name its type, which is the point.
     */
    private static List<String> eventsOf(Plugin plugin) {
        try {
            Field field = plugin.getClass().getField("EVENTS");
            if (field.get(null) instanceof List<?> recorded) {
                List<String> events = new ArrayList<>();
                for (Object event : recorded) {
                    events.add(String.valueOf(event));
                }
                return events;
            }
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            return List.of("<unreadable: " + unreadable + ">");
        }
        return List.of();
    }

    /** Calls a public no-argument static method on the plugin, returning null when it threw. */
    private static String call(Plugin plugin, String method) {
        try {
            return String.valueOf(plugin.getClass().getMethod(method).invoke(null));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }

    /** Reads a public static String field of the plugin's own class, returning null when it is unreadable. */
    private static String staticText(Plugin plugin, String fieldName) {
        try {
            Object value = plugin.getClass().getField(fieldName).get(null);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            return null;
        }
    }

    private static String loadClassName(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader).getName();
        } catch (ClassNotFoundException | LinkageError | SecurityException failure) {
            return null;
        }
    }

    private static byte[] readResource(ClassLoader loader, String name) {
        try (InputStream in = loader.getResourceAsStream(name)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException failure) {
            return null;
        }
    }

    private static boolean throwsIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private record Entry(String name, byte[] bytes) {
    }

    private static Entry text(String name, String content) {
        return new Entry(name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static Entry classEntry(Path classesRoot, String binaryName) throws IOException {
        String resource = binaryName.replace('.', '/') + ".class";
        return new Entry(resource, Files.readAllBytes(classesRoot.resolve(resource)));
    }

    private static Entry descriptor(String id, String version, String main, String description) {
        return text(PluginDescriptor.RESOURCE, "id=" + id + "\nversion=" + version + "\nmain=" + main
                + "\ndescription=" + description + "\n");
    }

    private static Entry service(String... binaryNames) {
        return text("META-INF/services/" + PLUGIN_API, String.join("\n", binaryNames) + "\n");
    }

    private static void writeJar(Path jar, Entry... entries) throws IOException {
        Files.createDirectories(jar.getParent());
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Entry entry : entries) {
                out.putNextEntry(new JarEntry(entry.name()));
                out.write(entry.bytes());
                out.closeEntry();
            }
        }
    }

    private static String clashingSource(String pluginName) {
        return CLASHING_SOURCE.formatted(pluginName);
    }

    private static String plainSource(String packageName, String className, String pluginName) {
        return PLAIN_SOURCE.formatted(packageName, className, pluginName);
    }
    private static final String DEMO_SOURCE = """
            package ocplugin.demo;

            import io.github.sob1234509876.oc.server.plugin.Plugin;
            import io.github.sob1234509876.oc.server.plugin.PluginContext;

            import java.io.InputStream;
            import java.lang.invoke.MethodHandles;
            import java.util.ArrayList;
            import java.util.List;

            /** Generated by the self check; it is not part of the repository. */
            public class DemoPlugin implements Plugin {

                public static final List<String> EVENTS = new ArrayList<>();

                /** The data root the server handed over, so the check knows which constructor was used. */
                public static String dataRoot;

                @Override
                public String name() {
                    return "demo-plugin";
                }

                @Override
                public String version() {
                    return "1.2.3";
                }

                @Override
                public String description() {
                    return "self check plugin";
                }

                @Override
                public void onLoad(PluginContext context) {
                    EVENTS.add("onLoad:" + context.serverVersion());
                    dataRoot = context.dataDirectory().toString();
                    context.dataDirectoryOf(name());
                }

                @Override
                public void onEnable() {
                    EVENTS.add("onEnable");
                }

                @Override
                public void onDisable() {
                    EVENTS.add("onDisable");
                }

                /** Asks for a class of this jar by name, long after the loader finished. */
                public static String lateClassName() throws Exception {
                    return Class.forName("ocplugin.demo.LateHelper", true,
                            DemoPlugin.class.getClassLoader()).getName();
                }

                /**
                 * Reads class bytes out of this jar and defines them. Both steps need the jar file the loader
                 * opened, so this only works while the loader is still usable.
                 */
                public static String defineLateClass() throws Throwable {
                    byte[] bytes;
                    try (InputStream in = DemoPlugin.class
                            .getResourceAsStream("/ocplugin/demo/BytesHelper.class.bin")) {
                        if (in == null) {
                            throw new IllegalStateException("late class bytes are not readable");
                        }
                        bytes = in.readAllBytes();
                    }
                    Class<?> type = MethodHandles.lookup().defineClass(bytes);
                    return (String) type.getMethod("greet").invoke(null);
                }
            }
            """;

    private static final String LATE_HELPER_SOURCE = """
            package ocplugin.demo;

            public final class LateHelper {

                public static String greet() {
                    return "late";
                }
            }
            """;

    private static final String BYTES_HELPER_SOURCE = """
            package ocplugin.demo;

            public final class BytesHelper {

                public static String greet() {
                    return "bytes";
                }
            }
            """;

    private static final String LIBRARY_SOURCE = """
            package ocplugin.lib;

            /** A library that lives in a jar of its own, dropped next to the plugin that uses it. */
            public final class Library {

                public static String text() {
                    return "library";
                }
            }
            """;

    private static final String DEPENDENCY_SOURCE = """
            package ocplugin.dep;

            import io.github.sob1234509876.oc.server.plugin.Plugin;
            import io.github.sob1234509876.oc.server.plugin.PluginContext;
            import ocplugin.lib.Library;

            import java.util.ArrayList;
            import java.util.List;

            /** Generated by the self check; it references a class that only jar B contains. */
            public class DepPlugin implements Plugin {

                public static final List<String> EVENTS = new ArrayList<>();

                @Override
                public String name() {
                    return "dep-plugin";
                }

                @Override
                public String version() {
                    return "1.0";
                }

                @Override
                public void onLoad(PluginContext context) {
                    EVENTS.add("onLoad");
                }

                @Override
                public void onEnable() {
                    EVENTS.add("onEnable:" + Library.text());
                }

                /** Resolves the dropped dependency again, on demand. */
                public static String dependency() {
                    return Library.text();
                }
            }
            """;

    private static final String CLASHING_SOURCE = """
            package ocplugin.dup;

            import io.github.sob1234509876.oc.server.plugin.Plugin;

            import java.util.ArrayList;
            import java.util.List;

            /** Generated by the self check; the same class name exists in another plugin jar. */
            public class ClashingPlugin implements Plugin {

                public static final List<String> EVENTS = new ArrayList<>();

                @Override
                public String name() {
                    return "%s";
                }

                @Override
                public String version() {
                    return "1.0";
                }

                @Override
                public void onEnable() {
                    EVENTS.add(name());
                }
            }
            """;

    private static final String PLAIN_SOURCE = """
            package %s;

            import io.github.sob1234509876.oc.server.plugin.Plugin;

            /** Generated by the self check. */
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

    private static final String THROWING_SOURCE = """
            package ocplugin.throwing;

            import io.github.sob1234509876.oc.server.plugin.Plugin;
            import io.github.sob1234509876.oc.server.plugin.PluginContext;

            /** Generated by the self check; it must cost only itself. */
            public class ThrowingPlugin implements Plugin {

                @Override
                public String name() {
                    return "throwing";
                }

                @Override
                public String version() {
                    return "1.0";
                }

                @Override
                public void onLoad(PluginContext context) {
                    throw new IllegalStateException("deliberate failure");
                }
            }
            """;
}
