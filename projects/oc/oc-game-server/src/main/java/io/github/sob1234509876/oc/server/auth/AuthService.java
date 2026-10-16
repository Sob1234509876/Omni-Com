package io.github.sob1234509876.oc.server.auth;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Registration and login for game accounts.
 *
 * <p>The service owns the rules that a player can trip over, and the store owns the bytes. Keeping the
 * two apart means the rules can be checked without touching a disk, and the store can be swapped
 * without re-testing password handling.
 *
 * <p><b>Thread safety.</b> A game server authenticates from several client threads at once, and the
 * store is explicitly not required to be thread safe. Every access to the store therefore happens
 * inside one {@link ReentrantLock}, which makes a register-then-save pair atomic: without it, two
 * threads registering the same name could both find it absent and both write it, and one of the two
 * players would silently own the other's account. The lock is reentrant so the methods may call each
 * other freely, and it is held across the hashing work for the same reason, because the decision to
 * write depends on the state that was read.
 *
 * <p>The cost of that choice is contention rather than corruption: registration and login serialise.
 * Both are rare compared with gameplay traffic, and PBKDF2 is deliberately slow anyway, so the
 * simplest correct arrangement is also the right one here.
 */
public final class AuthService {

    /** Shortest password the server accepts. */
    public static final int MIN_PASSWORD_LENGTH = 6;

    private final UserStore store;
    private final PasswordHasher hasher;
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * A service hashing with the production {@link Pbkdf2PasswordHasher}.
     *
     * <p>This is the constructor a server uses; the two argument form exists so that a self check can
     * substitute a cheap hasher instead of paying for real PBKDF2 on every assertion.
     */
    public AuthService(UserStore store) {
        this(store, new Pbkdf2PasswordHasher());
    }

    public AuthService(UserStore store, PasswordHasher hasher) {
        this.store = Objects.requireNonNull(store, "store");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
    }

    /**
     * Creates an account and persists it immediately.
     *
     * <p>The name and password are checked before anything is hashed or written, so a rejected attempt
     * leaves no trace in the store and costs no CPU. A name that is already taken is refused rather
     * than overwritten: replacing the record would hand an existing account to whoever asked second.
     *
     * <p>The new record is flushed on the spot. Registration is the one moment where losing the write
     * means losing the account entirely, because the player has no other copy of the credentials.
     */
    public AuthResult register(String name, String password) {
        if (!UserRecord.isNameValid(name) || !isPasswordAcceptable(password)) {
            return AuthResult.INVALID_CREDENTIALS;
        }
        lock.lock();
        try {
            if (store.find(name).isPresent()) {
                return AuthResult.ALREADY_REGISTERED;
            }
            String salt = hasher.newSalt();
            int iterations = hasher.defaultIterations();
            String hash = hasher.hash(password, salt, iterations);
            UserRecord record = new UserRecord(name, hash, salt, iterations, System.currentTimeMillis(), 0L);
            store.save(record);
            store.flush();
            return AuthResult.SUCCESS;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks a password and records the successful login.
     *
     * <p>A wrong password is a normal outcome, not an error: the caller gets {@link AuthResult} and
     * decides what to tell the client. Nothing is updated on a failure, so a failed attempt cannot be
     * used to move a stored record forward.
     *
     * <p>The successful case stores {@link UserRecord#withLastLogin(long)} and flushes. That write is
     * best effort in spirit: a crash between the authentication and the flush costs a timestamp, not an
     * account. The record is looked up again under the lock before it is replaced, so a concurrent
     * removal is not undone by writing a record that no longer exists.
     */
    public AuthResult authenticate(String name, String password) {
        if (name == null || password == null) {
            return AuthResult.INVALID_CREDENTIALS;
        }
        lock.lock();
        try {
            Optional<UserRecord> found = store.find(name);
            if (found.isEmpty()) {
                return AuthResult.UNKNOWN_USER;
            }
            UserRecord record = found.get();
            boolean matches = hasher.verify(password, record.passwordSalt(), record.passwordIterations(),
                    record.passwordHash());
            if (!matches) {
                return AuthResult.WRONG_PASSWORD;
            }
            store.save(record.withLastLogin(System.currentTimeMillis()));
            store.flush();
            return AuthResult.SUCCESS;
        } finally {
            lock.unlock();
        }
    }

    /** The stored account, for callers that need more than the result of an attempt. */
    public Optional<UserRecord> find(String name) {
        lock.lock();
        try {
            return store.find(name);
        } finally {
            lock.unlock();
        }
    }

    /** How many accounts exist, used for reports and for the empty-server case. */
    public int userCount() {
        lock.lock();
        try {
            return store.all().size();
        } finally {
            lock.unlock();
        }
    }

    /** Persists pending changes; a no-op when nothing changed since the last write. */
    public void save() {
        lock.lock();
        try {
            store.flush();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether a password is worth hashing.
     *
     * <p>Only a length floor is enforced. Composition rules (a digit, a symbol, a capital) push people
     * towards predictable patterns and are not applied; length is the property that actually costs an
     * attacker work, and the server's job is to refuse the obviously unusable, not to lecture.
     */
    private static boolean isPasswordAcceptable(String password) {
        return password != null && password.length() >= MIN_PASSWORD_LENGTH;
    }
}
