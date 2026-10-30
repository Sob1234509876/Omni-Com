/**
 * Pure playlist and time-formatting rules for the built-in music player.
 *
 * The player itself (the `<audio>` element, object URLs and the DOM) lives in `musicview.ts`; everything
 * here is deterministic and DOM-free so `scripts/check-render-math.ts` can exercise it: adding and
 * de-duplicating tracks, removing one, stepping through the list under the loop/shuffle options with an
 * injected random source, and formatting the elapsed/total clock.
 *
 * The playlist is session state only. A refresh loses it because a plain `<input type="file">` hands the
 * page a `File` and no re-openable path; the File System Access API is deliberately not used.
 */

/** Loop modes offered by the player's select. */
export type LoopMode = "off" | "all" | "one";

/** Every loop mode, in menu order. */
export const LOOP_MODES: readonly LoopMode[] = ["off", "all", "one"] as const;

/** Menu labels for the loop modes. */
export const LOOP_MODE_LABELS: Readonly<Record<LoopMode, string>> = {
  off: "不循环",
  all: "列表循环",
  one: "单曲循环",
};

/** Everything needed to create one track; the view supplies the object URL after choosing a file. */
export interface TrackSource {
  /** Stable de-duplication key; the view builds it from name + size + lastModified. */
  readonly key: string;
  /** Display name, which is the file name. */
  readonly name: string;
  /** `URL.createObjectURL(file)` for the chosen local file. */
  readonly url: string;
}

/** One playlist entry. Ids are local, monotonic and never reused inside a session. */
export interface Track extends TrackSource {
  readonly id: number;
}

/** The result of adding files; `added` is what the caller must keep, `skipped` counts duplicates. */
export interface AddTracksResult {
  readonly tracks: readonly Track[];
  readonly added: readonly Track[];
  readonly skipped: number;
}

const FALLBACK_TRACK_NAME = "未命名音频";

function nextTrackId(tracks: readonly Track[]): number {
  let highest = 0;
  for (const track of tracks) {
    if (Number.isSafeInteger(track.id) && track.id > highest) {
      highest = track.id;
    }
  }
  return highest + 1;
}

/**
 * Appends the new sources that are not already present, by `key`.
 *
 * The list is returned as a new array; the input is never mutated. A source without a URL is skipped (its
 * object URL creation failed), and a blank name gets a neutral label so the row is still clickable.
 */
export function addTracks(
  tracks: readonly Track[],
  sources: readonly TrackSource[],
): AddTracksResult {
  const seen = new Set<string>();
  for (const track of tracks) {
    seen.add(track.key);
  }
  const playlist = [...tracks];
  const added: Track[] = [];
  let skipped = 0;
  let id = nextTrackId(tracks);
  for (const source of sources) {
    const key = source.key.trim() === "" ? source.url : source.key;
    if (source.url.trim() === "" || key === "" || seen.has(key)) {
      skipped += 1;
      continue;
    }
    seen.add(key);
    const track: Track = {
      id,
      key,
      name: source.name.trim() === "" ? FALLBACK_TRACK_NAME : source.name,
      url: source.url,
    };
    id += 1;
    playlist.push(track);
    added.push(track);
  }
  return { tracks: playlist, added, skipped };
}

/** The result of removing one track: the new list, where it was, and what was removed. */
export interface RemoveTrackResult {
  readonly tracks: readonly Track[];
  /** Index the removed track had, or `-1` when the id is unknown. */
  readonly index: number;
  readonly removed: Track | null;
}

/** Removes one track by id, preserving the order of the rest. */
export function removeTrack(tracks: readonly Track[], id: number): RemoveTrackResult {
  const index = tracks.findIndex((track) => track.id === id);
  const removed = index < 0 ? null : (tracks[index] ?? null);
  if (index < 0) {
    return { tracks: [...tracks], index: -1, removed };
  }
  return {
    tracks: [...tracks.slice(0, index), ...tracks.slice(index + 1)],
    index,
    removed,
  };
}

/** Clamps a track index into `[0, length - 1]`; any invalid index becomes `0`, an empty list `-1`. */
export function clampIndex(index: number, length: number): number {
  const count = Math.max(0, Math.floor(Number.isFinite(length) ? length : 0));
  if (count === 0) {
    return -1;
  }
  if (!Number.isFinite(index)) {
    return 0;
  }
  return Math.min(count - 1, Math.max(0, Math.floor(index)));
}

