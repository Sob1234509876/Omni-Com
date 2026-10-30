/**
 * The one UI font stack, shared by the page's monospace chrome and every canvas `font` assignment.
 *
 * `index.html` bundles JetBrains Mono from `public/fonts/JetBrainsMono-Medium.ttf` as a single
 * `@font-face` declared over the whole weight range (100..900). The range stops the browser synthesising a
 * faux bold, but one Medium file cannot create real weight: 600 and 700 render exactly like 500, so the
 * stylesheet only asks for 400 and 500. A real bold needs a separate Bold cut, which is not bundled. The
 * system monospace stack stays after the bundled face as the fallback, and the page's base body stack adds
 * Microsoft YaHei because the bundled font has no Chinese glyphs.
 */
export const FONT_STACK =
  '"JetBrains Mono", ui-monospace, "Cascadia Code", Consolas, monospace';
