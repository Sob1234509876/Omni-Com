/**
 * A tiny typed WebSocket client for OC/1.
 *
 * One command is sent per TEXT frame, and one answer is resolved per command. Answers are matched in
 * order, which is safe because a WebSocket preserves message order in both directions. A timeout closes
 * the connection: after a missing answer the queue can no longer be trusted.
 */

export interface CloseInfo {
  readonly code: number;
  readonly reason: string;
  readonly wasClean: boolean;
}

export interface SocketHooks {
  readonly onOpen?: () => void;
  readonly onClose?: (info: CloseInfo) => void;
  readonly onError?: (error: Error) => void;
  /** Non-fatal notice, such as an ignored binary frame; the connection stays open. */
  readonly onNotice?: (message: string) => void;
  readonly onLine?: (line: string) => void;
}

export interface SocketOptions extends SocketHooks {
  /** How long one command may wait for its answer. Defaults to 10 seconds. */
  readonly timeoutMs?: number;
}

export interface CommandSocket {
  /** Resolves once the connection is closed; it never rejects. */
  readonly closed: Promise<CloseInfo>;
  readonly isOpen: boolean;
  /** Sends one command line and resolves with the one answer line. */
  send(line: string): Promise<string>;
  close(reason?: string): void;
}

interface Pending {
  readonly resolve: (answer: string) => void;
  readonly reject: (error: Error) => void;
  timer: number;
  settled: boolean;
}

function describeClose(info: CloseInfo): string {
  const reason = info.reason === "" ? "无原因" : info.reason;
  return `连接已关闭（code ${info.code}，${reason}）`;
}

function stripOneLineEnding(line: string): string {
  if (line.endsWith("\r\n")) {
    return line.slice(0, -2);
  }
  if (line.endsWith("\n") || line.endsWith("\r")) {
    return line.slice(0, -1);
  }
  return line;
}

export function connect(url: string, options: SocketOptions = {}): CommandSocket {
  const timeoutMs = options.timeoutMs ?? 10_000;
  if (!Number.isFinite(timeoutMs) || timeoutMs <= 0) {
    throw new Error(`回答超时必须是正数毫秒，当前是 ${String(timeoutMs)}`);
  }

  const websocket = new WebSocket(url);
  const pending: Pending[] = [];
  // Once an answer is missing or a frame breaks the protocol, the in-order queue is no longer trustworthy.
  let broken = false;

  let resolveClosed: (info: CloseInfo) => void = () => {
    // The real resolver is installed by the Promise constructor immediately below.
  };
  const closed = new Promise<CloseInfo>((resolve) => {
    resolveClosed = resolve;
  });
  let closeReported = false;

  function failAll(error: Error): void {
    const entries = pending.splice(0, pending.length);
    for (const entry of entries) {
      if (entry.settled) {
        continue;
      }
      entry.settled = true;
      window.clearTimeout(entry.timer);
      entry.reject(error);
    }
  }

  websocket.addEventListener("open", () => {
    options.onOpen?.();
  });

  websocket.addEventListener("message", (event) => {
    const data: unknown = event.data;
    if (typeof data !== "string") {
      // The server response to a client binary frame is a text ERR plus an open connection. A binary
      // frame arriving here is therefore a non-fatal oddity: say so, and keep the in-order queue intact.
      options.onNotice?.("服务端发来二进制帧，已忽略（协议只使用文本帧）");
      return;
    }
    const line = stripOneLineEnding(data);
    if (line.includes("\r") || line.includes("\n")) {
      broken = true;
      const error = new Error("一个帧里包含多条命令行：OC/1 要求每个帧恰好一条命令");
      options.onError?.(error);
      failAll(error);
      websocket.close(1002, "one line per frame");
      return;
    }
    if (broken) {
      options.onLine?.(line);
      return;
    }
    const entry = pending.shift();
    if (entry === undefined) {
      options.onLine?.(line);
      return;
    }
    entry.settled = true;
    window.clearTimeout(entry.timer);
    entry.resolve(line);
  });

  websocket.addEventListener("error", () => {
    options.onError?.(new Error("WebSocket 连接出错（浏览器没有给出更具体的原因）"));
  });

  websocket.addEventListener("close", (event) => {
    if (closeReported) {
      return;
    }
    closeReported = true;
    broken = true;
    const info: CloseInfo = {
      code: event.code,
      reason: event.reason,
      wasClean: event.wasClean,
    };
    resolveClosed(info);
    failAll(new Error(describeClose(info)));
    options.onClose?.(info);
  });

  function send(line: string): Promise<string> {
    if (line.includes("\r") || line.includes("\n")) {
      return Promise.reject(new Error("一条命令只能是一行：命令里不能有换行"));
    }
    if (broken) {
      return Promise.reject(new Error("连接已经关闭，不能再发命令"));
    }
    if (websocket.readyState !== WebSocket.OPEN) {
      return Promise.reject(new Error("WebSocket 尚未连接或已经关闭"));
    }
    return new Promise<string>((resolve, reject) => {
      const entry: Pending = {
        resolve,
        reject,
        timer: 0,
        settled: false,
      };
      entry.timer = window.setTimeout(() => {
        if (entry.settled) {
          return;
        }
        entry.settled = true;
        broken = true;
        const index = pending.indexOf(entry);
        if (index >= 0) {
          pending.splice(index, 1);
        }
        const error = new Error(`等待回答超时（${timeoutMs} 毫秒）；连接将关闭`);
        reject(error);
        options.onError?.(error);
        websocket.close(1001, "answer timeout");
      }, timeoutMs);
      pending.push(entry);
      try {
        websocket.send(line);
      } catch (failure: unknown) {
        entry.settled = true;
        window.clearTimeout(entry.timer);
        const index = pending.indexOf(entry);
        if (index >= 0) {
          pending.splice(index, 1);
        }
        const message = failure instanceof Error ? failure.message : String(failure);
        reject(new Error(`发送失败：${message}`));
      }
    });
  }

  function close(reason = "client closed"): void {
    if (
      websocket.readyState === WebSocket.CONNECTING ||
      websocket.readyState === WebSocket.OPEN
    ) {
      websocket.close(1000, reason.slice(0, 100));
    }
  }

  return {
    closed,
    get isOpen(): boolean {
      return websocket.readyState === WebSocket.OPEN;
    },
    send,
    close,
  };
}
