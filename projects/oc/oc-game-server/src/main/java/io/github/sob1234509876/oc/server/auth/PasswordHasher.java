package io.github.sob1234509876.oc.server.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Turns a password into something that can be stored without keeping the password.
 *
 * <p>The split into {@link #newSalt()} and {@link #hash(String, String, int)} exists so that the salt
 * is generated once, at registration, and then travels with the record. A verifier never invents a salt:
 * it reuses the one stored next to the hash, otherwise every login would fail. The iteration count is
 * passed in for the same reason, which is also what allows the cost of new passwords to be raised later
 * without invalidating existing accounts.
 *
 * <p>The interface is kept free of any algorithm details so that a test can substitute a cheap
 * implementation: the real PBKDF2 configuration is intentionally slow, and a self check that runs it
 * for every assertion would take minutes.
 *
 * @see Pbkdf2PasswordHasher
 */
public interface PasswordHasher {

    /** A fresh random salt, encoded as text so it can be stored in the account record. */
    String newSalt();

    /** The hash of the password under the given salt and iteration count. */
    String hash(String password, String salt, int iterations);

    /**
     * The iteration count to use for new passwords.
     *
     * <p>Stored records keep the count they were created with; only {@link AuthService#register} reads
     * this value.
     */
    int defaultIterations();

    /**
     * Whether the password reproduces the expected hash.
     *
     * <p>The comparison is constant time: it touches every byte of both values and stops nowhere early,
     * so the time it takes does not reveal how long a prefix of the hash was correct. Leaking that
     * would let an attacker recover a stored hash byte by byte and then search offline against it.
     *
     * <p>The default implementation is the comparable one for any text encoding of a hash; an
     * implementation only overrides it when its hash is best compared in another form.
     */
    default boolean verify(String password, String salt, int iterations, String expected) {
        if (expected == null) {
            return false;
        }
        byte[] actual = hash(password, salt, iterations).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actual, expected.getBytes(StandardCharsets.UTF_8));
    }
}
