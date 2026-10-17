package io.github.sob1234509876.oc.server.ws;

import java.util.Locale;

/**
 * Maps a static file name to the MIME type its browser needs.
 *
 * <p>A browser is strict about a few of these in ways that matter for a game client: an ES module is only
 * executed when it is served as JavaScript, a WebAssembly module only compiles with {@code application/wasm},
 * and a stylesheet is only applied with {@code text/css}. Those are exactly the files a bundled client is
 * made of, so the list below is the game client's file set rather than an attempt at the IANA registry.
 *
 * <p>Anything unknown is {@code application/octet-stream}. That is the safe answer: a browser downloads
 * such a file instead of interpreting it, so a new asset type cannot accidentally execute as a script.
 */
final class MimeTypes {

    private MimeTypes() {
    }

    /**
     * Returns the MIME type for a file name, by its extension.
     *
     * <p>The lookup is case insensitive because a file called {@code LOGO.PNG} is a PNG. A name without a
     * dot, or with a trailing dot, has no extension and falls back to the octet-stream default.
     *
     * @param fileName the file name, not a path with directories
     * @return the MIME type, never {@code null}
     */
    static String of(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "html" -> "text/html; charset=utf-8";
            case "js", "mjs" -> "text/javascript; charset=utf-8";
            case "css" -> "text/css; charset=utf-8";
            case "svg" -> "image/svg+xml";
            case "png" -> "image/png";
            case "jpg" -> "image/jpeg";
            case "ico" -> "image/x-icon";
            case "json", "map" -> "application/json";
            case "woff2" -> "font/woff2";
            case "wasm" -> "application/wasm";
            default -> "application/octet-stream";
        };
    }
}
