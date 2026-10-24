/**
 * Vite resolves CSS imports at build time; this declaration lets `tsc --noEmit` accept the static
 * `katex/dist/katex.min.css` import in `main.ts` without adding Vite's ambient client types.
 */
declare module "*.css";
