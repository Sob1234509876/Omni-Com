/**
 * The owner's UI design tokens, kept in one place so the CSS, the TypeScript and the offline check
 * cannot drift apart.
 *
 * `index.html` has to spell these values literally (the stylesheet is inlined and there is no build step
 * that turns TypeScript into CSS), so this module is the machine-readable reference the render check
 * asserts against the stylesheet. The rules are the owner's `global.css` language:
 *
 * - one page colour (`#101010`), one panel surface (`#202020`), one border (`#303030`), one text colour
 *   (`#e0e0e0`) and one quieter text colour (`#b0b0b0`) - all achromatic;
 * - the **only** chromatic treatment is an error: `#402020` background, `#ffe0e0` text, `#503030`
 *   border;
 * - everything is square: no radii, shadows, blurs or glows;
 * - one bundled Medium cut, so weight 600 would render exactly like 500 and must not be claimed. The
 *   stylesheet uses 400 for body text and 500 for emphasis; real bold needs a separate Bold cut of the
 *   font, which this repository does not bundle.
 *
 * Game content is not chrome: terrain, ownership rings, the red enemy marker and the gold selection ring
 * keep their colours on the canvas. These tokens describe the UI around the canvas.
 */

/** The chrome palette. Every value is used literally in `index.html`. */
export const UI_PALETTE = {
  page: "#101010",
  surface: "#202020",
  border: "#303030",
  text: "#e0e0e0",
  muted: "#b0b0b0",
  errorBackground: "#402020",
  errorText: "#ffe0e0",
  errorBorder: "#503030",
} as const;

/** The only weights the single Medium cut can actually show: 600 and 700 are identical to 500. */
export const UI_WEIGHTS = {
  body: 400,
  emphasis: 500,
} as const;

/** The owner's toast slide-in duration: animate the horizontal position over about one second. */
export const TOAST_SLIDE_MS = 1_000;

/** CSS declarations the render check looks for when it verifies the square, unshadowed chrome. */
export const UI_FORBIDDEN_STYLE_PROPERTIES: readonly string[] = [
  "border-radius",
  "box-shadow",
  "text-shadow",
  "backdrop-filter",
] as const;
