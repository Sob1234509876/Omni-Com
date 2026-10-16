package io.github.sob1234509876.oc.server.net;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Support for checks that need an authentic frame without sending it.
 *
 * <p>The replay guard lives inside {@link SecureChannel} and is only reachable with genuine ciphertext:
 * a frame made of arbitrary bytes is rejected by the tag check long before the counter is compared, so a
 * check built on hand written bytes would pass for the wrong reason. The session key never leaves the
 * channel, so producing that genuine ciphertext from outside the package is impossible. This class is the
 * bridge: it exposes exactly one operation, and it does nothing in the server itself.
 *
 * <p>The frame returned counts as one write against the channel's own nonce counter, so the peer's next
 * read must accept it and the read after that must refuse it - which is precisely what the network self
 * check asserts.
 */
public final class ChannelTestSupport {

    private ChannelTestSupport() {
    }

    /**
     * Returns the frame a channel would send for one line, without writing it to the socket.
     *
     * @param channel the channel whose session key and counter must be used
     * @param line    the line to seal, without a terminator
     * @return the complete frame, four byte length prefix included
     * @throws IOException if the line cannot be sealed
     */
    public static byte[] captureLine(SecureChannel channel, String line) throws IOException {
        byte[] body = line.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[body.length + 1];
        System.arraycopy(body, 0, payload, 0, body.length);
        payload[body.length] = '\n';
        return channel.captureFrame(payload);
    }
}
