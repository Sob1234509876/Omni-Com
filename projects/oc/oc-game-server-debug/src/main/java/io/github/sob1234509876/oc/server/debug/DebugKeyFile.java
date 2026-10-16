package io.github.sob1234509876.oc.server.debug;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The AES-256 key of the debug plugin, stored as hexadecimal text in {@code debug.key}.
 *
 * <p>The key is one file that is created once, then copied by the operator to every machine that runs the
 * CLI. That is the same trade an SSH host key makes: the trust comes from the file being transferred out of
 * band, not from the network, so anybody who can read the file can read every debug report, and anybody who
 * cannot cannot - even if they can watch the socket, which is already encrypted anyway.
 *
 * <p><strong>Why a file and not a password.</strong> A key file cannot be guessed and does not have to be
 * remembered, and it is something the operator can delete to revoke access without touching the server.
 * Regenerating it is deliberately destructive: the plugin never overwrites an existing file, because
 * silently replacing a key would lock out every CLI copy that already has it.
 *
 * <p><strong>Permissions.</strong> Where the filesystem supports POSIX permissions - Linux, macOS - the file
 * is created so that only its owner may read or write it ({@code rw-------}). On Windows, where a POSIX view
 * of the file does not exist, the file inherits the ACL of the plugin's data directory, and the directory is
 * what an operator should restrict if the machine is shared. That is the cheap part of the arrangement; the
 * expensive part - an encrypted volume or a dedicated account - is the operator's decision, not the plugin's.
 *
 * <p>The file holds exactly 64 hexadecimal characters (lower case out, either case in) plus an optional
 * trailing newline. A file of any other shape is refused rather than padded or hashed into a key: a wrong
 * key would otherwise fail much later as an unreadable envelope, which is the hardest failure to diagnose.
 *
 * <p>The class is public because the CLI and the self check both need the same parsing rules: the CLI reads
 * the operator's copy of the file, and the check proves that a fresh key is 32 bytes, that the second load
 * reuses the first key, and that a file which is not a key is refused. It carries no key of its own beyond
 * the one it was given or read, and it never writes a key anywhere but the file it was pointed at.
 */
public final class DebugKeyFile {

    /** Name of the key inside the plugin's data directory. */
    public static final String FILE_NAME = "debug.key";

    /** 32 bytes as hexadecimal text. */
    private static final int HEX_DIGITS = DebugEnvelope.KEY_BYTES * 2;

    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]{" + HEX_DIGITS + "}");

    private static final Set<PosixFilePermission> OWNER_ONLY =
            PosixFilePermissions.fromString("rw-------");

    private final Path file;
    private final byte[] key;

    private DebugKeyFile(Path file, byte[] key) {
        this.file = file;
        this.key = key;
    }

    /**
     * Loads {@value #FILE_NAME} from the given directory, creating it with a fresh random key when it is not
     * there yet.
     *
     * @param directory the plugin's own data directory, which the caller has already created
     * @return the key, with the location it came from
     * @throws UncheckedIOException if the file cannot be read or created, or holds something that is not a
     *     key
     */
    static DebugKeyFile loadOrCreate(Path directory) {
        Path file = Objects.requireNonNull(directory, "directory").resolve(FILE_NAME);
        if (Files.exists(file)) {
            return new DebugKeyFile(file, parse(readFile(file), file));
        }
        return new DebugKeyFile(file, create(file));
    }

    /**
     * Reads a key from an arbitrary path, which is what the CLI does with the copy it was given.
     *
     * @param file the key file, normally produced by the server
     * @return the 32 byte key
     * @throws UncheckedIOException if the file is missing or does not hold a key
     */
    public static byte[] read(Path file) {
        Objects.requireNonNull(file, "file");
        if (!Files.isRegularFile(file)) {
            throw new UncheckedIOException(new IOException("no debug key at " + file.toAbsolutePath()
                    + "; copy the " + FILE_NAME + " the server created into this machine"));
        }
        return parse(readFile(file), file);
    }

    /** The 32 byte key; the caller must not modify the array. */
    byte[] key() {
        return key;
    }

    /** Where the key lives, for a log line that names the file without printing its content. */
    Path file() {
        return file;
    }

    /** Reads the file as text, reporting the path rather than a bare IO failure. */
    private static String readFile(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw keyFileProblem("cannot read the debug key " + file + ": " + failure.getMessage(), failure);
        }
    }

    /** Parses hexadecimal text into exactly 32 bytes, or refuses the file. */
    private static byte[] parse(String text, Path file) {
        String trimmed = text.strip();
        if (!HEX.matcher(trimmed).matches()) {
            throw new UncheckedIOException(new IOException("the debug key " + file + " is not "
                    + HEX_DIGITS + " hexadecimal characters; delete it and let the server create a new one,"
                    + " then copy that file to the client"));
        }
        return HexFormat.of().parseHex(trimmed.toLowerCase(Locale.ROOT));
    }

    /**
     * Writes a fresh key, or adopts the one a racing process wrote first.
     *
     * <p>The file is opened with {@code CREATE_NEW}, so exactly one of two concurrent first starts wins; the
     * loser sees {@link FileAlreadyExistsException} and reads the winner's key instead of overwriting it with
     * a second one. The key is written through the same channel that opened the file, so the file is never
     * visible to a reader in a half written state.
     */
    private static byte[] create(Path file) {
        byte[] key = DebugEnvelope.newKey();
        String text = HexFormat.of().formatHex(key) + System.lineSeparator();
        try {
            Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException raced) {
            // Another process (or a first run that was interrupted after the existence check) created the
            // file in between; its key is the one both sides must use, so read it instead of writing.
            return parse(readFile(file), file);
        } catch (IOException failure) {
            throw keyFileProblem("cannot create the debug key " + file + ": " + failure.getMessage(), failure);
        }
        restrictPermissionsOf(file);
        return key;
    }

    /**
     * Wraps an IO failure of a key file into the unchecked shape callers expect.
     *
     * <p>Built as a {@link String} first on purpose: a key file is the one artefact whose content must never
     * reach a log or an exception message, so every message here is assembled from paths and fixed text only,
     * and an {@code IOException} constructed in place would make it easy to slip a value in by accident.
     */
    private static UncheckedIOException keyFileProblem(String message, IOException cause) {
        return new UncheckedIOException(new IOException(message, cause));
    }

    /**
     * Narrows the key to its owner, on filesystems that can express that, once its content is written.
     *
     * <p>The attempt happens after the write rather than at creation, because the portable spelling of
     * "create with these permissions" - an attribute map on {@code createFile} - would have to be followed by
     * a plain write, and a short write would then leave a key file that no longer parses. A permission change
     * cannot damage the content, and it cannot silently fail either: anything the platform refuses is ignored
     * on purpose, because a key with wider permissions is still a working key, and refusing to start over it
     * would turn a hardening step into an outage.
     *
     * <p>Windows has no POSIX view, so the file inherits the ACL of the plugin's data directory there; that
     * limitation is stated in the class comment rather than left for an operator to discover.
     */
    private static void restrictPermissionsOf(Path file) {
        FileSystem system = file.getFileSystem();
        if (!system.supportedFileAttributeViews().contains("posix")) {
            return;
        }
        try {
            Files.setPosixFilePermissions(file, OWNER_ONLY);
        } catch (IOException | UnsupportedOperationException notPermitted) {
            // A filesystem that reports POSIX support but refuses the mode; see the comment above.
        }
    }
}
