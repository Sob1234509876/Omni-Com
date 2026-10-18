package io.github.sob1234509876.oc.server.ws;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The request head of one HTTP/1.x request, read without buffering past its blank line.
 *
 * <p>The endpoint handles one request per connection and then closes it, so a request is only a method, a
 * target and a handful of headers. Parsing that inline in the server would bury the two routes in byte
 * juggling; this class keeps the splitting and the strictness in one place.
 *
 * <p><strong>Why the strictness.</strong> The same parser decides whether a request is a WebSocket upgrade,
 * and that decision must not be ambiguous. A request line that is not exactly {@code METHOD TARGET VERSION},
 * obsolete header folding, a control character in a header name or a head that never ends are all refused
 * with {@code 400} instead of being guessed at. In particular the parser reads the head byte by byte and
 * stops at the terminator, so bytes a client pipelines after the head are never consumed as headers and can
 * therefore never smuggle a second request into a WebSocket upgrade.
 *
 * <p>The head is decoded as UTF-8; browsers percent-encode path characters, but a local client may send a
 * raw UTF-8 file name, and refusing that would be surprising. Invalid UTF-8 in a header is a malformed
 * request, not something to replace with {@code U+FFFD}.
 */
final class HttpRequest {

    /** Largest accepted head, terminator included. Command headers are tiny; a big head is an attack. */
    private static final int MAX_HEAD_BYTES = 16 * 1024;

    /** Largest accepted number of header lines, for the same reason. */
    private static final int MAX_HEADER_LINES = 100;

    /** Characters that may not appear in an HTTP token such as a method or a header name. */
    private static final String TOKEN_SEPARATORS = "()<>@,;:\\\"/[]?={} \t";

    private final String method;
    private final String target;
    private final String version;
    private final Map<String, List<String>> headers;

    private HttpRequest(String method, String target, String version, Map<String, List<String>> headers) {
        this.method = method;
        this.target = target;
        this.version = version;
        this.headers = headers;
    }

    /**
     * Reads one request head from a socket stream.
     *
     * <p>The caller's stream is read one byte at a time on purpose: buffering ahead would swallow the first
     * WebSocket frame of an upgrade request, and the frame reader must see exactly the first byte after the
     * blank line.
     *
     * @param in the socket input
     * @return the parsed request, or {@code null} when the peer closed without sending anything
     * @throws HttpError   if the head is malformed or does not fit the limits
     * @throws IOException if the socket fails
     */
    static HttpRequest read(InputStream in) throws IOException {
        byte[] head = readHead(in);
        if (head == null) {
            return null;
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(head))
                    .toString();
        } catch (CharacterCodingException failure) {
            throw new HttpError(400, "the request head is not valid UTF-8");
        }

        // The terminator may be CRLF CRLF or, from a lenient hand written client, LF LF. Splitting on either
        // keeps both readable without accepting a bare CR in the middle of the head.
        String[] lines = text.split("\r\n|\n", -1);
        String[] requestLine = lines[0].split(" ", -1);
        if (requestLine.length != 3) {
            throw new HttpError(400, "the request line must be exactly METHOD TARGET VERSION");
        }
        String method = requestLine[0];
        String target = requestLine[1];
        String version = requestLine[2];
        if (!isToken(method)) {
            throw new HttpError(400, "the request method is not a token");
        }
        if (!target.startsWith("/")) {
            throw new HttpError(400, "only origin-form request targets are served");
        }
        if (!"HTTP/1.1".equals(version) && !"HTTP/1.0".equals(version)) {
            throw new HttpError(400, "unsupported HTTP version '" + version + "'");
        }

