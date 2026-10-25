/**
 * Pointer and keyboard control for the game canvas.
 *
 * Every listener is attached to the canvas itself, never to `window` or `document`, so the protocol
 * form keeps its normal typing, wheel and keyboard behaviour. Keyboard input starts working when the
 * canvas is clicked (which focuses it) and stops when focus moves back to a form field.
 *
 * Selection model (see the READMEs for the user-facing table):
 * - a plain left click replaces the selection with the entity under the cursor;
 * - Shift+left click adds or removes one unit;
 * - a left drag that starts on empty ground draws a marquee and selects the own units inside it;
 * - a left drag that starts on an entity still selects that entity;
 * - the middle button, or Space held with the left button, pans the camera. A left drag on empty
 *   ground is deliberately no longer a camera pan, because that gesture is the marquee;
 * - Ctrl+1..9 assigns the selection, Shift+1..9 adds to it, and 1..9 recalls it. The pure model is
 *   `src/controlGroups.ts`; this controller only maps the key and reports the mode.
 */

import type { Camera, TileCoord, TileRange } from "./camera";
import { marqueeTileRange, screenRectBetween, type ScreenRect } from "./marquee";
import { pickEntityAt } from "./picking";
import { isWorldTile, type WorldView } from "./view";

const DRAG_THRESHOLD_PX = 4;
/** Two clicks on the same target within this window count as a double click. */
const DOUBLE_CLICK_MS = 350;
/** How far a double click's second press may drift from the first, in CSS pixels. */
const DOUBLE_CLICK_SLOP_PX = 6;
const KEY_PAN_SPEED_PX_PER_SECOND = 560;
const ZOOM_STEP = 1.18;
const WHEEL_ZOOM_SENSITIVITY = 0.0015;
const SPACE_KEY = " ";
const PAN_KEYS: ReadonlySet<string> = new Set<string>([
  "w",
  "a",
  "s",
  "d",
  "arrowup",
  "arrowdown",
  "arrowleft",
  "arrowright",
]);

/** How one click changes the selection: replace it, or add/remove the clicked unit. */
export type SelectionMode = "replace" | "toggle";

/** How a number key acts on a control group: replace it, add to it, or select it. */
export type ControlGroupMode = "assign" | "add" | "recall";

/** The group number of a number key, or `null` for every other key. */
function controlGroupOf(event: KeyboardEvent): number | null {
  const code = /^(?:Digit|Numpad)([1-9])$/u.exec(event.code);
  if (code !== null) {
    return Number(code[1]);
  }
  // `event.code` is the stable half (Shift+1 becomes "!" in `key`); this fallback keeps browsers that
  // do not report a code usable.
  const key = /^[1-9]$/u.exec(event.key);
  return key === null ? null : Number(event.key);
}

export interface InputHooks {
  /** The world currently rendered; read lazily so the demo map could be replaced later. */
  readonly getWorld: () => WorldView;
  /** Apply one click's selection change. `mode` is `toggle` for Shift+left click. */
  readonly onSelect: (id: number | null, mode: SelectionMode) => void;
  /**
   * A finished marquee drag: every own unit drawn inside this inclusive tile range should become the
   * selection. The camera itself never moved during the drag, so the range is stable.
   */
  readonly onMarquee?: (range: TileRange) => void;
  /** Whether an empty-ground left drag may draw a marquee; placement mode returns false. */
  readonly isMarqueeEnabled?: () => boolean;
  readonly onHoverChange?: (tile: TileCoord | null) => void;
  readonly onCameraChange?: () => void;
  /** A right click on a world tile; `targetId` is the entity under the cursor, if any, and `shift` requests a waypoint. */
  readonly onRightClick?: (tile: TileCoord, targetId: number | null, shift: boolean) => void;
  /**
   * A second click on the same entity (or on empty ground) within the double-click window. The page
   * replaces the selection with the whole-world same-kind rule, or with a single clicked building.
   * A drag never counts as either half of a double click.
   */
  readonly onDoubleClick?: (targetId: number | null) => void;
  /** The `O` hotkey: the page selects every own engineer in the loaded window. */
  readonly onSelectEngineers?: () => void;
  /**
   * The `H` hotkey: the page centres the camera on the local player's own base anchor (the `anchor` of
   * its own `players` row). When no anchor is known the page says so on the status line instead.
   */
  readonly onJumpHome?: () => void;
  /**
   * The `M` hotkey: the page places a personal marker under the cursor (or at the camera centre when the
   * pointer is outside the canvas). The markers are client-only session state; see `markers.ts`.
   */
  readonly onPlaceMarker?: () => void;
  /** `Shift+M`: remove the most recently placed marker. The page reports an empty list on the status line. */
  readonly onRemoveMarker?: () => void;
  /**
   * A plain left click on a world tile, before selection. Returning `true` consumes the click, which is
   * how the build placement mode places a site instead of changing the selection.
   */
  readonly onLeftClick?: (tile: TileCoord, targetId: number | null) => boolean;
  /**
   * Escape, before the default "clear selection". Returning `true` consumes the key, which is how an
   * armed build placement is cancelled without also deselecting the engineer.
   */
  readonly onCancel?: () => boolean;
  /**
   * A number key 1..9: Ctrl (or Cmd) assigns the current selection, Shift adds it, and a plain number
   * recalls the group. Alt+number is left to the browser. The page decides what "empty" means, because
   * it owns the status line.
   */
  readonly onControlGroup?: (group: number, mode: ControlGroupMode) => void;
}

