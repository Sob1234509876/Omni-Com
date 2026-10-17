/**
 * Pointer and keyboard control for the game canvas.
 *
 * Every listener is attached to the canvas itself, never to `window` or `document`, so the protocol
 * form keeps its normal typing, wheel and keyboard behaviour. Keyboard input starts working when the
 * canvas is clicked (which focuses it) and stops when focus moves back to a form field.
 */

import type { Camera, TileCoord } from "./camera";
import { isBuildingKind, type WorldView } from "./view";

const DRAG_THRESHOLD_PX = 4;
const KEY_PAN_SPEED_PX_PER_SECOND = 560;
const ZOOM_STEP = 1.18;
const WHEEL_ZOOM_SENSITIVITY = 0.0015;
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

export interface InputHooks {
  /** The world currently rendered; read lazily so the demo map could be replaced later. */
  readonly getWorld: () => WorldView;
  readonly onSelectionChange: (id: number | null) => void;
  readonly onHoverChange?: (tile: TileCoord | null) => void;
  readonly onCameraChange?: () => void;
  /** A right click on a map tile; `targetId` is the entity under the cursor, if any. */
  readonly onRightClick?: (tile: TileCoord, targetId: number | null) => void;
  /**
   * A plain left click on a map tile, before selection. Returning `true` consumes the click, which is
   * how the build placement mode places a site instead of changing the selection.
   */
  readonly onLeftClick?: (tile: TileCoord, targetId: number | null) => boolean;
  /**
   * Escape, before the default "clear selection". Returning `true` consumes the key, which is how an
   * armed build placement is cancelled without also deselecting the engineer.
   */
  readonly onCancel?: () => boolean;
}

interface ScreenPoint {
  readonly x: number;
  readonly y: number;
}

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

  private pointerActive = false;
  private dragging = false;
  private activePointerId: number | null = null;
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
    canvas.addEventListener("wheel", this.handleWheel, { passive: false });
    canvas.addEventListener("keydown", this.handleKeyDown);
    canvas.addEventListener("keyup", this.handleKeyUp);
    canvas.addEventListener("blur", this.handleBlur);
  }

  /** Tile under the pointer, or null when it is outside the canvas or the map. */
  get hoverTile(): TileCoord | null {
    return this.hoverTileValue;
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
    const world = this.hooks.getWorld();
    const tile = this.camera.tileAtScreen(screenX, screenY);
    const inside = tile.x >= 0 && tile.y >= 0 && tile.x < world.width && tile.y < world.height;
    const next: TileCoord | null = inside ? tile : null;
    if (!sameTile(next, this.hoverTileValue)) {
      this.hoverTileValue = next;
      this.hooks.onHoverChange?.(next);
    }
  }

  private clearHover(): void {
    if (this.hoverTileValue !== null) {
      this.hoverTileValue = null;
      this.hooks.onHoverChange?.(null);
    }
  }

  /** Picks the entity whose body is under the cursor; the frontmost (largest y) wins ties. */
  private pickEntity(screenX: number, screenY: number): number | null {
    const world = this.hooks.getWorld();
    const tilePixels = this.camera.effectiveTileSize;
    let bestId: number | null = null;
    let bestY = Number.NEGATIVE_INFINITY;
    let bestDistance = Number.POSITIVE_INFINITY;

    for (const entity of world.entities) {
      const building = isBuildingKind(entity.kind);
      const point = this.camera.worldToScreen(
        entity.x * this.camera.tileSize,
        entity.y * this.camera.tileSize,
      );
      const bodyY = point.y - tilePixels * (building ? 0.55 : 0.3);
      const radius = Math.max(7, tilePixels * (building ? 0.85 : 0.42));
      const distance = Math.hypot(point.x - screenX, bodyY - screenY);
      if (distance > radius) {
        continue;
      }
      if (entity.y > bestY || (entity.y === bestY && distance < bestDistance)) {
        bestId = entity.id;
        bestY = entity.y;
        bestDistance = distance;
      }
    }
    return bestId;
  }

  private setSelection(id: number | null): void {
    this.hooks.onSelectionChange(id);
  }

  private zoomAtCenter(factor: number): void {
    this.camera.zoomAt(this.camera.viewportWidth / 2, this.camera.viewportHeight / 2, factor);
    if (this.hoverTileValue !== null) {
      this.updateHover(this.pointerX, this.pointerY);
    }
    this.hooks.onCameraChange?.();
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
  }

  private readonly handlePointerDown = (event: PointerEvent): void => {
    if (!event.isPrimary) {
      return;
    }
    if (event.button === 2) {
      // The right button never starts a drag; it only aims an order. Focus the canvas so the keyboard
      // shortcuts (including Escape) keep working after the click.
      const point = this.localPoint(event);
      this.pointerX = point.x;
      this.pointerY = point.y;
      this.updateHover(point.x, point.y);
      this.canvas.focus({ preventScroll: true });
      event.preventDefault();
      return;
    }
    if (event.button !== 0) {
      return;
    }
    const point = this.localPoint(event);
    this.pointerX = point.x;
    this.pointerY = point.y;
    this.activePointerId = event.pointerId;
    this.pointerActive = true;
    this.dragging = false;
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
    if (!this.dragging && totalDistance >= DRAG_THRESHOLD_PX) {
      this.dragging = true;
      this.camera.pan(-(point.x - this.downX), -(point.y - this.downY));
      this.hooks.onCameraChange?.();
    } else if (this.dragging) {
      this.camera.pan(-(point.x - this.lastX), -(point.y - this.lastY));
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
      this.emitRightClick(point.x, point.y);
      event.preventDefault();
      return;
    }
    if (event.pointerId !== this.activePointerId) {
      return;
    }
    const point = this.localPoint(event);
    this.updateHover(point.x, point.y);
    const wasDragging = this.dragging;
    this.releasePointer();
    if (!wasDragging) {
      const targetId = this.pickEntity(point.x, point.y);
      const tile = this.hoverTileValue;
      const consumed = tile !== null && this.hooks.onLeftClick?.(tile, targetId) === true;
      if (!consumed) {
        this.setSelection(targetId);
      }
    }
  };

  /** Emits a right click with its tile and the entity body under the cursor, if the tile is on the map. */
  private emitRightClick(screenX: number, screenY: number): void {
    const tile = this.hoverTileValue;
    if (tile === null) {
      return;
    }
    this.hooks.onRightClick?.(tile, this.pickEntity(screenX, screenY));
  }

  private readonly handleContextMenu = (event: MouseEvent): void => {
    // The canvas uses the right button for orders, so the browser menu must not open over it.
    event.preventDefault();
  };

  private readonly handlePointerCancel = (event: PointerEvent): void => {
    if (event.pointerId !== this.activePointerId) {
      return;
    }
    this.releasePointer();
    this.clearHover();
  };

  private readonly handlePointerLeave = (): void => {
    if (this.pointerActive) {
      this.releasePointer();
    }
    this.clearHover();
  };

  private readonly handleLostPointerCapture = (): void => {
    if (!this.pointerActive) {
      return;
    }
    this.pointerActive = false;
    this.dragging = false;
    this.activePointerId = null;
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
    if (event.ctrlKey || event.metaKey || event.altKey) {
      return;
    }
    const key = event.key.toLowerCase();
    if (key === "escape") {
      event.preventDefault();
      if (this.hooks.onCancel?.() === true) {
        return;
      }
      this.setSelection(null);
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
