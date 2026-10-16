package io.github.sob1234509876.oc.server.auth;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One registered account as it is stored: the public name plus everything needed to check a password
 * later.
 *
 * <p>The record carries the hashing parameters (<code>passwordSalt</code> and
 * <code>passwordIterations</code>) next to the hash itself, because those parameters must be able to
 * change over time. A stored record is always verified with the parameters it was created with, so
 * raising the default iteration count hardens new registrations without locking existing players out.
 * A record therefore is not comparable by hash alone: two records agree only when all of their
 * components agree.
 *
 * <p>The password itself is never part of a record, and neither is anything derived that could be
 * replayed: the stored hash is computed with PBKDF2 over a per-account random salt, so a stolen file
 * still does not reveal passwords and identical passwords of two accounts do not produce identical
 * hashes.
 *
 * <p>Validation happens in the compact constructor so that an instance of this record cannot exist in
 * an invalid state. That matters because the record is the only thing the store reads and writes: if
 * it accepted a blank hash or a future timestamp, the corruption would be persisted before anybody
 * noticed.
 *
 * @param name the account name, matching {@link #NAME_PATTERN}
 * @param passwordHash the hex encoded PBKDF2 hash of the password
 * @param passwordSalt the hex encoded per-account salt the hash was computed with
 * @param passwordIterations how many PBKDF2 iterations the hash was computed with
 * @param registeredAtMillis when the account was created, in epoch milliseconds
 * @param lastLoginMillis when the account last authenticated successfully, or zero before the first
 *     successful authentication
 * @see PasswordHasher
 */
public record UserRecord(String name, String passwordHash, String passwordSalt, int passwordIterations,
        long registeredAtMillis, long lastLoginMillis) {

    /** Smallest account name length, in characters. */
    public static final int MIN_NAME_LENGTH = 3;

    /** Largest account name length, in characters. */
    public static final int MAX_NAME_LENGTH = 32;

    /**
     * The only shape an account name may have: letters, digits, underscore, dot and hyphen.
     *
     * <p>The set is deliberately narrow. A name ends up in log lines, in file paths chosen by
     * operators, in URLs and in the tab separated user file, so anything shell significant,
     * whitespace, control character or separator-like is rejected at the door instead of being escaped
     * in every place that later consumes it.
     */
    public static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_.-]{" + MIN_NAME_LENGTH + ","
            + MAX_NAME_LENGTH + "}");

    /**
     * Lower bound on the PBKDF2 iteration count.
     *
     * <p>Ten thousand iterations is already far below {@link Pbkdf2PasswordHasher#defaultIterations()}
     * and is not meant to be a safe setting: it is the point below which a stored record is treated as
     * damaged rather than merely weak, so a truncated or hand-edited user file is rejected loudly
     * instead of authenticating against a nearly unhashed password.
     */
    public static final int MIN_ITERATIONS = 10_000;

    /**
     * Validates every component; throws rather than storing an account that could never authenticate.
     */
    public UserRecord {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(passwordSalt, "passwordSalt");
        if (!isNameValid(name)) {
            throw new IllegalArgumentException(
                    "account name must match " + NAME_PATTERN.pattern() + ": " + name);
        }
        if (passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }
        if (passwordSalt.isBlank()) {
            throw new IllegalArgumentException("passwordSalt must not be blank");
        }
        if (passwordIterations < MIN_ITERATIONS) {
            throw new IllegalArgumentException("passwordIterations must be at least " + MIN_ITERATIONS + ": "
                    + passwordIterations);
        }
        if (registeredAtMillis < 0L) {
            throw new IllegalArgumentException(
                    "registeredAtMillis must not be negative: " + registeredAtMillis);
        }
        if (lastLoginMillis < 0L) {
            throw new IllegalArgumentException("lastLoginMillis must not be negative: " + lastLoginMillis);
        }
    }

    /**
     * Whether the given text is an acceptable account name.
     *
     * <p>Exposed as a static predicate so that a caller can reject a name before any hashing or file
     * access happens, using exactly the rule the record itself enforces.
     */
    public static boolean isNameValid(String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    /**
     * A copy of this record with a new {@link #lastLoginMillis()}.
     *
     * <p>Records are immutable, so recording a successful login means replacing the stored value. The
     * other components, including the salt and the iteration count, are carried over untouched: a login
     * must never change how the password is hashed, or the next login with the same password would fail.
     */
    public UserRecord withLastLogin(long millis) {
        return new UserRecord(name, passwordHash, passwordSalt, passwordIterations, registeredAtMillis,
                millis);
    }
}
