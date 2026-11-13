package io.github.sob1234509876.oc.googology.ordinal;

import java.util.Objects;

/**
 * One name of an ordinal in the two text worlds the register serves: an ASCII spelling for logs and
 * protocols, and a LaTeX spelling for math rendering. The two spellings of one entry belong together -
 * {@code p(psd. Pi(w))} and {@code \psi(\mathrm{psd.}\ \Pi_\omega)} are the same name of the same
 * ordinal, so they are stored as a pair rather than as two independent chains that could drift apart.
 *
 * @param ascii  the ASCII spelling, never blank
 * @param latex  the LaTeX spelling, never blank
 */
public record OrdinalName(String ascii, String latex) {

    public OrdinalName {
        ascii = requireNonBlank(ascii, "ascii");
        latex = requireNonBlank(latex, "latex");
    }

    /** The pair with the given spellings. */
    public static OrdinalName of(String ascii, String latex) {
        return new OrdinalName(ascii, latex);
    }

    private static String requireNonBlank(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) {
            throw new IllegalArgumentException("the " + label + " spelling of an ordinal name must not be blank");
        }
        return value;
    }
}
