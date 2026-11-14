/**
 * The DOM half of the built-in music player.
 *
 * It owns one `<audio>` element, the playlist rows and every control; the deterministic rules (add,
 * de-duplicate, remove, next/previous, loop/shuffle, clock formatting) live in `music.ts`.
 *
 * Files are played through `URL.createObjectURL` only: nothing is uploaded and no network request is
 * made. The URL of a removed track (and of every track on unload) is revoked. The playlist is session
 * state: a refresh loses it, because a plain file input gives the page no re-openable path.
 */

import {
  LOOP_MODES,
  addTracks,
  clampVolume,
  formatTime,
  nextIndex,
  prevIndex,
  progressSliderValue,
  removeTrack,
  type LoopMode,
  type Track,
  type TrackSource,
} from "./music";
import { t } from "./i18n";

type MusicStatusState = "idle" | "ok" | "error";

function need<T extends Element>(root: ParentNode, selector: string): T {
  const found = root.querySelector<T>(selector);
  if (found === null) {
    throw new Error(`音乐播放器缺少元素 ${selector}`);
  }
  return found;
}

export class MusicPlayerView {
  private readonly overlay: HTMLDivElement;
  private readonly opener: HTMLButtonElement;
  private readonly panel: HTMLElement;
  private readonly closeButton: HTMLButtonElement;
  private readonly fileInput: HTMLInputElement;
  private readonly playlist: HTMLUListElement;
  private readonly empty: HTMLParagraphElement;
  private readonly previousButton: HTMLButtonElement;
  private readonly playButton: HTMLButtonElement;
  private readonly nextButton: HTMLButtonElement;
  private readonly loopSelect: HTMLSelectElement;
  private readonly shuffleInput: HTMLInputElement;
  private readonly volumeInput: HTMLInputElement;
  private readonly progressInput: HTMLInputElement;
  private readonly elapsedLabel: HTMLElement;
  private readonly totalLabel: HTMLElement;
  private readonly statusLine: HTMLElement;
  private readonly nowLine: HTMLElement;
  private readonly audio: HTMLAudioElement;

  private tracks: Track[] = [];
  private currentIndex = -1;
  private loopMode: LoopMode = "all";
  private shuffle = false;
  private seeking = false;

