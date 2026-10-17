package io.github.sob1234509876.oc.server.ws;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One HTTP/1.1 response, written with a fixed shape so every route answers consistently.
 *
 * <p>The endpoint serves a developer tool, not a general purpose web site, so its responses are deliberately
 * simple: one request, one answer, {@code Connection: close}, and {@code Content-Length} always present.
 * Closing after every response means there is no keep-alive state machine to get wrong and a client that
 * reads until end of stream can never be confused by a half-written body. The cost - a new TCP connection
 * per asset - is irrelevant on loopback and is the trade a debugging tool should make.
 *
 * <p>{@code HEAD} uses the same object as {@code GET}; the body is simply not written, while
 * {@code Content-Length} still describes what a {@code GET} would have returned.
 */
final class HttpResponse {

    private final int status;
    private final String reason;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final byte[] body;

    private HttpResponse(int status, String reason, byte[] body) {
        this.status = status;
        this.reason = reason;
        this.body = body;
    }

    /**
     * A plain text response, which is what every error path and the built-in messages use.
     *
     * @param status the HTTP status
     * @param reason the reason phrase
     * @param text   the body, encoded as UTF-8
     * @return the response
     */
    static HttpResponse text(int status, String reason, String text) {
        HttpResponse response = new HttpResponse(status, reason, text.getBytes(StandardCharsets.UTF_8));
        response.headers.put("Content-Type", "text/plain; charset=utf-8");
        return response;
    }

    /**
     * A file response with a MIME type and, when it applies, a cache policy.
     *
     * @param status       the HTTP status
     * @param reason       the reason phrase
     * @param body         the exact file bytes
     * @param contentType  the MIME type, including a charset where one applies
     * @param cacheControl the {@code Cache-Control} value, or {@code null} to send no such header
     * @return the response
     */
    static HttpResponse file(int status, String reason, byte[] body, String contentType,
            String cacheControl) {
        HttpResponse response = new HttpResponse(status, reason, body);
        response.headers.put("Content-Type", contentType);
        if (cacheControl != null) {
            response.headers.put("Cache-Control", cacheControl);
        }
        return response;
    }

    /**
     * Adds a response header, replacing a previous value of the same name.
     *
     * @param name  the header name
     * @param value the header value
     * @return this response, so a factory call can be chained
     */
    HttpResponse withHeader(String name, String value) {
        headers.put(name, value);
        return this;
    }

    /**
     * Writes the status line, the headers and, unless this is a {@code HEAD}, the body.
     *
     * <p>The header values here are all chosen by server code - MIME types, cache policies, fixed guidance -
     * so no client supplied text reaches a header. The body may contain the configured static root, which
     * is a path and not a header, so it cannot end the head early.
     *
     * @param out      the socket output
     * @param headOnly whether to suppress the body
     * @throws IOException if the socket fails
     */
    void writeTo(OutputStream out, boolean headOnly) throws IOException {
        StringBuilder head = new StringBuilder(128);
        head.append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            head.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }
        head.append("Content-Length: ").append(body.length).append("\r\n");
        head.append("Connection: close\r\n");
        head.append("\r\n");
        out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
        if (!headOnly) {
            out.write(body);
        }
        out.flush();
    }

    /** The status code, for callers that want to assert on it. */
    int status() {
        return status;
    }

    /** The reason phrase that belongs to a status, used for the error responses the routes build. */
    static String reasonFor(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 500 -> "Internal Server Error";
            default -> "Error";
        };
    }
}
