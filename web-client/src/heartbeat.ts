/**
 * The client heartbeat: how often the page proves the socket is still alive.
 *
 * The browser endpoint closes an idle WebSocket after its configured idle timeout (one minute in the
 * old server), and `/world/snapshot` is plain HTTP, so it does not refresh the WebSocket. While the
 * connection is usable the page therefore sends `PING` at this interval through the same in-order
 * command queue as every other line; the server answers `PONG`.
 *
 * The state helper is pure so `scripts/check-render-math.ts` can prove the timer's start/stop rule
 * without a browser: the timer may run only after a successful connection and never queues a second
 * `PING` while one is still waiting for its answer.
 */

/** How often a connected client sends `PING`, in milliseconds. */
export const HEARTBEAT_MS = 20_000;

/** The connection facts the heartbeat decision depends on. */
export interface HeartbeatState {
  /** True once the WebSocket handshake succeeded and the connection is usable. */
  readonly connected: boolean;
  /** True while a `PING` is still waiting for its `PONG`; a second one must not be queued. */
  readonly pingInFlight: boolean;
}

/**
 * Whether the heartbeat should send a `PING` now.
 *
 * `false` before a successful connection, after the connection closed, and while the previous
 * `PING` has not been answered yet. The timer itself only exists while `connected` is true; this
 * helper additionally guards against piling commands up behind a slow answer.
 */
export function shouldHeartbeat(state: HeartbeatState): boolean {
  return state.connected && !state.pingInFlight;
}
