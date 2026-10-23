package io.github.sob1234509876.oc.server.debug;

import io.github.sob1234509876.oc.googology.HyperOperation;
import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.googology.NaturalFormat;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.content.ContentRegistry;
import io.github.sob1234509876.oc.rts.content.TerrainType;
import io.github.sob1234509876.oc.rts.core.ChunkPos;
import io.github.sob1234509876.oc.rts.dimension.DimensionId;
import io.github.sob1234509876.oc.rts.dimension.DimensionProfile;
import io.github.sob1234509876.oc.rts.map.Chunk;
import io.github.sob1234509876.oc.rts.map.ChunkedMap;
import io.github.sob1234509876.oc.rts.map.FlatChunkGenerator;
import io.github.sob1234509876.oc.rts.map.InfiniteTerrainMap;
import io.github.sob1234509876.oc.rts.map.StreamingTerrainMap;
import io.github.sob1234509876.oc.server.plugin.Plugin;
import io.github.sob1234509876.oc.server.plugin.PluginContext;
import io.github.sob1234509876.oc.server.plugin.RequestHandler;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The Omni Com debug plugin: it only reports information, and it is the only thing this plugin does.
 *
 * <p>The server deliberately has no debug endpoint of its own. A reporting feature inside the server would
 * be a permanent, security sensitive hole in the engine - every operator would ship it, and every future
 * change to it would be a change to the server's attack surface. Making the feature a plugin turns that
 * around: the operator who wants it drops the jar into {@code plugins/}, the operator who does not deletes
 * it, and the server only carries the two small, passive interfaces this plugin needs
 * ({@code RequestHandler} and {@code RequestRegistry}).
 *
 * <h2>The security property, stated plainly</h2>
 *
 * <p>This plugin cannot reach a live game. {@link PluginContext} hands out a directory, a version string, a
 * logger and the request registry, and nothing else: there is no world, no engine, no user record and no
 * socket in it. The engine facts below are therefore computed from values this plugin constructs itself - a
 * throwaway {@code World} built over a flat generator inside the handler, read once and dropped - so the
 * report describes the engine version and its building blocks, not any running match.
 *
 * <p>What the plugin exposes is a read-only request named {@value #REQUEST_NAME}. It accepts exactly two
 * request lines, {@code INFO} and {@code INFO json}, and answers with text. Anything else is refused with
 * {@code ERR unsupported request}. There is no request that changes a file, a setting or a game object,
 * because there is nothing here to change: the handler holds no writable reference at all.
 *
 * <h2>Why the payload is encrypted again</h2>
 *
 * <p>The report is encrypted with AES-256-GCM in an {@link DebugEnvelope}, on top of the channel's own
 * encryption, using a key that lives only in {@code debug.key} and in the operator's copy of that file. The
 * channel already protects the bytes in transit; the envelope makes the report readable only by somebody who
 * holds the key, whoever terminates the channel. The key is created with {@link java.security.SecureRandom}
 * on first enable and is never regenerated behind the operator's back - see {@link DebugKeyFile}.
 *
 * <h2>Request and response</h2>
 *
 * <p>The client sends {@code REQ engine-info <base64 envelope>} after logging in, where the envelope seals
 * one of the two request lines. The response is a sealed envelope whose payload is a line oriented report in
 * the same two flavours:
 *
 * <pre>
 * INFO       -&gt; "key: value" lines, one fact per line
 * INFO json  -&gt; the same facts as one JSON object
 * anything else -&gt; "ERR unsupported request"
 * </pre>
 *
 * <p>A handler that throws is turned into {@code ERR request failed} by the server, and a payload the plugin
 * cannot open is answered with {@code ERR bad request} inside a valid envelope, so the CLI always has
 * something readable to print.
 */
public final class DebugPlugin implements Plugin {

    /** The one request name this plugin registers. */
    public static final String REQUEST_NAME = "engine-info";

    /** The request line that asks for the text report. */
    public static final String REQUEST_TEXT = "INFO";

    /** The request line that asks for the JSON report. */
    public static final String REQUEST_JSON = "INFO json";

    /** The report's own format version, so a CLI can refuse a report it does not understand. */
    private static final String SCHEMA = "1";

    private final AtomicLong servedRequests = new AtomicLong();

    private PluginContext context;
    private DebugKeyFile keyFile;

    @Override
    public String name() {
        return "oc-debug";
    }

    @Override
    public String version() {
        return "1.0";
    }

    @Override
    public String description() {
        return "Read-only debug report: answers the encrypted engine-info request and never changes state";
    }

    /**
     * Remembers the context and publishes the one request handler.
     *
     * <p>Registration happens here rather than in {@link #onEnable()} because the server opens its socket
     * only after every plugin has been loaded and enabled, so a handler registered at load time is reachable
     * through the very first client that connects. Nothing is published if a later step fails: the loader
     * calls {@link #onDisable()} on a plugin whose enable failed, which unregisters cleanly.
     *
     * @param context everything the plugin is given; see the class comment for why that is not much
     */
    @Override
    public void onLoad(PluginContext context) {
        this.context = Objects.requireNonNull(context, "context");
        // The key is loaded lazily, on the first request, so a server that never receives one never touches
        // the key file - but the data directory itself is created here, as a plugin is expected to do.
        context.dataDirectoryOf(name());
        context.requests().register(REQUEST_NAME, this::handleEngineInfo);
    }

    /**
     * Reports that the plugin is live.
     *
     * <p>The message names the key file and says what to do with it, because that is the one manual step of
     * the whole feature and the place an operator looks first when the CLI answers "bad tag". It never
     * prints the key itself.
     */
    @Override
    public void onEnable() {
        context.logger().log(System.Logger.Level.INFO, () -> "oc-debug ready; request '" + REQUEST_NAME
                + "' is active and the AES-256 key is " + keyPath()
                + " (copy that file to the machine that runs the debug CLI)");
    }

    /**
     * Withdraws the request, so a disabled plugin stops answering immediately instead of leaving a dangling
     * handler behind in a registry the server keeps using.
     */
    @Override
    public void onDisable() {
        if (context != null) {
            context.requests().unregister(REQUEST_NAME);
        }
    }

    /**
     * Answers one {@code engine-info} request and can do nothing else.
     *
     * <p>The method is the whole remote surface of this plugin, so it is worth reading as such: it opens the
     * envelope (which is what authenticates the caller), parses the plaintext line by exact comparison,
     * builds a report from constants and from a {@code World} that is created and dropped inside this call,
     * seals the answer and returns it. No field of the plugin is written, no engine object survives the call
     * and no request can name anything - the only input that reaches the report is the choice between text
     * and JSON.
     *
     * @param request the sealed envelope the client sent
     * @return a sealed envelope holding the report, or holding {@code ERR bad request}
     */
    private byte[] handleEngineInfo(byte[] request) {
        byte[] key = key().key();
        byte[] plaintext;
        try {
            plaintext = DebugEnvelope.open(request, key, System.currentTimeMillis() / 1000L);
        } catch (IllegalArgumentException refused) {
            // The reason goes to the operator's log, never to the client: the client either has the key and
            // sees a working reply, or it does not and learns nothing about why. The payload is not logged.
            context.logger().log(System.Logger.Level.WARNING,
                    "engine-info refused a request: " + refused.getMessage());
            return DebugEnvelope.seal(ENVELOPE_BAD_REQUEST, key);
        }
        String line = new String(plaintext, StandardCharsets.UTF_8);
        if (containsLineBreak(line)) {
            return DebugEnvelope.seal(ENVELOPE_BAD_REQUEST, key);
        }
        String trimmed = line.strip();
        if (!REQUEST_TEXT.equals(trimmed) && !REQUEST_JSON.equals(trimmed)) {
            return DebugEnvelope.seal(ENVELOPE_UNSUPPORTED, key);
        }
        servedRequests.incrementAndGet();
        boolean json = REQUEST_JSON.equalsIgnoreCase(trimmed);
        String report = json ? renderJson(facts()) : renderText(facts());
        return DebugEnvelope.seal(report.getBytes(StandardCharsets.UTF_8), key);
    }

    /** The payload of a refused envelope: short, fixed, and safe to send to anybody who holds the key. */
    private static final byte[] ENVELOPE_BAD_REQUEST =
            "ERR bad request".getBytes(StandardCharsets.UTF_8);

    /** The payload of an envelope whose plaintext asked for something this plugin does not do. */
    private static final byte[] ENVELOPE_UNSUPPORTED =
            "ERR unsupported request".getBytes(StandardCharsets.UTF_8);

    /** Whether the decrypted request tried to carry more than the single line this plugin accepts. */
    private static boolean containsLineBreak(String text) {
        return text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
    }

    /** The key, read from {@code debug.key} on first use and created there when the file is missing. */
    private synchronized DebugKeyFile key() {
        if (keyFile == null) {
            keyFile = DebugKeyFile.loadOrCreate(keyDirectory());
        }
        return keyFile;
    }

    /** This plugin's own data directory, {@code <server data>/oc-debug/}. */
    private Path keyDirectory() {
        return context.dataDirectory().resolve(name());
    }

    /** The key file, for a log line and for the report; never its content. */
    private Path keyPath() {
        return key().file();
    }

    // ------------------------------------------------------------------ the facts

    /**
     * Every fact the report contains, in the order it is printed.
     *
     * <p>A {@link LinkedHashMap} rather than a record because the two renderers must agree on order, and the
     * order is part of what an operator reads: the server and the plugin first, the machine next, the engine
     * last. The map is built fresh for each request, so no fact can be stale from an earlier one - which also
     * means the report's only stateful entries (uptime, heap, threads, request count) are read at the moment
     * the request is answered.
     *
     * @return the facts, in print order
     */
    private Map<String, String> facts() {
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("schema", SCHEMA);
        facts.put("server-version", context.serverVersion());
        facts.put("plugin", name());
        facts.put("plugin-version", version());
        facts.put("plugin-description", description());
        facts.put("plugin-read-only", "true");
        facts.put("plugin-data-directory", keyDirectory().toAbsolutePath().normalize().toString());
        facts.put("plugin-key-file", keyPath().toAbsolutePath().normalize().toString());
        facts.put("plugin-requests-registered", String.valueOf(context.requests().size()));
        facts.put("plugin-request-names", String.join(",", context.requests().names()));
        facts.put("plugin-requests-served", String.valueOf(servedRequests.get()));

        facts.put("jvm-vendor", System.getProperty("java.vendor", "unknown"));
        facts.put("jvm-name", System.getProperty("java.vm.name", "unknown"));
        facts.put("jvm-version", System.getProperty("java.version", "unknown"));
        facts.put("os-name", System.getProperty("os.name", "unknown"));
        facts.put("os-version", System.getProperty("os.version", "unknown"));
        facts.put("os-arch", System.getProperty("os.arch", "unknown"));
        facts.put("available-processors", String.valueOf(Runtime.getRuntime().availableProcessors()));
        facts.put("jvm-uptime-seconds", String.valueOf(uptimeSeconds()));

        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        facts.put("heap-used-bytes", String.valueOf(memory.getHeapMemoryUsage().getUsed()));
        facts.put("heap-max-bytes", String.valueOf(memory.getHeapMemoryUsage().getMax()));
        facts.put("live-thread-count", String.valueOf(ManagementFactory.getThreadMXBean().getThreadCount()));

        // One throwaway world, built here, read by the next call and dropped when this request ends.
        engineFacts(facts, flatWorld());
        return facts;
    }

    /**
     * The engine facts, all of which come from values this plugin builds itself.
     *
     * <p>Nothing here needs a running game, which is the point: the hyperoperation table, a
     * {@link Natural} rendered by {@link NaturalFormat} and a small throwaway {@link World} over
     * {@link FlatChunkGenerator} demonstrate what the engine on the server's class path can do without
     * touching - or being able to touch - a live match.
     *
     * @param facts the map to fill
     * @param world the throwaway world, created by the caller so exactly one is built per request
     */
    private static void engineFacts(Map<String, String> facts, World world) {
        facts.put("engine-hyperoperations", hyperOperations());

        // A ten tall power tower of tens: exact evaluation would be astronomically expensive, so the engine
        // keeps it structural, which is exactly the behaviour worth showing in a debug report.
        Natural tower = Natural.tetration(Natural.TEN, Natural.of(10));
        facts.put("engine-natural-value", "10^^10");
        facts.put("engine-natural-rendered", NaturalFormat.scientific(tower));
        facts.put("engine-natural-description", NaturalFormat.describe(tower));
        facts.put("engine-natural-hyperoperation", NaturalFormat.operationOf(tower)
                .map(HyperOperation::label)
                .orElse("none (the level is not a classic hyperoperation)"));

        // The report must work for the hosted world, which is an unbounded InfiniteTerrainMap. The map is
        // inspected through the streaming contract, never through the finite ChunkedMap type: every signed
        // tile coordinate exists, and the loaded count plus the budget describe the memory ceiling instead
        // of a map size that no longer exists.
        StreamingTerrainMap map = (StreamingTerrainMap) world.map();
        Chunk chunk = map.chunk(new ChunkPos(0, 0));
        facts.put("engine-world-dimensions", String.valueOf(world.dimensionCount()));
        facts.put("engine-world-entities", String.valueOf(world.entityCount()));
        facts.put("engine-world-tick", String.valueOf(world.tickNumber()));
        facts.put("engine-world-default-dimension",
                String.valueOf(world.defaultDimensionId()));
        facts.put("engine-world-terrain", world.map().terrainAt(0, 0).key().toString());
        facts.put("engine-chunk-size", String.valueOf(map.chunkSize()));
        facts.put("engine-chunk-terrain-count", chunk.count(world.map().terrainAt(0, 0)) + " of "
                + (chunk.size() * chunk.size()));
        facts.put("engine-chunks-loaded", String.valueOf(map.loadedChunkCount()));
        facts.put("engine-chunks-generated", String.valueOf(map.generatedChunkCount()));
        facts.put("engine-chunks-budget", String.valueOf(map.maxLoadedChunks()));
        facts.put("engine-map-bounded", String.valueOf(map instanceof ChunkedMap));
    }

    /** The hyperoperation levels the googology library names, as {@code rank:label notation} triples. */
    private static String hyperOperations() {
        StringBuilder text = new StringBuilder();
        for (HyperOperation operation : HyperOperation.values()) {
            if (text.length() > 0) {
                text.append(';');
            }
            text.append(operation.rank()).append(':').append(operation.label()).append(' ')
                    .append(operation.notation());
        }
        return text.toString();
    }

    /**
     * A throwaway world: one dimension, an unbounded flat grass map, no players and no systems.
     *
     * <p>Built inside the handler and dropped when the request ends; it is never ticked, never stored in a
     * field and never handed to anybody. The map is an {@link InfiniteTerrainMap} over a flat generator, the
     * same implementation the server hosts, so the report exercises the streaming/unbounded path instead of
     * the historical finite {@code ChunkedMap}. The generator is flat so the world costs a couple of tables
     * instead of a noise field, and the terrain comes from a content registry this method creates, so
     * nothing is borrowed from a running server.
     */
    private static World flatWorld() {
        ContentRegistry content = ContentRegistry.builder("debug")
                .terrain("grass").name("Grass").buildable().moveCost(1f)
                .build();
        World world = new World(0L, content);
        world.addDimension(DimensionId.overworld(), DimensionProfile.earth(),
                new InfiniteTerrainMap(new FlatChunkGenerator(content.terrain("grass"))));
        return world;
    }

    /** Whole seconds since the JVM started; never negative, because the start time comes from the JVM. */
    private static long uptimeSeconds() {
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        return Math.max(0L, uptime) / 1000L;
    }

    // ------------------------------------------------------------------ rendering

    /**
     * The text report: {@code key: value} per line, in fact order.
     *
     * <p>Line oriented because the operator reads it in a terminal and pipes it into {@code findstr} or
     * {@code grep}; the CLI prints it unchanged.
     */
    private static String renderText(Map<String, String> facts) {
        StringBuilder text = new StringBuilder(facts.size() * 48);
        for (Map.Entry<String, String> fact : facts.entrySet()) {
            text.append(fact.getKey()).append(": ").append(fact.getValue()).append('\n');
        }
        return text.toString();
    }

    /**
     * The JSON report: one object with the same keys, in the same order.
     *
     * <p>Hand written rather than produced by a library because the module has no dependencies and the shape
     * is flat: every value is a string or a number, no value is an array or a nested object, and every key is
     * a constant of this class. Values are escaped anyway, because two of them (the hyperoperation labels and
     * the natural number description) contain characters a JSON writer must not pass through raw.
     */
    private static String renderJson(Map<String, String> facts) {
        StringBuilder json = new StringBuilder(facts.size() * 56).append('{');
        boolean first = true;
        for (Map.Entry<String, String> fact : facts.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(escape(fact.getKey())).append("\":\"")
                    .append(escape(fact.getValue())).append('"');
        }
        return json.append('}').toString();
    }

    /** Escapes the characters JSON forbids in a string: quote, backslash and every control character. */
    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
