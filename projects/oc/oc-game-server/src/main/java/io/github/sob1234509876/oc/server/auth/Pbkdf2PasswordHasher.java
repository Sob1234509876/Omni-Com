package io.github.sob1234509876.oc.server.auth;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * The production {@link PasswordHasher}: PBKDF2 with HMAC-SHA256, a per-account random salt and a
 * deliberately high iteration count.
 *
 * <p>PBKDF2 is chosen because it ships with the JDK. The project forbids third-party dependencies, and
 * a memory-hard scheme such as Argon2 or scrypt is not part of the platform, so the honest choice is a
 * salted, iterated, standard KDF rather than a hand-rolled substitute.
 *
 * <p>Three parameters carry the security of a hash:
 *
 * <ul>
 *   <li><b>HMAC-SHA256 and a 256 bit derived key.</b> The key is as wide as the digest, so there is no
 *       truncation to exploit and no reason to ask for more output than the algorithm provides.</li>
 *   <li><b>A 128 bit random salt from {@link SecureRandom}.</b> The salt makes precomputed tables
 *       useless and stops two accounts with the same password from sharing a hash. It is generated
 *       through the platform's cryptographic generator, never through {@code java.util.Random}, whose
 *       output is predictable from a handful of values.</li>
 *   <li><b>{@value Pbkdf2PasswordHasher#DEFAULT_ITERATIONS} iterations by default.</b> Iteration is the
 *       only cost knob
 *       PBKDF2 has, and its purpose is to make guessing expensive. The count is stored per account, so
 *       it can be raised later.</li>
 * </ul>
 *
 * <p>Salt and hash are hex encoded so a record survives the tab separated user file untouched: hex
 * contains neither a tab, a newline nor a percent sign, which is what the store escapes.
 *
 * <p>Instances are immutable apart from the internal {@link SecureRandom}, which is itself thread safe.
 * A single instance can therefore be shared by every client thread.
 */
public final class Pbkdf2PasswordHasher implements PasswordHasher {

    /**
     * Default PBKDF2 iteration count.
     *
     * <p>210000 follows the OWASP recommendation for PBKDF2-HMAC-SHA256. It is high enough that
     * guessing a password offline costs real time on server hardware, while a single successful login
     * still costs a few tens of milliseconds, which a player does not notice. The value is stored per
     * account, so raising it only affects new registrations.
     */
    public static final int DEFAULT_ITERATIONS = 210_000;

    /** Length of the derived key in bits: exactly one SHA-256 output, with no truncation or stretching. */
    private static final int KEY_LENGTH_BITS = 256;

    /** Length of the random salt in bytes. 128 bits is far beyond the point where collisions matter. */
    private static final int SALT_LENGTH_BYTES = 16;

    /** De-facto standard PBKDF2 JCE algorithm name; the HMAC is fixed by the name. */
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private static final HexFormat HEX = HexFormat.of();

    /** Cached because the factory is a lookup and never varies; the algorithm always exists on a JDK. */
    private static final SecretKeyFactory FACTORY = factory();

    private final SecureRandom random;

    /** A hasher with a default {@link SecureRandom} seeded by the platform. */
    public Pbkdf2PasswordHasher() {
        this(new SecureRandom());
    }

    /**
     * A hasher drawing salts from the given generator.
     *
     * <p>Useful for a deterministic test; production code should use {@link #Pbkdf2PasswordHasher()},
     * because a predictable salt defeats the reason for having one.
     */
    public Pbkdf2PasswordHasher(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    @Override
    public String newSalt() {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        random.nextBytes(salt);
        return HEX.formatHex(salt);
    }

    @Override
    public String hash(String password, String salt, int iterations) {
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(salt, "salt");
        if (iterations < 1) {
            throw new IllegalArgumentException("iterations must be positive: " + iterations);
        }
        char[] secret = password.toCharArray();
        byte[] saltBytes = HEX.parseHex(salt);
        PBEKeySpec spec = new PBEKeySpec(secret, saltBytes, iterations, KEY_LENGTH_BITS);
        try {
            return HEX.formatHex(FACTORY.generateSecret(spec).getEncoded());
        } catch (InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 rejected the key specification", e);
        } finally {
            // Wipe the copies the JCE holds; the caller's own String is out of reach of this class.
            spec.clearPassword();
            Arrays.fill(secret, '\0');
        }
    }

    @Override
    public int defaultIterations() {
        return DEFAULT_ITERATIONS;
    }

    /** Resolves the factory once, so a missing algorithm fails at class initialisation, not mid-login. */
    private static SecretKeyFactory factory() {
        try {
            return SecretKeyFactory.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ALGORITHM + " is required but missing from this JDK", e);
        }
    }
}