  constructor(overlay: HTMLDivElement, opener: HTMLButtonElement) {
    this.overlay = overlay;
    this.opener = opener;
    this.panel = need<HTMLElement>(overlay, "#music-panel");
    this.closeButton = need<HTMLButtonElement>(overlay, "#music-close");
    this.fileInput = need<HTMLInputElement>(overlay, "#music-files");
    this.playlist = need<HTMLUListElement>(overlay, "#music-playlist");
    this.empty = need<HTMLParagraphElement>(overlay, "#music-empty");
    this.previousButton = need<HTMLButtonElement>(overlay, "#music-prev");
    this.playButton = need<HTMLButtonElement>(overlay, "#music-play");
    this.nextButton = need<HTMLButtonElement>(overlay, "#music-next");
    this.loopSelect = need<HTMLSelectElement>(overlay, "#music-loop");
    this.shuffleInput = need<HTMLInputElement>(overlay, "#music-shuffle");
    this.volumeInput = need<HTMLInputElement>(overlay, "#music-volume");
    this.progressInput = need<HTMLInputElement>(overlay, "#music-progress");
    this.elapsedLabel = need<HTMLElement>(overlay, "#music-elapsed");
    this.totalLabel = need<HTMLElement>(overlay, "#music-total");
    this.statusLine = need<HTMLElement>(overlay, "#music-status");
    this.nowLine = need<HTMLElement>(overlay, "#music-now");

    this.audio = new Audio();
    this.audio.preload = "metadata";
    this.audio.volume = 1;
    this.audio.loop = false;

    this.fileInput.addEventListener("change", () => {
      this.addFiles(this.fileInput.files);
    });
    this.closeButton.addEventListener("click", () => {
      this.close();
    });
    this.playButton.addEventListener("click", () => {
      this.toggle();
    });
    this.previousButton.addEventListener("click", () => {
      this.previous();
    });
    this.nextButton.addEventListener("click", () => {
      this.next();
    });
    this.loopSelect.addEventListener("change", () => {
      this.setLoopMode(this.loopSelect.value);
    });
    this.shuffleInput.addEventListener("change", () => {
      this.shuffle = this.shuffleInput.checked;
    });
    this.volumeInput.addEventListener("input", () => {
      this.audio.volume = clampVolume(Number(this.volumeInput.value) / 100);
    });
    this.progressInput.addEventListener("pointerdown", () => {
      this.seeking = true;
    });
    this.progressInput.addEventListener("input", () => {
      this.seekFromSlider();
    });
    this.progressInput.addEventListener("change", () => {
      this.seekFromSlider();
      this.seeking = false;
    });

    this.audio.addEventListener("loadedmetadata", () => {
      this.updateClock();
    });
    this.audio.addEventListener("durationchange", () => {
      this.updateClock();
    });
    this.audio.addEventListener("timeupdate", () => {
      if (!this.seeking) {
        this.updateClock();
      }
    });
    this.audio.addEventListener("play", () => {
      this.updatePlayButton();
      this.updateNowPlaying();
    });
    this.audio.addEventListener("pause", () => {
      this.updatePlayButton();
      this.updateNowPlaying();
    });
    this.audio.addEventListener("ended", () => {
      this.handleEnded();
    });
    this.audio.addEventListener("error", () => {
      this.setStatus(t("music.cannotPlay"), "error");
      this.updatePlayButton();
    });

    // Keys are handled only while focus is inside the player panel, so WASD, the marquee and every game
    // binding on the canvas are untouched. Inputs/selects keep their native behaviour everywhere; a
    // focused button keeps Space as "activate" and the arrow keys still skip tracks.
    this.panel.addEventListener("keydown", (event) => {
      const target = event.target;
      const formControl =
        target instanceof HTMLElement && target.closest("input, select, textarea") !== null;
      if (formControl) {
        return;
      }
      const onButton = target instanceof HTMLElement && target.closest("button") !== null;
      if (event.key === " ") {
        if (onButton) {
          return;
        }
        event.preventDefault();
        event.stopPropagation();
        this.toggle();
      } else if (event.key === "ArrowLeft") {
        event.preventDefault();
        event.stopPropagation();
        this.previous();
      } else if (event.key === "ArrowRight") {
        event.preventDefault();
        event.stopPropagation();
        this.next();
      }
    });

    this.setLoopMode("all");
    this.updatePlaylist();
    this.updatePlayButton();
    this.updateNowPlaying();
    this.updateClock();
  }

  /** True while the player overlay is visible. */
  get isOpen(): boolean {
    return !this.overlay.hidden;
  }

  /** Shows the panel. Opening never starts playback, so the first play always comes from an explicit click. */
  open(): void {
    this.overlay.hidden = false;
    this.opener.setAttribute("aria-expanded", "true");
    this.opener.classList.add("armed");
    this.updateUi();
    this.panel.focus({ preventScroll: true });
  }

  /** Hides the panel without stopping playback; the music keeps playing while the operator plays. */
  close(): void {
    this.overlay.hidden = true;
    this.opener.setAttribute("aria-expanded", "false");
    this.opener.classList.remove("armed");
    this.opener.focus({ preventScroll: true });
  }

  toggleOpen(): void {
    if (this.isOpen) {
      this.close();
    } else {
      this.open();
    }
  }

  /** Revokes every object URL; called before the page unloads. */
  dispose(): void {
    this.audio.pause();
    this.audio.removeAttribute("src");
    this.audio.load();
    for (const track of this.tracks) {
      URL.revokeObjectURL(track.url);
    }
    this.tracks = [];
    this.currentIndex = -1;
  }

  private addFiles(files: FileList | null): void {
    if (files === null || files.length === 0) {
      return;
    }
    const sources: TrackSource[] = [];
    for (const file of files) {
      const url = URL.createObjectURL(file);
      sources.push({
        // A plain file input gives no path, so the name plus size plus mtime is the session identity.
        key: `${file.name}|${file.size}|${file.lastModified}`,
        name: file.name,
        url,
      });
    }
    const result = addTracks(this.tracks, sources);
    const kept = new Set(result.added.map((track) => track.url));
    for (const source of sources) {
      if (!kept.has(source.url)) {
        URL.revokeObjectURL(source.url);
      }
    }
    this.tracks = [...result.tracks];
    if (result.added.length > 0) {
      if (this.currentIndex < 0) {
        this.loadTrack(0, false);
      }
      
      this.setStatus(
        result.skipped > 0
          ? t("music.addedSkipped", { count: result.added.length, skipped: result.skipped })
          : t("music.added", { count: result.added.length }),
        "ok",
      );
    } else {
      this.setStatus(
        result.skipped > 0
          ? t("music.duplicates", { count: result.skipped })
          : t("music.nonePicked"),
        "idle",
      );
    }
    // Clear the input so choosing the same file again after removing it still fires `change`.
    this.fileInput.value = "";
    this.updatePlaylist();
    this.updatePlayButton();
    this.updateNowPlaying();
  }

