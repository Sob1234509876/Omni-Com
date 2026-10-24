/**
 * LaTeX support for every piece of dynamic text on the page: log lines, the status chip, toasts, the
 * hover tooltip, the HUD and the command bar's selection / queue / TRAIN / BUILD labels.
 *
 * The module follows the house pattern of a pure part plus a thin DOM part:
 *
 * - {@link splitTex} is a total, DOM-free splitter. It recognises inline math (`$...$`) and display
 *   math (`$$...$$`) on one string and never throws; `\$` is a literal dollar, an unterminated `$`
 *   stays plain text, `$$` wins over `$`, and an empty pair is left literal.
 * - {@link renderTexSegment} turns one math segment into KaTeX HTML. It uses the KaTeX defaults the
 *   page wants: `throwOnError: false`, `displayMode` for display math and `output: "html"`. Both the
 *   library and its stylesheet are optional at runtime: an unavailable or throwing KaTeX produces an
 *   escaped plain-text fallback instead of an exception.
 * - {@link renderLatexInto} is the DOM helper that rebuilds one element from segments. Text stays a
 *   text node (so it is escaped by construction), math is inserted as KaTeX's own nodes, and a
 *   node/text pair is never rendered twice. A long log can therefore append one element per line
 *   instead of rebuilding its whole history.
 *
 * The KaTeX entry is deliberately lazy: `import("katex")` runs on the first formula only, so a page
 * that never shows math never downloads the library. The stylesheet has to be static for Vite to
 * bundle the 60 fonts, but it lives in `main.ts` rather than here so Node's type-stripping check can
 * import this module (Node cannot load CSS).
 */

/** The three kinds of piece {@link splitTex} produces. */
export type TexSegmentKind = "text" | "inline" | "display";

/** One piece of a text string: plain text or one math span without its delimiters. */
export interface TexSegment {
  readonly kind: TexSegmentKind;
  /** Plain text, or the TeX source between the dollars (delimiters excluded). */
  readonly text: string;
}

/** The slice of the KaTeX API this module uses. Kept structural so a test can inject a stub. */
export interface TexMathRenderer {
  renderToString(tex: string, options?: TexMathRenderOptions): string;
}

/** The exact KaTeX options the page asks for. */
export interface TexMathRenderOptions {
  readonly throwOnError: boolean;
  readonly displayMode: boolean;
  readonly output: "html";
}

/** Loads KaTeX, or answers `null` when it cannot be loaded. Injected only by the offline checks. */
export type TexMathLoader = () => Promise<TexMathRenderer | null>;

/**
 * Splits one display string into plain-text and math segments.
 *
 * The rules are intentionally small and total:
 *
 * - `\$` is one literal dollar in the plain text and never opens math;
 * - `$$...$$` is display math and is tried before `$...$`;
 * - `$...$` is inline math, provided it is closed later on the same string;
 * - an unterminated or empty pair is plain text, never an exception and never an empty formula;
 * - a string with no dollar at all is exactly one plain segment.
 *
 * Escaped dollars are unescaped in plain segments (`price \$5` renders `price $5`); the TeX source of
 * a math segment is passed through unchanged. Adjacent text is merged, and empty text is never added.
 */
export function splitTex(text: string): TexSegment[] {
  const segments: TexSegment[] = [];
  let plain = "";
  let index = 0;

  const flushPlain = (): void => {
    if (plain !== "") {
      segments.push({ kind: "text", text: plain });
      plain = "";
    }
  };

  while (index < text.length) {
    const character = text.charAt(index);
    if (character === "\\" && text.charAt(index + 1) === "$") {
      plain += "$";
      index += 2;
      continue;
    }
    if (character !== "$") {
      plain += character;
      index += 1;
      continue;
    }

    if (text.startsWith("$$", index)) {
      const close = findDelimiter(text, index + 2, "$$");
      if (close >= 0) {
        const source = text.slice(index, close + 2);
        if (text.slice(index + 2, close).trim() === "") {
          // `$$$$` or `$$   $$`: an empty display formula is not worth a block, so keep the text.
          plain += source;
        } else {
          flushPlain();
          segments.push({ kind: "display", text: text.slice(index + 2, close) });
        }
        index = close + 2;
        continue;
      }
      // No closing `$$`: the two dollars are ordinary text. Advancing two characters also keeps a
      // second `$` from being mistaken for an inline opener.
      plain += "$$";
      index += 2;
      continue;
    }

    const close = findDelimiter(text, index + 1, "$");
    if (close >= 0) {
      const source = text.slice(index, close + 1);
      if (text.slice(index + 1, close).trim() === "") {
        plain += source;
      } else {
        flushPlain();
        segments.push({ kind: "inline", text: text.slice(index + 1, close) });
      }
      index = close + 1;
      continue;
    }
    plain += "$";
    index += 1;
  }

  flushPlain();
  // An empty string has no dollar yet still has one plain segment.
  return segments.length === 0 ? [{ kind: "text", text: "" }] : segments;
}

/**
 * Finds the next unescaped delimiter at or after `from`; `-1` when the string has no such close.
 * A backslash-dollar pair is skipped so `$a\$b$` closes only at its last dollar.
 */
function findDelimiter(text: string, from: number, delimiter: "$" | "$$"): number {
  let index = from;
  while (index <= text.length - delimiter.length) {
    if (text.charAt(index) === "\\" && text.charAt(index + 1) === "$") {
      index += 2;
      continue;
    }
    if (text.startsWith(delimiter, index)) {
      return index;
    }
    index += 1;
  }
  return -1;
}