interface ScreenPoint {
  readonly x: number;
  readonly y: number;
}

type DragMode = "none" | "camera" | "entity" | "marquee";

function sameTile(left: TileCoord | null, right: TileCoord | null): boolean {
  if (left === null || right === null) {
    return left === right;
  }
  return left.x === right.x && left.y === right.y;
}

export class InputController {
  private readonly canvas: HTMLCanvasElement;
  private readonly camera: Camera;
  private readonly hooks: InputHooks;
  private readonly pressedKeys = new Set<string>();
  private hoverTileValue: TileCoord | null = null;
  /** The entity body under the pointer, or null; refreshed with the hover tile. */
  private hoverEntityIdValue: number | null = null;
  /** Last pointer position in client (viewport) pixels, used to place the DOM tooltip. */
  private hoverClientX = 0;
  private hoverClientY = 0;
  /** The last completed click, for double-click detection; null after a drag or a placement click. */
  private lastClick: { at: number; id: number | null; x: number; y: number } | null = null;

  private pointerActive = false;
  private dragging = false;
  private activePointerId: number | null = null;
  private dragMode: DragMode = "none";
  private downTargetId: number | null = null;
  private downShift = false;
  private downX = 0;
  private downY = 0;
  private lastX = 0;
  private lastY = 0;
  private pointerX = 0;
  private pointerY = 0;

  constructor(canvas: HTMLCanvasElement, camera: Camera, hooks: InputHooks) {
    this.canvas = canvas;
    this.camera = camera;
    this.hooks = hooks;
    if (canvas.tabIndex < 0) {
      canvas.tabIndex = 0;
    }
    canvas.addEventListener("pointerdown", this.handlePointerDown);
    canvas.addEventListener("pointermove", this.handlePointerMove);
    canvas.addEventListener("pointerup", this.handlePointerUp);
    canvas.addEventListener("pointercancel", this.handlePointerCancel);
    canvas.addEventListener("pointerleave", this.handlePointerLeave);
    canvas.addEventListener("lostpointercapture", this.handleLostPointerCapture);
    canvas.addEventListener("contextmenu", this.handleContextMenu);
    canvas.addEventListener("auxclick", this.handleAuxClick);
    canvas.addEventListener("wheel", this.handleWheel, { passive: false });
    canvas.addEventListener("keydown", this.handleKeyDown);
    canvas.addEventListener("keyup", this.handleKeyUp);
    canvas.addEventListener("blur", this.handleBlur);
  }

  /** Tile under the pointer, or null when it is outside the canvas. */
  get hoverTile(): TileCoord | null {
    return this.hoverTileValue;
  }

  /** Entity under the pointer, or null when it is outside the canvas or over empty ground. */
  get hoverEntityId(): number | null {
    return this.hoverEntityIdValue;
  }

  /** Pointer position in client pixels; the tooltip anchors to it. */
  get hoverClientPoint(): ScreenPoint {
    return { x: this.hoverClientX, y: this.hoverClientY };
  }

  /**
   * The marquee rectangle in CSS pixels while a left drag on empty ground is in progress, otherwise
   * null. The renderer draws it on top of the world frame; it is already normalised.
   */
  get marqueeRect(): ScreenRect | null {
    if (this.dragMode !== "marquee" || !this.dragging) {
      return null;
    }
    return screenRectBetween(
      { x: this.downX, y: this.downY },
      { x: this.pointerX, y: this.pointerY },
    );
  }

