package io.github.sob1234509876.oc.server.check;

import io.github.sob1234509876.oc.server.auth.PasswordHasher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * A deliberately cheap password hasher shared by the self checks that have to register and log in accounts.
 *
 * <p>The production hasher is PBKDF2 with a high iteration count, which is exactly right for accounts and
 * exactly wrong for a self check that registers users on every run: it would add seconds for no extra
 * coverage, because password stretching is the auth module's subject, not the network protocol's. The
 * iteration count still satisfies the record's own floor, so what a check that uses this exercises is the
 * wire protocol, not the hashing.
 */
final class CheckHashers {

    /** The lowest iteration count a {@code UserRecord} accepts. */
    static final int ITERATIONS = 10_000;

    private CheckHashers() {
    }

    /** A hasher that is fast on purpose; see the class comment. */
    static PasswordHasher fast() {
        return new FastHasher();
    }

    private static final class FastHasher implements PasswordHasher {

        private final SecureRandom random = new SecureRandom();

        @Override
        public String newSalt() {
            byte[] salt = new byte[16];
            random.nextBytes(salt);
            return HexFormat.of().formatHex(salt);
        }

        @Override
        public String hash(String password, String salt, int iterations) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest((salt + ':' + password).getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(digest);
            } catch (NoSuchAlgorithmException failure) {
                throw new IllegalStateException("SHA-256 is unavailable in this JVM", failure);
            }
        }

        @Override
        public int defaultIterations() {
            return ITERATIONS;
        }
    }
}
