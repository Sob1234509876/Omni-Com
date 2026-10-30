/**
 * The one UI font stack, shared by the page's monospace chrome and every canvas `font` assignment.
 *
 * `index.html` bundles JetBrains Mono from `public/fonts/JetBrainsMono-Medium.ttf` as a single
 * `@font-face` declared over the whole weight range (100..900). A requested bold therefore matches
 * that one Medium face instead of making the browser synthesise a bold; the old system monospace
 * stack stays after it as the fallback, and the page's base body stack adds Microsoft YaHei because
 * the bundled font has no Chinese glyphs.
 */
export const FONT_STACK =
  '"JetBrains Mono", ui-monospace, "Cascadia Code", Consolas, monospace';
