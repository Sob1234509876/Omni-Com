package io.github.sob1234509876.oc.googology.ordinal;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The special rendering of one ordinal: an ordered chain of names, kept as aligned
 * {@link OrdinalName} pairs so that the ASCII chain and the LaTeX chain can never drift apart. The
 * first name is conventionally the ordinal's own expression, and the following names are the shorthand
 * and community names it is known by, so the rendering of
 * {@code BMS(0)(1,1,1)(2,2)} is the chain {@code BMS(0)(1,1,1)(2,2)} = {@code ψ(psd. Π_ω)} =
 * {@code SSO} and renders as {@code BMS(0)(1,1,1)(2,2)=p(psd. Pi(w))=SSO} in ASCII and as
 * {@code \mathrm{BMS}(0)(1,1,1)(2,2)=\psi(\mathrm{psd.}\ \Pi_\omega)=\mathrm{SSO}} in LaTeX.
 *
 * <p>Instances are immutable and safe to share.
 *
 * @param names the aligned name chain, at least one name
 */
public record OrdinalRendering(List<OrdinalName> names) {

    public OrdinalRendering {
        Objects.requireNonNull(names, "names");
        names = List.copyOf(names);
        if (names.isEmpty()) {
            throw new IllegalArgumentException("a rendering needs at least one name");
        }
    }

    /** The chain with the given names. */
    public static OrdinalRendering of(OrdinalName... names) {
        return new OrdinalRendering(Arrays.asList(names));
    }

    /** The chain with the given names. */
    public static OrdinalRendering of(List<OrdinalName> names) {
        return new OrdinalRendering(names);
    }

    /**
     * Two full chains split on {@code '='}: the ASCII text and the LaTeX text of the same names, in the
     * same order. The split is a convenience for writing table entries down; a name that legitimately
     * contains an equals sign should use {@link #of(OrdinalName...)} instead. The two chains must have
     * the same number of names.
     */
    public static OrdinalRendering parse(String asciiChain, String latexChain) {
        Objects.requireNonNull(asciiChain, "asciiChain");
        Objects.requireNonNull(latexChain, "latexChain");
        String[] ascii = asciiChain.split("=", -1);
        String[] latex = latexChain.split("=", -1);
        if (ascii.length != latex.length) {
            throw new IllegalArgumentException("the two chains must have the same number of names, but the "
                    + "ASCII chain has " + ascii.length + " and the LaTeX chain has " + latex.length);
        }
        OrdinalName[] names = new OrdinalName[ascii.length];
        for (int i = 0; i < ascii.length; i++) {
            names[i] = new OrdinalName(ascii[i], latex[i]);
        }
        return new OrdinalRendering(List.of(names));
    }

    /** The ASCII chain, the names joined by {@code '='}. */
    public String ascii() {
        return join(OrdinalName::ascii);
    }

    /** The LaTeX chain, the names joined by {@code '='}. */
    public String latex() {
        return join(OrdinalName::latex);
    }

    private String join(java.util.function.Function<OrdinalName, String> spelling) {
        return names.stream().map(spelling).reduce((left, right) -> left + "=" + right).orElseThrow();
    }
}
