package io.github.sob1234509876.oc.server.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The local {@link UserStore}: one text file next to the server, one account per line.
 *
 * <p>A text file is chosen over a database because a game server that runs on somebody's machine must
 * start with nothing installed. The file stays readable and hand-editable, and an operator can back it
 * up or move it with the tools they already have.
 *
 * <p><b>Format.</b> After an optional header line beginning with {@code #}, every line is one account
 * with six tab separated fields: name, password hash, salt, iteration count, registration time and last
 * login time. A separator inside a field would silently shift every later field, so the three characters
 * that could do that, plus the escape character itself, are percent-encoded as
 * {@code %25}, {@code %09}, {@code %0A} and {@code %0D}. A name can no longer break a line, whichever
 * characters a future name rule allows.
 *
 * <p><b>Durability.</b> {@link #flush()} writes a sibling temporary file and moves it over the original.
 * A move within one directory is atomic on every filesystem this runs on, so a crash mid-write leaves
 * either the old file or the new one, never a half-written account list. A failure during the write
 * removes the temporary file rather than leaving debris next to the real one.
 *
 * <p><b>Errors.</b> A missing file means an empty store, which is the normal first start. A line that
 * cannot be parsed, on the other hand, throws with its line number: quietly dropping accounts would
 * turn a damaged file into mysterious "no such user" reports, and silently rewriting the file would
 * destroy whatever the operator was trying to recover.
 *
 * <p>Instances are not thread safe; {@link AuthService} serialises access. Reads go through the
 * in-memory map, and the file is touched only by {@link #flush()}.
 *
 * @see UserRecord
 */
public final class LocalUserStore implements UserStore {

    /** File name used by {@link #inDirectory(Path)}. */
    public static final String FILE_NAME = "users.txt";

    /** Suffix of the file the new content is built in before it replaces the real one. */
    public static final String TEMP_SUFFIX = ".tmp";

    /** First characters of the header line; a line starting with it is skipped by the loader. */
    private static final String HEADER_PREFIX = "#";

    private static final char SEPARATOR = '\t';
    private static final char ESCAPE = '%';
    private static final String HEX_DIGITS = "0123456789ABCDEF";

    /** Number of fields in a record line, checked on load so a truncated line cannot pass silently. */
    private static final int FIELD_COUNT = 6;

    /** Insertion ordered so that {@link #all()} and the file agree, and a diff of two files reads well. */
    private final Map<String, UserRecord> records = new LinkedHashMap<>();

    private final Path file;

    /**
     * Whether the file differs from {@link #records}.
     *
     * <p>Starts true so that a store over a non-existent file materialises it on the first flush even
     * when nothing was saved: that first write is what creates the directory and proves the location is
     * writable, and it is better to find that out at startup than at the first registration.
     */
    private boolean dirty = true;

    /** Loads the accounts held in the given file; a missing file yields an empty store. */
    public LocalUserStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        if (file.getFileName() == null) {
            throw new IllegalArgumentException("the user file must be named: " + file);
        }
        load();
    }

    /** A store over exactly the given file, whose parent directories are created on the first flush. */
    public static LocalUserStore at(Path file) {
        return new LocalUserStore(file);
    }

    /** A store over {@value #FILE_NAME} inside the given directory. */
    public static LocalUserStore inDirectory(Path directory) {
        return new LocalUserStore(Objects.requireNonNull(directory, "directory").resolve(FILE_NAME));
    }

    @Override
    public Optional<UserRecord> find(String name) {
        return Optional.ofNullable(records.get(name));
    }

    @Override
    public Collection<UserRecord> all() {
        return Collections.unmodifiableList(new ArrayList<>(records.values()));
    }

    @Override
    public void save(UserRecord record) {
        Objects.requireNonNull(record, "record");
        records.put(record.name(), record);
        dirty = true;
    }

    @Override
    public boolean remove(String name) {
        boolean existed = records.remove(name) != null;
        if (existed) {
            dirty = true;
        }
        return existed;
    }

    /**
     * Writes the whole file when anything changed, and does nothing otherwise.
     *
     * <p>The whole file is rewritten because that is what makes the atomic move possible; an in-place
     * append would be cheap but could tear. The dirty flag keeps the cost off the common path, since a
     * successful login flushes and most logins change nothing except one timestamp.
     */
    @Override
    public void flush() {
        if (!dirty) {
            return;
        }
        Path parent = file.toAbsolutePath().getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = file.resolveSibling(file.getFileName() + TEMP_SUFFIX);
            try {
                Files.writeString(temporary, render(), StandardCharsets.UTF_8);
                moveIntoPlace(temporary);
            } catch (IOException | RuntimeException e) {
                Files.deleteIfExists(temporary);
                throw e;
            }
            dirty = false;
        } catch (IOException e) {
            throw new IllegalStateException("cannot write the user store " + file, e);
        }
    }

    @Override
    public Path location() {
        return file;
    }

    /**
     * Replaces the file with the temporary one.
     *
     * <p>An atomic move is asked for first, because that is the whole point of writing a temporary file.
     * When it is refused the plain move is attempted before giving up: a filesystem that cannot move
     * atomically, and a Windows machine where a reader or a scanner holds the target open for a moment,
     * both report a failure that the ordinary replace still survives. The content is complete before
     * either move starts, so the fallback is never worse than the atomic path, and a failure of the
     * second attempt is the real error.
     */
    private void moveIntoPlace(Path temporary) throws IOException {
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return;
        } catch (AtomicMoveNotSupportedException e) {
            // Expected on a filesystem without atomic moves; the plain move below is the documented path.
        } catch (IOException e) {
            // A transient refusal, such as a Windows sharing violation, still leaves a plain replace.
        }
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /** The complete file content, header first and one line per account. */
    private String render() {
        StringBuilder text = new StringBuilder(records.size() * 96 + 64);
        text.append(HEADER_PREFIX).append(" oc game server accounts v1").append(System.lineSeparator());
        for (UserRecord record : records.values()) {
            text.append(encode(record.name())).append(SEPARATOR)
                    .append(encode(record.passwordHash())).append(SEPARATOR)
                    .append(encode(record.passwordSalt())).append(SEPARATOR)
                    .append(record.passwordIterations()).append(SEPARATOR)
                    .append(record.registeredAtMillis()).append(SEPARATOR)
                    .append(record.lastLoginMillis()).append(System.lineSeparator());
        }
        return text.toString();
    }

    /** Reads the file into the map; called once, from the constructor. */
    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read the user store " + file, e);
        }
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank() || line.startsWith(HEADER_PREFIX)) {
                continue;
            }
            UserRecord record = parse(line, index + 1);
            records.put(record.name(), record);
        }
    }

    /**
     * Turns one line into a record, or fails with the line number.
     *
     * <p>Every failure mode is reported as an {@link IllegalStateException} carrying the file and the
     * line, including the one the record constructor raises: a hand-edited file with a bad name or a
     * future iteration count is a damaged store, and the operator needs the line, not a stack trace
     * about a parameter.
     */
    private UserRecord parse(String line, int lineNumber) {
        String[] fields = line.split(Character.toString(SEPARATOR), -1);
        if (fields.length != FIELD_COUNT) {
            throw malformed(lineNumber, "expected " + FIELD_COUNT + " tab separated fields but found "
                    + fields.length);
        }
        try {
            return new UserRecord(decode(fields[0]), decode(fields[1]), decode(fields[2]),
                    Integer.parseInt(fields[3]), Long.parseLong(fields[4]), Long.parseLong(fields[5]));
        } catch (IllegalArgumentException e) {
            throw malformed(lineNumber, e.getMessage());
        }
    }

    private IllegalStateException malformed(int lineNumber, String detail) {
        return new IllegalStateException(
                "malformed user store " + file + " line " + lineNumber + ": " + detail);
    }

    /**
     * Percent-encodes the separator and the escape character.
     *
     * <p>Encoding is unconditional rather than "only when the separator is present": a value that is
     * encoded depending on its content is decoded the same way, so a name that literally contains
     * {@code %25} round-trips without any second-guessing.
     */
    private static String encode(String value) {
        StringBuilder encoded = null;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (needsEncoding(character)) {
                if (encoded == null) {
                    encoded = new StringBuilder(value.length() + 8).append(value, 0, index);
                }
                encoded.append(ESCAPE).append(HEX_DIGITS.charAt(character >>> 4))
                        .append(HEX_DIGITS.charAt(character & 0xF));
            } else if (encoded != null) {
                encoded.append(character);
            }
        }
        return encoded == null ? value : encoded.toString();
    }

    /** Whether a character would collide with the line format and therefore has to be escaped. */
    private static boolean needsEncoding(char character) {
        return character == ESCAPE || character == SEPARATOR || character == '\n' || character == '\r';
    }

    /** The inverse of {@link #encode(String)}; a stray percent sign is a format error, not data. */
    private static String decode(String value) {
        int escape = value.indexOf(ESCAPE);
        if (escape < 0) {
            return value;
        }
        StringBuilder decoded = new StringBuilder(value.length()).append(value, 0, escape);
        int index = escape;
        while (index < value.length()) {
            char character = value.charAt(index);
            if (character != ESCAPE) {
                decoded.append(character);
                index++;
                continue;
            }
            if (index + 2 >= value.length()) {
                throw new IllegalArgumentException("truncated escape sequence at offset " + index);
            }
            int high = HEX_DIGITS.indexOf(Character.toUpperCase(value.charAt(index + 1)));
            int low = HEX_DIGITS.indexOf(Character.toUpperCase(value.charAt(index + 2)));
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("invalid escape sequence at offset " + index);
            }
            decoded.append((char) ((high << 4) | low));
            index += 3;
        }
        return decoded.toString();
    }
}