  private remove(id: number): void {
    const wasPlaying = !this.audio.paused;
    const result = removeTrack(this.tracks, id);
    if (result.removed === null) {
      return;
    }
    URL.revokeObjectURL(result.removed.url);
    this.tracks = [...result.tracks];
    if (this.tracks.length === 0) {
      this.stopAndUnload();
      this.setStatus(t("music.removedPlaylistEmpty", { name: result.removed.name }), "idle");
    } else if (result.index === this.currentIndex) {
      const nextCurrent = Math.min(result.index, this.tracks.length - 1);
      this.audio.pause();
      this.audio.removeAttribute("src");
      this.audio.load();
      this.loadTrack(nextCurrent, wasPlaying);
      this.setStatus(t("music.removedNext", { name: result.removed.name }), "ok");
    } else {
      if (result.index < this.currentIndex) {
        this.currentIndex -= 1;
      }
      this.setStatus(t("music.removed", { name: result.removed.name }), "ok");
    }
    this.updatePlaylist();
    this.updatePlayButton();
    this.updateNowPlaying();
    this.updateClock();
  }

  private stopAndUnload(): void {
    this.audio.pause();
    this.audio.removeAttribute("src");
    this.audio.load();
    this.currentIndex = -1;
    this.elapsedLabel.textContent = "0:00";
    this.totalLabel.textContent = "0:00";
    this.progressInput.value = "0";
  }

  private playIndex(index: number): void {
    if (index < 0 || index >= this.tracks.length) {
      return;
    }
    if (index === this.currentIndex && this.audio.src !== "") {
      this.requestPlay();
      return;
    }
    this.loadTrack(index, true);
  }

  private loadTrack(index: number, play: boolean): void {
    const track = this.tracks[index];
    if (track === undefined) {
      return;
    }
    this.currentIndex = index;
    this.audio.src = track.url;
    this.audio.load();
    this.audio.loop = this.loopMode === "one";
    this.updatePlaylist();
    this.updateNowPlaying();
    this.updatePlayButton();
    this.updateClock();
    if (play) {
      this.requestPlay();
    }
  }

  private toggle(): void {
    if (this.tracks.length === 0) {
      this.setStatus(t("music.needAddFirst"), "error");
      return;
    }
    if (this.currentIndex < 0) {
      this.loadTrack(0, true);
      return;
    }
    if (this.audio.paused) {
      this.requestPlay();
    } else {
      this.audio.pause();
    }
  }

  /** Starts playback and turns a rejected `play()` into a status line instead of an exception. */
  private requestPlay(): void {
    const track = this.tracks[this.currentIndex];
    if (track === undefined) {
      this.setStatus(t("music.needSelection"), "error");
      return;
    }
    if (this.audio.src === "") {
      this.audio.src = track.url;
    }
    let started: Promise<void> | undefined;
    try {
      started = this.audio.play();
    } catch (error: unknown) {
      const reason = error instanceof Error ? error.message : String(error);
      this.setStatus(t("music.playFailed", { reason }), "error");
      this.updatePlayButton();
      return;
    }
    if (started !== undefined) {
      void started.catch((error: unknown) => {
        const reason = error instanceof Error ? error.message : String(error);
        this.setStatus(t("music.playFailed", { reason }), "error");
        this.updatePlayButton();
      });
    }
  }

  private next(): void {
    if (this.tracks.length === 0) {
      this.setStatus(t("music.empty"), "error");
      return;
    }
    const shouldPlay = this.currentIndex < 0 || !this.audio.paused;
    const target =
      this.currentIndex < 0
        ? 0
        : nextIndex(this.currentIndex, this.tracks.length, {
            loop: this.loopMode,
            shuffle: this.shuffle,
            rand: Math.random,
          });
    if (target < 0) {
      this.setStatus(t("music.endOfList"), "idle");
      return;
    }
    this.loadTrack(target, shouldPlay);
  }

