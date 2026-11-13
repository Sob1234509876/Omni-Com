package io.github.sob1234509876.oc.googology.ordinal;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A register that gives named ordinals their special renderings. The googology library renders values
 * mechanically - {@code (0)(1,1,1)(2,2)}, {@code 10↑↑10} - but the community knows many ordinals by
 * dedicated names and chains of names: {@code BMS(0)(1,1,1)(2,2)} is {@code ψ(psd. Π_ω)} and simply
 * {@code SSO}. This class is the seam where such renderings are registered; a rendering is an
 * {@link OrdinalRendering}, an aligned chain of ASCII and LaTeX spellings.
 *
 * <p>The register is keyed by the ordinal's <b>canonical text</b> - the exact string its own
 * {@code toString()} produces, whatever type it is. There is deliberately no common supertype behind
 * {@code BmsOrdinal}, {@code LadderOrdinal}, {@link io.github.sob1234509876.oc.googology.Natural} and
 * future notations, so the register takes any value and looks up {@code value.toString()}; registering
 * therefore means copying the text the value itself prints. Registering the same canonical text twice
 * is almost certainly a mistake and is rejected.
 *
 * <p>The render methods are total: a registered value renders as its chain, and anything else falls
 * back to its own text - a {@link io.github.sob1234509876.oc.googology.Natural} falls back to its ASCII
 * or LaTeX form, every other object to {@code toString()}. The register itself is content free: it
 * ships empty on purpose, and the table of entries is filled in separately (it is data, not mechanism).
 *
 * <p>Safe for concurrent reads once registration is done; registrations are atomic but the usual
 * populate-then-read discipline applies.
 */
public final class OrdinalRenderRegister {

    private static final OrdinalRenderRegister STANDARD = new OrdinalRenderRegister();

    private final Map<String, OrdinalRendering> renderings = new ConcurrentHashMap<>();

    private OrdinalRenderRegister() {
    }

    /**
     * The shared register. It starts empty; whoever fills the ordinal table registers into this
     * instance so that every reader sees the same table. Callers that want a private table (a test, an
     * experiment) create their own with {@link #create()}.
     */
    public static OrdinalRenderRegister standard() {
        return STANDARD;
    }

    /** A fresh, independent register. */
    public static OrdinalRenderRegister create() {
        return new OrdinalRenderRegister();
    }

    /**
     * Registers the rendering of the ordinal whose canonical text is the given one.
     *
     * @throws IllegalArgumentException when the canonical text is blank or already registered
     */
    public void register(String canonical, OrdinalRendering rendering) {
        Objects.requireNonNull(canonical, "canonical");
        if (canonical.isBlank()) {
            throw new IllegalArgumentException("the canonical text of a registered ordinal must not be blank");
        }
        Objects.requireNonNull(rendering, "rendering");
        OrdinalRendering previous = renderings.putIfAbsent(canonical, rendering);
        if (previous != null) {
            throw new IllegalArgumentException("an ordinal is already registered under " + canonical
                    + " as " + previous.ascii());
        }
    }

    /** The rendering registered under the given canonical text, if any. */
    public Optional<OrdinalRendering> find(String canonical) {
        return Optional.ofNullable(renderings.get(Objects.requireNonNull(canonical, "canonical")));
    }

    /** Whether an ordinal is registered under the given canonical text. */
    public boolean knows(String canonical) {
        return renderings.containsKey(Objects.requireNonNull(canonical, "canonical"));
    }

    /** How many ordinals are registered. */
    public int size() {
        return renderings.size();
    }

    /**
     * The ASCII text of the value: its registered chain when one is registered under the value's own
     * {@code toString()}, otherwise the value's own ASCII text - {@link
     * io.github.sob1234509876.oc.googology.Natural#toAsciiString()} for a natural number,
     * {@code toString()} for anything else.
     */
    public String ascii(Object value) {
        Objects.requireNonNull(value, "value");
        OrdinalRendering found = lookup(value);
        if (found != null) {
            return found.ascii();
        }
        return value instanceof io.github.sob1234509876.oc.googology.Natural natural
                ? natural.toAsciiString()
                : value.toString();
    }

    /**
     * The LaTeX text of the value: its registered chain when one is registered under the value's own
     * {@code toString()}, otherwise the value's own LaTeX text - {@link
     * io.github.sob1234509876.oc.googology.Natural#toLaTeX()} for a natural number,
     * {@code toString()} for anything else.
     */
    public String latex(Object value) {
        Objects.requireNonNull(value, "value");
        OrdinalRendering found = lookup(value);
        if (found != null) {
            return found.latex();
        }
        return value instanceof io.github.sob1234509876.oc.googology.Natural natural
                ? natural.toLaTeX()
                : value.toString();
    }

    private OrdinalRendering lookup(Object value) {
        String canonical = value.toString();
        return canonical == null ? null : renderings.get(canonical);
    }
}