/** The options every step consumes; `rand` is injected so a check can seed the shuffle. */
export interface StepOptions {
  readonly loop: LoopMode;
  readonly shuffle: boolean;
  /** A `[0, 1)` source; `Math.random` in the browser, a seeded function in the check. */
  readonly rand?: () => number;
  /** Set when the step is an automatic advance at the end of a track (loop-one repeats it). */
  readonly auto?: boolean;
}

/** A uniform sample in `[0, 1)`, tolerating a missing or malformed injected source. */
function sample(rand: (() => number) | undefined): number {
  if (rand === undefined) {
    return Math.random();
  }
  const value = rand();
  if (!Number.isFinite(value)) {
    return 0;
  }
  return Math.min(0.999_999, Math.max(0, value));
}

/** A random index other than `current`; a one-track list stays on that track. */
function randomOtherIndex(current: number, count: number, rand: (() => number) | undefined): number {
  if (count <= 1) {
    return 0;
  }
  // Draw from the count-1 other slots; shifting past `current` excludes it uniformly.
  const offset = Math.floor(sample(rand) * (count - 1));
  return offset >= current ? offset + 1 : offset;
}

/**
 * The next index for an explicit 下一首 or an automatic end-of-track advance.
 *
 * - `loop: "one"` repeats the current track only on an **automatic** advance; the explicit next button
 *   still moves on, which is what an operator expects from a next button;
 * - `shuffle` picks any other track (a one-track list repeats it);
 * - otherwise the list advances and wraps for `loop: "all"`; with `loop: "off"` the end answers `-1`
 *   so the caller can stop and say that the playlist ended.
 */
export function nextIndex(index: number, length: number, options: StepOptions): number {
  const count = Math.max(0, Math.floor(Number.isFinite(length) ? length : 0));
  if (count === 0) {
    return -1;
  }
  const current = clampIndex(index, count);
  if (options.auto === true && options.loop === "one") {
    return current;
  }
  if (options.shuffle) {
    return randomOtherIndex(current, count, options.rand);
  }
  const candidate = current + 1;
  if (candidate < count) {
    return candidate;
  }
  return options.loop === "off" ? -1 : 0;
}

/**
 * The previous index. The explicit previous button wraps for both loop modes; `loop: "off"` stops at the
 * first track. Shuffle picks another track, mirroring {@link nextIndex}.
 */
export function prevIndex(index: number, length: number, options: StepOptions): number {
  const count = Math.max(0, Math.floor(Number.isFinite(length) ? length : 0));
  if (count === 0) {
    return -1;
  }
  const current = clampIndex(index, count);
  if (options.shuffle) {
    return randomOtherIndex(current, count, options.rand);
  }
  const candidate = current - 1;
  if (candidate >= 0) {
    return candidate;
  }
  return options.loop === "off" ? -1 : count - 1;
}

/**
 * Formats a media clock: `m:ss`, or `h:mm:ss` from one hour on.
 *
 * A missing/negative/non-finite duration (what `HTMLMediaElement.duration` reports before metadata
 * arrives, as `NaN`) answers `0:00` instead of `NaN:NaN`.
 */
export function formatTime(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) {
    return "0:00";
  }
  const total = Math.floor(seconds);
  const hours = Math.floor(total / 3_600);
  const minutes = Math.floor((total % 3_600) / 60);
  const secs = total % 60;
  const pad = (value: number): string => String(value).padStart(2, "0");
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(secs)}` : `${minutes}:${pad(secs)}`;
}

/** Clamps a volume to `[0, 1]`; a non-finite value falls back to the supplied default (1 = full). */
export function clampVolume(value: number, fallback = 1): number {
  if (!Number.isFinite(value)) {
    return Math.min(1, Math.max(0, fallback));
  }
  return Math.min(1, Math.max(0, value));
}

/** The 0..1000 integer the progress slider stores for a `[0, 1]` fraction. */
export function progressSliderValue(fraction: number): number {
  if (!Number.isFinite(fraction)) {
    return 0;
  }
  return Math.round(Math.min(1, Math.max(0, fraction)) * 1_000);
}