/** Escapes a string for safe use as HTML text or in an attribute. */
export function escapeHtml(text: string): string {
  return text
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

/** The source form of a segment, dollars included; used for the pre-render and fallback text. */
function segmentSource(segment: TexSegment): string {
  if (segment.kind === "display") {
    return `$$${segment.text}$$`;
  }
  if (segment.kind === "inline") {
    return `$${segment.text}$`;
  }
  return segment.text;
}

/**
 * The escaped plain-text fallback of one segment, dollars included.
 *
 * This is what the page shows when KaTeX cannot be loaded or throws: the original text, HTML-escaped,
 * never an exception and never unescaped TeX. It is exported so the offline check can assert the
 * escaping without needing to break the real library.
 */
export function plainTexFallback(kind: TexSegmentKind, tex: string): string {
  if (kind === "text") {
    return escapeHtml(tex);
  }
  return escapeHtml(kind === "display" ? `$$${tex}$$` : `$${tex}$`);
}

/** The one KaTeX promise; `null` before the first formula, so a page without math never loads it. */
let katexPromise: Promise<TexMathRenderer | null> | null = null;

/**
 * Loads KaTeX on first use. A rejected import is cached as `null` too: the page falls back to plain
 * text and never retries the network on every later formula. The literal `await import("katex")` is
 * the only reference to the library in this module, so Vite emits it as a lazy chunk.
 */
function loadKatex(): Promise<TexMathRenderer | null> {
  katexPromise ??= (async (): Promise<TexMathRenderer | null> => {
    try {
      const module = await import("katex");
      const renderer = module.default;
      if (typeof renderer.renderToString !== "function") {
        return null;
      }
      return {
        renderToString: (tex: string, options?: TexMathRenderOptions): string =>
          renderer.renderToString(tex, options),
      };
    } catch {
      return null;
    }
  })();
  return katexPromise;
}

/**
 * Renders one math segment as KaTeX HTML, or as an escaped plain-text fallback.
 *
 * `load` is an injection point for the offline check only; the page always uses {@link loadKatex},
 * which keeps the library in a lazy chunk. The function never rejects: both a missing library and a
 * throwing renderer end in {@link plainTexFallback}.
 */
export async function renderTexSegment(
  kind: TexSegmentKind,
  tex: string,
  load: TexMathLoader = loadKatex,
): Promise<string> {
  if (kind === "text") {
    return escapeHtml(tex);
  }
  try {
    const katex = await load();
    if (katex === null) {
      return plainTexFallback(kind, tex);
    }
    return katex.renderToString(tex, {
      throwOnError: false,
      displayMode: kind === "display",
      output: "html",
    });
  } catch {
    return plainTexFallback(kind, tex);
  }
}

/** One in-flight (or finished) render of a node, keyed by the text it was asked to show. */
interface PendingRender {
  readonly text: string;
  /** Monotonic id; a newer call invalidates an older asynchronous completion. */
  readonly token: number;
  readonly done: Promise<void>;
}

const pendingRenders = new WeakMap<HTMLElement, PendingRender>();
let nextRenderToken = 1;

/**
 * Replaces an element's contents with `text`, rendering `$...$` / `$$...$$` through KaTeX.
 *
 * Plain text takes the synchronous fast path and becomes exactly one text node, so a string with no
 * dollar looks and measures exactly as before. Math first appears as its source text and is replaced
 * by KaTeX output when the lazy chunk resolves; a newer call on the same element invalidates that
 * replacement, and asking for the same text twice reuses the first render instead of rendering the
 * same node again. Text nodes are used for plain segments, so no text is ever interpreted as HTML.
 */
export function renderLatexInto(element: HTMLElement, text: string): Promise<void> {
  const previous = pendingRenders.get(element);
  if (previous !== undefined && previous.text === text) {
    return previous.done;
  }

  if (!text.includes("$")) {
    element.textContent = text;
    const done = Promise.resolve();
    pendingRenders.set(element, { text, token: nextRenderToken, done });
    nextRenderToken += 1;
    return done;
  }

  const segments = splitTex(text);
  if (segments.every((segment) => segment.kind === "text")) {
    element.textContent = segments.map((segment) => segment.text).join("");
    const done = Promise.resolve();
    pendingRenders.set(element, { text, token: nextRenderToken, done });
    nextRenderToken += 1;
    return done;
  }

  const token = nextRenderToken;
  nextRenderToken += 1;
  // Show readable source immediately; measurements and long log lines never see an empty node.
  element.textContent = segments.map((segment) => segmentSource(segment)).join("");

  const done = Promise.all(
    segments.map(async (segment): Promise<Node> => {
      if (segment.kind === "text") {
        return document.createTextNode(segment.text);
      }
      const html = await renderTexSegment(segment.kind, segment.text);
      const template = document.createElement("template");
      template.innerHTML = html;
      return template.content;
    }),
  )
    .then((nodes) => {
      const current = pendingRenders.get(element);
      if (current === undefined || current.token !== token) {
        return;
      }
      element.replaceChildren(...nodes);
    })
    .catch(() => {
      // `renderTexSegment` already handles library failures; keep the plain source if anything else
      // (for example a DOM operation) fails.
    });

  pendingRenders.set(element, { text, token, done });
  return done;
}
