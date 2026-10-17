package io.github.sob1234509876.oc.server.ws;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One plugin's {@code assets/manifest.json}, read far enough to rewrite it for the browser.
 *
 * <p>The document is the same schema the web client already parses for the old local {@code atlas.json},
 * with the one rule the browser needs on top of it: {@code image} names a file inside the plugin's own
 * {@code assets/} directory and is turned into a URL by the server, so the manifest itself never has to
 * know where the server is listening. Nothing else in the document is interpreted here.
 *
 * <p><strong>Why the frames are not parsed.</strong> {@code tiles} and {@code entities} are validated by the
 * client that draws them, deeply and defensively, and a second half-parser on the server would be a second
 * place for the two schemas to drift apart. The server therefore checks the skeleton - an object, a usable
 * image name, an object for each of the two frame tables - and forwards those two tables byte for byte.
 * A manifest that cannot even be scanned is skipped with a log line instead of being guessed at.
 *
 * <p><strong>Why a hand written scanner.</strong> The module builds offline with zero third-party
 * dependencies, so there is no JSON library to call. The scanner only has to understand the shape of a JSON
 * document - strings with escapes, nested objects and arrays, literals - and it deliberately does not build
 * a value tree: a member's raw text is kept so that a number such as {@code 0.75} and a nested table reach
 * the browser exactly as the plugin author wrote them.
 */
final class PluginManifest {

    /** The resource name a plugin publishes its asset manifest under. */
    static final String RESOURCE = "assets/manifest.json";

    /** The value the client assumes when a manifest omits {@code tilePixels}; mirrors the client default. */
    private static final String DEFAULT_TILE_PIXELS = "32";

    /** An empty frame table, used when a manifest omits {@code tiles} or {@code entities}. */
    private static final String EMPTY_TABLE = "{}";

    private final String image;
    private final String tilePixels;
    private final String tiles;
    private final String entities;

    private PluginManifest(String image, String tilePixels, String tiles, String entities) {
        this.image = image;
        this.tilePixels = tilePixels;
        this.tiles = tiles;
        this.entities = entities;
    }

