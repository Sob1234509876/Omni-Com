package io.github.sob1234509876.oc.server;

import io.github.sob1234509876.oc.server.auth.AuthService;
import io.github.sob1234509876.oc.server.auth.LocalUserStore;
import io.github.sob1234509876.oc.server.net.GameServer;
import io.github.sob1234509876.oc.server.net.ServerConfig;
import io.github.sob1234509876.oc.server.plugin.Plugin;
import io.github.sob1234509876.oc.server.plugin.PluginLoader;
import io.github.sob1234509876.oc.server.plugin.RequestRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
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
 * <p>The vanilla game is meant to be nothing special - it is a plugin like any other - so this class only
 * owns the parts a plugin must not own: the listening socket, the user records and the timer that writes them
 * to disk. Everything game shaped is loaded from {@code plugins/*.jar} and is given a data directory of its
 * own, so a plugin can be replaced or removed without touching the server.
 *
 * <p>Shutdown is deliberate about ordering: the autosave timer stops first, then plugins are disabled (before
 * the socket closes, so a plugin may still flush something), then the socket closes and finally the user
 * store is written once more. The user store is written on a timer as well as at shutdown, because a server
 * that crashes between two logins is a server that lost them.
 */
public final class OcGameServer {

    /** Version reported to clients and plugins. */
    public static final String VERSION = "2.0a";

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

    private final int saveIntervalSeconds;
    private ScheduledExecutorService saver;
    private List<Plugin> plugins = List.of();
    private volatile boolean started;

    public OcGameServer(ServerConfig config, Path pluginsDirectory, Path dataDirectory,
            int saveIntervalSeconds) {
        this.config = Objects.requireNonNull(config, "config");
        this.pluginsDirectory = Objects.requireNonNull(pluginsDirectory, "pluginsDirectory")
                .toAbsolutePath().normalize();
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory")
                .toAbsolutePath().normalize();
        if (saveIntervalSeconds <= 0) {
            throw new IllegalArgumentException("save interval must be positive, was " + saveIntervalSeconds);
        }
        this.saveIntervalSeconds = saveIntervalSeconds;
        this.auth = new AuthService(LocalUserStore.inDirectory(this.dataDirectory));
        // One registry, two owners: the loader hands it to every plugin through the PluginContext, and the
        // socket consults it for REQ. A plugin loaded here is therefore reachable the moment a client logs
        // in, without the server needing to know which plugin registered what.
        this.requests = new RequestRegistry();
        this.pluginLoader = new PluginLoader(this.pluginsDirectory, VERSION, this.dataDirectory, this.requests);
        this.server = new GameServer(config, auth, this.requests);
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
        server.start();
        saver = Executors.newSingleThreadScheduledExecutor(daemonFactory("oc-autosave"));
        saver.scheduleWithFixedDelay(this::saveQuietly, saveIntervalSeconds, saveIntervalSeconds,
                TimeUnit.SECONDS);
        started = true;
        LOG.log(System.Logger.Level.INFO, () -> "Omni Com server " + VERSION + " listening on port " + server.port()
                + " with " + plugins.size() + " plugin(s) and " + auth.userCount() + " account(s)");
    }

    /** Stops the timer, disables the plugins, closes the socket and writes the user records. */
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
                      --help              this text
                    """);
            return;
        }
        int port = Integer.parseInt(options.getOrDefault("port", "25566"));
        Path plugins = Path.of(options.getOrDefault("plugins", DEFAULT_PLUGINS_DIRECTORY.toString()));
        Path data = Path.of(options.getOrDefault("data", DEFAULT_DATA_DIRECTORY.toString()));
        int saveSeconds = Integer.parseInt(options.getOrDefault("save-seconds", "60"));
        ServerConfig config = ServerConfig.defaults().withPort(port);
        if (options.containsKey("bind")) {
            config = new ServerConfig(port, options.get("bind"), config.backlog(), config.maxClients(),
                    config.readTimeoutMillis(), config.maxFrameBytes(), config.maxLineLength());
        }

        OcGameServer server = new OcGameServer(config, plugins, data, saveSeconds);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "oc-shutdown"));
        System.out.println("Omni Com " + VERSION + " listening on " + config.bindAddress() + ":" + server.port());
        System.out.println("plugins: " + server.plugins().size() + " from " + server.pluginsDirectory());
        System.out.println("accounts: " + server.auth().userCount() + ", records in " + server.dataDirectory());
        // The acceptor threads are daemons, so this latch is what keeps the process alive.
        new CountDownLatch(1).await();
    }
}