  private previous(): void {
    if (this.tracks.length === 0) {
      this.setStatus(t("music.empty"), "error");
      return;
    }
    const shouldPlay = this.currentIndex < 0 || !this.audio.paused;
    const target =
      this.currentIndex < 0
        ? 0
        : prevIndex(this.currentIndex, this.tracks.length, {
            loop: this.loopMode,
            shuffle: this.shuffle,
            rand: Math.random,
          });
    if (target < 0) {
      this.setStatus(t("music.startOfList"), "idle");
      return;
    }
    this.loadTrack(target, shouldPlay);
  }

  /** Automatic end-of-track step: loop-one repeats the track, otherwise the ordinary next rule applies. */
  private handleEnded(): void {
    if (this.tracks.length === 0) {
      return;
    }
    const target = nextIndex(this.currentIndex, this.tracks.length, {
      loop: this.loopMode,
      shuffle: this.shuffle,
      rand: Math.random,
      auto: true,
    });
    if (target < 0) {
      this.setStatus(t("music.playlistEnded"), "idle");
      this.updatePlayButton();
      return;
    }
    this.loadTrack(target, true);
  }

  private setLoopMode(value: string): void {
    const mode = LOOP_MODES.find((candidate) => candidate === value);
    this.loopMode = mode ?? "all";
    this.loopSelect.value = this.loopMode;
    this.audio.loop = this.loopMode === "one";
  }

  private seekFromSlider(): void {
    const duration = this.audio.duration;
    const value = Number(this.progressInput.value);
    if (!Number.isFinite(duration) || duration <= 0 || !Number.isFinite(value)) {
      return;
    }
    this.audio.currentTime = (Math.min(1_000, Math.max(0, value)) / 1_000) * duration;
    this.updateClock();
  }

  private updateUi(): void {
    this.updatePlaylist();
    this.updatePlayButton();
    this.updateNowPlaying();
    this.updateClock();
    if (this.tracks.length === 0) {
      this.setStatus(t("music.noFilesYet"), "idle");
    }
  }

  private updatePlaylist(): void {
    const rows = this.tracks.map((track, index) => {
      const row = document.createElement("li");
      row.className = index === this.currentIndex ? "music-track active" : "music-track";
      const name = document.createElement("button");
      name.type = "button";
      name.className = "music-track-name";
      name.textContent = track.name;
      name.title = t("music.rowPlayTitle", { name: track.name });
      name.addEventListener("click", () => {
        this.playIndex(index);
      });
      const remove = document.createElement("button");
      remove.type = "button";
      remove.className = "music-track-remove";
      remove.textContent = t("music.rowRemove");
      remove.title = t("music.rowRemoveTitle", { name: track.name });
      remove.addEventListener("click", () => {
        this.remove(track.id);
      });
      row.append(name, remove);
      return row;
    });
    this.playlist.replaceChildren(...rows);
    this.empty.hidden = this.tracks.length > 0;
    this.playlist.hidden = this.tracks.length === 0;
    this.playButton.disabled = this.tracks.length === 0;
    this.previousButton.disabled = this.tracks.length === 0;
    this.nextButton.disabled = this.tracks.length === 0;
  }

  private updatePlayButton(): void {
    const playing = !this.audio.paused && this.audio.src !== "";
    this.playButton.textContent = playing ? t("music.pause") : t("music.play");
    this.playButton.setAttribute("aria-pressed", playing ? "true" : "false");
  }

  private updateNowPlaying(): void {
    const track = this.tracks[this.currentIndex];
    if (track === undefined) {
      this.nowLine.textContent = t("music.nowIdle");
      return;
    }
    const prefix = this.audio.paused ? t("music.paused") : t("music.playing");
    this.nowLine.textContent = `${prefix}：${track.name}`;
  }

  private updateClock(): void {
    const duration = this.audio.duration;
    const hasDuration = Number.isFinite(duration) && duration > 0;
    this.elapsedLabel.textContent = formatTime(this.audio.currentTime);
    this.totalLabel.textContent = hasDuration ? formatTime(duration) : "0:00";
    if (hasDuration && !this.seeking) {
      this.progressInput.value = String(progressSliderValue(this.audio.currentTime / duration));
    }
  }

  private setStatus(text: string, state: MusicStatusState): void {
    this.statusLine.textContent = text;
    this.statusLine.dataset["state"] = state;
  }
}