  /** Applies held-key panning. The main loop calls this once per animation frame. */
  update(deltaSeconds: number): void {
    if (!Number.isFinite(deltaSeconds) || deltaSeconds <= 0) {
      return;
    }
    let dx = 0;
    let dy = 0;
    const step = KEY_PAN_SPEED_PX_PER_SECOND * deltaSeconds;
    if (this.isPressed("a") || this.isPressed("arrowleft")) {
      dx -= step;
    }
    if (this.isPressed("d") || this.isPressed("arrowright")) {
      dx += step;
    }
    if (this.isPressed("w") || this.isPressed("arrowup")) {
      dy -= step;
    }
    if (this.isPressed("s") || this.isPressed("arrowdown")) {
      dy += step;
    }
    if (dx !== 0 || dy !== 0) {
      this.camera.pan(dx, dy);
      // The camera moved under a stationary cursor, so the hovered tile usually changed too.
      if (this.hoverTileValue !== null) {
        this.updateHover(this.pointerX, this.pointerY);
      }
      this.hooks.onCameraChange?.();
    }
  }

  dispose(): void {
    this.canvas.removeEventListener("pointerdown", this.handlePointerDown);
    this.canvas.removeEventListener("pointermove", this.handlePointerMove);
    this.canvas.removeEventListener("pointerup", this.handlePointerUp);
    this.canvas.removeEventListener("pointercancel", this.handlePointerCancel);
    this.canvas.removeEventListener("pointerleave", this.handlePointerLeave);
    this.canvas.removeEventListener("lostpointercapture", this.handleLostPointerCapture);
    this.canvas.removeEventListener("contextmenu", this.handleContextMenu);
    this.canvas.removeEventListener("auxclick", this.handleAuxClick);
    this.canvas.removeEventListener("wheel", this.handleWheel);
    this.canvas.removeEventListener("keydown", this.handleKeyDown);
    this.canvas.removeEventListener("keyup", this.handleKeyUp);
    this.canvas.removeEventListener("blur", this.handleBlur);
    this.releasePointer();
    this.pressedKeys.clear();
  }

  private isPressed(key: string): boolean {
    return this.pressedKeys.has(key);
  }

  private localPoint(event: PointerEvent | WheelEvent): ScreenPoint {
    const rect = this.canvas.getBoundingClientRect();
    return { x: event.clientX - rect.left, y: event.clientY - rect.top };
  }

  private updateHover(screenX: number, screenY: number): void {
    const tile = this.camera.tileAtScreen(screenX, screenY);
    // The world is unbounded, so every whole tile is addressable. The camera is not clamped to the
    // loaded window, so this tile may be outside it; it is still a real tile for commands and the HUD.
    const next: TileCoord | null = isWorldTile(tile.x, tile.y) ? tile : null;
    // The entity under the cursor is refreshed even when the tile did not change: a moving unit can
    // slide under a stationary pointer, and the tooltip must follow the same picker that clicks use.
    this.hoverEntityIdValue = this.pickEntity(screenX, screenY);
    const rect = this.canvas.getBoundingClientRect();
    this.hoverClientX = rect.left + screenX;
    this.hoverClientY = rect.top + screenY;
    if (!sameTile(next, this.hoverTileValue)) {
      this.hoverTileValue = next;
      this.hooks.onHoverChange?.(next);
    }
  }

  /** Re-picks the hovered tile and entity without pointer movement (moving world, camera, zoom). */
  refreshHover(): void {
    if (this.hoverTileValue !== null) {
      this.updateHover(this.pointerX, this.pointerY);
    }
  }

  private clearHover(): void {
    this.hoverEntityIdValue = null;
    if (this.hoverTileValue !== null) {
      this.hoverTileValue = null;
      this.hooks.onHoverChange?.(null);
    }
  }

  /** Picks the entity whose drawn body is under the cursor; see `render/picking.ts`. */
  private pickEntity(screenX: number, screenY: number): number | null {
    return pickEntityAt(this.hooks.getWorld(), this.camera, screenX, screenY);
  }

  private zoomAtCenter(factor: number): void {
    this.camera.zoomAt(this.camera.viewportWidth / 2, this.camera.viewportHeight / 2, factor);
    if (this.hoverTileValue !== null) {
      this.updateHover(this.pointerX, this.pointerY);
    }
    this.hooks.onCameraChange?.();
  }

