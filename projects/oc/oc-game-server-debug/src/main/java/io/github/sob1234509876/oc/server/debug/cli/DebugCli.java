package io.github.sob1234509876.oc.server.debug.cli;

import io.github.sob1234509876.oc.server.debug.DebugEnvelope;
import io.github.sob1234509876.oc.server.debug.DebugPlugin;
import io.github.sob1234509876.oc.server.net.GameClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The command line client of the debug plugin: it logs in, asks for {@code engine-info} and prints the
 * report.
 *
 * <p>The CLI is a client of the plugin and nothing more. It speaks the ordinary game protocol over the same
 * encrypted channel a player uses - {@code LOGIN} first, then {@code REQ engine-info <base64 envelope>} - so
 * the server needs no second listener, no second port and no special case for a debug tool. What travels
 * inside the envelope is sealed with the key the operator copied from the server, which is why the CLI is the
 * only party besides the plugin that can read a report.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * // On the server machine, once: the plugin creates data/oc-debug/debug.key on its first start.
 * // Copy that file to the client machine, then:
 * java -m oc.projects.oc.game.server.debug/io.github.sob1234509876.oc.server.debug.cli.DebugCli \
 *         --host 127.0.0.1 --port 25566 --user admin --password 'secret' \
 *         --key C:\keys\oc-debug.key
 * }</pre>
 *
 * <p>{@code --json} prints the report as one JSON object instead of {@code key: value} lines. Every failure
 * exits with a non-zero status and one diagnostic line on stderr, so the CLI can be used from a script:
 * a missing key file, a refused login, an unknown request name, a bad authentication tag and a timestamp
 * outside the window all name themselves.
 *
 * <p>The password is taken from the command line, which is the operator's own machine and shell; the CLI
 * never writes it anywhere, never puts it into a message and never logs it. The key is read from the file
 * and never printed.
 */
public final class DebugCli {

    /** Default host: the debug plugin is normally driven on the machine that runs the server. */
    private static final String DEFAULT_HOST = "127.0.0.1";

    /** Default port, matching {@code ServerConfig.DEFAULT_PORT}. */
    private static final int DEFAULT_PORT = 25566;

    /** Exit status for every failure of a run; zero means the report was printed. */
    private static final int EXIT_FAILURE = 1;

    /** What the key file must look like: 32 bytes as hexadecimal text. */
    private static final Pattern KEY_TEXT = Pattern.compile("[0-9a-fA-F]{64}");

    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final Path keyFile;
    private final boolean json;

