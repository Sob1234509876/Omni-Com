package io.github.sob1234509876.oc.googology.cardinalregister;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The document reference of one {@link CardinalRung}: a path <strong>relative to the repository root</strong>
 * and the heading of the section inside that document which defines the rung.
 *
 * <p>The cardinal register does not define any cardinal itself. Every rung delegates its definition to a
 * section of {@code documents/Human/Cardinals/}, and this record is that delegation made checkable: the path
 * is relative (so the register does not depend on where the repository is checked out), {@link #resolve()}
 * locates the file on disk, and the self check asserts that the file exists and that it contains a heading
 * which mentions the rung. A {@code RelativePath} is therefore the only link from the Java register to the
 * prose definition, and the check refuses a dangling one.
 *
 * <p><b>Why the repository root is searched, not assumed.</b> Gradle runs the self check with the module
 * directory ({@code projects/oc/oc-googology}) as its working directory when it is invoked from the project,
 * and with the repository root as its working directory when it is invoked from a wrapper at the top. A path
 * such as {@code documents/Human/Cardinals/Big Cardinals.md} is interpreted from the repository root, so
 * {@link #resolve()} walks up from the current directory until it finds the directory that contains
 * {@code documents/Human/Ordinals.md}. That makes the same constant work under both working directories
 * without a hard-coded absolute path.
 *
 * <p>The section is stored without the leading {@code #} characters and without the Markdown heading level,
 * for example {@code "2. $\\alpha$-Mahlo cardinals"}. The check compares it with the document's headings
 * without regard to case or repeated whitespace, so the stored text has to be a heading that really is in the
 * file - not a paraphrase of it.
 *
 * @see CardinalRung#definition()
 * @since 2.0a
 */
public final class RelativePath {

    /** The marker the register uses to find the repository root from any working directory. */
    private static final String ROOT_MARKER = "documents/Human/Ordinals.md";

    private final Path path;
    private final String section;

    private RelativePath(Path path, String section) {
        this.path = path;
        this.section = section;
    }

    /**
     * A document reference.
     *
     * @param path    the path of the document relative to the repository root, with {@code /} separators;
     *                it must be relative, must not contain {@code ..} and must not be blank
     * @param section the heading of the section that defines the rung, without the leading {@code #} and
     *                without the heading level; it must not be blank
     * @return the reference
     * @throws NullPointerException     if either argument is {@code null}
     * @throws IllegalArgumentException if the path is absolute, contains {@code ..}, is blank, or the section
     *                                  is blank
     */
    public static RelativePath of(String path, String section) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(section, "section");
        Path relative = Path.of(path);
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("the document path must be relative to the repository root,"
                    + " but was absolute: " + path);
        }
        for (Path part : relative) {
            if (part.toString().equals("..")) {
                throw new IllegalArgumentException("the document path must not climb out of the repository,"
                        + " but contained '..': " + path);
            }
        }
        if (relative.toString().isBlank()) {
            throw new IllegalArgumentException("the document path must not be blank");
        }
        return new RelativePath(relative, requireText(section, "section"));
    }

    /** The document path, relative to the repository root. */
    public Path path() {
        return path;
    }

    /** The heading of the section that defines the rung, without the leading {@code #} characters. */
    public String section() {
        return section;
    }

    /**
     * The absolute path of the document, found from the current working directory by walking up to the first
     * ancestor that contains {@code documents/Human/Ordinals.md} and then resolving this reference there.
     *
     * @return the absolute path of the document
     * @throws IllegalStateException if no ancestor of the current directory is the repository root
     */
    public Path resolve() {
        Path root = repositoryRoot();
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalStateException(display() + " escapes the repository root " + root);
        }
        return resolved;
    }

    /** Whether {@link #resolve()} names a regular file. A missing repository root counts as {@code false}. */
    public boolean exists() {
        try {
            return Files.isRegularFile(resolve());
        } catch (IllegalStateException missingRoot) {
            return false;
        }
    }

    /** The reference as text, for a report: the path, a section sign and the section heading. */
    public String display() {
        return path.toString().replace('\\', '/') + " \u00a7 " + section;
    }

    @Override
    public String toString() {
        return display();
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve(ROOT_MARKER))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("cannot locate the repository root: no ancestor of "
                + Path.of("").toAbsolutePath() + " contains " + ROOT_MARKER);
    }

    private static String requireText(String text, String what) {
        if (text.isBlank()) {
            throw new IllegalArgumentException("the " + what + " must not be blank");
        }
        return text;
    }
}