  /** Starts one pointer drag of a concrete mode; shared by left, middle and Space+left drags. */
  private beginDrag(event: PointerEvent, mode: DragMode): void {
    const point = this.localPoint(event);
    this.pointerX = point.x;
    this.pointerY = point.y;
    this.activePointerId = event.pointerId;
    this.pointerActive = true;
    this.dragging = false;
    this.dragMode = mode;
    this.downShift = event.shiftKey;
    this.downX = point.x;
    this.downY = point.y;
    this.lastX = point.x;
    this.lastY = point.y;
    this.updateHover(point.x, point.y);
    this.canvas.focus({ preventScroll: true });
    try {
      this.canvas.setPointerCapture(event.pointerId);
    } catch {
      // Capture is an optimisation; dragging still works while the pointer stays over the canvas.
    }
    event.preventDefault();
  }

  private releasePointer(): void {
    if (this.activePointerId !== null) {
      try {
        if (this.canvas.hasPointerCapture(this.activePointerId)) {
          this.canvas.releasePointerCapture(this.activePointerId);
        }
      } catch {
        // The pointer can already be gone; releasing a lost capture is not an error we can act on.
      }
    }
    this.activePointerId = null;
    this.pointerActive = false;
    this.dragging = false;
    this.dragMode = "none";
    this.downTargetId = null;
    this.downShift = false;
  }

  /** Forgets the pending first click; every drag and every consumed placement click does this. */
  private resetDoubleClick(): void {
    this.lastClick = null;
  }

  /**
   * Resolves a completed click: placement consumes it first, then it is either the second half of a
   * double click or a normal selection change. A double click only counts when both clicks landed on
   * the same target within {@link DOUBLE_CLICK_MS} and barely moved; any drag clears the first click,
   * so a click that ends a marquee (or follows a camera/entity drag) can never complete one.
   */
  private finishClick(screenX: number, screenY: number, shift: boolean): void {
    const targetId = this.pickEntity(screenX, screenY);
    const tile = this.hoverTileValue;
    const consumed = tile !== null && this.hooks.onLeftClick?.(tile, targetId) === true;
    if (consumed) {
      this.resetDoubleClick();
      return;
    }

    const now = performance.now();
    const previous = this.lastClick;
    const isDoubleClick =
      this.hooks.onDoubleClick !== undefined &&
      previous !== null &&
      now - previous.at <= DOUBLE_CLICK_MS &&
      previous.id === targetId &&
      Math.hypot(screenX - previous.x, screenY - previous.y) <= DOUBLE_CLICK_SLOP_PX;
    if (isDoubleClick) {
      // A double click replaces the selection as a whole, so the single-click half is not repeated.
      this.resetDoubleClick();
      this.hooks.onDoubleClick?.(targetId);
      return;
    }

    this.hooks.onSelect(targetId, shift ? "toggle" : "replace");
    this.lastClick = { at: now, id: targetId, x: screenX, y: screenY };
  }

  private readonly handlePointerDown = (event: PointerEvent): void => {
    if (!event.isPrimary) {
      return;
    }
    if (event.button === 2) {
      // The right button never starts a drag; it only aims an order. Focus the canvas so the keyboard
      // shortcuts (including Escape) keep working after the click.
      this.resetDoubleClick();
      const point = this.localPoint(event);
      this.pointerX = point.x;
      this.pointerY = point.y;
      this.updateHover(point.x, point.y);
      this.canvas.focus({ preventScroll: true });
      event.preventDefault();
      return;
    }
    if (event.button === 1) {
      // Middle-button drag is the camera pan that does not collide with the marquee.
      this.resetDoubleClick();
      this.downTargetId = null;
      this.beginDrag(event, "camera");
      return;
    }
    if (event.button !== 0) {
      return;
    }
    this.downTargetId = null;
    if (this.isPressed(SPACE_KEY)) {
      // Space held keeps the old left-drag camera pan available as a keyboard modifier.
      this.resetDoubleClick();
      this.beginDrag(event, "camera");
      return;
    }
    const point = this.localPoint(event);
    const targetId = this.pickEntity(point.x, point.y);
    this.downTargetId = targetId;
    if (targetId !== null) {
      // A left drag that starts on an entity selects it; it never becomes a marquee and never pans.
      this.beginDrag(event, "entity");
      return;
    }
    if (this.hooks.isMarqueeEnabled?.() === false) {
      // Placement mode: an empty-ground drag keeps panning the camera, and its left click places.
      this.beginDrag(event, "camera");
      return;
    }
    this.beginDrag(event, "marquee");
  };

