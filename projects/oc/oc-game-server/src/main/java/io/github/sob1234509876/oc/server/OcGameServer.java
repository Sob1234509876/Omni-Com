package io.github.sob1234509876.oc.server;

import io.github.sob1234509876.oc.rts.content.ScenarioWorldContent;
import io.github.sob1234509876.oc.rts.content.WorldContent;
import io.github.sob1234509876.oc.server.auth.AuthService;
import io.github.sob1234509876.oc.server.auth.LocalUserStore;
import io.github.sob1234509876.oc.server.net.GameServer;
import io.github.sob1234509876.oc.server.net.ServerConfig;
import io.github.sob1234509876.oc.server.plugin.Plugin;
import io.github.sob1234509876.oc.server.plugin.PluginLoader;
import io.github.sob1234509876.oc.server.plugin.RequestRegistry;
import io.github.sob1234509876.oc.server.ws.CommandWebSocketHandler;
import io.github.sob1234509876.oc.server.ws.HttpWebServer;
import io.github.sob1234509876.oc.server.ws.WebSocketConfig;
import io.github.sob1234509876.oc.server.world.WorldHost;
import io.github.sob1234509876.oc.server.world.WorldTickHook;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * The Omni Com game server: users, authentication, an encrypted TCP channel, plugins and periodic saving,
 * wired together in one startable program.
 *
 * <p>The vanilla game content is meant to be nothing special - it is a plugin like any other - so this class
 * only owns the parts a plugin must not own: the listening socket, the user records and the timer that writes
 * them to disk. Everything game shaped is loaded from {@code plugins/*.jar} and is given a data directory of
 * its own, so a plugin can be replaced or removed without touching the server. The one game shaped thing the
 * server does own is the optional {@link WorldHost} described below, because the browser view needs a live
 * engine whose snapshot only the server process can produce.
 *
 * <p>Shutdown is deliberate about ordering: the autosave timer stops first, then plugins are disabled (before
 * the socket closes, so a plugin may still flush something), then the socket closes and finally the user
 * store is written once more. The user store is written on a timer as well as at shutdown, because a server
 * that crashes between two logins is a server that lost them.
 *
 * <p><strong>The browser endpoint is optional.</strong> When a {@link WebSocketConfig} with a non-zero port
 * is supplied, this server also starts an {@link HttpWebServer} on its own port so a browser can fetch the
 * static web client, the loaded plugins' assets under {@code /plugin-assets/} and speak the same commands
 * over a WebSocket. When that port is {@code 0} no HTTP socket is bound at all, and the server behaves
 * exactly as it did before the endpoint existed. {@link #stop()} closes both sockets in the same shutdown
 * step.
 *
 * <p><strong>The server side world is optional.</strong> With {@code --world on} (the default) the endpoint
 * also gets one {@link WorldHost} and publishes its snapshot at {@code GET /world/snapshot}. The world is
 * built before any socket opens, started with the rest of the server and stopped after the endpoint that
 * reads it; {@code --world off}, or {@code --http-port 0}, leaves it unstarted and the snapshot route answers
 * {@code 503}.
 *
 * <p><strong>Which game the world runs.</strong> A plugin claims the world by registering a
 * {@link WorldContent} in {@link Plugin#onLoad(PluginContext)}; the server serves the first registration in
 * load order and falls back to {@link ScenarioWorldContent} when there is none. The rule, and why it is
 * "first" instead of "last", is documented on {@link #selectWorldContent(java.util.List)}.
 *
 * <p><strong>What runs inside the world.</strong> A plugin may also register a {@link WorldTickHook}: a
 * callback that runs after every fixed step of that world. Unlike the content, every hook runs, in plugin
 * load order, because a hook contributes to the world instead of claiming it; see
 * {@link #selectWorldTickHooks(java.util.List)}.
 */
public final class OcGameServer {

    /** Version reported to clients and plugins. */
    public static final String VERSION = "2.0a";

    /**
     * Default WebSocket idle timeout in seconds: five minutes, exposed as {@code --ws-idle 300}.
     *
     * <p>It is long enough that an open page is not disconnected just for being idle. The browser endpoint
     * also sends a ping frame every 25 seconds; a browser answers with a pong automatically, so a session
     * that is only being kept alive by those pings keeps its read timeout from expiring.
     */
    public static final int DEFAULT_WS_IDLE_SECONDS = 300;

    private static final System.Logger LOG = System.getLogger("oc.server");

    /** Plugins live here by default; the user drops their jars (and their dependencies) into it. */
    public static final Path DEFAULT_PLUGINS_DIRECTORY = Path.of("plugins");

    /** Server owned state (user records today) lives here by default. */
    public static final Path DEFAULT_DATA_DIRECTORY = Path.of("data");

    private final ServerConfig config;
    private final Path pluginsDirectory;
    private final Path dataDirectory;
    private final AuthService auth;

    /** The request surface plugins publish into and the socket relaying to; see the constructor. */
    private final RequestRegistry requests;

    private final PluginLoader pluginLoader;
    private final GameServer server;

    /**
     * Settings for the optional browser endpoint. A port of {@code 0} means "no browser endpoint", which is
     * what the constructor without a configuration uses so that a programmatic server keeps its old shape.
     */
    private final WebSocketConfig webSocketConfig;

    /** The running browser endpoint, or {@code null} when it is disabled or not started. */
    private volatile HttpWebServer webServer;

    /** Whether this server was asked to host a world; a port of {@code 0} always turns it off. */
    private final boolean worldEnabled;

    /** Seed of the hosted world. It is ignored when {@link #worldEnabled} is false. */
    private final long worldSeed;

    /** The running world, or {@code null} when it is disabled or not started. */
    private volatile WorldHost worldHost;

    private final int saveIntervalSeconds;
    private ScheduledExecutorService saver;
    private List<Plugin> plugins = List.of();
    private volatile boolean started;

    /**
     * Creates a server without a browser endpoint.
     *
     * <p>This is the historical constructor and its behaviour is unchanged: only the encrypted TCP socket is
     * bound. An embedding program that wants the web client passes a {@link WebSocketConfig} to the other
     * constructor; the command line server enables it by default.
     *
     * @param config              the game socket and framing limits
     * @param pluginsDirectory    where plugin jars live
     * @param dataDirectory       server owned state, user records today
     * @param saveIntervalSeconds how often user records are written
     */
    public OcGameServer(ServerConfig config, Path pluginsDirectory, Path dataDirectory,
            int saveIntervalSeconds) {
        this(config, pluginsDirectory, dataDirectory, saveIntervalSeconds,
                WebSocketConfig.defaults().withPort(0), false, WorldHost.DEFAULT_SEED);
    }

    /**
     * Creates a server that may also serve a browser client.
     *
     * <p>Loading happens before either socket opens, so a plugin that fails to come up cannot leave clients
     * talking to half a server. If the browser endpoint cannot bind, the game socket that was already bound is
     * closed again and this constructor's {@link #start()} throws, rather than leaving a server that is
     * half listening.
     *
     * <p>The world follows the endpoint: this form hosts one world with {@link WorldHost#DEFAULT_SEED}
     * whenever the endpoint port is not {@code 0}, and hosts none when the browser endpoint is disabled.
     * Use the seven argument form to choose the seed or to switch the world off explicitly.
     *
     * @param config              the game socket and framing limits
     * @param pluginsDirectory    where plugin jars live
     * @param dataDirectory       server owned state, user records today
     * @param saveIntervalSeconds how often user records are written
     * @param webSocketConfig     the browser endpoint settings; a port of {@code 0} disables the endpoint
     */
    public OcGameServer(ServerConfig config, Path pluginsDirectory, Path dataDirectory,
            int saveIntervalSeconds, WebSocketConfig webSocketConfig) {
        this(config, pluginsDirectory, dataDirectory, saveIntervalSeconds, webSocketConfig,
                webSocketConfig.port() != 0, WorldHost.DEFAULT_SEED);
    }

    /**
     * Creates a server with explicit world settings.
     *
     * <p>A world is only started when it is enabled <em>and</em> the browser endpoint has a non-zero port:
     * with no HTTP endpoint there is nothing that could serve a snapshot, and starting a tick thread for no
     * reader would be waste. The constructor only remembers the decision; {@link #start()} builds and starts
     * the {@link WorldHost}, and {@link #stop()} stops it before the final save.
     *
     * @param config              the game socket and framing limits
     * @param pluginsDirectory    where plugin jars live
     * @param dataDirectory       server owned state, user records today
     * @param saveIntervalSeconds how often user records are written
     * @param webSocketConfig     the browser endpoint settings; a port of {@code 0} disables the endpoint
     * @param worldEnabled        whether to host a world (ignored when the endpoint port is {@code 0})
     * @param worldSeed           seed of the hosted world
     */
    public OcGameServer(ServerConfig config, Path pluginsDirectory, Path dataDirectory,
            int saveIntervalSeconds, WebSocketConfig webSocketConfig, boolean worldEnabled, long worldSeed) {
        this.config = Objects.requireNonNull(config, "config");
        this.pluginsDirectory = Objects.requireNonNull(pluginsDirectory, "pluginsDirectory")
                .toAbsolutePath().normalize();
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory")
                .toAbsolutePath().normalize();
        if (saveIntervalSeconds <= 0) {
            throw new IllegalArgumentException("save interval must be positive, was " + saveIntervalSeconds);
        }
        this.saveIntervalSeconds = saveIntervalSeconds;
        this.webSocketConfig = Objects.requireNonNull(webSocketConfig, "webSocketConfig");
        this.worldEnabled = worldEnabled;
        this.worldSeed = worldSeed;
        this.auth = new AuthService(LocalUserStore.inDirectory(this.dataDirectory));
        // One registry, two owners: the loader hands it to every plugin through the PluginContext, and the
        // socket consults it for REQ. A plugin loaded here is therefore reachable the moment a client logs
        // in, without the server needing to know which plugin registered what.
        this.requests = new RequestRegistry();
        this.pluginLoader = new PluginLoader(this.pluginsDirectory, VERSION, this.dataDirectory, this.requests);
        // The world is built in start(), after this constructor, so the dispatcher gets a supplier that
        // reads the volatile field per command rather than a host instance captured too early.
        this.server = new GameServer(config, auth, this.requests, () -> this.worldHost);
    }

    /** The authentication service; plugins may reach it through a context of their own later. */
    public AuthService auth() {
        return auth;
    }

    /**
     * The request surface plugins register into and the socket relays to.
     *
     * <p>Exposed so a diagnostic tool can report what a running server answers under {@code REQ} without
     * having to ask a client: the CLI of the debug plugin uses it that way.
     *
     * @return the live registry, never {@code null}
     */
    public RequestRegistry requests() {
        return requests;
    }

    public GameServer server() {
        return server;
    }

    /**
     * The running world host, or {@code null} when the world is disabled or the server is not started.
     *
     * <p>Exposed for diagnostics and for a check that wants to assert what the endpoint is publishing; the
     * HTTP route reads it on every request, so a caller must not keep the reference across a restart.
     *
     * @return the live host, or {@code null}
     */
    public WorldHost worldHost() {
        return worldHost;
    }

    public Path pluginsDirectory() {
        return pluginsDirectory;
    }

    public Path dataDirectory() {
        return dataDirectory;
    }

    /** Plugins loaded at startup, in load order. */
    public List<Plugin> plugins() {
        return plugins;
    }

    /** The port the socket is bound to; only meaningful after {@link #start()}. */
    public int port() {
        return server.port();
    }

    /**
     * The port of the browser facing endpoint.
     *
     * <p>After a successful {@link #start()} this is the port the operating system chose when the
     * configuration asked for {@code 0}. Before starting it is the configured port, and it stays {@code 0}
     * when the endpoint is disabled, which is how a caller can tell the two cases apart without inspecting
     * the configuration.
     *
     * @return the bound or configured HTTP port, or {@code 0} when the endpoint is disabled
     */
    public int httpPort() {
        HttpWebServer browser = webServer;
        return browser == null ? webSocketConfig.port() : browser.port();
    }

    /**
     * Chooses the catalogue the hosted world will run.
     *
     * <p><strong>First one wins, deliberately.</strong> When several plugins register world content, the one
     * that registered first is served and the later ones are ignored; when none registers,
     * {@link ScenarioWorldContent} keeps the historical sample world alive.
     *
     * <p>Why first and not last: registration order is load order, load order is the sorted jar-file order of
     * the plugin folder, and that makes "which plugin owns this server's world" a visible, reviewable property
     * of the installation - the plugin folder shows it, and an operator can pin a world by naming its jar so
     * that it sorts first. Last-one-wins would hide the decision inside load timing: adding an unrelated
     * content plugin whose jar happens to sort later would silently swap the whole game a running server
     * serves, with nothing in the installation that says so. First-wins also means a server that keeps its
     * existing plugin folder keeps its existing world when new content is added beside it.
     *
     * <p>The decision is made once, in {@link #start()}, before any socket is bound; a content that cannot
     * build its catalogue therefore fails startup instead of serving a half world.
     *
     * @param registered the content the plugins registered, in load order
     * @return the first registered catalogue, or a fresh {@link ScenarioWorldContent} when none was registered
     */
    public static WorldContent selectWorldContent(List<WorldContent> registered) {
        Objects.requireNonNull(registered, "registered");
        return registered.isEmpty() ? new ScenarioWorldContent() : registered.get(0);
    }

    /**
     * Chooses the tick hooks the hosted world will run.
     *
     * <p><strong>All of them, in load order.</strong> The world content seam is first-one-wins because a
     * world can only be one game; a tick hook is not a claim on the world but a contribution to it - the
     * classic plugin's income and its enemy AI are two hooks that must both run - so no registration is
     * discarded. The order is plugin load order, which is the sorted jar-file order of the plugin folder,
     * and within one plugin the order its {@code onLoad} registered them in. That makes the hooks of a
     * world as visible in the installation as the world itself: moving a jar moves its hooks, and a hook
     * that must run before another one can be ordered by registering it first.
     *
     * <p>Every hook sees the same world and the same tick number, one after another, on the world thread.
     * Because they share the world, a hook's writes are visible to the hooks that run after it in the same
     * step, which is the property that lets an income hook pay into a warehouse before an AI hook spends
     * the gold.
     *
     * <p>The decision is made once, in {@link #start()}, before any socket is bound: the hooks are copied
     * into the {@link WorldHost}, so a plugin unloaded later cannot tear a hook out of a running world.
     *
     * @param registered the hooks the plugins registered, in load order
     * @return every registered hook, in the same order; empty when none was registered
     */
    public static List<WorldTickHook> selectWorldTickHooks(List<WorldTickHook> registered) {
        Objects.requireNonNull(registered, "registered");
        return List.copyOf(registered);
    }

    /**
     * Loads the plugins and starts listening. Loading happens before the socket opens so a plugin that fails
     * to come up cannot leave clients talking to half a server.
     */
    public void start() throws IOException {
        if (started) {
            return;
        }
        plugins = pluginLoader.loadAll();
        for (String problem : pluginLoader.problems()) {
            LOG.log(System.Logger.Level.WARNING, "plugin problem: " + problem);
        }
        // Build the world before any socket opens: a broken catalogue is then a startup failure that leaves
        // no half listening server, exactly like a plugin that cannot load. The catalogue is the one the
        // plugins offered, or the engine's sample catalogue when no plugin claimed the world.
        WorldContent content = selectWorldContent(pluginLoader.worldContents());
        WorldHost host = worldEnabled && webSocketConfig.port() != 0
                ? new WorldHost(worldSeed, content, selectWorldTickHooks(pluginLoader.worldTickHooks()))
                : null;
        server.start();
        if (host != null) {
            try {
                host.start();
            } catch (RuntimeException failure) {
                server.stop();
                throw failure;
            }
            worldHost = host;
        }
        if (webSocketConfig.port() != 0) {
            HttpWebServer browser = new HttpWebServer(webSocketConfig,
                    new CommandWebSocketHandler(server.dispatcher()), pluginLoader, host);
            try {
                browser.start();
            } catch (IOException failure) {
                // A half started server would leave the game socket bound with no browser route, so undo the
                // world, the socket and the plugins and report the bind failure as the reason start() failed.
                if (host != null) {
                    host.stop();
                }
                worldHost = null;
                server.stop();
                try {
                    pluginLoader.unloadAll();
                } catch (RuntimeException stopFailure) {
                    LOG.log(System.Logger.Level.WARNING,
                            "a plugin failed while undoing a failed startup", stopFailure);
                }
                throw failure;
            }
            webServer = browser;
        }
        saver = Executors.newSingleThreadScheduledExecutor(daemonFactory("oc-autosave"));
        saver.scheduleWithFixedDelay(this::saveQuietly, saveIntervalSeconds, saveIntervalSeconds,
                TimeUnit.SECONDS);
        started = true;
        LOG.log(System.Logger.Level.INFO, () -> "Omni Com server " + VERSION + " listening on port " + server.port()
                + " with " + plugins.size() + " plugin(s) and " + auth.userCount() + " account(s)"
                + (webServer == null ? "" : ", browser endpoint on port " + webServer.port()));
    }

    /** Stops the timer, disables the plugins, closes the sockets and writes the user records. */
    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        if (saver != null) {
            saver.shutdownNow();
            saver = null;
        }
        try {
            pluginLoader.unloadAll();
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "a plugin failed while shutting down", failure);
        }
        server.stop();
        HttpWebServer browser = webServer;
        webServer = null;
        if (browser != null) {
            browser.stop();
        }
        // Stop the tick thread after the endpoint that reads it and before the final save, so no request can
        // observe a world that is shutting down halfway through its last tick.
        WorldHost host = worldHost;
        worldHost = null;
        if (host != null) {
            host.stop();
        }
        saveQuietly();
    }

    private void saveQuietly() {
        try {
            auth.save();
        } catch (RuntimeException failure) {
            // Losing one save must not kill the server; the next tick tries again.
            LOG.log(System.Logger.Level.WARNING, "could not save user records", failure);
        }
    }

    private static ThreadFactory daemonFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    /** Parses the command line; see {@link #main(String[])} for the accepted options. */
    public static Map<String, String> parseArguments(String[] args) {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("unexpected argument '" + arg + "'");
            }
            String key = arg.substring(2);
            String value = "true";
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                value = args[++i];
            }
            parsed.put(key, value);
        }
        return parsed;
    }

    /**
     * Reads the {@code --world} switch. {@code on}/{@code true} and {@code off}/{@code false} are accepted;
     * anything else is a command line error rather than a silent "on".
     */
    private static boolean parseWorldOption(String value) {
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "on", "true" -> true;
            case "off", "false" -> false;
            default -> throw new IllegalArgumentException("--world must be on or off, was '" + value + "'");
        };
    }

    /**
     * Parses and validates a {@code --ws-idle} value: a positive whole number of seconds.
     *
     * <p>The command line speaks seconds because that is how an operator reasons about an idle timeout; the
     * socket layer speaks milliseconds because that is what {@link java.net.Socket#setSoTimeout(int)}
     * takes. The conversion and its range check live here so {@code main} and a self check exercise the
     * same rule. Zero is rejected for the same reason {@link WebSocketConfig} rejects a zero timeout: it
     * would make every connection expire immediately instead of never.
     *
     * @param seconds the raw option value
     * @return the equivalent timeout in milliseconds
     * @throws IllegalArgumentException when the value is not a positive whole number of seconds, or is so
     *         large that the millisecond value cannot be represented as an {@code int}
     */
    public static int parseWsIdleMillis(String seconds) {
        long parsed;
        try {
            parsed = Long.parseLong(seconds.strip());
        } catch (NumberFormatException malformed) {
            throw new IllegalArgumentException("--ws-idle must be a whole number of seconds, was '"
                    + seconds + "'", malformed);
        }
        if (parsed < 1L) {
            throw new IllegalArgumentException("--ws-idle must be positive, was " + parsed);
        }
        if (parsed > Integer.MAX_VALUE / 1_000L) {
            throw new IllegalArgumentException("--ws-idle is too large, was " + parsed
                    + " seconds (the socket timeout is an int of milliseconds)");
        }
        return (int) (parsed * 1_000L);
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArguments(args);
        if (options.containsKey("help")) {
            System.out.println("""
                    Omni Com game server

                      --port N            listening port, 0 picks a free one (default 25566)
                      --bind ADDRESS      interface to bind (default 0.0.0.0)
                      --plugins DIRECTORY where plugin jars live (default plugins)
                      --data DIRECTORY    server owned state, user records today (default data)
                      --save-seconds N    how often user records are written (default 60)
                      --http-port N       browser facing HTTP/WebSocket port, 0 disables it (default 25567)
                      --static-root DIR   directory the HTTP route serves (default web-client/dist)
                      --ws-idle N         WebSocket idle timeout in seconds (default 300, must be positive)
                      --world on|off      start the server side world (default on; off answers 503)
                      --seed N            seed of the server side world (default 1)
                      --help              this text
                    """);
            return;
        }
        int port = Integer.parseInt(options.getOrDefault("port", "25566"));
        Path plugins = Path.of(options.getOrDefault("plugins", DEFAULT_PLUGINS_DIRECTORY.toString()));
        Path data = Path.of(options.getOrDefault("data", DEFAULT_DATA_DIRECTORY.toString()));
        int saveSeconds = Integer.parseInt(options.getOrDefault("save-seconds", "60"));
        int httpPort = Integer.parseInt(options.getOrDefault("http-port",
                Integer.toString(WebSocketConfig.DEFAULT_PORT)));
        String staticRoot = options.getOrDefault("static-root", WebSocketConfig.DEFAULT_STATIC_ROOT);
        int wsIdleMillis = parseWsIdleMillis(options.getOrDefault("ws-idle",
                Integer.toString(DEFAULT_WS_IDLE_SECONDS)));
        boolean worldEnabled = parseWorldOption(options.getOrDefault("world", "on")) && httpPort != 0;
        long worldSeed = Long.parseLong(options.getOrDefault("seed",
                Long.toString(WorldHost.DEFAULT_SEED)));
        ServerConfig config = ServerConfig.defaults().withPort(port);
        WebSocketConfig webConfig = WebSocketConfig.defaults()
                .withPort(httpPort)
                .withStaticRoot(staticRoot)
                .withIdleTimeoutMillis(wsIdleMillis);
        if (options.containsKey("bind")) {
            config = new ServerConfig(port, options.get("bind"), config.backlog(), config.maxClients(),
                    config.readTimeoutMillis(), config.maxFrameBytes(), config.maxLineLength());
            webConfig = webConfig.withBindAddress(options.get("bind"));
        }

        OcGameServer server = new OcGameServer(config, plugins, data, saveSeconds, webConfig,
                worldEnabled, worldSeed);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "oc-shutdown"));
        System.out.println("Omni Com " + VERSION + " listening on " + config.bindAddress() + ":" + server.port());
        System.out.println("plugins: " + server.plugins().size() + " from " + server.pluginsDirectory());
        System.out.println("accounts: " + server.auth().userCount() + ", records in " + server.dataDirectory());
        if (server.httpPort() == 0) {
            System.out.println("browser endpoint: disabled (--http-port 0)");
        } else {
            System.out.println("browser endpoint: http://" + config.bindAddress() + ":" + server.httpPort()
                    + "/ (static root " + webConfig.staticRoot() + ", ws idle "
                    + (webConfig.idleTimeoutMillis() / 1_000) + "s)");
        }
        // The acceptor threads are daemons, so this latch is what keeps the process alive.
        new CountDownLatch(1).await();
    }
}
