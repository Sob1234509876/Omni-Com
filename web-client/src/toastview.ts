/**
 * The DOM half of the toast console: a fixed bottom-right stack of the newest exchanges.
 *
 * The pure rules (lifetime, cap, error tone, trimming) live in `src/toast.ts`; this class only owns the
 * elements and the timers. The stack stores entries oldest first but renders each new toast at the top,
 * so "newest on top" holds even while the fifth-oldest toast is removed.
 */

import { renderLatexInto } from "./render/latex";
import {
  TOAST_VISIBLE_CAP,
  toastLifetimeMs,
  trimToastLine,
  type ToastEntry,
  type ToastTone,
} from "./toast";

/** What the caller supplies for one toast; the console assigns the id and the timer. */
export interface ToastInput {
  readonly tone: ToastTone;
  readonly command: string | null;
  readonly answer: string;
}

function appendLine(host: HTMLElement, arrow: string, text: string): void {
  const line = document.createElement("p");
  line.className = "toast-line";

  const marker = document.createElement("span");
  marker.className = "toast-arrow";
  marker.textContent = arrow;
  marker.setAttribute("aria-hidden", "true");

  const body = document.createElement("span");
  body.className = "toast-text";
  // A plain answer keeps its old single text node; `$...$` is upgraded to KaTeX when it arrives. The
  // trim happens first so whitespace collapsing does not split a formula across the visible line.
  void renderLatexInto(body, trimToastLine(text));

  line.append(marker, body);
  host.append(line);
}

export class ToastConsole {
  private readonly host: HTMLElement;
  private readonly entries: ToastEntry[] = [];
  private readonly timers = new Map<number, number>();
  private nextId = 1;

  constructor(host: HTMLElement) {
    this.host = host;
  }

  /** Number of toasts currently on screen; never more than {@link TOAST_VISIBLE_CAP}. */
  get visibleCount(): number {
    return this.entries.length;
  }

  /** Adds one toast at the top and starts its own dismissal timer. */
  push(input: ToastInput): void {
    const entry: ToastEntry = {
      id: this.nextId,
      tone: input.tone,
      command: input.command,
      answer: input.answer,
    };
    this.nextId += 1;
    this.entries.push(entry);

    const element = this.createElement(entry);
    this.host.prepend(element);
    this.timers.set(
      entry.id,
      window.setTimeout(() => {
        this.remove(entry.id);
      }, toastLifetimeMs(entry.tone)),
    );

    while (this.entries.length > TOAST_VISIBLE_CAP) {
      const oldest = this.entries[0];
      if (oldest === undefined) {
        break;
      }
      this.remove(oldest.id);
    }
  }

  /** Removes every toast and cancels all pending timers; used by the log overlay's clear action. */
  clear(): void {
    for (const timer of this.timers.values()) {
      window.clearTimeout(timer);
    }
    this.timers.clear();
    this.entries.length = 0;
    this.host.replaceChildren();
  }

  private remove(id: number): void {
    const timer = this.timers.get(id);
    if (timer !== undefined) {
      window.clearTimeout(timer);
      this.timers.delete(id);
    }
    const index = this.entries.findIndex((entry) => entry.id === id);
    if (index >= 0) {
      this.entries.splice(index, 1);
    }
    for (const element of this.host.children) {
      if (element instanceof HTMLElement && element.dataset["toastId"] === String(id)) {
        element.remove();
        break;
      }
    }
  }

  private createElement(entry: ToastEntry): HTMLElement {
    const toast = document.createElement("div");
    toast.className = `toast toast-${entry.tone}`;
    toast.dataset["toastId"] = String(entry.id);
    toast.setAttribute("role", entry.tone === "error" ? "alert" : "status");
    if (entry.command !== null && entry.command !== "") {
      appendLine(toast, "→", entry.command);
      appendLine(toast, "←", entry.answer);
    } else {
      // A notice or a client-side refusal has no command half, so it shows one marker line.
      appendLine(toast, entry.tone === "error" ? "!" : "←", entry.answer);
    }
    return toast;
  }
}
