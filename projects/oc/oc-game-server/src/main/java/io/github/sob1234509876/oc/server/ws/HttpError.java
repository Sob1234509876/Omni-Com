package io.github.sob1234509876.oc.server.ws;

import java.io.IOException;

/**
 * A request that is malformed enough to deserve its own HTTP status, rather than a generic failure.
 *
 * <p>The static route and the handshake both reject input for reasons an operator wants to tell apart:
 * a bad request is the client's mistake ({@code 400}), a path that escapes the static root is forbidden
 * ({@code 403}), and a missing asset is simply not found ({@code 404}). Carrying the status with the
 * message keeps the decision where the check is made and lets the outer HTTP layer turn it into one
 * consistent answer.
 */
final class HttpError extends IOException {

    private final int status;

    HttpError(int status, String reason) {
        super(reason);
        this.status = status;
    }

    /** The HTTP status this failure maps to. */
    int status() {
        return status;
    }
}
