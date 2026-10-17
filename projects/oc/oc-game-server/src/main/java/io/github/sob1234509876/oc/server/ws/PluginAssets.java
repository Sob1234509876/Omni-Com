package io.github.sob1234509876.oc.server.ws;

import io.github.sob1234509876.oc.server.plugin.PluginLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The plugin asset route: {@code /plugin-assets/manifest.json} and {@code /plugin-assets/<plugin>/<file>}.
 *
 * <p>A plugin that ships art ships it inside its own jar, under {@code assets/}. This route is the only way
 * that art reaches a browser: the merged manifest tells the client which plugins publish an atlas, and each
 * {@code atlasUrl} in it points back at this route, which reads the bytes through the plugin's class loader.
 * The browser therefore never needs a plugin's folder, and a plugin can never expose a file that is not its
 * own resource.
 *
 * <p><strong>No filesystem at all.</strong> Every byte is read with {@link ClassLoader#getResourceAsStream},
 * never with {@link java.nio.file.Files}. That is what makes the traversal rules below a second line of
 * defence rather than the only one: even a request that slipped past them could only name a resource of a
 * plugin's class path, not a file of the server's disk. The normalised resource name has to start with
 * {@code assets/} and must not contain a {@code ..} segment - {@code 403} otherwise - and the resource must
 * come from the plugin's <em>own</em> jar, so one plugin cannot serve another plugin's files through its own
 * id.
 *
 * <p><strong>Request rules.</strong> Only {@code GET} and {@code HEAD} reach this route; anything else is
 * answered with {@code 405} and an {@code Allow} header by the endpoint, exactly like the static route. A
 * path containing {@code ..}, an encoded dot ({@code %2e}), a backslash, a NUL, a drive letter or an empty
 * plugin id is a {@code 400}: those are broken requests, not requests for something that happens to be
 * missing. An unknown plugin, a file the plugin does not have, and a plugin without an {@code assets/}
 * directory are all ordinary {@code 404}s.
 *
 * <p><strong>Merging.</strong> The manifest is an array, one element per loaded plugin that publishes one,
 * in plugin load order. A plugin without {@code assets/manifest.json} contributes nothing; when no plugin
 * has one the answer is {@code []} with status {@code 200}, never {@code 404}, because "no plugin publishes
 * art" is a valid, complete answer that the client is expected to handle by drawing procedurally. The
 * client applies the array in order and lets a later element override an earlier one for the same tile
 * path or entity kind; the server does not resolve those conflicts, so the merge rule lives in one place.
 *
 * <p><strong>Caching.</strong> Everything under this route is {@code Cache-Control: no-cache}. Plugin jars
 * are typically swapped while an operator is iterating on art, and the manifest is the one document that
 * would make a stale cache visible as wrong textures; the assets are small, on loopback, and correctness is
 * worth more here than a saved round trip. Hashed file names are not produced by this route, so the
 * immutable policy the static route uses for bundled assets would be a promise this route cannot keep.
 */
final class PluginAssets {

    /** The route prefix, without a trailing slash. */
    static final String PREFIX = "/plugin-assets";

    /** The merged manifest's exact path. */
    static final String MANIFEST_PATH = PREFIX + "/manifest.json";

    /** The directory inside a plugin jar that the route is allowed to read from. */
    private static final String ASSETS_DIRECTORY = "assets/";

    /** Upper bound on a plugin manifest, so one oversized jar cannot pin memory per request. */
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;

    /** The content type of the merged manifest; the raw files use {@link MimeTypes} instead. */
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    /** The cache policy of every response of this route; see the class documentation. */
    private static final String CACHE_CONTROL = "no-cache";

    /** Upper bound on a plugin id a client could reasonably address, in characters. */
    private static final int MAX_PLUGIN_ID_LENGTH = 128;

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private static final System.Logger LOG = System.getLogger(PluginAssets.class.getName());

    private final PluginLoader plugins;

    /**
     * Creates the route over the plugins of one loader.
     *
     * <p>The loader is read on every request rather than copied once, so a plugin that is unloaded stops
     * being served immediately and a plugin loaded later becomes visible without rebuilding the endpoint.
     *
     * @param plugins the loader whose enabled plugins publish assets
     */
    PluginAssets(PluginLoader plugins) {
        this.plugins = Objects.requireNonNull(plugins, "plugins");
    }

    /**
     * Whether a raw request path belongs to this route.
     *
     * <p>The comparison is on the raw target, before percent-decoding, because that is what the routing
     * layer has; an encoded prefix simply falls through to the static route and answers as a client side
     * route, which is harmless. Everything after the prefix is decoded by {@link #get(String)}.
     *
     * @param rawPath the request path, query string already removed
     * @return {@code true} for {@code /plugin-assets} and every path below it
     */
    static boolean isRoute(String rawPath) {
        return rawPath.equals(PREFIX) || rawPath.startsWith(PREFIX + "/");
    }

    /**
     * Answers one {@code GET} or {@code HEAD} of this route.
     *
     * <p>Never throws for a bad path: every refusal is turned into a response, like {@link StaticFiles}
     * does, so the routing layer stays a two line dispatch.
     *
     * @param rawPath the request path, query string already removed
     * @return the response to write
     */
    HttpResponse get(String rawPath) {
        try {
            return route(decode(rawPath));
        } catch (HttpError refusal) {
            return HttpResponse.text(refusal.status(), HttpResponse.reasonFor(refusal.status()),
                    refusal.getMessage());
        }
    }

    /** Dispatches a decoded path to the marker or to one file of one plugin. */
    private HttpResponse route(String path) throws HttpError {
        String rest = path.substring(PREFIX.length());
        if (rest.isEmpty() || rest.equals("/")) {
            return notFound("no plugin asset was named: try " + MANIFEST_PATH);
        }
        String relative = rest.substring(1);
        if (relative.equals("manifest.json")) {
            return manifest();
        }
        int slash = relative.indexOf('/');
        if (slash < 0) {
            return notFound("no file was named for plugin '" + relative + "'");
        }
        String pluginId = relative.substring(0, slash);
        String file = relative.substring(slash + 1);
        if (pluginId.isEmpty()) {
            throw new HttpError(400, "the request path must name a plugin after " + PREFIX + "/");
        }
        URLClassLoader loader = plugins.classLoadersByPlugin().get(pluginId);
        if (loader == null) {
            return notFound("no loaded plugin is named '" + pluginId + "'");
        }
        String resource = resourceName(file);
        byte[] body = readOwnResource(loader, resource);
        if (body == null) {
            return notFound("plugin '" + pluginId + "' has no " + resource);
        }
        String fileName = resource.substring(resource.lastIndexOf('/') + 1);
        return HttpResponse.file(200, "OK", body, MimeTypes.of(fileName), CACHE_CONTROL);
    }

    /**
     * Rejects the path forms the route refuses outright, before anything is looked up.
     *
     * <p>Percent-decoding itself is shared with the static route, which refuses a truncated or non-UTF-8
     * escape, a backslash and a NUL. This route adds the two rules that matter for a resource name: an
     * encoded dot is refused the same way a literal one is, and a colon is refused because it is how a
     * Windows drive letter would be smuggled in. The decoded path then has to still start with the prefix,
     * which it does by construction, but the assertion is kept so a future routing change cannot silently
     * hand this method a path of another route.
     */
    private static String decode(String rawPath) throws HttpError {
        if (containsEncodedDot(rawPath)) {
            throw new HttpError(400, "the request path must not contain an encoded dot");
        }
        String decoded = StaticFiles.decode(rawPath);
        if (decoded.indexOf("..") >= 0) {
            throw new HttpError(400, "the request path must not contain a '..' segment");
        }
        if (decoded.indexOf(':') >= 0) {
            throw new HttpError(400, "the request path must not name a drive");
        }
        if (!decoded.startsWith(PREFIX)) {
            throw new HttpError(400, "the request path must start with " + PREFIX);
        }
        return decoded;
    }

    /**
     * Turns the file part of the request into a normalised resource name below {@code assets/}.
     *
     * <p>The normalisation is the same stack walk a path library would do, written out because the resource
     * name is a class path name and not a filesystem path. A request that resolves to the {@code assets/}
     * directory itself, or to anything above it, is a {@code 403}: the route serves files, and no request
     * has a reason to name the directory.
     */
    private static String resourceName(String file) throws HttpError {
        String normalised = normalise(ASSETS_DIRECTORY + file);
        if (!normalised.startsWith(ASSETS_DIRECTORY) || normalised.length() == ASSETS_DIRECTORY.length()) {
            throw new HttpError(403, "the requested asset does not resolve to a file under assets/");
        }
        for (String segment : normalised.split("/")) {
            if ("..".equals(segment)) {
                throw new HttpError(403, "the requested asset escapes assets/");
            }
        }
        return normalised;
    }

    /**
     * Normalises a slash separated name: empty and {@code .} segments vanish, and {@code ..} pops the
     * previous segment (or is dropped at the root, which {@link #resourceName(String)} then refuses).
     */
    private static String normalise(String name) {
        List<String> segments = new ArrayList<>();
        for (String segment : name.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (!segments.isEmpty()) {
                    segments.remove(segments.size() - 1);
                }
                continue;
            }
            segments.add(segment);
        }
        return String.join("/", segments);
    }

    /**
     * Builds the merged manifest, one element per plugin that publishes one.
     *
     * <p>The array is rebuilt per request instead of cached. That is deliberate: the loader is read live, a
     * plugin that fails to enable is absent, and the response is {@code no-cache}, so there is no cached
     * copy that could disagree with what the server is currently running.
     */
    private HttpResponse manifest() {
        StringBuilder json = new StringBuilder(256).append('[');
        boolean first = true;
        for (Map.Entry<String, URLClassLoader> entry : plugins.classLoadersByPlugin().entrySet()) {
            String pluginId = entry.getKey();
            if (!isAddressableId(pluginId)) {
                LOG.log(System.Logger.Level.WARNING, "plugin '" + pluginId + "' cannot publish assets: its"
                        + " name cannot be used as a URL path segment");
                continue;
            }
            byte[] manifestBytes = readManifest(entry.getValue());
            if (manifestBytes == null) {
                continue;
            }
            PluginManifest manifest;
            try {
                manifest = PluginManifest.parse(manifestBytes);
            } catch (PluginManifest.InvalidManifestException invalid) {
                LOG.log(System.Logger.Level.WARNING, "plugin '" + pluginId + "' has an unusable "
                        + PluginManifest.RESOURCE + ": " + invalid.getMessage());
                continue;
            }
            String atlasUrl = PREFIX + "/" + encodeSegment(pluginId) + "/"
                    + encodeSegment(manifest.image());
            if (!first) {
                json.append(',');
            }
            json.append(manifest.toEntry(pluginId, atlasUrl));
            first = false;
        }
        json.append(']');
        return HttpResponse.file(200, "OK", json.toString().getBytes(StandardCharsets.UTF_8),
                JSON_CONTENT_TYPE, CACHE_CONTROL);
    }

    /**
     * Reads a plugin's manifest, refusing one that is too large to be a manifest.
     *
     * <p>The resource must come from the plugin's own jar: the loader of a plugin also searches the other
     * jars of the folder, so a plugin without art would otherwise see its neighbour's manifest and publish
     * art that is not its own. That single check is what makes "a plugin without {@code assets/} contributes
     * nothing" true rather than merely likely.
     */
    private static byte[] readManifest(URLClassLoader loader) {
        URL url = ownResource(loader, PluginManifest.RESOURCE);
        if (url == null) {
            return null;
        }
        try (InputStream in = loader.getResourceAsStream(PluginManifest.RESOURCE)) {
            if (in == null) {
                return null;
            }
            byte[] bytes = in.readNBytes(MAX_MANIFEST_BYTES + 1);
            if (bytes.length > MAX_MANIFEST_BYTES) {
                LOG.log(System.Logger.Level.WARNING, "plugin manifest " + PluginManifest.RESOURCE
                        + " of " + url + " exceeds " + MAX_MANIFEST_BYTES + " bytes and was ignored");
                return null;
            }
            return bytes;
        } catch (IOException failure) {
            return null;
        }
    }

    /** Reads one resource of the plugin's own jar, or returns {@code null} when it is not there. */
    private static byte[] readOwnResource(URLClassLoader loader, String resource) {
        if (ownResource(loader, resource) == null) {
            return null;
        }
        try (InputStream in = loader.getResourceAsStream(resource)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException failure) {
            return null;
        }
    }

    /**
     * Returns the URL of a resource when it is inside the loader's own jar.
     *
     * <p>The loader's first URL is its own jar (see {@link PluginLoader}); the rest of the folder follows so
     * that dropped dependencies resolve. {@link ClassLoader#getResource} walks that list in order, so the
     * only way to tell "this plugin has the file" from "a neighbour has it" is to compare the URL prefix.
     * A jar entry URL is {@code jar:<jar-url>!/<entry>}, which is exactly the prefix built below.
     */
    private static URL ownResource(URLClassLoader loader, String resource) {
        URL url = loader.getResource(resource);
        if (url == null) {
            return null;
        }
        URL[] urls = loader.getURLs();
        if (urls.length == 0) {
            return null;
        }
        String own = urls[0].toExternalForm();
        String prefix = own.startsWith("jar:") ? own : "jar:" + own + "!/";
        return url.toExternalForm().startsWith(prefix) ? url : null;
    }

    /**
     * Whether a plugin id can appear as one path segment of this route.
     *
     * <p>The id is a plugin's own name, and the route refuses {@code ..}, a slash, a backslash, a colon and
     * a control character anywhere in a request path. A plugin whose name contains one of those could never
     * be fetched, so publishing an {@code atlasUrl} for it would only produce a manifest the client has to
     * skip; such a plugin is reported and left out instead.
     */
    private static boolean isAddressableId(String pluginId) {
        if (pluginId.isEmpty() || pluginId.length() > MAX_PLUGIN_ID_LENGTH) {
            return false;
        }
        if (pluginId.contains("..")) {
            return false;
        }
        for (int index = 0; index < pluginId.length(); index++) {
            char character = pluginId.charAt(index);
            if (character < 0x20 || character == 0x7F || "/\\:".indexOf(character) >= 0) {
                return false;
            }
        }
        return true;
    }

    /** Percent-encodes one path segment, leaving the unreserved characters the URL grammar allows. */
    private static String encodeSegment(String value) {
        StringBuilder encoded = new StringBuilder(value.length());
        for (byte raw : value.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = raw & 0xFF;
            char character = (char) unsigned;
            boolean unreserved = (character >= 'A' && character <= 'Z')
                    || (character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '-' || character == '.' || character == '_' || character == '~';
            if (unreserved) {
                encoded.append(character);
            } else {
                encoded.append('%').append(HEX[unsigned >> 4]).append(HEX[unsigned & 0x0F]);
            }
        }
        return encoded.toString();
    }

    /** Whether a raw path contains {@code %2e} in either case, which would decode to a dot. */
    private static boolean containsEncodedDot(String rawPath) {
        for (int index = 0; index + 3 <= rawPath.length(); index++) {
            if (rawPath.charAt(index) != '%' || rawPath.charAt(index + 1) != '2') {
                continue;
            }
            char last = rawPath.charAt(index + 2);
            if (last == 'e' || last == 'E') {
                return true;
            }
        }
        return false;
    }

    /** A plain 404 with a body that names what was missing, never a client supplied path as a header. */
    private static HttpResponse notFound(String detail) {
        return HttpResponse.text(404, "Not Found", detail);
    }
}