    /**
     * Reads and checks one manifest.
     *
     * <p>UTF-8 is decoded strictly and a leading byte order mark is tolerated, because the file is usually
     * produced by an editor on Windows. A document that is not a JSON object, has no usable {@code image},
     * or keeps something other than an object in {@code tiles} or {@code entities} is refused: the caller
     * skips that plugin and serves the rest, so one bad manifest costs only its own content.
     *
     * @param bytes the exact bytes of {@code assets/manifest.json}
     * @return the parsed manifest
     * @throws InvalidManifestException if the document cannot be used, with a one line reason
     */
    static PluginManifest parse(byte[] bytes) throws InvalidManifestException {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException failure) {
            throw new InvalidManifestException("it is not valid UTF-8");
        }
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        Map<String, String> members = members(text);
        String image = imageOf(members.get("image"));
        String tilePixels = members.get("tilePixels");
        if (tilePixels == null || !isNumber(tilePixels)) {
            tilePixels = DEFAULT_TILE_PIXELS;
        }
        String tiles = tableOf("tiles", members.get("tiles"));
        String entities = tableOf("entities", members.get("entities"));
        return new PluginManifest(image, tilePixels, tiles, entities);
    }

    /** The validated file name of the plugin's atlas image, relative to its {@code assets/} directory. */
    String image() {
        return image;
    }

    /**
     * Renders this manifest as one element of the merged array the server publishes.
     *
     * <p>The shape is fixed: {@code plugin}, the rewritten {@code atlasUrl}, then the three fields the
     * client needs. {@code image} is dropped on purpose - the URL already names the file, and a browser that
     * had to resolve a bare name against the manifest URL would need to know the plugin id anyway.
     *
     * @param pluginId the id the asset route addresses this plugin by
     * @param atlasUrl the absolute path of the image on this server
     * @return one JSON object, without a trailing comma or newline
     */
    String toEntry(String pluginId, String atlasUrl) {
        return "{\"plugin\":" + quote(pluginId)
                + ",\"atlasUrl\":" + quote(atlasUrl)
                + ",\"tilePixels\":" + tilePixels
                + ",\"tiles\":" + tiles
                + ",\"entities\":" + entities
                + '}';
    }

    /** Validates the {@code image} member and returns it as a plain file name. */
    private static String imageOf(String raw) throws InvalidManifestException {
        if (raw == null || raw.isEmpty() || raw.charAt(0) != '"') {
            throw new InvalidManifestException("it has no string 'image' member");
        }
        int[] end = new int[1];
        String image = readString(raw, 0, end);
        if (end[0] != raw.length()) {
            throw new InvalidManifestException("its 'image' member is not a plain string");
        }
        if (image.isEmpty() || image.length() > 255) {
            throw new InvalidManifestException("its 'image' member is not a usable file name");
        }
        for (int index = 0; index < image.length(); index++) {
            char character = image.charAt(index);
            if (character < 0x20 || character == 0x7F || "/\\:".indexOf(character) >= 0) {
                throw new InvalidManifestException("its 'image' member must be a file name inside assets/");
            }
        }
        if (image.contains("..")) {
            throw new InvalidManifestException("its 'image' member must not contain '..'");
        }
        return image;
    }

    /** Requires an optional frame table to be an object, otherwise substituting an empty one. */
    private static String tableOf(String name, String raw) throws InvalidManifestException {
        if (raw == null) {
            return EMPTY_TABLE;
        }
        if (raw.isEmpty() || raw.charAt(0) != '{' || raw.charAt(raw.length() - 1) != '}') {
            throw new InvalidManifestException("its '" + name + "' member is not an object");
        }
        return raw;
    }

    /**
     * Splits a JSON object into member name and raw value text, preserving the written order.
     *
     * <p>Only the top level is dissected; a value is skipped as one balanced run, so nested frame tables
     * cost one pass and no allocation. Duplicate names keep the last value, matching how every JSON reader
     * the client could use would behave.
     */
    private static Map<String, String> members(String text) throws InvalidManifestException {
        Map<String, String> members = new LinkedHashMap<>();
        int index = skipWhitespace(text, 0);
        if (index >= text.length() || text.charAt(index) != '{') {
            throw new InvalidManifestException("its top level is not a JSON object");
        }
        index = skipWhitespace(text, index + 1);
        if (index < text.length() && text.charAt(index) == '}') {
            index++;
        } else {
            while (true) {
                index = skipWhitespace(text, index);
                if (index >= text.length() || text.charAt(index) != '"') {
                    throw new InvalidManifestException("a member name is not a string");
                }
                int[] nameEnd = new int[1];
                String name = readString(text, index, nameEnd);
                index = skipWhitespace(text, nameEnd[0]);
                if (index >= text.length() || text.charAt(index) != ':') {
                    throw new InvalidManifestException("member '" + name + "' has no ':' after its name");
                }
                index = skipWhitespace(text, index + 1);
                int valueEnd = skipValue(text, index);
                members.put(name, text.substring(index, valueEnd).strip());
                index = skipWhitespace(text, valueEnd);
                if (index >= text.length()) {
                    throw new InvalidManifestException("the object ends before its closing '}'");
                }
                char separator = text.charAt(index);
                if (separator == ',') {
                    index++;
                    continue;
                }
                if (separator == '}') {
                    index++;
                    break;
                }
                throw new InvalidManifestException("unexpected '" + separator + "' between members");
            }
        }
        if (skipWhitespace(text, index) != text.length()) {
            throw new InvalidManifestException("it has text after its top level object");
        }
        return members;
    }

    /** Skips one JSON value and returns the index just past it. */
    private static int skipValue(String text, int start) throws InvalidManifestException {
        if (start >= text.length()) {
            throw new InvalidManifestException("a member has no value");
        }
        char first = text.charAt(start);
        if (first == '"') {
            int[] end = new int[1];
            readString(text, start, end);
            return end[0];
        }
        if (first == '{' || first == '[') {
            return skipContainer(text, start);
        }
        int index = start;
        while (index < text.length() && isLiteralCharacter(text.charAt(index))) {
            index++;
        }
        if (index == start) {
            throw new InvalidManifestException("a member value is not JSON");
        }
        return index;
    }

    /** Skips a balanced object or array, ignoring brackets that appear inside strings. */
    private static int skipContainer(String text, int start) throws InvalidManifestException {
        int depth = 0;
        int index = start;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (character == '"') {
                int[] end = new int[1];
                readString(text, index, end);
                index = end[0];
                continue;
            }
            if (character == '{' || character == '[') {
                depth++;
            } else if (character == '}' || character == ']') {
                depth--;
                if (depth == 0) {
                    return index + 1;
                }
            }
            index++;
        }
        throw new InvalidManifestException("an object or array is never closed");
    }

    /**
     * Reads one JSON string, decoding its escapes, and reports the index just past the closing quote.
     *
     * <p>The decoded value is only needed for member names and for {@code image}; every other value is
     * forwarded as raw text, so nothing else is re-encoded here.
     */
    private static String readString(String text, int start, int[] endOut) throws InvalidManifestException {
        StringBuilder value = new StringBuilder();
        int index = start + 1;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (character == '"') {
                endOut[0] = index + 1;
                return value.toString();
            }
            if (character == '\\') {
                index++;
                if (index >= text.length()) {
                    break;
                }
                char escape = text.charAt(index);
                switch (escape) {
                    case '"' -> value.append('"');
                    case '\\' -> value.append('\\');
                    case '/' -> value.append('/');
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> {
                        if (index + 4 >= text.length()) {
                            throw new InvalidManifestException("a '\\u' escape is truncated");
                        }
                        int code = 0;
                        for (int digit = 1; digit <= 4; digit++) {
                            int hex = Character.digit(text.charAt(index + digit), 16);
                            if (hex < 0) {
                                throw new InvalidManifestException("a '\\u' escape is not hexadecimal");
                            }
                            code = (code << 4) | hex;
                        }
                        value.append((char) code);
                        index += 4;
                    }
                    default -> throw new InvalidManifestException("a string has an unknown escape");
                }
                index++;
                continue;
            }
            if (character < 0x20) {
                throw new InvalidManifestException("a string contains an unescaped control character");
            }
            value.append(character);
            index++;
        }
        throw new InvalidManifestException("a string is never closed");
    }

    /** Whether a raw value has the shape of a JSON number, which is all {@code tilePixels} has to be. */
    private static boolean isNumber(String raw) {
        return raw.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");
    }

    /** The characters a JSON literal (number, {@code true}, {@code false}, {@code null}) is made of. */
    private static boolean isLiteralCharacter(char character) {
        return (character >= '0' && character <= '9') || (character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z') || "+-.".indexOf(character) >= 0;
    }

    private static int skipWhitespace(String text, int start) {
        int index = start;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (character != ' ' && character != '\t' && character != '\r' && character != '\n') {
                return index;
            }
            index++;
        }
        return index;
    }

    /**
     * Encodes a string for the merged document.
     *
     * <p>Only the characters JSON requires are escaped; everything else is emitted as UTF-8, which the
     * response declares. Plugin names and generated URLs are ASCII in practice, but a plugin may report a
     * name in another script and the document must stay valid either way.
     */
    private static String quote(String value) {
        StringBuilder quoted = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\b' -> quoted.append("\\b");
                case '\f' -> quoted.append("\\f");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (character < 0x20) {
                        quoted.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        quoted.append(character);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }

    /** A manifest that cannot be used, with a reason short enough for one log line. */
    static final class InvalidManifestException extends Exception {

        private static final long serialVersionUID = 1L;

        InvalidManifestException(String reason) {
            super(reason, null, false, false);
        }
    }
}
