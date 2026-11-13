/**
 * A register of special renderings for named ordinals.
 *
 * <p>The googology library renders every value mechanically: a BMS ordinal as its matrix
 * {@code (0)(1,1,1)(2,2)}, a natural number in arrow notation {@code 10↑↑10}. But the googology
 * community knows many ordinals by dedicated names, often as a chain of spellings - the ordinal
 * {@code BMS(0)(1,1,1)(2,2)} is {@code ψ(psd. Π_ω)} and simply {@code SSO}, and a reader wants to see
 * {@code BMS(0)(1,1,1)(2,2)=p(psd. Pi(w))=SSO} in ASCII or
 * {@code \mathrm{BMS}(0)(1,1,1)(2,2)=\psi(\mathrm{psd.}\ \Pi_\omega)=\mathrm{SSO}} in LaTeX.
 *
 * <p>{@link OrdinalRenderRegister} is the seam: entries are registered under the canonical text the
 * value's own {@code toString()} produces, and each entry is an {@link OrdinalRendering} - an aligned
 * chain of {@link OrdinalName} pairs, one ASCII and one LaTeX spelling per name.
 *
 * <p>The register ships <b>empty by design</b>. The table of entries is content that is filled in
 * separately (and deliberately not by this library's own self check), while this module only provides
 * and checks the mechanism: registration, lookup, the chain format, and the fallbacks for values that
 * are not registered. Run the mechanism check with
 * {@code ./gradlew :projects:oc:oc-googology-ordinal:selfcheck}.
 */
package io.github.sob1234509876.oc.googology.ordinal;
