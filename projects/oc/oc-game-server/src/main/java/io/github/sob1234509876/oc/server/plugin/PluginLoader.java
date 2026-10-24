package io.github.sob1234509876.oc.server.plugin;

import io.github.sob1234509876.oc.rts.content.WorldContent;
import io.github.sob1234509876.oc.server.world.WorldTickHook;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Loads the plugins of one folder, one jar at a time.
 *
 * <p>The folder is the whole dependency mechanism. Every jar in it is put on the search path of every
 * plugin, so a plugin's libraries are resolved by dropping their jars next to it - there is no repository, no
 * POM and no download, which is what makes the server usable off a USB stick on a machine with no network.
 * The consequence has to be said plainly: <strong>only one version of a given library may live in the plugin
 * folder at a time</strong>, because every plugin sees the folder, and the first jar that holds a class in
 * the sorted order wins for a plugin that does not ship that class itself.
 *
 * <p>Each jar still gets a loader of its own, with its own jar first in the URL list and the rest of the
 * folder behind it. A plugin's own class therefore always wins over a same named class in another jar, so two
 * plugins may each ship their own copy of the same library, and unloading bookkeeping stays per plugin. The
 * parent of every loader is the loader that read {@link Plugin} itself, and delegation is parent first, so
 * the server API is one shared set of classes and a plugin cannot quietly replace it with its own version.
 *
 * <p>Discovery inside a jar is deliberately forgiving and ordered:
 *
 * <ol>
 *   <li>the {@code META-INF/services/} file, which is what a plugin built with a service loader or a build
 *       plugin gets for free;</li>
 *   <li>the {@code main} key of {@link PluginDescriptor#RESOURCE};</li>
 *   <li>a scan of every class in the jar that is not {@code module-info}, not {@code package-info} and not an
 *       inner or anonymous class, kept when it implements {@link Plugin} and can be instantiated.</li>
 * </ol>
 *
 * <p>Every step records what it could not do and continues: one unloadable class, one broken descriptor or
 * one plugin that throws must not cost the operator the rest of the folder. The plugins are reported sorted
 * by class name, and that is the order in which they are constructed, loaded and enabled, so a folder
 * produces the same startup twice.
 *
 * <p><strong>Plugin state</strong> is kept apart from the jars: a plugin writes into
 * {@code dataDirectory/<pluginName>/}, and which root that is depends on the constructor used. A server
 * passes its own data directory (the preferred choice, because the plugin folder must stay a drop zone for
 * jars that an operator can delete and replace), while the convenience constructor derives
 * {@code pluginsDirectory/data} for a self contained folder.
 *
 * <p><strong>Thread safety:</strong> none, on purpose. Loading is a startup step that runs once on the main
 * thread before the server accepts clients, and a shutdown step that runs once after it stopped. Making the
 * lists concurrent would buy nothing and hide the real mistake, which is calling into a plugin from two
 * threads at once.
 */
public final class PluginLoader {

    private static final String JAR_SUFFIX = ".jar";
    private static final String CLASS_SUFFIX = ".class";
    private static final String MODULE_INFO = "module-info";
    private static final String PACKAGE_INFO = "package-info";
    private static final String SERVICES_ENTRY = "META-INF/services/" + Plugin.class.getName();
    private static final String DATA_DIRECTORY_NAME = "data";

    private final Path pluginsDirectory;
    private final String serverVersion;

    /** The root of the per plugin data directories; never the plugin folder unless it was asked for. */
    private final Path dataDirectory;

    /**
     * The request surface every plugin of this loader may publish into.
     *
     * <p>It is owned by whoever also owns the server (the game server passes the same instance to its
     * socket), because that is the only object both sides must agree on: a plugin registers here, and the
     * running server looks names up here. A loader that was not given one keeps an empty registry of its
     * own, so a plugin loaded by a test still gets a working, if unreachable, surface instead of a
     * {@code null}.
     */
    private final RequestRegistry requests;

    /**
     * The server options handed to every plugin through {@link PluginContext#options()}.
     *
     * <p>Today this is the parsed {@code --bots} value; keeping it a map instead of a bot-specific field
     * means adding a future plugin option does not change this class's shape again.
     */
    private final Map<String, String> options;

    private final System.Logger logger;

    /**
     * The class loaders of the jars seen so far. They are intentionally never closed.
     *
     * <p>{@link URLClassLoader#close()} releases the jar files a loader has open, and from that moment the
     * loader can no longer define a class, or read a resource, that it has not touched yet. A plugin that
     * loads something lazily - a serializer, a scripting engine, a JDBC driver, anything it looks up by name
     * only when the feature is first used - then fails with {@code NoClassDefFoundError}, which is exactly
     * the symptom this loader exists to avoid. Closing would also make the behaviour depend on when the
     * plugin was first used, which is the worst kind of bug to reproduce.
     *
     * <p>So {@code close()} is never called here, not even by {@link #unloadAll()}. Nothing is leaked that a
     * server process would notice: the loaders live exactly as long as the process that owns the plugins, the
     * JVM keeps the classes of a loaded plugin alive anyway, and a server process outlives every plugin it
     * ever loaded. The price is stated rather than hidden: a disabled plugin's classes stay in memory until
     * the server exits.
     */
    private final List<URLClassLoader> classLoaders = new ArrayList<>();

    private final List<Plugin> plugins = new ArrayList<>();

    /**
     * The game world catalogues the loaded plugins registered, in load order.
     *
     * <p>One list for the whole startup is handed to every plugin through
     * {@link PluginContext#registerWorldContent(WorldContent)}, so the order here is exactly the order in
     * which the plugins were loaded - which is the order of their jars in the folder, because discovery
     * sorts by file name. The server picks the first entry; see {@code OcGameServer} for why first and not
     * last. A plugin whose lifecycle failed never appends anything, because a plugin that is not part of the
     * server must not publish content either.
     *
     * <p>Like the plugin list, this is startup state and is not thread safe: it is written while plugins are
     * loaded and read once, before the sockets open.
     */
    private final List<WorldContent> worldContents = new ArrayList<>();

    /**
     * The world tick hooks the loaded plugins registered, in load order.
     *
     * <p>One list for the whole startup is handed to every plugin through
     * {@link PluginContext#registerWorldTickHook(WorldTickHook)}, so the order here is exactly the order
     * in which the plugins were loaded. Unlike {@link #worldContents}, the server does not choose among
     * these: every hook of the list runs, in this order, because a hook does not claim the world - it
     * contributes to it.
     *
     * <p>Like the plugin list, this is startup state and is not thread safe: it is written while plugins
     * are loaded and read once, when the world host is built, before the sockets open.
     */
    private final List<WorldTickHook> worldTickHooks = new ArrayList<>();

    /**
     * The loader of every enabled plugin, keyed by the name the plugin reports.
     *
     * <p>The name is the only identity a plugin exposes that a browser can safely put in a URL: it is what
     * {@link #plugin(String)} looks up and what names the plugin's data directory. For a conventionally
     * packaged plugin it equals the {@code id} of {@link PluginDescriptor}, but the name is authoritative
     * here because it comes from the live object rather than from a file the plugin might forget to update.
     *
     * <p>The order is the load order, which is also the order the asset manifest is merged in. A plugin
     * whose {@code onLoad} or {@code onEnable} failed never appears: it is not part of the server, so it
     * must not publish content either.
     */
    private final Map<String, URLClassLoader> classLoadersByPlugin = new LinkedHashMap<>();

    private final List<DiscoveryResult> discoveries = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();

    /**
     * Creates a loader whose plugins keep their state under the server's own data directory.
     *
     * <p>This is the constructor a server should use. The plugin folder is the operator's drop zone for
     * jars - the place where files are added, replaced and deleted by hand - so generated state must not
     * appear in it: a folder that mixes plugins with runtime data is a folder nobody dares to clean up, and
     * a backup of the server's state would depend on where the plugins happen to be installed. Plugin state
     * therefore lives under {@code dataDirectory/<pluginName>/}, next to the server's other state.
     *
     * @param pluginsDirectory where the plugin jars live, together with the dependency jars they need; the
     *     directory does not have to exist yet, an absent one simply means no plugins
     * @param serverVersion the version reported to plugins through {@link PluginContext#serverVersion()}
     * @param dataDirectory the root of the per plugin data directories, normally the same directory that
     *     holds the server's own state
     */
    public PluginLoader(Path pluginsDirectory, String serverVersion, Path dataDirectory) {
        this(pluginsDirectory, serverVersion, dataDirectory, new RequestRegistry());
    }

    /**
     * Creates a loader whose plugins publish their requests into the given registry.
     *
     * <p>This is the constructor a server uses, because the server must hand the very same registry to the
     * socket that serves clients; see {@link PluginContext#requests()}. The four argument form keeps the
     * registry private to the loader, which is what a test wants.
     *
     * @param pluginsDirectory where the plugin jars live, together with the dependency jars they need
     * @param serverVersion the version reported to plugins through {@link PluginContext#serverVersion()}
     * @param dataDirectory the root of the per plugin data directories
     * @param requests the registry {@link PluginContext#requests()} exposes to every plugin
     */
    public PluginLoader(Path pluginsDirectory, String serverVersion, Path dataDirectory,
            RequestRegistry requests) {
        this(pluginsDirectory, serverVersion, dataDirectory, requests, Map.of());
    }

    /**
     * Creates a loader that also publishes server options to its plugins.
     *
     * <p>The options reach every {@link Plugin#onLoad(PluginContext)} through
     * {@link PluginContext#options()}; the map is copied, so a plugin cannot observe a later change and the
     * caller cannot change a loaded plugin's view. This is the constructor {@code OcGameServer} uses for
     * {@code --bots}.
     *
     * @param pluginsDirectory where the plugin jars live, together with the dependency jars they need
     * @param serverVersion the version reported to plugins through {@link PluginContext#serverVersion()}
     * @param dataDirectory the root of the per plugin data directories
     * @param requests the registry {@link PluginContext#requests()} exposes to every plugin
     * @param options the immutable map {@link PluginContext#options()} reports; never {@code null}
     */
    public PluginLoader(Path pluginsDirectory, String serverVersion, Path dataDirectory,
            RequestRegistry requests, Map<String, String> options) {
        this.pluginsDirectory = Objects.requireNonNull(pluginsDirectory, "pluginsDirectory")
                .toAbsolutePath().normalize();
        this.serverVersion = Objects.requireNonNull(serverVersion, "serverVersion");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory")
                .toAbsolutePath().normalize();
        this.requests = Objects.requireNonNull(requests, "requests");
        this.options = Map.copyOf(Objects.requireNonNull(options, "options"));
        this.logger = System.getLogger(PluginLoader.class.getName());
    }

    /**
     * Convenience for a self contained plugin folder: plugin state lands in {@code pluginsDirectory/data}.
     *
     * <p>The plugin folder can then be moved or copied as a whole, which is handy for a test, a demo or a
     * server whose state is deliberately packed next to its plugins. A server that already owns a data
     * directory should pass it to {@link #PluginLoader(Path, String, Path)} instead, so that adding or
     * replacing a plugin jar never touches the server's state and vice versa.
     *
     * @param pluginsDirectory where the plugin jars live; under this constructor it also holds the data
     *     directory of every plugin
     * @param serverVersion the version reported to plugins through {@link PluginContext#serverVersion()}
     */
    public PluginLoader(Path pluginsDirectory, String serverVersion) {
        this(pluginsDirectory, serverVersion,
                Objects.requireNonNull(pluginsDirectory, "pluginsDirectory").resolve(DATA_DIRECTORY_NAME));
    }

    /**
     * Scans the folder, loads every plugin that can be loaded and enables it.
     *
     * <p>Intended to be called once per loader, from the startup thread. A second call scans the folder again
     * and adds what it finds now; nothing that is already loaded is cleared, and a jar that failed before is
     * simply inspected again. Nothing here throws for a bad jar: the plugins that work are returned and the
     * rest is in {@link #problems()}.
     *
     * @return the plugins loaded so far, in the order they were enabled
     */
    public List<Plugin> loadAll() {
        List<Path> jars = pluginJars();
        PluginContext context = new PluginContext(pluginsDirectory, dataDirectory, serverVersion, logger,
                requests, worldContents, worldTickHooks, options);
        for (Path jar : jars) {
            DiscoveryResult result = loadJar(jar, jars, context);
            discoveries.add(result);
            problems.addAll(result.problems());
            for (String problem : result.problems()) {
                logger.log(System.Logger.Level.WARNING, problem);
            }
        }
        return plugins();
    }

    /**
     * Loads one jar and returns what was found in it. The jar's loader is created before anything is read, so
     * the classes found here - and the ones the plugin will look up later - come from the same loader.
     */
    private DiscoveryResult loadJar(Path jar, List<Path> jars, PluginContext context) {
        Set<String> found = new LinkedHashSet<>();
        List<String> jarProblems = new ArrayList<>();
        URLClassLoader loader;
        try {
            loader = new URLClassLoader(classPathFor(jar, jars), Plugin.class.getClassLoader());
        } catch (MalformedURLException | SecurityException broken) {
            jarProblems.add(jar.getFileName() + " could not be opened: " + describe(broken));
            return new DiscoveryResult(jar, Optional.empty(), List.of(), jarProblems);
        }
        classLoaders.add(loader);

        Optional<PluginDescriptor> descriptor = Optional.empty();
        try (JarFile file = new JarFile(jar.toFile())) {
            descriptor = descriptorOf(jar, file, jarProblems);
            for (String className : serviceClasses(jar, file, jarProblems)) {
                requireCandidate(jar, className, loader, found, jarProblems);
            }
            if (descriptor.isPresent()) {
                requireCandidate(jar, descriptor.get().mainClass(), loader, found, jarProblems);
            }
            for (String className : classNamesOf(file)) {
                inspectCandidate(jar, className, loader, found, jarProblems);
            }
        } catch (IOException failure) {
            jarProblems.add(jar.getFileName() + " could not be read as a jar: " + describe(failure));
        }

        List<String> pluginClasses = new ArrayList<>(new TreeSet<>(found));
        for (String className : pluginClasses) {
            Plugin plugin = instantiate(jar, className, loader, context, jarProblems);
            if (plugin != null) {
                plugins.add(plugin);
                // A jar may hold more than one plugin, and they legitimately share one loader: every class
                // of the jar must resolve against the same class path, and the assets of the jar are the
                // same bytes for each of them.
                synchronized (classLoadersByPlugin) {
                    classLoadersByPlugin.put(plugin.name(), loader);
                }
            }
        }
        return new DiscoveryResult(jar, descriptor, pluginClasses, jarProblems);
    }

    /**
     * Reads {@link PluginDescriptor#RESOURCE}. A jar without it is not a problem - the folder is also where
     * dependencies live, and a library jar has no business carrying a plugin descriptor - but a jar with a
     * broken one is, because that is a packaging mistake the operator can fix.
     */
    private Optional<PluginDescriptor> descriptorOf(Path jar, JarFile file, List<String> jarProblems) {
        JarEntry entry = file.getJarEntry(PluginDescriptor.RESOURCE);
        if (entry == null) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try (InputStream in = file.getInputStream(entry)) {
            properties.load(in);
        } catch (IOException failure) {
            jarProblems.add(jar.getFileName() + ": " + PluginDescriptor.RESOURCE + " could not be read: "
                    + describe(failure));
            return Optional.empty();
        }
        Optional<PluginDescriptor> descriptor = PluginDescriptor.parse(properties);
        if (descriptor.isEmpty()) {
            jarProblems.add(jar.getFileName() + ": " + PluginDescriptor.RESOURCE
                    + " is unusable, id, version and main must all be present and non blank");
        }
        return descriptor;
    }

    /**
     * Reads the service file, if any. The file is UTF-8 by the specification of the service loader
     * mechanism, and comments after {@code #} are ignored the same way.
     */
    private List<String> serviceClasses(Path jar, JarFile file, List<String> jarProblems) {
        JarEntry entry = file.getJarEntry(SERVICES_ENTRY);
        if (entry == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(entry), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int comment = line.indexOf('#');
                String name = (comment < 0 ? line : line.substring(0, comment)).strip();
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        } catch (IOException failure) {
            jarProblems.add(jar.getFileName() + ": " + SERVICES_ENTRY + " could not be read: "
                    + describe(failure));
        }
        return names;
    }

    /** The binary names of the candidate classes in the jar, sorted so the scan is reproducible. */
    private static List<String> classNamesOf(JarFile file) {
        List<String> names = new ArrayList<>();
        for (Enumeration<JarEntry> entries = file.entries(); entries.hasMoreElements();) {
            JarEntry entry = entries.nextElement();
            if (entry.isDirectory() || !entry.getName().endsWith(CLASS_SUFFIX)) {
                continue;
            }
            String name = entry.getName();
            String binaryName = name.substring(0, name.length() - CLASS_SUFFIX.length()).replace('/', '.');
            if (binaryName.equals(MODULE_INFO) || binaryName.equals(PACKAGE_INFO)) {
                continue;
            }
            int lastDot = binaryName.lastIndexOf('.');
            if (binaryName.indexOf('$', lastDot + 1) >= 0) {
                // Inner, local and anonymous classes are compile time detail: a plugin is never named that
                // way, and an inner class would need an enclosing instance to be constructed.
                continue;
            }
            names.add(binaryName);
        }
        Collections.sort(names);
        return names;
    }

    /**
     * Handles a name that was written down on purpose, in the service file or in the descriptor, so a class
     * that turns out not to be a usable plugin is worth reporting.
     */
    private void requireCandidate(Path jar, String className, URLClassLoader loader, Set<String> found,
            List<String> jarProblems) {
        String name = className == null ? "" : className.strip();
        if (name.isEmpty()) {
            return;
        }
        try {
            Class<?> type = Class.forName(name, false, loader);
            if (!Plugin.class.isAssignableFrom(type)) {
                jarProblems.add(jar.getFileName() + ": " + name + " is not a " + Plugin.class.getName());
            } else if (!isConcrete(type)) {
                jarProblems.add(jar.getFileName() + ": " + name + " is abstract and cannot be instantiated");
            } else {
                found.add(name);
            }
        } catch (ClassNotFoundException | LinkageError | SecurityException failure) {
            jarProblems.add(jar.getFileName() + ": " + name + " could not be loaded: " + describe(failure));
        }
    }

    /**
     * Handles a name the scan stumbled over, so a class that is not a plugin at all - which is most of them -
     * is skipped in silence. Only a class that cannot even be read is recorded, because that is what a plugin
     * author who expected this class to be a plugin needs to hear.
     */
    private void inspectCandidate(Path jar, String className, URLClassLoader loader, Set<String> found,
            List<String> jarProblems) {
        try {
            Class<?> type = Class.forName(className, false, loader);
            if (Plugin.class.isAssignableFrom(type) && isConcrete(type)) {
                found.add(className);
            }
        } catch (ClassNotFoundException | LinkageError | SecurityException failure) {
            // Jars routinely carry classes for other environments; refusing to load them must never abort the
            // scan, so the class is a problem for the log and nothing more.
            jarProblems.add(jar.getFileName() + ": " + className + " could not be inspected: "
                    + describe(failure));
        }
    }

    /**
     * Constructs one discovered plugin and drives it through load and enable. A failure is recorded and
     * logged, never thrown, because the remaining jars must still get their turn.
     */
    private Plugin instantiate(Path jar, String className, URLClassLoader loader, PluginContext context,
            List<String> jarProblems) {
        Plugin plugin = null;
        boolean loaded = false;
        try {
            Class<?> type = Class.forName(className, false, loader);
            plugin = (Plugin) type.getDeclaredConstructor().newInstance();
            plugin.onLoad(context);
            loaded = true;
            plugin.onEnable();
            return plugin;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            // The problem is recorded and logged once, by the caller that reports the whole jar; logging here
            // as well would print every startup failure twice.
            String message = jar.getFileName() + ": plugin " + className + " failed: " + describe(failure);
            jarProblems.add(message);
            if (loaded) {
                // onLoad succeeded and handed the plugin its resources before onEnable failed, so give it the
                // shutdown call it would otherwise never get. It never becomes visible to the server.
                disableQuietly(plugin, jarProblems);
            }
            return null;
        }
    }

    /** Calls {@code onDisable} during a failed startup, where a second failure is only worth recording. */
    private void disableQuietly(Plugin plugin, List<String> jarProblems) {
        try {
            plugin.onDisable();
        } catch (RuntimeException | LinkageError failure) {
            jarProblems.add(plugin.getClass().getName() + " also failed while being disabled: "
                    + describe(failure));
        }
    }

    /**
     * The URLs of one plugin's loader: its own jar first, then every other jar of the folder, sorted. Own jar
     * first is what lets a plugin ship its own copy of a class that another plugin also ships, and the rest
     * of the folder is how a dropped dependency is found.
     */
    private static URL[] classPathFor(Path jar, List<Path> jars) throws MalformedURLException {
        List<URL> urls = new ArrayList<>(jars.size());
        urls.add(jar.toUri().toURL());
        for (Path other : jars) {
            if (!other.equals(jar)) {
                urls.add(other.toUri().toURL());
            }
        }
        return urls.toArray(new URL[0]);
    }

    /** Every {@code *.jar} of the folder, sorted by file name so two runs see the same order. */
    private List<Path> pluginJars() {
        if (!Files.isDirectory(pluginsDirectory)) {
            // A server whose plugin folder has not been created yet has no plugins; that is not a failure.
            return List.of();
        }
        List<Path> jars = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(pluginsDirectory)) {
            for (Path entry : entries) {
                String fileName = entry.getFileName().toString().toLowerCase(Locale.ROOT);
                if (Files.isRegularFile(entry) && fileName.endsWith(JAR_SUFFIX)) {
                    jars.add(entry);
                }
            }
        } catch (IOException failure) {
            String message = "cannot list the plugin folder " + pluginsDirectory + ": " + describe(failure);
            problems.add(message);
            logger.log(System.Logger.Level.WARNING, message, failure);
        }
        jars.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return jars;
    }

    /** The plugins loaded so far, in the order they were enabled. */
    public List<Plugin> plugins() {
        return List.copyOf(plugins);
    }

    /**
     * The game world catalogues the loaded plugins registered, in load order.
     *
     * <p>The order is the order in which the jars were loaded, and therefore the order in which their
     * {@link Plugin#onLoad(PluginContext)} calls ran: a jar whose name sorts first registers first. The
     * server takes the first entry, which makes "which plugin owns the world" a visible property of the
     * installation rather than a race; see {@code OcGameServer#selectWorldContent(java.util.List)}.
     *
     * <p>The list is emptied by {@link #unloadAll()}, like the plugin list: a disabled plugin is no longer
     * part of the server, so a later startup must not inherit its content.
     *
     * @return the registered catalogues in load order; empty before {@link #loadAll()}
     */
    public List<WorldContent> worldContents() {
        return List.copyOf(worldContents);
    }

    /**
     * The world tick hooks the loaded plugins registered, in load order.
     *
     * <p>The order is the order in which the jars were loaded, and therefore the order in which their
     * {@link Plugin#onLoad(PluginContext)} calls ran: a jar whose name sorts first registers first. The
     * server runs all of them in this order after every fixed world step; see {@code WorldTickHook} and
     * {@code OcGameServer#selectWorldTickHooks(java.util.List)} for why it is "all" here while the world
     * content seam is "first one wins".
     *
     * <p>The list is handed to the {@link io.github.sob1234509876.oc.server.world.WorldHost} once, when
     * the world is built, and is emptied by {@link #unloadAll()}: a hook of a disabled plugin must not be
     * inherited by a later startup.
     *
     * @return the registered hooks in load order; empty before {@link #loadAll()}
     */
    public List<WorldTickHook> worldTickHooks() {
        return List.copyOf(worldTickHooks);
    }

    /** What each jar of the folder turned out to contain, including the jars that produced nothing. */
    public List<DiscoveryResult> discoveries() {
        return List.copyOf(discoveries);
    }

    /** Everything that went wrong, one line per problem, ready for the server log. */
    public List<String> problems() {
        return List.copyOf(problems);
    }

    /** Looks a plugin up by the name it reports, not by its class or its jar. */
    public Optional<Plugin> plugin(String name) {
        for (Plugin plugin : plugins) {
            if (Objects.equals(plugin.name(), name)) {
                return Optional.of(plugin);
            }
        }
        return Optional.empty();
    }

    /**
     * The loaders of the jars that were opened, in load order. They stay open for the lifetime of the server;
     * see the field documentation for why that is not a leak.
     */
    public List<URLClassLoader> classLoaders() {
        return List.copyOf(classLoaders);
    }

    /**
     * The class loader of every enabled plugin, keyed by {@link Plugin#name()}, in load order.
     *
     * <p>This is the mapping the {@code /plugin-assets} route resolves a request's plugin id against. The
     * route asks this map for the loader and then reads the asset through it, so the HTTP layer never needs
     * a plugin's jar path, folder or any other filesystem handle: it only ever sees a loader the server
     * already owns and never closes (see the field documentation above).
     *
     * <p>The map is an unmodifiable snapshot that preserves the load order, because the merged asset
     * manifest is built in that order and a caller that sorted it would change which plugin wins a tile.
     * The copy is taken under the map's monitor: the asset route reads this mapping from connection threads
     * while shutdown may be clearing it, and a snapshot that could observe a half cleared map would turn a
     * teardown into an exception in an unrelated request. Plugin lifecycle calls themselves stay single
     * threaded, as documented above.
     *
     * @return the enabled plugins' loaders by plugin name, in load order; empty before {@link #loadAll()}
     */
    public Map<String, URLClassLoader> classLoadersByPlugin() {
        synchronized (classLoadersByPlugin) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(classLoadersByPlugin));
        }
    }

    /**
     * The request surface this loader handed to its plugins, which is the registry a server must give to the
     * component that serves clients.
     *
     * @return the registry, never {@code null} and never a copy
     */
    public RequestRegistry requests() {
        return requests;
    }

    /**
     * The server options every plugin of this loader received through {@link PluginContext#options()}.
     *
     * @return the immutable option map; empty when the loader was built without options
     */
    public Map<String, String> options() {
        return options;
    }

    /**
     * Disables every loaded plugin, in load order, and forgets them.
     *
     * <p>The class loaders are <strong>not</strong> closed and are not removed, because a plugin that is
     * disabled may still be asked for a class later in the same JVM - and closing would break exactly the
     * dynamic loading described in the field documentation. The JVM therefore keeps the classes of a
     * disabled plugin alive until the process ends; that is the accepted price of a plugin being able to
     * load its own classes at any time, and a server process outlives its plugins anyway.
     *
     * <p>The plugin-to-loader map is cleared, however. A disabled plugin is no longer part of the server,
     * so its assets must stop being served the moment its lifecycle ends; a later {@link #loadAll()} adds
     * the mapping again for whatever it loads.
     */
    public void unloadAll() {
        for (Plugin plugin : plugins) {
            try {
                plugin.onDisable();
            } catch (RuntimeException | LinkageError failure) {
                // Shutdown continues: one plugin that cannot clean up must not stop the others or the
                // server's own shutdown.
                String message = plugin.getClass().getName() + " failed while being disabled: "
                        + describe(failure);
                problems.add(message);
                logger.log(System.Logger.Level.WARNING, message, failure);
            }
        }
        plugins.clear();
        // The content a plugin registered is content of the disabled plugin: a later loadAll() must start
        // from what the folder offers then, not from what an unloaded plugin left behind.
        worldContents.clear();
        // The same holds for its tick hooks: a hook is a live callback into the world, so it must not
        // outlive the plugin whose onDisable() just ran.
        worldTickHooks.clear();
        synchronized (classLoadersByPlugin) {
            classLoadersByPlugin.clear();
        }
    }

    private static boolean isConcrete(Class<?> type) {
        return !type.isInterface() && !Modifier.isAbstract(type.getModifiers());
    }

    /**
     * A short, one line description of a failure for the log. A reflective call wraps the plugin's own
     * exception, and unwrapping it is the difference between "InvocationTargetException" and the actual
     * message the plugin author needs.
     */
    private static String describe(Throwable failure) {
        Throwable cause = failure;
        if (cause instanceof InvocationTargetException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
