package io.github.sob1234509876.oc.server.auth;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

/**
 * Where accounts live.
 *
 * <p>The interface exists so that authentication does not care whether accounts sit in a text file, a
 * database or only in memory during a test. It deliberately exposes nothing about how a store keeps its
 * data: a caller cannot ask for a file handle or a stream, only for whole records, which keeps the
 * persistence format private to the implementation.
 *
 * <p><b>Implementations are not required to be thread safe.</b> {@link AuthService} serialises every
 * access it performs with its own lock, so an implementation may be written for a single thread, which
 * is what makes the local text file store simple. A caller that uses a store directly has to provide
 * its own synchronisation.
 *
 * <p>{@link #save(UserRecord)} only guarantees that the record is durable once
 * {@link #flush()} has returned, so a batch of registrations costs one rewrite instead of one per
 * account.
 *
 * @see LocalUserStore
 */
public interface UserStore {

    /** The account with the given name, or empty when no such account is registered. */
    Optional<UserRecord> find(String name);

    /**
     * Every stored account.
     *
     * <p>The returned collection is an immutable copy in insertion order, so iterating it never
     * observes a concurrent change and never lets a caller modify the store behind its back. Order is
     * part of the contract because a listing that shuffles between calls is unusable for anything that
     * compares two snapshots.
     */
    Collection<UserRecord> all();

    /** Inserts the record, or replaces the stored account of the same name. */
    void save(UserRecord record);

    /** Removes the account and reports whether it existed. */
    boolean remove(String name);

    /** Persists every change made since the last flush; does nothing when there is nothing to write. */
    void flush();

    /**
     * Where this store persists.
     *
     * <p>A store that is not {@link #persistent()} still names a location, because the path is what
     * lets an operator and a log message point at the same place: an in-memory store answers with the
     * file it would have used.
     */
    Path location();

    /** Whether {@link #flush()} actually reaches durable storage. */
    default boolean persistent() {
        return true;
    }
}
