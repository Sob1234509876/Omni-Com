/**
 * The only Node declarations the offline check script needs.
 *
 * The browser project deliberately has no `@types/node` dependency: `tsc --noEmit` covers `src/`,
 * which is browser-only, and the check script is a Node program. Declaring just `readFileSync` keeps
 * `tsc -p scripts/tsconfig.check.json` strict without adding a package.
 */
declare module "node:fs" {
  /**
   * Reads a text file. `path` may be a `file:` URL, which is how the check locates the classic
   * plugin's manifest relative to `import.meta.url`.
   */
  export function readFileSync(path: string | URL, encoding: "utf8"): string;
}
