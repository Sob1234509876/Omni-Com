package io.github.sob1234509876.oc.server.debug;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The end-to-end encrypted envelope the debug plugin answers with, and the only format the plugin's CLI
 * opens.
 *
 * <p>The game channel already encrypts everything between a client and the server, but the channel key is
 * negotiated by the server itself: an operator who can reach the server's socket, or a future component that
 * terminates the channel elsewhere, would see the plugin's report in the clear. The envelope therefore
 * encrypts the request and the response a second time with a key that only the operator holds - the same
 * idea as an SSH host key, and the reason the key file is generated once and copied to the client machine by
 * hand rather than derived from anything the server knows.
 *
 * <p><strong>Layout.</strong> One envelope is:
 *
 * <pre>
 * "OCDBG1"      6 bytes   ASCII magic, so a wrong key or a foreign blob fails before any crypto runs
 * timestamp     8 bytes   seconds since the epoch, big endian, sent in the clear
 * nonce        12 bytes   fresh random bytes, never reused with the same key
 * ciphertext    n bytes   AES-256-GCM of the payload, with its 16 byte tag appended
 * </pre>
 *
 * <p>The key is 32 raw bytes, AES-256. The nonce is random rather than a counter because the two sides keep
 * no shared counter: the server can be restarted and the CLI may run from a different machine, and a random
 * 96 bit nonce is the standard, safe choice for GCM as long as it is drawn from {@link SecureRandom} and the
 * key is not used for an unbounded number of messages.
 *
 * <p><strong>Replay.</strong> The timestamp is what makes a recorded request useless: {@link #open} refuses
 * anything more than {@link #MAX_CLOCK_SKEW_SECONDS} away from the reader's clock, in either direction. That
 * is a bounded defence by design - clocks drift, so the window cannot be zero - and it is the reason the
 * plugin's payloads must be stateless reports rather than commands: a replay inside the window would still
 * be harmless, because there is nothing to repeat.
 *
 * <p><strong>What the tag does and does not cover.</strong> GCM authenticates the ciphertext and the nonce
 * that is passed to it, not the magic bytes and not the timestamp field of the header, which are parsed
 * before the cipher runs. Tampering with those two fields therefore cannot produce a valid payload: the
 * magic mismatch or the timestamp window rejects the envelope first, and a changed timestamp moves the
 * envelope out of the window rather than past the tag check. The one thing an attacker can do is delay or
 * drop an envelope, which the timestamp window already bounds.
 *
 * <p>Instances are not needed and not provided: the class is a pair of static operations over byte arrays,
 * so the same code serves the plugin (which seals the response) and the CLI (which seals the request).
 *
 * <p>The class is public because the CLI is a second package of this module, not a second program: both
 * ends of the envelope must use exactly this code, and duplicating the format in the CLI is how the two ends
 * would drift apart. The key never travels through this class - the caller reads it from a file and passes
 * the bytes in - so a public {@code open} cannot expose anybody's key.
 */
public final class DebugEnvelope {

    /** ASCII magic that opens every envelope. */
    public static final byte[] MAGIC = "OCDBG1".getBytes(StandardCharsets.US_ASCII);

    /** Size of the fixed header: magic, timestamp and nonce. */
    public static final int HEADER_BYTES = MAGIC.length + Long.BYTES + 12;

    /** AES-GCM authentication tag length in bits, the JDK's 128 bit default. */
    private static final int TAG_BITS = 128;

    /** Size of the AES-256 key, in bytes. */
    public static final int KEY_BYTES = 32;

    /** Size of the random nonce, in bytes. */
    private static final int NONCE_BYTES = 12;

    /** How far a timestamp may differ from the reader's clock before the envelope is refused. */
    public static final long MAX_CLOCK_SKEW_SECONDS = 60L;

    private static final SecureRandom RANDOM = new SecureRandom();

    private DebugEnvelope() {
    }

    /**
     * Draws a fresh 256 bit key from {@link SecureRandom}.
     *
     * <p>The one place the plugin creates key material. {@code java.util.Random} is deliberately not used
     * anywhere in this module: it is a predictable generator and would make the envelope's confidentiality
     * nominal.
     *
     * @return 32 fresh random bytes
     */
    public static byte[] newKey() {
        byte[] key = new byte[KEY_BYTES];
        RANDOM.nextBytes(key);
        return key;
    }

    /**
     * Encrypts a payload into an envelope for the current time.
     *
     * @param payload the plaintext, for example the request line {@code INFO}
     * @param key the 32 byte AES-256 key
     * @return the complete envelope, ready for base64
     * @throws IllegalArgumentException if the key is not exactly 32 bytes
     * @throws IllegalStateException if this JVM cannot run AES-GCM, which no supported JVM lacks
     */
    public static byte[] seal(byte[] payload, byte[] key) {
        return seal(payload, key, System.currentTimeMillis() / 1000L);
    }

    /**
     * Encrypts a payload into an envelope with an explicit timestamp, which is what the self check uses to
     * build an envelope that is deliberately old.
     *
     * @param payload the plaintext
     * @param key the 32 byte AES-256 key
     * @param epochSeconds the timestamp to write into the header
     * @return the complete envelope
     * @throws IllegalArgumentException if the key is not exactly 32 bytes
     */
    public static byte[] seal(byte[] payload, byte[] key, long epochSeconds) {
        Objects.requireNonNull(payload, "payload");
        requireKey(key);
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        byte[] sealed = cipher(Cipher.ENCRYPT_MODE, key, nonce, payload);
        ByteBuffer envelope = ByteBuffer.allocate(HEADER_BYTES + sealed.length);
        envelope.put(MAGIC);
        envelope.putLong(epochSeconds);
        envelope.put(nonce);
        envelope.put(sealed);
        return envelope.array();
    }

    /**
     * Decrypts an envelope that was sealed with the same key and is recent enough.
     *
     * <p>Every failure is an {@link IllegalArgumentException} with a message that names the stage, never the
     * payload: "bad magic", "clock skew", "authentication tag mismatch". The caller decides how to report it
     * to an operator, and no path here logs a key or a plaintext.
     *
     * @param envelope the bytes received
     * @param key the 32 byte AES-256 key the envelope was sealed with
     * @param nowEpochSeconds the reader's clock, in seconds
     * @return the decrypted payload
     * @throws IllegalArgumentException if the envelope is malformed, its timestamp is outside the window, or
     *     its tag does not verify
     */
    public static byte[] open(byte[] envelope, byte[] key, long nowEpochSeconds) {
        Objects.requireNonNull(envelope, "envelope");
        requireKey(key);
        if (envelope.length < HEADER_BYTES + (TAG_BITS / 8)) {
            throw new IllegalArgumentException("envelope is " + envelope.length
                    + " bytes, too short to be a sealed payload (minimum " + (HEADER_BYTES + TAG_BITS / 8)
                    + " bytes)");
        }
        for (int index = 0; index < MAGIC.length; index++) {
            if (envelope[index] != MAGIC[index]) {
                throw new IllegalArgumentException("envelope does not start with the " + magicText()
                        + " magic; it was not produced by this plugin or the wrong bytes were sent");
            }
        }
        ByteBuffer header = ByteBuffer.wrap(envelope);
        header.position(MAGIC.length);
        long stamp = header.getLong();
        long age = nowEpochSeconds - stamp;
        if (age > MAX_CLOCK_SKEW_SECONDS) {
            throw new IllegalArgumentException("envelope is " + age + " seconds old, which is beyond the "
                    + MAX_CLOCK_SKEW_SECONDS + " second window; it is a replayed or very stale request");
        }
        if (-age > MAX_CLOCK_SKEW_SECONDS) {
            throw new IllegalArgumentException("envelope is timestamped " + (-age) + " seconds in the future,"
                    + " which is beyond the " + MAX_CLOCK_SKEW_SECONDS + " second window; check the clock of"
                    + " this machine");
        }
        byte[] nonce = Arrays.copyOfRange(envelope, MAGIC.length + Long.BYTES, HEADER_BYTES);
        byte[] sealed = Arrays.copyOfRange(envelope, HEADER_BYTES, envelope.length);
        try {
            return cipher(Cipher.DECRYPT_MODE, key, nonce, sealed);
        } catch (IllegalArgumentException tampered) {
            throw new IllegalArgumentException("envelope failed to open: " + tampered.getMessage(), tampered);
        }
    }

    /** Runs one AES-256-GCM operation over the payload with the given nonce. */
    private static byte[] cipher(int mode, byte[] key, byte[] nonce, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            return cipher.doFinal(input);
        } catch (AEADBadTagException tampered) {
            // The one failure an attacker can cause, and the one worth naming precisely: the tag covers the
            // ciphertext and the nonce, so a wrong key and an edited message are indistinguishable here, on
            // purpose - saying which one it was would tell an attacker whether the key guess was close.
            throw new IllegalArgumentException("authentication tag mismatch: the envelope was altered, or"
                    + " this machine holds a different debug.key", tampered);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("this JVM cannot run AES-256-GCM: " + failure, failure);
        }
    }

    /** Rejects a key that would silently weaken AES-256 to something else. */
    private static void requireKey(byte[] key) {
        Objects.requireNonNull(key, "key");
        if (key.length != KEY_BYTES) {
            throw new IllegalArgumentException("the debug key must be exactly " + KEY_BYTES + " bytes, was "
                    + key.length + " (it is read from debug.key as " + (KEY_BYTES * 2) + " hex digits)");
        }
    }

    /** The magic as text, for the error messages; it is ASCII by construction. */
    private static String magicText() {
        return new String(MAGIC, StandardCharsets.US_ASCII);
    }
}