  private readonly handlePointerMove = (event: PointerEvent): void => {
    const point = this.localPoint(event);
    this.pointerX = point.x;
    this.pointerY = point.y;
    this.updateHover(point.x, point.y);
    if (!this.pointerActive || event.pointerId !== this.activePointerId) {
      return;
    }

    const totalDistance = Math.hypot(point.x - this.downX, point.y - this.downY);
    const wasDragging = this.dragging;
    if (!this.dragging && totalDistance >= DRAG_THRESHOLD_PX) {
      this.dragging = true;
      // A drag is never half of a double click; drop any first click that came before it.
      this.resetDoubleClick();
    }
    if (this.dragMode === "camera" && this.dragging) {
      // On the frame that crosses the threshold, apply the whole movement since pointerdown; later
      // frames apply only their own delta. This keeps the grabbed world point under the cursor.
      const dx = wasDragging ? point.x - this.lastX : point.x - this.downX;
      const dy = wasDragging ? point.y - this.lastY : point.y - this.downY;
      this.camera.pan(-dx, -dy);
      this.hooks.onCameraChange?.();
    }
    this.lastX = point.x;
    this.lastY = point.y;
  };

  private readonly handlePointerUp = (event: PointerEvent): void => {
    if (event.button === 2) {
      if (!event.isPrimary) {
        return;
      }
      const point = this.localPoint(event);
      this.updateHover(point.x, point.y);
      this.emitRightClick(point.x, point.y, event.shiftKey);
      event.preventDefault();
      return;
    }
    if (event.button === 1) {
      if (event.pointerId === this.activePointerId) {
        this.releasePointer();
      }
      return;
    }
    if (event.pointerId !== this.activePointerId) {
      return;
    }
    const point = this.localPoint(event);
    this.pointerX = point.x;
    this.pointerY = point.y;
    this.updateHover(point.x, point.y);
    const mode = this.dragMode;
    const wasDragging = this.dragging;
    const downTargetId = this.downTargetId;
    const shift = this.downShift;
    const marquee = mode === "marquee" && wasDragging ? this.marqueeRect : null;
    this.releasePointer();
    if (wasDragging) {
      // The mouseup that ends a marquee or any other drag is not a click and must not seed one.
      this.resetDoubleClick();
    }

    if (mode === "marquee") {
      if (wasDragging) {
        if (marquee !== null && this.hooks.isMarqueeEnabled?.() !== false) {
          this.hooks.onMarquee?.(marqueeTileRange(this.camera, marquee));
        }
        return;
      }
      this.finishClick(point.x, point.y, shift);
      return;
    }
    if (mode === "entity") {
      if (!wasDragging) {
        this.finishClick(point.x, point.y, shift);
      } else if (downTargetId !== null) {
        this.hooks.onSelect(downTargetId, shift ? "toggle" : "replace");
      }
      return;
    }
    // Camera mode (middle button, Space+left, or placement-mode left drag). A left click that never
    // crossed the drag threshold is still a placement click / selection click.
    if (mode === "camera" && event.button === 0 && !wasDragging) {
      this.finishClick(point.x, point.y, shift);
    }
  };

  /** Emits a right click with its tile, the entity body under the cursor and the Shift waypoint flag. */
  private emitRightClick(screenX: number, screenY: number, shift: boolean): void {
    const tile = this.hoverTileValue;
    if (tile === null) {
      return;
    }
    this.hooks.onRightClick?.(tile, this.pickEntity(screenX, screenY), shift);
  }

  private readonly handleContextMenu = (event: MouseEvent): void => {
    // The canvas uses the right button for orders, so the browser menu must not open over it.
    event.preventDefault();
  };

  private readonly handleAuxClick = (event: MouseEvent): void => {
    // Middle click must not start the browser's auto-scroll; it belongs to camera panning.
    if (event.button === 1) {
      event.preventDefault();
    }
  };

  private readonly handlePointerCancel = (event: PointerEvent): void => {
    if (event.pointerId !== this.activePointerId) {
      return;
    }
    this.resetDoubleClick();
    this.releasePointer();
    this.clearHover();
  };

