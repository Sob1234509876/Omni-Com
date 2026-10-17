package io.github.sob1234509876.oc.server.ws;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The static file route: the built web client, served from one directory.
 *
 * <p>It is a development convenience, and the design follows from that. The root is resolved freshly for
 * every request, so a client can be rebuilt while the server keeps running; a missing root is not an error
 * - it is the normal state before the first build - and answers a plain {@code 404} saying so, once in the
 * log and always in the body.
 *
 * <p><strong>Traversal is refused, not normalised away.</strong> The resolved path is checked twice: the
 * lexical check rejects any {@code ..} segment, backslash, NUL byte or drive letter in the decoded target
 * with {@code 400}, and the resolved path must start with the normalised root or the answer is {@code 403}.
 * Afterwards the file is resolved through {@link Path#toRealPath()}, so a symbolic link inside the root that
 * points outside it is refused as well. Every one of those refusals happens before a byte is read, and the
 * checks never depend on the file actually existing.
 *
 * <p><strong>Single page application behaviour.</strong> A client side router owns paths such as
 * {@code /lobby}; they look like files but are not. A path without an extension therefore falls back to
 * {@code index.html}, while a path with an extension is a genuinely missing asset and answers {@code 404}.
 * A directory is never listed and never served as a file: it is answered with the same fallback.
 *
 * <p><strong>Caching.</strong> HTML is {@code no-cache} so a rebuilt client is picked up immediately. Files
 * under {@code /assets/} are hashed by the usual bundler and are therefore immutable for a year. Anything
 * else gets no cache header, which leaves the decision to the browser's heuristics rather than pretending
 * to know how a particular project names its files.
 */
final class StaticFiles {

    private static final System.Logger LOG = System.getLogger(StaticFiles.class.getName());

    private static final String IMMUTABLE = "public, max-age=31536000, immutable";
    private static final String NO_CACHE = "no-cache";

    private final Path root;
    private final AtomicBoolean notBuiltLogged = new AtomicBoolean();

    /**
     * Creates the route for one static root.
     *
     * @param staticRoot the configured directory, relative to the working directory or absolute
     */
    StaticFiles(String staticRoot) {
        this.root = Path.of(staticRoot).toAbsolutePath().normalize();
    }

    /** The normalised root this route serves, for logs and diagnostics. */
    Path root() {
        return root;
    }

    /**
     * Answers one {@code GET} for a path that did not match the WebSocket route.
     *
     * <p>The caller has already established that the method is {@code GET} or {@code HEAD}; this method
     * only decides status, headers and body.
     *
     * @param rawPath the request path, query string already removed
     * @return the response to write
     */
    HttpResponse get(String rawPath) {
        try {
            String path = decode(rawPath);
            if (!path.startsWith("/")) {
                throw new HttpError(400, "the request path must start with '/'");
            }
            for (String segment : path.split("/")) {
                if ("..".equals(segment) || "..".equals(trimTrailingDotsAndSpaces(segment))) {
                    // The second test is the Windows variant: Win32 ignores trailing spaces and dots in a
                    // path component, so ".. " names the parent directory even though it is not spelled "..".
                    throw new HttpError(400, "the request path must not contain a '..' segment");
                }
            }
            Path candidate = resolve(path);
            if (!Files.isDirectory(root)) {
                return notBuilt();
            }
            if (Files.isDirectory(candidate)) {
                return serveIndex();
            }
            if (Files.isRegularFile(candidate)) {
                return serveFile(candidate, path);
            }
            if (hasExtension(path)) {
                return HttpResponse.text(404, "Not Found", "no such file: " + path);
            }
            return serveIndex();
        } catch (HttpError refusal) {
            return HttpResponse.text(refusal.status(), HttpResponse.reasonFor(refusal.status()),
                    refusal.getMessage());
        }
    }

    /** Resolves a decoded path below the root and proves that it stays there. */
    private Path resolve(String path) throws HttpError {
        String relative = path.substring(1);
        if (relative.isEmpty()) {
            return root;
        }
        if (path.startsWith("//")) {
            throw new HttpError(400, "the request path must not begin with two slashes");
        }
        int slash = relative.indexOf('/');
        String firstSegment = slash < 0 ? relative : relative.substring(0, slash);
        if (firstSegment.length() >= 2 && Character.isLetter(firstSegment.charAt(0))
                && firstSegment.charAt(1) == ':') {
            throw new HttpError(400, "the request path must not name a Windows drive");
        }
        Path candidate;
        try {
            candidate = root.resolve(relative).normalize();
        } catch (InvalidPathException failure) {
            throw new HttpError(400, "the request path is not a usable file name: " + failure.getMessage());
        }
        if (!candidate.startsWith(root)) {
            throw new HttpError(403, "the request path escapes the static root");
        }
        return candidate;
    }

    /** Serves {@code index.html}, whether the path was empty, a directory or a client side route. */
    private HttpResponse serveIndex() {
        Path index = root.resolve("index.html");
        if (!Files.isRegularFile(index)) {
            return notBuilt();
        }
        return serveFile(index, "/index.html");
    }

    /** Reads and types one regular file, after proving that it resolves inside the root. */
    private HttpResponse serveFile(Path file, String logicalPath) {
        try {
            Path realRoot = root.toRealPath();
            Path realFile = file.toRealPath();
            if (!realFile.startsWith(realRoot)) {
                return HttpResponse.text(403, "Forbidden",
                        "the requested file resolves outside the static root");
            }
            byte[] body = Files.readAllBytes(realFile);
            String cacheControl = cacheControlFor(file, logicalPath);
            return HttpResponse.file(200, "OK", body, MimeTypes.of(file.getFileName().toString()),
                    cacheControl);
        } catch (IOException failure) {
            // The file was a regular file a moment ago; if it vanished or cannot be read, the request is
            // simply not found. The message names no client text, so it cannot leak a path outside the root.
            return HttpResponse.text(404, "Not Found", "the requested file could not be read");
        }
    }

    /** Chooses the cache policy from the served file and the path the client used. */
    private static String cacheControlFor(Path file, String logicalPath) {
        if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".html")) {
            return NO_CACHE;
        }
        if (logicalPath.startsWith("/assets/")) {
            return IMMUTABLE;
        }
        return null;
    }

    /** The answer for a client that was never built, logged once because the condition is not per request. */
    private HttpResponse notBuilt() {
        if (notBuiltLogged.compareAndSet(false, true)) {
            LOG.log(System.Logger.Level.INFO,
                    "the web client is not built: nothing to serve from " + root
                            + " (static requests answer 404 until it is)");
        }
        return HttpResponse.text(404, "Not Found",
                "the web client is not built: nothing to serve at " + root);
    }

    /**
     * Percent-decodes a request path into a file path.
     *
     * <p>Raw characters are re-encoded as UTF-8 as they are appended, so a client that sends a raw UTF-8
     * name and a client that percent-encodes it produce the same path. A truncated escape, a non-hexadecimal
     * escape or a byte sequence that is not UTF-8 is a {@code 400}, never a guess.
     */
    private static String decode(String rawPath) throws HttpError {
        if (rawPath.indexOf('\\') >= 0) {
            throw new HttpError(400, "the request path must not contain a backslash");
        }
        if (rawPath.indexOf('\0') >= 0) {
            throw new HttpError(400, "the request path must not contain a NUL byte");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(rawPath.length());
        for (int index = 0; index < rawPath.length(); index++) {
            char character = rawPath.charAt(index);
            if (character == '%') {
                if (index + 2 >= rawPath.length()) {
                    throw new HttpError(400, "the request path has a truncated percent escape");
                }
                int high = Character.digit(rawPath.charAt(index + 1), 16);
                int low = Character.digit(rawPath.charAt(index + 2), 16);
                if (high < 0 || low < 0) {
                    throw new HttpError(400, "the request path has a percent escape that is not hexadecimal");
                }
                bytes.write((high << 4) | low);
                index += 2;
                continue;
            }
            int width = index + 1 < rawPath.length() ? Character.charCount(character) : 1;
            String piece = rawPath.substring(index, index + width);
            bytes.writeBytes(piece.getBytes(StandardCharsets.UTF_8));
            index += width - 1;
        }
        String decoded;
        try {
            decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray()))
                    .toString();
        } catch (CharacterCodingException failure) {
            throw new HttpError(400, "the request path is not valid UTF-8");
        }
        if (decoded.indexOf('\0') >= 0) {
            throw new HttpError(400, "the request path must not contain a NUL byte");
        }
        if (decoded.indexOf('\\') >= 0) {
            throw new HttpError(400, "the request path must not contain a backslash");
        }
        return decoded;
    }

    /** Whether the last path segment has a file extension, which separates a missing asset from a route. */
    private static boolean hasExtension(String path) {
        int slash = path.lastIndexOf('/');
        String name = path.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1;
    }

    /** Strips the trailing dots and spaces Win32 ignores in a path component. */
    private static String trimTrailingDotsAndSpaces(String segment) {
        int end = segment.length();
        while (end > 0) {
            char character = segment.charAt(end - 1);
            if (character != ' ' && character != '.') {
                break;
            }
            end--;
        }
        return segment.substring(0, end);
    }
}