        Map<String, List<String>> headers = new LinkedHashMap<>();
        int headerCount = 0;
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];
            if (line.isEmpty()) {
                break;
            }
            if (line.charAt(0) == ' ' || line.charAt(0) == '\t') {
                throw new HttpError(400, "obsolete header folding is not supported");
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new HttpError(400, "a header line has no name separated by ':'");
            }
            String name = line.substring(0, colon).strip();
            if (!isToken(name)) {
                throw new HttpError(400, "a header name is not a token");
            }
            if (++headerCount > MAX_HEADER_LINES) {
                throw new HttpError(400, "the request carries more than " + MAX_HEADER_LINES
                        + " header lines");
            }
            String value = line.substring(colon + 1).strip();
            headers.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(value);
        }
        return new HttpRequest(method, target, version, headers);
    }

    /** Reads bytes until the blank line, or returns {@code null} when the peer sent nothing at all. */
    private static byte[] readHead(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream(512);
        boolean sawAnyByte = false;
        int last = -1;
        int previous = -1;
        int beforePrevious = -1;
        int beforeThat = -1;
        while (true) {
            int value = in.read();
            if (value < 0) {
                if (!sawAnyByte) {
                    return null;
                }
                throw new HttpError(400, "the request head ended before its blank line");
            }
            sawAnyByte = true;
            head.write(value);
            if (head.size() > MAX_HEAD_BYTES) {
                throw new HttpError(400, "the request head exceeds " + MAX_HEAD_BYTES + " bytes");
            }
            beforeThat = beforePrevious;
            beforePrevious = previous;
            previous = last;
            last = value;
            if (last == '\n' && previous == '\n') {
                return head.toByteArray();
            }
            if (last == '\n' && previous == '\r' && beforePrevious == '\n' && beforeThat == '\r') {
                return head.toByteArray();
            }
        }
    }

    /** Whether a value is a legal HTTP token, which method names and header names must be. */
    private static boolean isToken(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character <= 0x20 || character >= 0x7F || TOKEN_SEPARATORS.indexOf(character) >= 0) {
                return false;
            }
        }
        return true;
    }

    /** The request method, already validated to be a token, in the spelling the client used. */
    String method() {
        return method;
    }

    /** The raw request target, including any query string. */
    String target() {
        return target;
    }

    /** The HTTP version, either {@code HTTP/1.1} or {@code HTTP/1.0}. */
    String version() {
        return version;
    }

    /** The request path without the query string, which is what the routes compare against. */
    String path() {
        int end = target.length();
        int query = target.indexOf('?');
        if (query >= 0) {
            end = Math.min(end, query);
        }
        int fragment = target.indexOf('#');
        if (fragment >= 0) {
            end = Math.min(end, fragment);
        }
        return target.substring(0, end);
    }

    /**
     * Returns the first value of a header.
     *
     * @param name the header name, case insensitive
     * @return the value, or {@code null} when the header is absent
     */
    String header(String name) {
        List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    /**
     * Returns a query parameter of the request target, or {@code null} when it is absent.
     *
     * <p>The target is decoded as UTF-8 with {@code application/x-www-form-urlencoded} rules, which is what
     * a browser sends for a {@code fetch} URL with a query string: {@code +} means space and percent escapes
     * are decoded. Only the first value of a repeated name is returned. A malformed escape is treated as an
     * absent value instead of failing the whole request, because the snapshot route has a documented default
     * for every parameter and a broken optional parameter should not make the page unusable.
     *
     * @param name the parameter name, case sensitive as query parameter names are
     * @return the decoded value, or {@code null} when the target has no such parameter or it cannot be
     *     decoded
     */
    String queryParameter(String name) {
        int query = target.indexOf('?');
        if (query < 0) {
            return null;
        }
        int end = target.indexOf('#', query);
        String queryString = end < 0 ? target.substring(query + 1) : target.substring(query + 1, end);
        for (String pair : queryString.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String key = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            if (!name.equals(key)) {
                continue;
            }
            try {
                return URLDecoder.decode(value, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException malformed) {
                return null;
            }
        }
        return null;
    }

    /**
     * Returns whether a comma separated header contains a token, ignoring case and surrounding space.
     *
     * <p>This is how {@code Connection: keep-alive, Upgrade} and {@code Upgrade: WebSocket} are accepted:
     * RFC 6455 defines both as token lists, and a client that adds another connection option is still
     * speaking the protocol.
     *
     * @param name  the header name, case insensitive
     * @param token the token to look for, case insensitive
     * @return {@code true} when at least one comma separated element equals the token
     */
    boolean headerContainsToken(String name, String token) {
        List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
        if (values == null) {
            return false;
        }
        for (String value : values) {
            for (String element : value.split(",")) {
                if (element.strip().equalsIgnoreCase(token)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns whether this request asks for a WebSocket upgrade.
     *
     * @return {@code true} when {@code Upgrade} names {@code websocket}
     */
    boolean isWebSocketAttempt() {
        return headerContainsToken("upgrade", "websocket");
    }
}