  private readonly handlePointerLeave = (): void => {
    // Pointer capture keeps a drag (marquee or camera) alive outside the canvas; do not cancel it.
    if (
      this.pointerActive &&
      this.activePointerId !== null &&
      this.canvas.hasPointerCapture(this.activePointerId)
    ) {
      return;
    }
    if (this.pointerActive) {
      this.releasePointer();
    }
    this.resetDoubleClick();
    this.clearHover();
  };

  private readonly handleLostPointerCapture = (): void => {
    if (!this.pointerActive) {
      return;
    }
    this.pointerActive = false;
    this.dragging = false;
    this.dragMode = "none";
    this.downTargetId = null;
    this.activePointerId = null;
    this.resetDoubleClick();
  };

  private readonly handleWheel = (event: WheelEvent): void => {
    event.preventDefault();
    const point = this.localPoint(event);
    this.pointerX = point.x;
    this.pointerY = point.y;
    const delta =
      event.deltaMode === 1
        ? event.deltaY * 16
        : event.deltaMode === 2
          ? event.deltaY * this.camera.viewportHeight
          : event.deltaY;
    const factor = Math.exp(-delta * WHEEL_ZOOM_SENSITIVITY);
    this.camera.zoomAt(point.x, point.y, factor);
    this.updateHover(point.x, point.y);
    this.hooks.onCameraChange?.();
  };

  private readonly handleKeyDown = (event: KeyboardEvent): void => {
    const group = controlGroupOf(event);
    if (group !== null && !event.altKey) {
      const mode: ControlGroupMode =
        event.ctrlKey || event.metaKey ? "assign" : event.shiftKey ? "add" : "recall";
      event.preventDefault();
      if (!event.repeat) {
        this.hooks.onControlGroup?.(group, mode);
      }
      return;
    }
    if (event.ctrlKey || event.metaKey || event.altKey) {
      return;
    }
    const key = event.key.toLowerCase();
    if (key === "escape") {
      event.preventDefault();
      if (this.hooks.onCancel?.() === true) {
        return;
      }
      this.hooks.onSelect(null, "replace");
      return;
    }
    if (key === " ") {
      // Space is the camera-pan modifier for the left button; stop the page from scrolling.
      event.preventDefault();
      this.pressedKeys.add(SPACE_KEY);
      return;
    }
    if (key === "f") {
      event.preventDefault();
      if (!event.repeat) {
        this.camera.reset();
        if (this.hoverTileValue !== null) {
          this.updateHover(this.pointerX, this.pointerY);
        }
        this.hooks.onCameraChange?.();
      }
      return;
    }
    if (key === "o") {
      // Select every own engineer in the loaded window. The snapshot has no order state, so this is
      // deliberately "all engineers", never "idle engineers"; the button is labelled 工兵.
      event.preventDefault();
      if (!event.repeat) {
        this.hooks.onSelectEngineers?.();
      }
      return;
    }
    if (key === "h") {
      // "Bring me home": centre the camera on my own published base anchor. The page owns the
      // unknown-anchor message, because only it knows whether the snapshot or the login is missing.
      event.preventDefault();
      if (!event.repeat) {
        this.hooks.onJumpHome?.();
      }
      return;
    }
    if (key === "m") {
      // `M` places a personal marker; `Shift+M` drops the most recently placed one. Neither is a game
      // command: markers are client-only session state and never reach the server.
      event.preventDefault();
      if (!event.repeat) {
        if (event.shiftKey) {
          this.hooks.onRemoveMarker?.();
        } else {
          this.hooks.onPlaceMarker?.();
        }
      }
      return;
    }
    if (key === "+" || key === "=") {
      event.preventDefault();
      if (!event.repeat) {
        this.zoomAtCenter(ZOOM_STEP);
      }
      return;
    }
    if (key === "-" || key === "_") {
      event.preventDefault();
      if (!event.repeat) {
        this.zoomAtCenter(1 / ZOOM_STEP);
      }
      return;
    }
    if (PAN_KEYS.has(key)) {
      event.preventDefault();
      this.pressedKeys.add(key);
    }
  };

  private readonly handleKeyUp = (event: KeyboardEvent): void => {
    this.pressedKeys.delete(event.key.toLowerCase());
  };

  private readonly handleBlur = (): void => {
    this.pressedKeys.clear();
  };
}
