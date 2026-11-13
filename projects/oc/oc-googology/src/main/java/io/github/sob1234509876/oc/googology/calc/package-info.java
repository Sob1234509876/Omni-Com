/**
 * The debugging calculator: an interactive REPL over the googology layer, plus the pure command engine
 * behind it. {@link io.github.sob1234509876.oc.googology.calc.Calc} is the launchable loop (Gradle task
 * {@code calc}), {@link io.github.sob1234509876.oc.googology.calc.CalcEngine} turns one line into one
 * answer and never throws, which is what the self check drives.
 *
 * <p>Everything the calculator exposes already exists in the library - expression evaluation over
 * {@code Natural}, the comparison channels, the level-resolved inverses, transfinite levels through
 * {@code TransfiniteHyperoperation}, and Bashicu ordinal inspection. This package only adds the forgiving
 * command grammar and the never-crash loop, so it stays a debugging aid and never a second arithmetic.
 */
package io.github.sob1234509876.oc.googology.calc;