    private DebugCli(String host, int port, String user, String password, Path keyFile, boolean json) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
        this.keyFile = keyFile;
        this.json = json;
    }

    /**
     * Runs one report request.
     *
     * @param args the command line; see the class comment
     */
    public static void main(String[] args) {
        Map<String, String> options = parseArguments(args);
        if (options.containsKey("help")) {
            System.out.println(usage());
            return;
        }
        try {
            System.out.println(fetchReport(options));
        } catch (IllegalArgumentException | UncheckedIOException | IOException failure) {
            // Every failure path ends here, with one line an operator can act on and a non-zero status, so
            // a script can tell a successful report from a refusal without parsing stdout.
            System.err.println("debug-cli: " + failure.getMessage());
            System.exit(EXIT_FAILURE);
        }
    }

    /**
     * Fetches one report for the given options, without printing it and without ever calling
     * {@link System#exit(int)}.
     *
     * <p>This is the whole CLI as a function, which is what makes it callable from the self check: a method
     * that printed its result and terminated the JVM could not be asserted on, and the exit status is a
     * presentation detail of {@link #main(String[])} rather than part of asking the server a question.
     *
     * @param options the parsed command line; the same keys {@link #parseArguments(String[])} produces
     * @return the report exactly as the plugin sent it, with a trailing newline removed
     * @throws IllegalArgumentException if an option is missing or unusable
     * @throws UncheckedIOException if the key file cannot be read
     * @throws IOException if the connection, the login or the request fails
     */
    public static String fetchReport(Map<String, String> options) throws IOException {
        return fromOptions(options).run();
    }

    /**
     * Parses the options, rejecting anything unknown instead of guessing.
     *
     * <p>{@code --help} and {@code --json} are flags, every other option needs a value. The parser is strict
     * on purpose: a mistyped option that silently became a default would send the operator looking for the
     * problem in the server's log.
     *
     * @param args the raw command line
     * @return the options, flags stored as {@code true}
     * @throws IllegalArgumentException if an option is unknown or missing its value
     */
    public static Map<String, String> parseArguments(String[] args) {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if (!argument.startsWith("--")) {
                throw new IllegalArgumentException("unexpected argument '" + argument + "'");
            }
            String key = argument.substring(2);
            if (key.equals("help") || key.equals("json")) {
                parsed.put(key, "true");
                continue;
            }
            if (!key.equals("host") && !key.equals("port") && !key.equals("user") && !key.equals("password")
                    && !key.equals("key")) {
                throw new IllegalArgumentException("unknown option '" + argument + "'; see --help");
            }
            if (index + 1 >= args.length) {
                throw new IllegalArgumentException("option '" + argument + "' needs a value");
            }
            parsed.put(key, args[++index]);
        }
        return parsed;
    }

    /** Checks the options and turns them into a runnable client. */
    private static DebugCli fromOptions(Map<String, String> options) {
        String user = options.get("user");
        String password = options.get("password");
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("--user is required");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("--password is required");
        }
        String key = options.get("key");
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("--key is required: copy debug.key from the server"
                    + " (see the plugin's log line for its path)");
        }
        String portText = options.getOrDefault("port", Integer.toString(DEFAULT_PORT));
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("--port must be a number, was '" + portText + "'");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("--port must be 1..65535, was " + port);
        }
        return new DebugCli(options.getOrDefault("host", DEFAULT_HOST), port, user, password, Path.of(key),
                options.containsKey("json"));
    }

    /**
     * Connects, logs in, asks for the report and returns it.
     *
     * <p>Each step fails with its own message, because the five ways this can go wrong look identical from
     * the outside: the connection is refused, the credentials are refused, the server has no such request
     * registered, the envelope does not open, or its timestamp is stale.
     *
     * @return the report payload, without its trailing newline
     * @throws IOException if the connection, the login or the request fails
     */
    private String run() throws IOException {
        byte[] key = readKey();
        String requestLine = json ? DebugPlugin.REQUEST_JSON : DebugPlugin.REQUEST_TEXT;
        byte[] envelope = DebugEnvelope.seal(requestLine.getBytes(StandardCharsets.UTF_8), key);

        try (GameClient client = GameClient.connect(host, port)) {
            String login = client.request("LOGIN " + user + " " + password);
            if (!login.startsWith("OK")) {
                throw new IOException("login as '" + user + "' was refused by " + host + ":" + port + ": "
                        + login);
            }
            String reply = client.request("REQ " + DebugPlugin.REQUEST_NAME + " "
                    + Base64.getEncoder().encodeToString(envelope));
            if (!reply.startsWith("OK ")) {
                throw new IOException("the server refused REQ " + DebugPlugin.REQUEST_NAME + ": " + reply
                        + " (is the debug plugin loaded and are you logged in?)");
            }
            byte[] sealed = Base64.getDecoder().decode(reply.substring("OK ".length()).strip());
            byte[] payload = DebugEnvelope.open(sealed, key, System.currentTimeMillis() / 1000L);
            return new String(payload, StandardCharsets.UTF_8).stripTrailing();
        }
    }

    /**
     * Reads the key file the operator copied from the server.
     *
     * <p>The format check lives here as well as on the server side because this is the copy most likely to
     * be wrong: a truncated transfer, a file that was edited, or the wrong file entirely. Failing here names
     * the file; failing later would name an authentication tag.
     */
    private byte[] readKey() {
        if (!Files.isRegularFile(keyFile)) {
            throw new UncheckedIOException(new IOException("no key file at " + keyFile.toAbsolutePath()
                    + "; copy the debug.key the server created into this machine"));
        }
        String text;
        try {
            text = Files.readString(keyFile, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            String message = "cannot read the key file " + keyFile + ": " + failure.getMessage();
            throw new UncheckedIOException(new IOException(message, failure));
        }
        String trimmed = text.strip();
        if (!KEY_TEXT.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("the key file " + keyFile + " is not 64 hexadecimal"
                    + " characters; copy the server's debug.key unchanged");
        }
        byte[] key = HexFormat.of().parseHex(trimmed.toLowerCase(Locale.ROOT));
        if (key.length != DebugEnvelope.KEY_BYTES) {
            throw new IllegalArgumentException("the key file " + keyFile + " holds " + key.length
                    + " bytes, expected " + DebugEnvelope.KEY_BYTES);
        }
        return key;
    }

    /** The help text, also printed for {@code --help}. */
    private static String usage() {
        return """
                Debug CLI of the Omni Com server's oc-debug plugin

                  --host ADDRESS   server to ask (default 127.0.0.1)
                  --port N         server port (default 25566)
                  --user NAME      account to log in with (required)
                  --password TEXT  password of that account (required)
                  --key FILE       the debug.key copied from the server (required)
                  --json           print the report as one JSON object
                  --help           this text

                The plugin answers only read-only information; it cannot change the server or the game.
                """;
    }
}
